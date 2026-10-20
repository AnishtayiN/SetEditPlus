package com.seteditplus.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.seteditplus.BuildConfig
import com.seteditplus.engine.EngineServiceImpl
import com.seteditplus.engine.IEngineService
import com.seteditplus.priv.ShellResult
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * پل Shizuku / Shizuku+ / Sui.
 * به‌جای فراخوانی مخفی newProcess (که در API 13 خصوصی شده)، یک UserService واقعی بالا می‌آوریم
 * که همان EngineServiceImpl را در پروسهٔ ممتاز شیزوکو اجرا می‌کند.
 */
object ShizukuBridge {
    private const val TAG = "ShizukuBridge"

    const val PKG_SHIZUKU = "moe.shizuku.privileged.api"
    const val PKG_SHIZUKU_PLUS = "af.shizuku.plus.api"

    @Volatile
    var isSui = false

    @Volatile
    private var service: IEngineService? = null
    private var latch = CountDownLatch(1)
    private var binding = false

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, EngineServiceImpl::class.java.name))
            .daemon(false)
            .processNameSuffix("engine")
            .debuggable(BuildConfig.DEBUG)
            .version(EngineServiceImpl.VERSION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IEngineService.Stub.asInterface(binder)
            }
            binding = false
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
        }
    }

    data class Variant(val packageName: String, val label: String, val versionName: String?)

    fun installedVariants(context: Context): List<Variant> {
        val out = mutableListOf<Variant>()
        for ((pkg, label) in listOf(PKG_SHIZUKU to "Shizuku", PKG_SHIZUKU_PLUS to "Shizuku+")) {
            try {
                val info = context.packageManager.getPackageInfo(pkg, 0)
                out.add(Variant(pkg, label, info.versionName))
            } catch (_: Exception) {}
        }
        return out
    }

    fun isRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        isRunning() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun isReady(): Boolean = hasPermission()

    fun serverUid(): Int = try { Shizuku.getUid() } catch (_: Throwable) { -1 }
    fun serverVersion(): Int = try { Shizuku.getVersion() } catch (_: Throwable) { -1 }

    /** اتصال به UserService (غیرمسدودکننده) */
    fun bind() {
        if (!hasPermission() || service?.asBinder()?.pingBinder() == true || binding) return
        binding = true
        latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                Shizuku.bindUserService(args, connection)
            } catch (t: Throwable) {
                Log.w(TAG, "bindUserService failed", t)
                binding = false
                latch.countDown()
            }
        }
    }

    fun unbind() {
        try { Shizuku.unbindUserService(args, connection, true) } catch (_: Throwable) {}
        service = null
    }

    private fun awaitService(timeoutMs: Long): IEngineService? {
        service?.let { if (it.asBinder().pingBinder()) return it }
        if (Looper.myLooper() == Looper.getMainLooper()) { bind(); return null }
        bind()
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return service
    }

    fun exec(command: String, timeoutMs: Long = 30_000): ShellResult {
        val s = awaitService(8000) ?: return legacyExec(command)
        return try {
            val b = s.exec(command, timeoutMs)
            ShellResult(
                b.getInt(EngineServiceImpl.KEY_EXIT, -1),
                b.getString(EngineServiceImpl.KEY_OUT) ?: "",
                b.getString(EngineServiceImpl.KEY_ERR) ?: ""
            )
        } catch (e: Throwable) {
            service = null
            legacyExec(command)
        }
    }

    /** مسیر قدیمی (newProcess از طریق reflection) — فقط به‌عنوان پشتیبان */
    private fun legacyExec(command: String): ShellResult {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            var err = ""
            val t = Thread { err = runCatching { p.errorStream.bufferedReader().readText() }.getOrDefault("") }
            t.start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            t.join(2000)
            ShellResult(code, out, err)
        } catch (e: Throwable) {
            ShellResult.fail("Shizuku: ${e.message}")
        }
    }
}

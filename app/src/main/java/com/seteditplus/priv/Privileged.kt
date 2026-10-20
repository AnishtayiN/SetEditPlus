package com.seteditplus.priv

import com.seteditplus.adb.AdbEngine
import com.seteditplus.engine.EngineClient
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.shizuku.ShizukuBridge
import java.util.concurrent.TimeUnit

/**
 * نقطهٔ واحد اجرای فرمان ممتاز. بهترین مسیر موجود را خودکار انتخاب می‌کند:
 * موتور داخلی ← Shizuku/Shizuku+/Sui ← Root ← اتصال مستقیم ADB
 */
object Privileged {

    /** روت فقط وقتی استفاده می‌شود که قبلاً تأیید شده باشد (تا پنجرهٔ su بی‌جهت باز نشود) */
    @Volatile
    var rootGranted = false

    fun best(): BackendType? = when {
        EngineClient.isAlive() -> BackendType.ENGINE
        ShizukuBridge.isReady() -> BackendType.SHIZUKU
        rootGranted -> BackendType.ROOT
        AdbEngine.isConnected() -> BackendType.ADB
        else -> null
    }

    fun isAvailable(): Boolean = best() != null

    fun exec(command: String, timeoutMs: Long = 30_000, prefer: BackendType? = null, log: LogCategory? = null): ShellResult {
        val backend = prefer?.takeIf { isUsable(it) } ?: best()
        val result = when (backend) {
            BackendType.ENGINE -> EngineClient.exec(command, timeoutMs)
            BackendType.SHIZUKU -> ShizukuBridge.exec(command, timeoutMs)
            BackendType.ROOT -> execRoot(command, timeoutMs)
            BackendType.ADB -> AdbEngine.exec(command)
            null -> ShellResult.fail("هیچ دسترسی ممتازی فعال نیست. موتور داخلی، Shizuku یا روت را فعال کن.")
        }
        if (log != null) ActivityLog.add(log, command.take(120), result.text.take(500), result.ok)
        return result
    }

    fun isUsable(type: BackendType): Boolean = when (type) {
        BackendType.ENGINE -> EngineClient.isAlive()
        BackendType.SHIZUKU -> ShizukuBridge.isReady()
        BackendType.ROOT -> rootGranted
        BackendType.ADB -> AdbEngine.isConnected()
    }

    fun execRoot(command: String, timeoutMs: Long = 30_000): ShellResult = runProcess(arrayOf("su", "-c", command), timeoutMs)

    fun runProcess(cmd: Array<String>, timeoutMs: Long): ShellResult {
        return try {
            val p = Runtime.getRuntime().exec(cmd)
            p.outputStream.close()
            var out = ""
            var err = ""
            val t1 = Thread { out = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("") }
            val t2 = Thread { err = runCatching { p.errorStream.bufferedReader().readText() }.getOrDefault("") }
            t1.start(); t2.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) p.destroy()
            t1.join(2000); t2.join(2000)
            ShellResult(if (done) p.exitValue() else -1, out, err)
        } catch (e: Throwable) {
            ShellResult.fail(e.toString())
        }
    }

    /** نقل‌قول امن برای sh */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}

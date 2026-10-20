package com.seteditplus.adb

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.ShellResult
import io.github.muntashirakon.adb.android.AdbMdns
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** عملیات سطح بالای ADB بی‌سیم: جفت‌سازی، اتصال، اجرای فرمان. */
object AdbEngine {
    private const val TAG = "AdbEngine"
    private const val LOCAL = "127.0.0.1"
    private const val EXIT_MARK = "__SE_EXIT__"

    val pairingSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isConnected(): Boolean = try { SetEditAdbManager.peek()?.isConnected == true } catch (_: Throwable) { false }

    fun isWirelessDebuggingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1

    fun isDeveloperOptionsOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /** جفت‌سازی با کد ۶ رقمی */
    fun pair(context: Context, port: Int, code: String): Result<Unit> = runCatching {
        SetEditAdbManager.get(context).pair(LOCAL, port, code.trim())
        ActivityLog.add(LogCategory.ENGINE, "جفت‌سازی ADB بی‌سیم موفق", "پورت $port")
        Unit
    }.onFailure {
        Log.w(TAG, "pair failed", it)
        ActivityLog.add(LogCategory.ENGINE, "جفت‌سازی ناموفق", it.message ?: it.toString(), false)
    }

    /** پیدا کردن پورت یک سرویس mDNS مربوط به adb (pairing یا connect) */
    fun discoverPort(context: Context, serviceType: String, timeoutMs: Long): Int {
        val port = AtomicInteger(-1)
        val latch = CountDownLatch(1)
        val mdns = AdbMdns(context, serviceType) { _, p ->
            if (p > 0) { port.set(p); latch.countDown() }
        }
        mdns.start()
        try { latch.await(timeoutMs, TimeUnit.MILLISECONDS) } finally { runCatching { mdns.stop() } }
        return port.get()
    }

    /** اتصال: پورت دستی ← کشف mDNS (TLS) ← پورت کلاسیک 5555 */
    fun connect(context: Context, manualPort: Int? = null, timeoutMs: Long = 12_000): Result<Unit> = runCatching {
        val m = SetEditAdbManager.get(context)
        if (m.isConnected) return@runCatching
        var lastError: Throwable? = null
        val ports = mutableListOf<Int>()
        if (manualPort != null && manualPort > 0) ports.add(manualPort)
        if (ports.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val p = discoverPort(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, timeoutMs)
            if (p > 0) ports.add(p)
        }
        ports.add(5555)
        for (p in ports) {
            try {
                if (m.connect(LOCAL, p)) {
                    ActivityLog.add(LogCategory.ENGINE, "اتصال ADB برقرار شد", "پورت $p")
                    return@runCatching
                }
                if (m.isConnected) return@runCatching
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("پورت ADB پیدا نشد. «اشکال‌زدایی بی‌سیم» روشن است؟")
    }.onFailure { Log.w(TAG, "connect failed", it) }

    fun disconnect() {
        runCatching { SetEditAdbManager.peek()?.disconnect() }
    }

    /** اجرای فرمان از طریق سرویس shell: در adbd (UID 2000) */
    fun exec(command: String): ShellResult {
        val m = SetEditAdbManager.peek() ?: return ShellResult.fail("ADB متصل نیست")
        if (!m.isConnected) return ShellResult.fail("ADB متصل نیست")
        return try {
            m.openStream("shell:{ $command ; } 2>&1; echo $EXIT_MARK\$?").use { stream ->
                val raw = stream.openInputStream().use { it.readBytes() }.toString(Charsets.UTF_8)
                val idx = raw.lastIndexOf(EXIT_MARK)
                if (idx >= 0) {
                    val code = raw.substring(idx + EXIT_MARK.length).trim().toIntOrNull() ?: 0
                    ShellResult(code, raw.substring(0, idx), "")
                } else ShellResult(0, raw, "")
            }
        } catch (t: Throwable) {
            ShellResult.fail("ADB: ${t.message}")
        }
    }
}

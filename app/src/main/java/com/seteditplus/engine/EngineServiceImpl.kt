package com.seteditplus.engine

import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.Process
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * پیاده‌سازی سرویس ممتاز. این کلاس داخل یک پروسهٔ app_process با UID شل (2000) یا روت (0) اجرا می‌شود:
 *  - سرور داخلی SetEdit+ (EngineServer) — بدون نیاز به هیچ اپ دیگری
 *  - UserService شیزوکو / شیزوکو+ / Sui
 *
 * هیچ وابستگی‌ای به Context ندارد چون در پروسهٔ سیستمی بدون Application اجرا می‌شود.
 */
class EngineServiceImpl() : IEngineService.Stub() {

    /** شیزوکو اگر سازندهٔ Context-دار باشد از آن استفاده می‌کند. */
    @Suppress("UNUSED_PARAMETER")
    constructor(context: android.content.Context) : this()

    companion object {
        const val VERSION = 3
        /** حداکثر حجم خروجی (برای جلوگیری از TransactionTooLargeException) */
        private const val MAX_OUTPUT = 900 * 1024

        /** فقط این UID اجازهٔ صدا زدن دارد (-1 = بدون محدودیت، برای حالت UserService که شیزوکو خودش محافظت می‌کند) */
        @Volatile
        @JvmStatic
        var allowedUid: Int = -1

        const val KEY_EXIT = "exit"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"
        const val KEY_TIMEOUT = "timeout"
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val caller = Binder.getCallingUid()
        val allowed = allowedUid
        if (allowed >= 0 && caller != allowed && caller != 0 && caller != 2000 && caller != Process.myUid()) {
            throw SecurityException("UID $caller is not allowed to use SetEdit+ engine")
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun getPid(): Int = Process.myPid()

    override fun getVersion(): Int = VERSION

    override fun getSELinuxContext(): String = try {
        File("/proc/self/attr/current").readText().trim().trimEnd('\u0000')
    } catch (e: Throwable) {
        "unknown"
    }

    override fun exec(command: String, timeoutMs: Long): Bundle {
        val result = Bundle()
        try {
            val process = ProcessBuilder("sh", "-c", command).start()
            process.outputStream.close()
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread { readCapped(process.inputStream, out) }
            val tErr = Thread { readCapped(process.errorStream, err) }
            tOut.start(); tErr.start()
            val finished = if (timeoutMs > 0) {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                process.waitFor(); true
            }
            if (!finished) {
                process.destroy()
                result.putBoolean(KEY_TIMEOUT, true)
            }
            tOut.join(2000); tErr.join(2000)
            result.putInt(KEY_EXIT, if (finished) process.exitValue() else -1)
            result.putString(KEY_OUT, out.toString())
            result.putString(KEY_ERR, err.toString())
        } catch (e: Throwable) {
            result.putInt(KEY_EXIT, -1)
            result.putString(KEY_OUT, "")
            result.putString(KEY_ERR, e.toString())
        }
        return result
    }

    private fun readCapped(input: InputStream, sb: StringBuilder) {
        try {
            input.bufferedReader().use { reader ->
                val buf = CharArray(8192)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    if (sb.length < MAX_OUTPUT) {
                        sb.append(buf, 0, minOf(n, MAX_OUTPUT - sb.length))
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}

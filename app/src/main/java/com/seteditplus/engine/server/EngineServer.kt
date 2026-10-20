package com.seteditplus.engine.server

import android.os.Looper
import android.util.Log
import com.seteditplus.engine.EngineServiceImpl
import java.io.File
import kotlin.system.exitProcess

/**
 * سرور ممتاز داخلی SetEdit+ — معادل سرور Shizuku، ولی داخل خود APK.
 *
 * با این فرمان (از طریق ADB بی‌سیم یا su) اجرا می‌شود:
 *   app_process -Djava.class.path=<apk> /system/bin --nice-name=seteditplus_engine \
 *       com.seteditplus.engine.server.EngineServer --pkg=com.seteditplus --uid=<uid> --user=<user> --apk=<apk>
 *
 * کار سرور:
 *  ۱) یک Binder از EngineServiceImpl می‌سازد (UID = shell یا root)
 *  ۲) آن را از طریق ContentProvider به اپ تحویل می‌دهد (مثل Shizuku)
 *  ۳) هر بار اپ دوباره باز شد (PID جدید) دوباره Binder را می‌فرستد
 *  ۴) اگر APK حذف/آپدیت شد، خودش خارج می‌شود
 */
object EngineServer {

    private const val TAG = "SetEditEngine"
    const val NICE_NAME = "setedit_engine"

    @JvmStatic
    fun main(args: Array<String>) {
        var pkg = "com.seteditplus"
        var uid = -1
        var user = 0
        var apk: String? = null
        for (a in args) {
            when {
                a.startsWith("--pkg=") -> pkg = a.substringAfter('=')
                a.startsWith("--uid=") -> uid = a.substringAfter('=').toIntOrNull() ?: -1
                a.startsWith("--user=") -> user = a.substringAfter('=').toIntOrNull() ?: 0
                a.startsWith("--apk=") -> apk = a.substringAfter('=')
            }
        }
        Log.i(TAG, "starting: pkg=$pkg uid=$uid user=$user myUid=${android.os.Process.myUid()}")

        @Suppress("DEPRECATION")
        Looper.prepareMainLooper()

        EngineServiceImpl.allowedUid = uid
        val service = EngineServiceImpl()
        val authority = "$pkg.engine"

        val watcher = Thread({
            var lastPid = -1
            var failures = 0
            while (true) {
                try {
                    val apkPath = apk
                    if (apkPath != null && !File(apkPath).exists()) {
                        Log.i(TAG, "apk removed/updated, exiting")
                        exitProcess(0)
                    }
                    val pid = ProcessFinder.findPid(pkg, lastPid)
                    if (pid > 0 && pid != lastPid) {
                        val ok = BinderDelivery.send(authority, user, service)
                        if (ok) {
                            Log.i(TAG, "binder delivered to pid $pid")
                            lastPid = pid
                            failures = 0
                        } else if (++failures > 5) {
                            // جلوگیری از اسپم؛ بعداً دوباره تلاش می‌شود
                            lastPid = pid
                            failures = 0
                        }
                    } else if (pid <= 0) {
                        lastPid = -1
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "watcher error", t)
                }
                try {
                    Thread.sleep(1500)
                } catch (_: InterruptedException) {
                }
            }
        }, "engine-watcher")
        watcher.isDaemon = true
        watcher.start()

        Looper.loop()
        exitProcess(0)
    }
}

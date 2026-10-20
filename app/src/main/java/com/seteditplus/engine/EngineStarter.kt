package com.seteditplus.engine

import android.content.Context
import android.os.Process
import com.seteditplus.adb.AdbEngine
import com.seteditplus.engine.server.EngineServer
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.Privileged
import com.seteditplus.priv.ShellResult

/** ساخت و اجرای فرمان راه‌اندازی سرور داخلی (معادل starter شیزوکو). */
object EngineStarter {

    fun command(context: Context): String {
        val apk = context.applicationInfo.sourceDir
        val uid = Process.myUid()
        val user = uid / 100000
        val pkg = context.packageName
        val nice = EngineServer.NICE_NAME
        val main = EngineServer::class.java.name
        return buildString {
            append("for p in $(pidof $nice 2>/dev/null); do kill \$p; done; ")
            append("S=''; command -v setsid >/dev/null 2>&1 && S=setsid; ")
            append("\$S nohup app_process -Djava.class.path=${Privileged.q(apk)} /system/bin --nice-name=$nice ")
            append("$main --pkg=$pkg --uid=$uid --user=$user --apk=${Privileged.q(apk)} >/dev/null 2>&1 & ")
            append("echo SETEDIT_ENGINE_STARTED")
        }
    }

    /** فرمانی که کاربر می‌تواند از کامپیوتر اجرا کند (adb shell ...) */
    fun adbPcCommand(context: Context): String = "adb shell \"" + command(context).replace("\"", "\\\"").replace("\$", "\\\$") + "\""

    /** راه‌اندازی از طریق اتصال ADB (بی‌سیم) */
    fun startViaAdb(context: Context): ShellResult {
        val r = AdbEngine.exec(command(context))
        ActivityLog.add(LogCategory.ENGINE, "راه‌اندازی موتور از طریق ADB", r.text, r.out.contains("SETEDIT_ENGINE_STARTED"))
        return r
    }

    /** راه‌اندازی با روت — سرور با UID 0 اجرا می‌شود */
    fun startViaRoot(context: Context): ShellResult {
        val r = Privileged.execRoot(command(context), 20_000)
        if (r.out.contains("SETEDIT_ENGINE_STARTED")) Privileged.rootGranted = true
        ActivityLog.add(LogCategory.ENGINE, "راه‌اندازی موتور با روت", r.text, r.out.contains("SETEDIT_ENGINE_STARTED"))
        return r
    }

    /** راه‌اندازی از طریق Shizuku (وقتی شیزوکو هست ولی می‌خواهی موتور مستقل داشته باشی) */
    fun startViaShizuku(context: Context): ShellResult {
        val r = com.seteditplus.shizuku.ShizukuBridge.exec(command(context), 20_000)
        ActivityLog.add(LogCategory.ENGINE, "راه‌اندازی موتور از طریق Shizuku", r.text, r.out.contains("SETEDIT_ENGINE_STARTED"))
        return r
    }

    /** به اپ خودمان WRITE_SECURE_SETTINGS می‌دهد تا «حالت مستقیم» دائمی (حتی بعد از ریبوت) کار کند */
    fun selfGrant(context: Context): ShellResult {
        val pkg = context.packageName
        val cmd = "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; " +
            "pm grant $pkg android.permission.DUMP 2>/dev/null; " +
            "pm grant $pkg android.permission.READ_LOGS 2>/dev/null; " +
            "appops set $pkg GET_USAGE_STATS allow 2>/dev/null; true"
        val r = Privileged.exec(cmd)
        ActivityLog.add(LogCategory.ENGINE, "اعطای WRITE_SECURE_SETTINGS به خود اپ", r.text, r.ok)
        return r
    }
}

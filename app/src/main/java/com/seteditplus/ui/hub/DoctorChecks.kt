package com.seteditplus.ui.hub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.seteditplus.adb.AdbEngine
import com.seteditplus.data.DirectSettings
import com.seteditplus.data.ProviderBridge
import com.seteditplus.engine.EngineClient
import com.seteditplus.priv.Privileged
import com.seteditplus.shizuku.ShizukuBridge

enum class FixAction { NONE, OPEN_DEV, ENABLE_DEV, ENABLE_WIFI_DBG, NOTIF, BATTERY, ENGINE, GRANT, SHIZUKU_PERM, OPEN_SHIZUKU }

data class Check(val title: String, val ok: Boolean?, val detail: String, val fix: FixAction = FixAction.NONE, val fixLabel: String = "")

/** Adaptive Service Doctor — تشخیص پیش‌نیازها و پیشنهاد رفع */
object DoctorChecks {

    fun run(ctx: Context): List<Check> {
        val out = mutableListOf<Check>()
        val direct = DirectSettings.isGranted(ctx)
        val dev = AdbEngine.isDeveloperOptionsOn(ctx)
        val wifiDbg = AdbEngine.isWirelessDebuggingOn(ctx)

        out += Check("نسخهٔ اندروید", true, "API ${Build.VERSION.SDK_INT} • ${Build.MANUFACTURER} ${Build.MODEL}" +
            if (AdbEngine.pairingSupported) "" else " — جفت‌سازی بی‌سیم نیاز به اندروید ۱۱+ دارد")
        out += Check("موتور داخلی", EngineClient.isAlive(),
            if (EngineClient.isAlive()) "فعال • ${EngineClient.state.value.identity}" else "خاموش",
            if (EngineClient.isAlive()) FixAction.NONE else FixAction.ENGINE, "راه‌اندازی")
        out += Check("WRITE_SECURE_SETTINGS برای SetEdit+", direct,
            if (direct) "اعطا شده — حالت مستقیم و شروع خودکار ممکن است" else "اعطا نشده",
            if (direct || !Privileged.isAvailable()) FixAction.NONE else FixAction.GRANT, "اعطا")
        out += Check("گزینه‌های توسعه‌دهنده", dev, if (dev) "روشن" else "خاموش",
            if (dev) FixAction.NONE else if (direct) FixAction.ENABLE_DEV else FixAction.OPEN_DEV, if (direct) "روشن کن" else "باز کردن")
        out += Check("اشکال‌زدایی بی‌سیم", wifiDbg, if (wifiDbg) "روشن" else "خاموش — موتور با ADB بی‌سیم به آن نیاز دارد",
            if (wifiDbg) FixAction.NONE else if (direct) FixAction.ENABLE_WIFI_DBG else FixAction.OPEN_DEV, if (direct) "روشن کن" else "باز کردن")
        out += Check("Wi-Fi", isWifi(ctx), if (isWifi(ctx)) "متصل" else "اشکال‌زدایی بی‌سیم فقط روی Wi-Fi کار می‌کند")
        out += Check("اتصال ADB داخلی", AdbEngine.isConnected(), if (AdbEngine.isConnected()) "متصل" else "متصل نیست (بعد از استارت موتور لازم نیست)")
        if (Build.VERSION.SDK_INT >= 33) {
            val n = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            out += Check("اجازهٔ اعلان", n, if (n) "داده شده" else "برای وارد کردن کد جفت‌سازی از اعلان لازم است",
                if (n) FixAction.NONE else FixAction.NOTIF, "اجازه بده")
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        out += Check("معافیت از بهینه‌سازی باتری", ignoring,
            if (ignoring) "معاف" else "برخی سازنده‌ها (شیائومی، سامسونگ، هواوی) سرویس پس‌زمینه را می‌کشند",
            if (ignoring) FixAction.NONE else FixAction.BATTERY, "معاف کن")

        val variants = ShizukuBridge.installedVariants(ctx)
        out += Check("Shizuku / Shizuku+ نصب", if (variants.isEmpty()) null else true,
            if (variants.isEmpty()) "نصب نیست (با موتور داخلی لازم نیست)" else variants.joinToString { "${it.label} ${it.versionName ?: ""}" })
        if (variants.isNotEmpty() || ShizukuBridge.isSui) {
            val running = ShizukuBridge.isRunning()
            out += Check("سرویس Shizuku", running,
                if (running) "در حال اجرا • v${ShizukuBridge.serverVersion()} • uid ${ShizukuBridge.serverUid()}" + (if (ShizukuBridge.isSui) " • Sui" else "")
                else "اجرا نمی‌شود", if (running) FixAction.NONE else FixAction.OPEN_SHIZUKU, "باز کردن Shizuku")
            if (running) {
                val perm = ShizukuBridge.hasPermission()
                out += Check("مجوز Shizuku", perm, if (perm) "داده شده" else "داده نشده",
                    if (perm) FixAction.NONE else FixAction.SHIZUKU_PERM, "درخواست")
            }
        }
        out += Check("روت", if (Privileged.rootGranted) true else null, if (Privileged.rootGranted) "تأیید شده" else "تأیید نشده / ناموجود")
        val bridge = ProviderBridge.isAvailable(ctx)
        out += Check("اپ پل (Settings Database Provider)", if (bridge) true else null, if (bridge) "آماده" else "نصب نیست یا مجوز ندارد (اختیاری)")
        return out
    }

    private fun isWifi(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Throwable) { false }
}

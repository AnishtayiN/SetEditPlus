package com.seteditplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.seteditplus.shizuku.ShizukuBridge
import rikka.sui.Sui

class SetEditApp : Application() {

    companion object {
        lateinit var instance: SetEditApp
            private set
        const val CHANNEL_ENGINE = "engine"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Sui (ماژول روت شیزوکو) — اگر نصب باشد Binder را مستقیم می‌گیریم
        try { ShizukuBridge.isSui = Sui.init(packageName) } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ENGINE, "موتور داخلی SetEdit+", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "جفت‌سازی ADB بی‌سیم و وضعیت سرویس ممتاز"
                }
            )
        }
    }
}

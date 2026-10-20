package com.seteditplus.adb

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import com.seteditplus.MainActivity
import com.seteditplus.SetEditApp
import com.seteditplus.engine.EngineClient
import com.seteditplus.engine.EngineStarter
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * سرویس پیش‌زمینه برای:
 *  - جفت‌سازی ADB بی‌سیم با وارد کردن کد از داخل اعلان (مثل Shizuku)
 *  - اتصال + راه‌اندازی موتور (دستی یا هنگام بوت)
 */
class EngineForegroundService : Service() {

    companion object {
        const val ACTION_PAIR = "com.seteditplus.action.PAIR"
        const val ACTION_PAIR_REPLY = "com.seteditplus.action.PAIR_REPLY"
        const val ACTION_START_ENGINE = "com.seteditplus.action.START_ENGINE"
        const val ACTION_BOOT_START = "com.seteditplus.action.BOOT_START"
        const val ACTION_STOP = "com.seteditplus.action.STOP"
        private const val KEY_CODE = "pairing_code"
        private const val NOTIF_ID = 1201

        fun start(context: Context, action: String) {
            val i = Intent(context, EngineForegroundService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mdns: AdbMdns? = null

    @Volatile
    private var pairingPort = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(build("موتور داخلی SetEdit+", "در حال آماده‌سازی…", ongoing = true))
        when (intent?.action) {
            ACTION_PAIR -> startPairingDiscovery()
            ACTION_PAIR_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()
                if (code.isNullOrBlank() || pairingPort <= 0) {
                    notify(build("کد نامعتبر", "دوباره «جفت‌سازی دستگاه با کد» را باز کن", ongoing = false))
                } else doPair(pairingPort, code)
            }
            ACTION_START_ENGINE -> scope.launch { connectAndStart(boot = false) }
            ACTION_BOOT_START -> scope.launch { connectAndStart(boot = true) }
            ACTION_STOP -> finish()
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun startPairingDiscovery() {
        if (!AdbEngine.pairingSupported) {
            notify(build("پشتیبانی نمی‌شود", "جفت‌سازی بی‌سیم به اندروید ۱۱ یا بالاتر نیاز دارد", ongoing = false))
            stopLater(); return
        }
        notify(
            build(
                "در جستجوی سرویس جفت‌سازی…",
                "تنظیمات توسعه‌دهنده ← اشکال‌زدایی بی‌سیم ← «جفت‌سازی دستگاه با کد» را باز کن",
                ongoing = true, withStop = true
            )
        )
        mdns?.stop()
        val m = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
            if (port > 0 && port != pairingPort) {
                pairingPort = port
                notify(buildCodeInput(port))
            }
        }
        mdns = m
        m.start()
        // بعد از ۱۰ دقیقه بی‌نتیجه، خودکار بسته شود
        scope.launch { delay(10 * 60_000L); if (!EngineClient.isAlive()) finish() }
    }

    private fun doPair(port: Int, code: String) {
        notify(build("در حال جفت‌سازی…", "پورت $port", ongoing = true))
        scope.launch {
            val r = AdbEngine.pair(this@EngineForegroundService, port, code)
            if (r.isSuccess) {
                mdns?.stop()
                notify(build("جفت‌سازی موفق ✓", "در حال اتصال و راه‌اندازی موتور…", ongoing = true))
                delay(1500)
                connectAndStart(boot = false)
            } else {
                notify(
                    build(
                        "جفت‌سازی ناموفق",
                        (r.exceptionOrNull()?.message ?: "کد اشتباه است یا منقضی شده") + " — دوباره امتحان کن",
                        ongoing = false
                    )
                )
                pairingPort = -1
            }
        }
    }

    private suspend fun connectAndStart(boot: Boolean) {
        if (boot) {
            notify(build("راه‌اندازی خودکار موتور", "روشن کردن اشکال‌زدایی بی‌سیم…", ongoing = true))
            try {
                Settings.Global.putInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            } catch (t: Throwable) {
                ActivityLog.add(LogCategory.ENGINE, "روشن کردن اشکال‌زدایی بی‌سیم ممکن نشد", t.message ?: "", false)
            }
            delay(4000)
        } else {
            notify(build("در حال اتصال به ADB…", "", ongoing = true))
        }
        var connected = false
        val attempts = if (boot) 6 else 2
        for (i in 0 until attempts) {
            if (AdbEngine.connect(this, null, 10_000).isSuccess) { connected = true; break }
            delay(3000)
        }
        if (!connected) {
            notify(build("اتصال ADB برقرار نشد", "اشکال‌زدایی بی‌سیم روشن و Wi-Fi وصل است؟ یک بار جفت‌سازی کرده‌ای؟", ongoing = false))
            stopLater(); return
        }
        notify(build("در حال راه‌اندازی موتور…", "", ongoing = true))
        EngineStarter.startViaAdb(this)
        val alive = EngineClient.awaitAlive(10_000)
        if (alive) {
            // خود اپ هم WRITE_SECURE_SETTINGS بگیرد تا حالت مستقیم همیشه کار کند
            EngineStarter.selfGrant(this)
            val st = EngineClient.state.value
            if (boot) {
                val prefs = com.seteditplus.data.PreferencesRepository(this)
                val mode = prefs.getPermissionMode()
                if (prefs.isAutostartEnabled() && mode != null) com.seteditplus.receiver.BootApplier.apply(this, mode)
            }
            notify(build("موتور فعال است ✓", "UID: ${st.identity} • PID: ${st.pid}", ongoing = false))
        } else {
            notify(build("موتور پاسخ نداد", "از صفحهٔ موتور داخلی، «عیب‌یاب» را اجرا کن", ongoing = false))
        }
        stopLater()
    }

    private fun stopLater() {
        scope.launch { delay(4000); finish() }
    }

    private fun finish() {
        mdns?.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        mdns?.stop()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- اعلان‌ها ----------

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "ENGINE")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(title: String, text: String, ongoing: Boolean, withStop: Boolean = false): Notification {
        val b = NotificationCompat.Builder(this, SetEditApp.CHANNEL_ENGINE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (withStop) {
            val stop = PendingIntent.getService(
                this, 2, Intent(this, EngineForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(0, "توقف", stop)
        }
        return b.build()
    }

    private fun buildCodeInput(port: Int): Notification {
        val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel("کد ۶ رقمی جفت‌سازی").build()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val reply = PendingIntent.getForegroundService(
            this, 1, Intent(this, EngineForegroundService::class.java).setAction(ACTION_PAIR_REPLY), flags
        )
        val action = NotificationCompat.Action.Builder(0, "وارد کردن کد", reply)
            .addRemoteInput(remoteInput).setAllowGeneratedReplies(false).build()
        return NotificationCompat.Builder(this, SetEditApp.CHANNEL_ENGINE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("سرویس جفت‌سازی پیدا شد (پورت $port)")
            .setContentText("«وارد کردن کد» را بزن و کد ۶ رقمی را بنویس")
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(action)
            .setContentIntent(openAppIntent())
            .build()
    }

    @Suppress("MissingPermission")
    private fun notify(n: Notification) {
        try { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

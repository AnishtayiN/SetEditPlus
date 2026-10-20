package com.seteditplus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.seteditplus.adb.EngineForegroundService
import com.seteditplus.data.DirectSettings
import com.seteditplus.data.PermissionChecker
import com.seteditplus.data.PermissionMode
import com.seteditplus.data.PreferencesRepository
import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.Privileged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return // منتظر باز شدن قفل می‌مانیم
        val prefs = PreferencesRepository(context)
        Privileged.rootGranted = prefs.isRootConfirmed()

        // ۱) شروع خودکار موتور داخلی (نیازمند WRITE_SECURE_SETTINGS برای روشن کردن اشکال‌زدایی بی‌سیم)
        if (prefs.isEngineAutostart() && DirectSettings.isGranted(context)) {
            try {
                EngineForegroundService.start(context, EngineForegroundService.ACTION_BOOT_START)
            } catch (t: Throwable) {
                Log.e("SetEditPlusBoot", "engine boot start failed", t)
            }
        }

        // ۲) بازگردانی تنظیمات تغییر‌یافته
        if (!prefs.isAutostartEnabled()) return
        val mode = prefs.getPermissionMode() ?: return
        if (mode == PermissionMode.ENGINE) return // بعد از بالا آمدن موتور، سرویس پیش‌زمینه اعمال می‌کند

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BootApplier.apply(context, mode)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object BootApplier {
    fun apply(context: Context, mode: PermissionMode) {
        val prefs = PreferencesRepository(context)
        val available = when (mode) {
            PermissionMode.ROOT -> PermissionChecker.isRootAvailable()
            PermissionMode.SHIZUKU -> PermissionChecker.isShizukuAvailable() && PermissionChecker.isShizukuPermissionGranted()
            PermissionMode.PROVIDER -> PermissionChecker.isProviderAvailable(context)
            PermissionMode.ENGINE -> PermissionChecker.isEngineAvailable()
            PermissionMode.DIRECT -> PermissionChecker.isDirectAvailable(context)
        }
        if (!available) {
            Log.e("SetEditPlusBoot", "Permission mode $mode not available on boot")
            ActivityLog.add(LogCategory.SYSTEM, "بازگردانی پس از بوت انجام نشد", "حالت $mode در دسترس نبود", false)
            return
        }
        val repo = SettingsRepository(context, mode)
        var ok = 0
        var fail = 0
        for (setting in prefs.getModifiedSettings()) {
            try {
                val table = SettingsTable.valueOf(setting.table.uppercase())
                val success = if (setting.isDeleted) repo.delete(table, setting.key) else repo.put(table, setting.key, setting.value)
                if (success) ok++ else fail++
            } catch (e: Exception) {
                fail++
            }
        }
        ActivityLog.add(LogCategory.SYSTEM, "بازگردانی تنظیمات پس از بوت", "موفق: $ok • ناموفق: $fail", fail == 0)
    }
}

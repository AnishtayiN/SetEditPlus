package com.seteditplus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * حالت بازی: یک پروفایل از کلیدهای system/secure/global برای یک بازی مشخص.
 * فلسفه: وقتی «شروع» می‌زنی، پروفایل اعمال می‌شود، بازی لانچ می‌شود،
 * و تا وقتی به SetEdit Plus برگردی تنظیمات فعال می‌مانند؛ برگشتی با یک کلیک بازگردانی می‌شوند.
 */
data class GamePreset(
    val id: String,
    val label: String,
    val packageName: String?,
    val tweaks: List<SettingsTableKey>
)

data class SettingsTableKey(
    val table: SettingsTable,
    val key: String,
    val value: String
)

object GameMode {

    /** کلیدهای امن و قابل‌بازگردانی؛ هیچ‌کدام در DENIED_KEYS اپ پل نیستند. */
    val PUBG: GamePreset = GamePreset(
        id = "pubg",
        label = "PUBG Mobile",
        packageName = "com.tencent.ig",
        tweaks = listOf(
            SettingsTableKey(SettingsTable.SYSTEM, "window_animation_scale", "0.0"),
            SettingsTableKey(SettingsTable.SYSTEM, "transition_animation_scale", "0.0"),
            SettingsTableKey(SettingsTable.SYSTEM, "animator_duration_scale", "0.0"),
            SettingsTableKey(SettingsTable.SYSTEM, "screen_off_timeout", "600000"),
            SettingsTableKey(SettingsTable.SYSTEM, "pointer_speed", "7"),
            SettingsTableKey(SettingsTable.GLOBAL, "wifi_scan_always_enabled", "0"),
            SettingsTableKey(SettingsTable.GLOBAL, "network_recommendations_enabled", "0"),
            SettingsTableKey(SettingsTable.GLOBAL, "bluetooth_scan_always_enabled", "0"),
            SettingsTableKey(SettingsTable.GLOBAL, "low_power_sticky", "0"),
            SettingsTableKey(SettingsTable.SECURE, "sleep_timeout", "1200000"),
            SettingsTableKey(SettingsTable.SECURE, "adaptive_sleep", "0")
        )
    )

    val GENERIC: GamePreset = GamePreset(
        id = "generic",
        label = "هر بازی (استاندارد)",
        packageName = null,
        tweaks = PUBG.tweaks
    )

    val PRESETS = listOf(GENERIC, PUBG)

    fun presetById(id: String): GamePreset? = PRESETS.firstOrNull { it.id == id }

    fun isAppInstalled(context: Context, packageName: String?): Boolean {
        if (packageName == null) return true
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchApp(context: Context, packageName: String?): Boolean {
        val pm = context.packageManager
        val intent: Intent? = if (packageName != null) {
            pm.getLaunchIntentForPackage(packageName)
        } else {
            null
        }
        return try {
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /** مقدار فعلی همهٔ کلیدهای پروفایل — برای بازگردانی دقیق. */
    fun snapshot(context: Context, repo: SettingsRepository, preset: GamePreset): Map<String, String?> {
        val out = mutableMapOf<String, String?>()
        for (t in preset.tweaks) {
            out[entryId(t)] = repo.get(t.table, t.key)
        }
        return out
    }

    fun entryId(t: SettingsTableKey): String = "${t.table.name}:${t.key}"

    /** لیست کلیدهایی که تغییرشان واقعاً ممکن بود (برای UI). */
    fun applyAll(repo: SettingsRepository, preset: GamePreset): List<Pair<SettingsTableKey, Boolean>> =
        preset.tweaks.map { it to repo.put(it.table, it.key, it.value) }

    /** بازگردانی: null یعنی کلید وجود نداشت → حذف منطقی (به مقدار پیش‌فرض سیستم واگذار). */
    fun restoreAll(repo: SettingsRepository, snapshot: Map<String, String?>, preset: GamePreset) {
        for (t in preset.tweaks) {
            val old = snapshot[entryId(t)] ?: continue
            repo.put(t.table, t.key, old)
        }
    }
}

package com.seteditplus.ui.hub

import com.seteditplus.data.SettingsTable
import com.seteditplus.data.SettingsTable.GLOBAL
import com.seteditplus.data.SettingsTable.SECURE
import com.seteditplus.data.SettingsTable.SYSTEM

/** یک ترفند: یا سوییچ (on/off) یا انتخاب از چند مقدار */
data class Tweak(
    val section: String,
    val title: String,
    val desc: String,
    val table: SettingsTable,
    val key: String,
    val on: String? = null,
    val off: String? = null,
    val choices: List<Pair<String, String>> = emptyList(),
    val companions: List<Pair<SettingsTable, String>> = emptyList()
) {
    val id get() = "${table.name}:$key"
}

private val ANIM_SCALES = listOf(
    "0" to "خاموش", "0.25" to "0.25x", "0.5" to "0.5x", "0.75" to "0.75x", "1" to "1x", "1.5" to "1.5x"
)

private val REFRESH = listOf("60" to "60Hz", "90" to "90Hz", "120" to "120Hz", "144" to "144Hz")

val TWEAKS: List<Tweak> = listOf(
    // انیمیشن
    Tweak(
        "انیمیشن و روانی", "سرعت انیمیشن‌ها", "هر سه مقیاس انیمیشن با هم",
        GLOBAL, "window_animation_scale", choices = ANIM_SCALES,
        companions = listOf(GLOBAL to "transition_animation_scale", GLOBAL to "animator_duration_scale")
    ),
    Tweak(
        "انیمیشن و روانی", "حداکثر نرخ نوسازی", "peak_refresh_rate (همهٔ گوشی‌ها پشتیبانی نمی‌کنند)",
        SYSTEM, "peak_refresh_rate", choices = REFRESH
    ),
    Tweak(
        "انیمیشن و روانی", "حداقل نرخ نوسازی", "min_refresh_rate — بالا بردن، مصرف باتری را زیاد می‌کند",
        SYSTEM, "min_refresh_rate", choices = REFRESH
    ),
    Tweak("انیمیشن و روانی", "کاهش انیمیشن‌ها", "remove_animations (دسترس‌پذیری)", GLOBAL, "remove_animations", "1", "0"),
    // پنجره‌ها
    Tweak("پنجره‌ها و چندوظیفگی", "تغییر اندازهٔ اجباری همهٔ اپ‌ها", "force_resizable_activities", GLOBAL, "force_resizable_activities", "1", "0"),
    Tweak("پنجره‌ها و چندوظیفگی", "پنجرهٔ آزاد (Freeform)", "enable_freeform_support", GLOBAL, "enable_freeform_support", "1", "0"),
    Tweak("پنجره‌ها و چندوظیفگی", "چندپنجره‌ای غیرقابل‌تغییر اندازه", "enable_non_resizable_multi_window", GLOBAL, "enable_non_resizable_multi_window", "1", "0"),
    Tweak("پنجره‌ها و چندوظیفگی", "پشتیبانی دسکتاپ روی نمایشگر دوم", "force_desktop_mode_on_external_displays", GLOBAL, "force_desktop_mode_on_external_displays", "1", "0"),
    // نمایش و لمس
    Tweak("نمایش و لمس", "نمایش لمس‌ها", "show_touches", SYSTEM, "show_touches", "1", "0"),
    Tweak("نمایش و لمس", "موقعیت اشاره‌گر", "pointer_location", SYSTEM, "pointer_location", "1", "0"),
    Tweak("نمایش و لمس", "بیدار ماندن هنگام شارژ", "stay_on_while_plugged_in (AC+USB+Wireless)", GLOBAL, "stay_on_while_plugged_in", "7", "0"),
    Tweak("نمایش و لمس", "زمان خاموشی صفحه", "screen_off_timeout", SYSTEM, "screen_off_timeout",
        choices = listOf("15000" to "15ث", "30000" to "30ث", "60000" to "1د", "300000" to "5د", "600000" to "10د", "1800000" to "30د")),
    Tweak("نمایش و لمس", "حالت تاریک سیستم", "ui_night_mode — ممکن است تا ریستارت UI اعمال نشود", SECURE, "ui_night_mode", "2", "1"),
    // باتری و شبکه
    Tweak("باتری و شبکه", "حالت صرفه‌جویی باتری", "low_power", GLOBAL, "low_power", "1", "0"),
    Tweak("باتری و شبکه", "اسکن همیشگی Wi-Fi", "wifi_scan_always_enabled — خاموش = مصرف کمتر", GLOBAL, "wifi_scan_always_enabled", "1", "0"),
    Tweak("باتری و شبکه", "اسکن همیشگی بلوتوث", "ble_scan_always_enabled", GLOBAL, "ble_scan_always_enabled", "1", "0"),
    Tweak("باتری و شبکه", "دادهٔ موبایل همیشه فعال", "mobile_data_always_on — خاموش = مصرف کمتر", GLOBAL, "mobile_data_always_on", "1", "0"),
    Tweak("باتری و شبکه", "نمایش درصد باتری", "status_bar_show_battery_percent", SYSTEM, "status_bar_show_battery_percent", "1", "0"),
    // توسعه‌دهنده
    Tweak("توسعه‌دهنده", "گزینه‌های توسعه‌دهنده", "development_settings_enabled", GLOBAL, "development_settings_enabled", "1", "0"),
    Tweak("توسعه‌دهنده", "اشکال‌زدایی USB", "adb_enabled", GLOBAL, "adb_enabled", "1", "0"),
    Tweak("توسعه‌دهنده", "اشکال‌زدایی بی‌سیم", "adb_wifi_enabled — موتور داخلی به این نیاز دارد", GLOBAL, "adb_wifi_enabled", "1", "0"),
    Tweak("توسعه‌دهنده", "غیرفعال کردن تأیید اپ‌های ADB", "verifier_verify_adb_installs", GLOBAL, "verifier_verify_adb_installs", "0", "1")
)

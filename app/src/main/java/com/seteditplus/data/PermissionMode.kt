package com.seteditplus.data

/**
 * Permission mode chosen by the user at startup.
 * PROVIDER = پل از طریق اپ Settings Database Provider — بدون روت
 * ENGINE   = موتور داخلی SetEdit+ (سرور ممتاز شبیه Shizuku، داخل خود اپ)
 * DIRECT   = خود اپ WRITE_SECURE_SETTINGS دارد (یک بار با موتور/ADB/روت اعطا شده) — دائمی، بدون هیچ سرویسی
 */
enum class PermissionMode {
    ROOT,
    SHIZUKU,
    PROVIDER,
    ENGINE,
    DIRECT
}

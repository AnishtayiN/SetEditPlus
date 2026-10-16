# SetEdit Plus (نسخهٔ增强 AnishtayiN)

ویرایشگر پایگاه دادهٔ تنظیمات اندروید (system / secure / global) با رابط کاربری مدرن Compose —
بر پایهٔ [SetEditPlus](https://github.com/kerneldroid/SetEditPlus) و [SetEdit](https://github.com/MuntashirAkon/SetEdit) با یک حالت کاملاً جدید:

## سه حالت دسترسی

| حالت | نیاز | مناسب برای |
|---|---|---|
| **حالت پل (PROVIDER)** | اپ [Settings Database Provider](https://github.com/AnishtayiN/Settings-Database-Provider) نصب + یک دستور adb | **گوشی معمولی بدون روت — پیشنهادی** |
| Root | دسترسی روت | گوشی روت‌شده |
| Shizuku | اپ Shizuku فعال (وایرلس دیباگ یا adb) | کاربران پیشرفته |

## راه‌اندازی حالت پل (بدون روت)

۱. اپ **Settings Database Provider** را نصب کن:
```
adb install --bypass-low-target-sdk-block settings-database-provider-v1.2.2-signed.apk
```
۲. دستوررسی را به آن بده:
```
adb shell pm grant com.anishtayin.settings.database.provider android.permission.WRITE_SECURE_SETTINGS
```
۳. این اپ را نصب کن، «حالت پل» را انتخاب کن — تمام. بدون روت، بدون Shizuku.

اعتبارسنجی خودکار: اپ قبل از ورود چک می‌کند اپ پل نصب باشد و دستوررسی داشته باشد.

## چه چیزهایی تغییر کرد (v2.0)

- **حالت پل جدید**: نوشتن مستقیم در `content://com.anishtayin.provider.SettingsDatabaseProvider` بدون شل و بدون رفلکشن شکستنی
- **تولباچین پایدار**: AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 (نسخهٔ بالادستی روی AGP 9-alpha و Gradle 9.7-snapshot بود که ناپایدار بیلد می‌داد)
- **امضای خودکار در CI** با GitHub Secrets
- `allowBackup=false`
- دکمهٔ حالت پل در صفحهٔ دسترسی + منوی تغییر حالت

## اعتبار

- [kerneldroid/SetEditPlus](https://github.com/kerneldroid/SetEditPlus) — رابط کاربری Compose
- [MuntashirAkon/SetEdit](https://github.com/MuntashirAkon/SetEdit) — مرجع اصلی SetEdit
- [gamemn02/Settings-Database-Provider](https://github.com/gamemn02/Settings-Database-Provider) — ایدهٔ پل

لایسنس: MIT

# SetEdit+ v3.0 (نسخهٔ AnishtayiN)

ویرایشگر پایگاه دادهٔ تنظیمات اندروید (system / secure / global) با رابط Compose —
**حالا با موتور ممتاز داخلی (Shizuku داخل خود اپ)** و جعبه‌ابزار کامل الهام‌گرفته از
[Shizuku](https://github.com/RikkaApps/Shizuku) و [Shizuku+](https://github.com/thejaustin/ShizukuPlus).

## پنج حالت دسترسی

| حالت | نیاز | توضیح |
|---|---|---|
| **موتور داخلی** | ADB بی‌سیم (اندروید ۱۱+) یا روت یا یک فرمان adb | سرور ممتاز خود SetEdit+؛ نیازی به نصب Shizuku نیست |
| **مستقیم** | یک بار اعطای `WRITE_SECURE_SETTINGS` | دائمی، حتی بعد از ریبوت، بدون هیچ سرویسی |
| Shizuku / Shizuku+ / Sui | یکی از آن‌ها فعال باشد | با UserService واقعی (نه reflection) |
| Root | Magisk / KernelSU / APatch | |
| پل | اپ Settings Database Provider | بدون روت |

## موتور داخلی چطور کار می‌کند؟

1. SetEdit+ با کتابخانهٔ [libadb-android](https://github.com/MuntashirAkon/libadb-android) خودش یک کلاینت ADB است:
   جفت‌سازی با کد (از داخل اعلان، مثل Shizuku) و اتصال به `adbd` روی همان گوشی.
2. از طریق ADB (یا su) پروسهٔ `app_process` با کلاس `com.seteditplus.engine.server.EngineServer` از داخل همین APK اجرا می‌شود
   (UID شل 2000 یا روت 0).
3. سرور Binder خود را از طریق ContentProvider (`com.seteditplus.engine`) به اپ تحویل می‌دهد — همان روش Shizuku.
   هر بار اپ دوباره باز شود، Binder دوباره ارسال می‌شود؛ با حذف/آپدیت APK سرور خودش خارج می‌شود.
4. موتور فقط به UID خود SetEdit+ پاسخ می‌دهد.
5. بعد از استارت، اپ به خودش `WRITE_SECURE_SETTINGS` می‌دهد ← حالت مستقیم دائمی.
6. شروع خودکار بعد از بوت: اپ خودش «اشکال‌زدایی بی‌سیم» را روشن می‌کند، وصل می‌شود و موتور را بالا می‌آورد.

همان رابط AIDL (`IEngineService`) به‌عنوان UserService در Shizuku/Shizuku+/Sui هم اجرا می‌شود.

## جعبه‌ابزار (آیکون 🛠 در صفحهٔ اصلی)

- **موتور داخلی** — جفت‌سازی با اعلان یا دستی، اتصال خودکار با mDNS، استارت با روت/Shizuku/کامپیوتر، توقف، اعطای دائمی، شروع خودکار
- **ترمینال ممتاز** — شل با UID شل/روت، حفظ مسیر `cd`، تاریخچه، میانبرها، کپی خروجی
- **مدیریت برنامه‌ها** — اعطا/لغو مجوزها، توقف اجباری، فعال/غیرفعال، پاک کردن داده، debloat (`pm uninstall --user 0`)،
  standby bucket، منع پس‌زمینه، معافیت Doze، dexopt؛ انتخاب گروهی با لمس طولانی
- **DNS خصوصی** — پیش‌تنظیم‌ها (Cloudflare، AdGuard، Quad9، Mullvad، ControlD، NextDNS، …) + DNS سفارشی
- **ترفندهای سریع** — انیمیشن، نرخ نوسازی، Freeform/تغییر اندازهٔ اجباری، نمایش لمس، بیدار ماندن، باتری، گزینه‌های توسعه‌دهنده
  (همه در تب Modified ثبت و قابل بازگردانی)
- **عیب‌یاب سرویس** — بررسی پیش‌نیازها با دکمهٔ رفع (اعلان، باتری، توسعه‌دهنده، Wi-Fi، Shizuku، روت، …)
- **گزارش فعالیت** — خط زمانی همهٔ تغییرات با گروه‌بندی تکرارها و فیلتر
- **راهنما** — آفلاین با فرمان‌های قابل کپی
- **کاشی تنظیمات سریع** — وضعیت و روشن/خاموش کردن موتور

## راه‌اندازی سریع موتور (بدون روت)

1. گزینه‌های توسعه‌دهنده ← «اشکال‌زدایی بی‌سیم» را روشن کن.
2. SetEdit+ ← موتور داخلی ← «جفت‌سازی با اعلان».
3. در تنظیمات «جفت‌سازی دستگاه با کد» را باز کن و کد را در اعلان SetEdit+ بنویس. تمام.

با کامپیوتر (همهٔ نسخه‌ها): فرمان کامل در صفحهٔ موتور قابل کپی است. برای حالت مستقیم کافی است:
```
adb shell pm grant com.seteditplus android.permission.WRITE_SECURE_SETTINGS
```

## اعتبار

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) و [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) — معماری سرور ممتاز و تحویل Binder (Apache-2.0)
- [thejaustin/ShizukuPlus](https://github.com/thejaustin/ShizukuPlus) — ایدهٔ ابزارها: Doctor، Activity Log، مدیریت گروهی، DNS، کاشی (Apache-2.0)
- [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android) — کلاینت ADB و جفت‌سازی
- [kerneldroid/SetEditPlus](https://github.com/kerneldroid/SetEditPlus)، [MuntashirAkon/SetEdit](https://github.com/MuntashirAkon/SetEdit)، [gamemn02/Settings-Database-Provider](https://github.com/gamemn02/Settings-Database-Provider)

لایسنس: MIT

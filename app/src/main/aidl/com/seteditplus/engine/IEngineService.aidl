// IEngineService.aidl
// رابط سرویس ممتاز SetEdit+ — هم توسط سرور داخلی (app_process) و هم به‌عنوان
// UserService شیزوکو/شیزوکو+ پیاده‌سازی می‌شود.
package com.seteditplus.engine;

import android.os.Bundle;

interface IEngineService {
    // کد تراکنش 16777114 قرارداد شیزوکو برای destroy در UserService است
    void destroy() = 16777114;

    int getUid() = 1;
    int getPid() = 2;
    int getVersion() = 3;
    String getSELinuxContext() = 4;

    // اجرای فرمان با sh -c ؛ خروجی: exit(int), out(String), err(String)
    Bundle exec(String command, long timeoutMs) = 5;
}

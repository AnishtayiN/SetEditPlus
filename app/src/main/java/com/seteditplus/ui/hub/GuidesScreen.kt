package com.seteditplus.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

private data class Guide(val title: String, val body: String, val commands: List<String> = emptyList(), val route: Route? = null)

private val GUIDES = listOf(
    Guide(
        "موتور داخلی چیست؟",
        "SetEdit+ حالا یک سرور ممتاز داخلی دارد (همان ایدهٔ Shizuku). با ADB بی‌سیم یا روت یک پروسهٔ app_process با UID شل یا روت بالا می‌آید و Binder خودش را به اپ تحویل می‌دهد. نیازی به نصب Shizuku نیست؛ اگر Shizuku یا Shizuku+ نصب باشد هم پشتیبانی می‌شود.",
        route = Route.ENGINE
    ),
    Guide(
        "جفت‌سازی ADB بی‌سیم قدم به قدم",
        "۱. تنظیمات، درباره گوشی، هفت بار روی شمارهٔ ساخت بزن.\n" +
            "۲. در گزینه‌های توسعه‌دهنده، اشکال‌زدایی بی‌سیم را روشن کن (فقط روی Wi-Fi).\n" +
            "۳. در SetEdit+ دکمهٔ جفت‌سازی با اعلان را بزن.\n" +
            "۴. در تنظیمات گزینهٔ جفت‌سازی دستگاه با کد را باز کن.\n" +
            "۵. اعلان SetEdit+ ظاهر می‌شود؛ کد شش رقمی را در آن بنویس.\n" +
            "۶. موتور خودکار وصل و اجرا می‌شود و اجازهٔ دائمی هم گرفته می‌شود.",
        route = Route.ENGINE
    ),
    Guide(
        "بعد از ریبوت چه می‌شود؟",
        "پروسهٔ موتور مثل Shizuku با ریبوت از بین می‌رود. اگر اجازهٔ دائمی (WRITE_SECURE_SETTINGS) داده شده باشد و شروع خودکار روشن باشد، SetEdit+ بعد از بوت خودش اشکال‌زدایی بی‌سیم را روشن می‌کند، وصل می‌شود و موتور را بالا می‌آورد. حالت مستقیم هم اصلاً به موتور نیاز ندارد."
    ),
    Guide(
        "اجرای موتور از کامپیوتر",
        "اگر اندروید زیر ۱۱ است یا Wi-Fi نداری، با کابل USB و adb این فرمان را بزن (فرمان کامل در صفحهٔ موتور داخلی قابل کپی است).",
        commands = listOf("adb devices", "adb shell pm grant com.seteditplus android.permission.WRITE_SECURE_SETTINGS"),
        route = Route.ENGINE
    ),
    Guide(
        "Shizuku و Shizuku+",
        "اگر Shizuku یا Shizuku+ (af.shizuku.plus.api) یا Sui فعال باشد، SetEdit+ یک UserService از همان موتور داخل پروسهٔ آن‌ها اجرا می‌کند. اگر Shizuku+ را بدون Compat Hub نصب کرده‌ای و دیده نمی‌شود، از کارت Compat Hub در خود Shizuku+ کمک بگیر.",
        route = Route.DOCTOR
    ),
    Guide(
        "حالت‌های دسترسی — کدام را انتخاب کنم؟",
        "مستقیم: بهترین گزینه بعد از یک بار اعطا؛ بدون سرویس، دائمی.\n" +
            "موتور داخلی: همه‌کاره (ترمینال، مدیریت برنامه، کلیدهای خصوصی system).\n" +
            "Shizuku: اگر از قبل داری.\n" +
            "روت: دسترسی کامل.\n" +
            "پل: از طریق اپ Settings Database Provider."
    ),
    Guide(
        "فرمان‌های مفید",
        "این‌ها را در ترمینال ممتاز امتحان کن:",
        commands = listOf(
            "settings list secure",
            "cmd package list packages -d",
            "dumpsys battery set level 100",
            "dumpsys battery reset",
            "wm density 400",
            "wm density reset",
            "cmd overlay list",
            "am set-standby-bucket com.example rare"
        ),
        route = Route.TERMINAL
    ),
    Guide(
        "امنیت",
        "موتور فقط به UID خود SetEdit+ پاسخ می‌دهد. کلید ADB در حافظهٔ خصوصی اپ ذخیره می‌شود. اگر دیگر لازم نیست، از تنظیمات توسعه‌دهنده گزینهٔ لغو مجوزهای اشکال‌زدایی را بزن تا جفت‌سازی پاک شود."
    )
)

@Composable
fun GuidesScreen(onBack: () -> Unit, onOpen: (Route) -> Unit) {
    var expanded by remember { mutableStateOf<Int?>(0) }
    HubScaffold(title = "راهنما", onBack = onBack) { padding ->
        HubList(padding) {
            items(GUIDES.size) { i ->
                val g = GUIDES[i]
                HubCard(onClick = { expanded = if (expanded == i) null else i }) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AnimatedVisibility(expanded == i) {
                        androidx.compose.foundation.layout.Column {
                            Text(g.body, style = MaterialTheme.typography.bodyMedium)
                            g.commands.forEach { CodeBlock(it) }
                            g.route?.let { r -> TextButton(onClick = { onOpen(r) }) { Text("رفتن به ${r.title}") } }
                        }
                    }
                }
            }
        }
    }
}

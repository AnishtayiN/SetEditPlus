package com.seteditplus.ui.hub

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seteditplus.adb.AdbEngine
import com.seteditplus.adb.EngineForegroundService
import com.seteditplus.data.DirectSettings
import com.seteditplus.data.PermissionChecker
import com.seteditplus.data.PermissionMode
import com.seteditplus.engine.EngineClient
import com.seteditplus.engine.EngineStarter
import com.seteditplus.priv.Privileged
import com.seteditplus.shizuku.ShizukuBridge
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun openDeveloperOptions(ctx: Context) {
    val intents = listOf(
        Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
    )
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

@Composable
fun EngineScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val engine by EngineClient.state.collectAsState()

    var busy by remember { mutableStateOf<String?>(null) }
    var adbConnected by remember { mutableStateOf(AdbEngine.isConnected()) }
    var devOn by remember { mutableStateOf(false) }
    var wifiDbgOn by remember { mutableStateOf(false) }
    var directGranted by remember { mutableStateOf(DirectSettings.isGranted(ctx)) }
    var autostart by remember { mutableStateOf(viewModel.preferences.isEngineAutostart()) }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            adbConnected = AdbEngine.isConnected()
            devOn = AdbEngine.isDeveloperOptionsOn(ctx)
            wifiDbgOn = AdbEngine.isWirelessDebuggingOn(ctx)
            directGranted = DirectSettings.isGranted(ctx)
            delay(1500)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        EngineForegroundService.start(ctx, EngineForegroundService.ACTION_PAIR)
        openDeveloperOptions(ctx)
    }

    fun task(label: String, block: suspend () -> String) {
        if (busy != null) return
        busy = label
        scope.launch {
            val msg = try { withContext(Dispatchers.IO) { block() } } catch (t: Throwable) { "خطا: ${t.message}" }
            busy = null
            snackbar.showSnackbar(msg)
        }
    }

    suspend fun startAndReport(): String {
        val alive = EngineClient.awaitAlive(10_000)
        return if (alive) {
            EngineStarter.selfGrant(ctx)
            "موتور فعال شد ✓ (${EngineClient.state.value.identity})"
        } else "موتور پاسخ نداد. «عیب‌یاب» را ببین."
    }

    HubScaffold(title = "موتور داخلی", onBack = onBack, snackbar = snackbar) { padding ->
        HubList(padding) {
            // ---------- وضعیت ----------
            item {
                HubCard(color = if (engine.running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Memory)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(
                                if (engine.running) "موتور در حال اجراست" else "موتور خاموش است",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "سرور ممتاز داخلی — مثل Shizuku ولی داخل خود SetEdit+",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        StatusDot(engine.running)
                    }
                    if (engine.running) {
                        InfoRow("هویت", engine.identity)
                        InfoRow("PID", engine.pid.toString())
                        InfoRow("نسخهٔ سرور", engine.version.toString())
                        InfoRow("SELinux", engine.context)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { task("stop") { EngineClient.stop(); "موتور متوقف شد" } }) { Text("توقف") }
                            FilledTonalButton(onClick = { viewModel.selectPermissionMode(PermissionMode.ENGINE) }) {
                                Text("استفاده برای ویرایش تنظیمات")
                            }
                        }
                    }
                    if (busy != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("در حال انجام…")
                        }
                    }
                }
            }

            // ---------- روش ۱: ADB بی‌سیم ----------
            item { SectionTitle("روش ۱ — ADB بی‌سیم (بدون روت، اندروید ۱۱+)") }
            item {
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Wifi)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text("وضعیت پیش‌نیازها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(devOn); Spacer(Modifier.width(8.dp)); Text("گزینه‌های توسعه‌دهنده") }
                    Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(wifiDbgOn); Spacer(Modifier.width(8.dp)); Text("اشکال‌زدایی بی‌سیم") }
                    Row(verticalAlignment = Alignment.CenterVertically) { StatusDot(adbConnected); Spacer(Modifier.width(8.dp)); Text("اتصال ADB داخلی") }
                    if (!AdbEngine.pairingSupported) {
                        Text("اندروید این گوشی زیر ۱۱ است؛ از روش ۲ (روت) یا ۳ (کامپیوتر) استفاده کن.", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "۱) گزینه‌های توسعه‌دهنده را فعال کن (۷ بار ضربه روی Build number)\n" +
                            "۲) «اشکال‌زدایی بی‌سیم» را روشن کن\n" +
                            "۳) دکمهٔ «جفت‌سازی با اعلان» را بزن، بعد در تنظیمات «جفت‌سازی دستگاه با کد» را باز کن\n" +
                            "۴) از اعلان SetEdit+ کد ۶ رقمی را وارد کن — بقیه خودکار است",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openDeveloperOptions(ctx) }) { Text("باز کردن تنظیمات") }
                        Button(
                            enabled = AdbEngine.pairingSupported,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else {
                                    EngineForegroundService.start(ctx, EngineForegroundService.ACTION_PAIR)
                                    openDeveloperOptions(ctx)
                                }
                            }
                        ) { Text("جفت‌سازی با اعلان") }
                    }
                }
            }
            item {
                HubCard {
                    Text("جفت‌سازی دستی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("اگر اعلان کار نکرد: پورت و کدی که در پنجرهٔ «جفت‌سازی با کد» نشان داده می‌شود را وارد کن (در حالت تقسیم صفحه).", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pairPort, onValueChange = { pairPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("پورت") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = pairCode, onValueChange = { pairCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("کد ۶ رقمی") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = pairPort.isNotEmpty() && pairCode.length == 6 && AdbEngine.pairingSupported,
                            onClick = {
                                task("pair") {
                                    val r = AdbEngine.pair(ctx, pairPort.toInt(), pairCode)
                                    if (r.isFailure) return@task "جفت‌سازی ناموفق: ${r.exceptionOrNull()?.message}"
                                    delay(1200)
                                    val c = AdbEngine.connect(ctx)
                                    if (c.isFailure) return@task "جفت شد ✓ ولی اتصال نشد: ${c.exceptionOrNull()?.message}"
                                    EngineStarter.startViaAdb(ctx)
                                    startAndReport()
                                }
                            }
                        ) { Text("جفت‌سازی و استارت") }
                    }
                }
            }
            item {
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Cable)
                        Spacer(Modifier.width(12.dp))
                        Text("قبلاً جفت شده؟ فقط استارت کن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("پورت به‌صورت خودکار با mDNS پیدا می‌شود؛ در صورت نیاز پورت «آدرس IP و پورت» را دستی بنویس.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = connectPort, onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                        label = { Text("پورت اتصال (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(onClick = {
                        task("connect") {
                            val c = AdbEngine.connect(ctx, connectPort.toIntOrNull())
                            if (c.isFailure) return@task "اتصال ناموفق: ${c.exceptionOrNull()?.message}"
                            EngineStarter.startViaAdb(ctx)
                            startAndReport()
                        }
                    }) { Text("اتصال و استارت موتور") }
                }
            }

            // ---------- روش ۲: روت ----------
            item { SectionTitle("روش ۲ — روت (Magisk / KernelSU / APatch)") }
            item {
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.AdminPanelSettings)
                        Spacer(Modifier.width(12.dp))
                        Text("استارت با su — موتور با UID 0 اجرا می‌شود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = {
                        task("root") {
                            if (!PermissionChecker.isRootAvailable()) return@task "روت در دسترس نیست"
                            Privileged.rootGranted = true
                            viewModel.preferences.setRootConfirmed(true)
                            EngineStarter.startViaRoot(ctx)
                            startAndReport()
                        }
                    }) { Text("استارت با روت") }
                }
            }

            // ---------- روش ۳: Shizuku یا PC ----------
            item { SectionTitle("روش ۳ — از Shizuku/Shizuku+ یا کامپیوتر") }
            item {
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Computer)
                        Spacer(Modifier.width(12.dp))
                        Text("با کابل و کامپیوتر (همهٔ نسخه‌های اندروید)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("این فرمان را در کامپیوتر اجرا کن:", style = MaterialTheme.typography.bodySmall)
                    CodeBlock(EngineStarter.adbPcCommand(ctx))
                    if (ShizukuBridge.isReady()) {
                        FilledTonalButton(onClick = {
                            task("shizuku") {
                                EngineStarter.startViaShizuku(ctx)
                                startAndReport()
                            }
                        }) { Text("استارت موتور از طریق Shizuku") }
                    }
                }
            }

            // ---------- اعطای دائمی ----------
            item { SectionTitle("حالت مستقیم (دائمی، حتی بعد از ریبوت)") }
            item {
                HubCard(color = if (directGranted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Key)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text("WRITE_SECURE_SETTINGS برای خود SetEdit+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(if (directGranted) "اعطا شده ✓" else "اعطا نشده", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusDot(directGranted)
                    }
                    Text(
                        "با یک بار اعطا، اپ بدون هیچ سرویسی جدول‌های secure/global را می‌نویسد و حتی می‌تواند بعد از ریبوت خودش اشکال‌زدایی بی‌سیم را روشن کند.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !directGranted, onClick = {
                            task("grant") {
                                if (!Privileged.isAvailable()) return@task "اول موتور/Shizuku/روت را فعال کن"
                                val r = EngineStarter.selfGrant(ctx)
                                if (DirectSettings.isGranted(ctx)) "اعطا شد ✓" else "ناموفق: ${r.text}"
                            }
                        }) { Text("اعطای دائمی") }
                        if (directGranted) {
                            FilledTonalButton(onClick = { viewModel.selectPermissionMode(PermissionMode.DIRECT) }) { Text("استفاده از حالت مستقیم") }
                        }
                    }
                    CodeBlock("adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS")
                }
            }
            item {
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text("شروع خودکار موتور بعد از بوت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("نیازمند حالت مستقیم + یک بار جفت‌سازی + Wi-Fi مورد اعتماد", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autostart, enabled = directGranted,
                            onCheckedChange = { autostart = it; viewModel.preferences.setEngineAutostart(it) }
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { onOpen(Route.DOCTOR) }, modifier = Modifier.fillMaxWidth()) { Text("مشکل داری؟ عیب‌یاب سرویس") }
            }
        }
    }
}

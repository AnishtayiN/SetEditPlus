package com.seteditplus.ui.hub

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seteditplus.engine.EngineStarter
import com.seteditplus.shizuku.ShizukuBridge
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@SuppressLint("BatteryLife")
@Composable
fun DoctorScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpen: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var checks by remember { mutableStateOf<List<Check>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) { checks = withContext(Dispatchers.IO) { DoctorChecks.run(ctx) } }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun fix(a: FixAction) {
        when (a) {
            FixAction.OPEN_DEV -> openDeveloperOptions(ctx)
            FixAction.ENABLE_DEV, FixAction.ENABLE_WIFI_DBG -> scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        Settings.Global.putInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                        if (a == FixAction.ENABLE_WIFI_DBG) Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
                    }
                }
                tick++
            }
            FixAction.NOTIF -> notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            FixAction.BATTERY -> runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            FixAction.ENGINE -> onOpen(Route.ENGINE)
            FixAction.GRANT -> scope.launch {
                val r = withContext(Dispatchers.IO) { EngineStarter.selfGrant(ctx) }
                snackbar.showSnackbar(if (r.ok) "اعطا شد ✓" else "ناموفق: ${r.text.take(100)}")
                tick++
            }
            FixAction.SHIZUKU_PERM -> runCatching { Shizuku.requestPermission(101) }
            FixAction.OPEN_SHIZUKU -> {
                val pkg = ShizukuBridge.installedVariants(ctx).firstOrNull()?.packageName
                val i = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
                if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            FixAction.NONE -> {}
        }
    }

    HubScaffold(
        title = "عیب‌یاب سرویس", onBack = onBack, snackbar = snackbar,
        actions = { IconButton(onClick = { tick++ }) { Icon(Icons.Rounded.Refresh, "بررسی دوباره") } }
    ) { padding ->
        HubList(padding) {
            item {
                val bad = checks.count { it.ok == false }
                HubCard(color = if (bad == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                    Text(if (bad == 0) "همه چیز مرتب است ✓" else "$bad مورد نیاز به توجه دارد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("موارد زرد اختیاری هستند.", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(checks.size) { i ->
                val c = checks[i]
                HubCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(c.ok)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold)
                            Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.fix != FixAction.NONE) FilledTonalButton(onClick = { fix(c.fix) }) { Text(c.fixLabel) }
                    }
                }
            }
        }
    }
}

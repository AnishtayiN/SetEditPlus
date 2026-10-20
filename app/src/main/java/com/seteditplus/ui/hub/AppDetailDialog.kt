package com.seteditplus.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.priv.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailDialog(app: AppItem, onDismiss: () -> Unit, onChanged: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = remember { mutableStateListOf<PermItem>() }
    var onlyDangerous by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<Pair<String, () -> ShellResult>?>(null) }
    var showBuckets by remember { mutableStateOf(false) }

    suspend fun loadPerms() {
        val p = withContext(Dispatchers.IO) { AppOps.permissions(ctx, app.pkg) }
        perms.clear(); perms.addAll(p)
    }
    LaunchedEffect(app.pkg) { loadPerms() }

    fun act(label: String, op: () -> ShellResult, close: Boolean = false) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { op() }
            loadPerms()
            onChanged(if (r.ok) "$label ✓" else "$label ناموفق: ${r.text.take(120)}")
            if (close) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${app.pkg} • ${app.versionName ?: ""}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { act("توقف اجباری", { AppOps.forceStop(app.pkg) }) }, label = { Text("توقف اجباری") })
                    if (app.enabled) AssistChip(onClick = { confirm = "غیرفعال‌سازی" to { AppOps.disable(app.pkg) } }, label = { Text("غیرفعال") })
                    else AssistChip(onClick = { act("فعال‌سازی", { AppOps.enable(app.pkg) }, true) }, label = { Text("فعال") })
                    AssistChip(onClick = { confirm = "پاک کردن داده‌ها" to { AppOps.clearData(app.pkg) } }, label = { Text("پاک کردن داده") })
                    AssistChip(onClick = { act("کامپایل speed-profile", { AppOps.compile(app.pkg) }) }, label = { Text("بهینه‌سازی (dexopt)") })
                    AssistChip(onClick = { act("محدودیت پس‌زمینه", { AppOps.backgroundRun(app.pkg, false) }) }, label = { Text("منع پس‌زمینه") })
                    AssistChip(onClick = { act("اجازهٔ پس‌زمینه", { AppOps.backgroundRun(app.pkg, true) }) }, label = { Text("اجازهٔ پس‌زمینه") })
                    AssistChip(onClick = { act("استثنای بهینه‌سازی باتری", { AppOps.batteryWhitelist(app.pkg, true) }) }, label = { Text("معاف از Doze") })
                    AssistChip(onClick = { showBuckets = !showBuckets }, label = { Text("Standby bucket") })
                    if (app.system) AssistChip(onClick = { confirm = "حذف برای کاربر (قابل بازگشت)" to { AppOps.uninstallForUser(app.pkg) } }, label = { Text("حذف (debloat)") })
                }
                if (showBuckets) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppOps.STANDBY_BUCKETS.forEach { b ->
                            AssistChip(onClick = { act("bucket=$b", { AppOps.standby(app.pkg, b) }) }, label = { Text(b) })
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مجوزها (${perms.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("فقط خطرناک", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = onlyDangerous, onCheckedChange = { onlyDangerous = it })
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(perms.filter { !onlyDangerous || it.dangerous }, key = { it.name }) { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name.removePrefix("android.permission."),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Switch(
                                checked = p.granted,
                                onCheckedChange = { on ->
                                    act(if (on) "اعطای ${p.name.substringAfterLast('.')}" else "لغو ${p.name.substringAfterLast('.')}", {
                                        if (on) AppOps.grant(app.pkg, p.name) else AppOps.revoke(app.pkg, p.name)
                                    })
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )

    confirm?.let { (label, op) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("$label؟") },
            text = { Text("${app.label}\n${app.pkg}\n\nاین کار روی برنامه‌های سیستمی می‌تواند خطرناک باشد.") },
            confirmButton = { TextButton(onClick = { confirm = null; act(label, op, true) }) { Text("انجام بده") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("لغو") } }
        )
    }
}

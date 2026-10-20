package com.seteditplus.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seteditplus.log.LogCategory
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/** ترفندهای سریع — همه از طریق حالت دسترسی فعلی نوشته و در تب «تغییرات» ثبت می‌شوند (قابل بازگردانی) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TweaksScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val values = remember { mutableStateMapOf<String, String?>() }

    LaunchedEffect(Unit) {
        TWEAKS.forEach { t -> values[t.id] = viewModel.readSetting(t.table, t.key) }
    }

    fun set(t: Tweak, v: String) {
        scope.launch {
            var ok = viewModel.writeSetting(t.table, t.key, v, LogCategory.TWEAK)
            t.companions.forEach { (tb, k) -> ok = viewModel.writeSetting(tb, k, v, LogCategory.TWEAK) && ok }
            values[t.id] = viewModel.readSetting(t.table, t.key)
            snackbar.showSnackbar(if (ok) "${t.title}: اعمال شد ✓" else "${t.title}: سیستم نپذیرفت")
        }
    }

    HubScaffold(title = "ترفندهای سریع", onBack = onBack, snackbar = snackbar) { padding ->
        HubList(padding) {
            item {
                Text(
                    "همهٔ تغییرات در تب «Modified» ثبت می‌شوند و با یک ضربه به مقدار اصلی برمی‌گردند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TWEAKS.groupBy { it.section }.forEach { (section, list) ->
                item(key = "s_$section") { SectionTitle(section) }
                list.forEach { t ->
                    item(key = t.id) {
                        val cur = values[t.id]
                        HubCard {
                            if (t.choices.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.title, fontWeight = FontWeight.SemiBold)
                                        Text("${t.desc} • فعلی: ${cur ?: "پیش‌فرض"}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Switch(checked = cur == t.on, onCheckedChange = { set(t, if (it) t.on!! else t.off!!) })
                                }
                            } else {
                                Text(t.title, fontWeight = FontWeight.SemiBold)
                                Text("${t.desc} • فعلی: ${cur ?: "پیش‌فرض"}", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    t.choices.forEach { (v, label) ->
                                        val selected = cur != null && (cur == v || cur.toFloatOrNull() == v.toFloatOrNull())
                                        FilterChip(selected = selected, onClick = { set(t, v) }, label = { Text(label) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

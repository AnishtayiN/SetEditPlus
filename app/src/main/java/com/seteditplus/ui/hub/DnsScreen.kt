package com.seteditplus.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seteditplus.data.SettingsTable
import com.seteditplus.log.LogCategory
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private data class DnsPreset(val name: String, val host: String, val note: String)

private val DNS_PRESETS = listOf(
    DnsPreset("Cloudflare", "one.one.one.one", "سریع، حریم خصوصی"),
    DnsPreset("Cloudflare (ضد بدافزار)", "security.cloudflare-dns.com", "مسدودسازی بدافزار"),
    DnsPreset("Cloudflare (خانواده)", "family.cloudflare-dns.com", "بدافزار + محتوای بزرگسال"),
    DnsPreset("Google", "dns.google", "پایدار"),
    DnsPreset("AdGuard", "dns.adguard-dns.com", "مسدودسازی تبلیغات"),
    DnsPreset("AdGuard (خانواده)", "family.adguard-dns.com", "تبلیغات + محتوای بزرگسال"),
    DnsPreset("Quad9", "dns.quad9.net", "امنیت"),
    DnsPreset("Mullvad (تبلیغات)", "adblock.dns.mullvad.net", "بدون لاگ + ضدتبلیغ"),
    DnsPreset("ControlD (ضد تبلیغ)", "p2.freedns.controld.com", "تبلیغات و ردیاب"),
    DnsPreset("NextDNS", "dns.nextdns.io", "قابل شخصی‌سازی")
)

/** Network & DNS Governor — مدیریت Private DNS (همهٔ حالت‌های دسترسی، حتی حالت پل/مستقیم) */
@Composable
fun DnsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var mode by remember { mutableStateOf("off") }
    var host by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val saved = remember { mutableStateListOf<String>().apply { addAll(viewModel.preferences.getCustomDns()) } }

    suspend fun refresh() {
        mode = viewModel.readSetting(SettingsTable.GLOBAL, "private_dns_mode") ?: "off"
        host = viewModel.readSetting(SettingsTable.GLOBAL, "private_dns_specifier") ?: ""
    }
    LaunchedEffect(Unit) { refresh() }

    fun apply(newMode: String, newHost: String?) {
        scope.launch {
            var ok = true
            if (newHost != null) ok = viewModel.writeSetting(SettingsTable.GLOBAL, "private_dns_specifier", newHost, LogCategory.DNS)
            ok = ok && viewModel.writeSetting(SettingsTable.GLOBAL, "private_dns_mode", newMode, LogCategory.DNS)
            refresh()
            snackbar.showSnackbar(if (ok) "DNS اعمال شد ✓" else "اعمال نشد — حالت دسترسی اجازهٔ نوشتن global را ندارد")
        }
    }

    HubScaffold(title = "DNS خصوصی", onBack = onBack, snackbar = snackbar) { padding ->
        HubList(padding) {
            item {
                HubCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("وضعیت فعلی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoRow("حالت", when (mode) { "off" -> "خاموش"; "opportunistic" -> "خودکار"; "hostname" -> "میزبان مشخص"; else -> mode })
                    if (mode == "hostname") InfoRow("میزبان", host)
                    val idx = listOf("off", "opportunistic", "hostname").indexOf(mode)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("off" to "خاموش", "opportunistic" to "خودکار", "hostname" to "میزبان").forEachIndexed { i, (m, l) ->
                            SegmentedButton(
                                selected = idx == i,
                                onClick = { if (m == "hostname" && host.isBlank()) apply(m, DNS_PRESETS.first().host) else apply(m, null) },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                label = { Text(l) }
                            )
                        }
                    }
                }
            }
            item { SectionTitle("پیش‌تنظیم‌ها") }
            items(DNS_PRESETS.size) { i ->
                val p = DNS_PRESETS[i]
                HubCard(onClick = { apply("hostname", p.host) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == "hostname" && host == p.host, onClick = { apply("hostname", p.host) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${p.host} • ${p.note}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { SectionTitle("DNS سفارشی (DoT)") }
            item {
                HubCard {
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it.trim() }, singleLine = true,
                        label = { Text("مثلاً xxxx.dns.nextdns.io") }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = custom.contains('.'), onClick = {
                            if (custom !in saved) { saved.add(custom); viewModel.preferences.setCustomDns(saved) }
                            apply("hostname", custom)
                        }) { Text("ذخیره و اعمال") }
                    }
                    saved.toList().forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == "hostname" && host == h, onClick = { apply("hostname", h) })
                            Text(h, modifier = Modifier.weight(1f))
                            IconButton(onClick = { saved.remove(h); viewModel.preferences.setCustomDns(saved) }) { Icon(Icons.Rounded.Delete, "حذف") }
                        }
                    }
                }
            }
        }
    }
}

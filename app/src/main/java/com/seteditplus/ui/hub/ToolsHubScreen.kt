package com.seteditplus.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.engine.EngineClient
import com.seteditplus.priv.BackendType
import com.seteditplus.priv.Privileged
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

private data class Tool(val route: Route, val icon: ImageVector, val desc: String)

private val TOOLS = listOf(
    Tool(Route.ENGINE, Icons.Rounded.Memory, "Shizuku داخلی: جفت‌سازی بی‌سیم، استارت با روت، اعطای دائمی"),
    Tool(Route.TERMINAL, Icons.Rounded.Terminal, "شل با دسترسی ADB/روت + میانبرها"),
    Tool(Route.APPS, Icons.Rounded.Apps, "مجوزها، توقف اجباری، غیرفعال‌سازی، گروهی"),
    Tool(Route.DNS, Icons.Rounded.Dns, "Private DNS با پیش‌تنظیم‌ها"),
    Tool(Route.TWEAKS, Icons.Rounded.Tune, "انیمیشن، پنجرهٔ آزاد، نرخ نوسازی و…"),
    Tool(Route.DOCTOR, Icons.Rounded.MedicalServices, "تشخیص و رفع مشکل سرویس‌ها"),
    Tool(Route.LOG, Icons.Rounded.History, "تاریخچهٔ همهٔ تغییرات"),
    Tool(Route.GUIDES, Icons.Rounded.MenuBook, "راهنمای آفلاین و فرمان‌های آماده")
)

@Composable
fun ToolsHubScreen(viewModel: SettingsViewModel, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val engine by EngineClient.state.collectAsState()
    var backend by remember { mutableStateOf<BackendType?>(null) }
    LaunchedEffect(engine) {
        while (true) {
            backend = Privileged.best()
            delay(2000)
        }
    }

    HubScaffold(title = "ابزارها", onBack = onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                HubCard(
                    color = if (backend != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    onClick = { onOpen(Route.ENGINE) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(backend != null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (backend != null) "دسترسی ممتاز فعال: ${backend!!.label}" else "دسترسی ممتاز فعال نیست",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        when {
                            engine.running -> "موتور داخلی • ${engine.identity} • PID ${engine.pid}"
                            backend != null -> "برای استقلال کامل از اپ‌های دیگر، موتور داخلی را راه بینداز"
                            else -> "برای ترمینال و مدیریت برنامه‌ها، موتور داخلی را راه بینداز (بدون روت و بدون نصب Shizuku)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(TOOLS) { tool ->
                HubCard(onClick = { onOpen(tool.route) }, modifier = Modifier.height(150.dp)) {
                    IconBadge(tool.icon)
                    Spacer(Modifier.height(4.dp))
                    Text(tool.route.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        tool.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

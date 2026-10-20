package com.seteditplus.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** گزارش فعالیت با خط زمانی، گروه‌بندی تکرارها و فیلتر دسته */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val entries by ActivityLog.entries.collectAsState()
    var filter by remember { mutableStateOf<LogCategory?>(null) }
    var onlyErrors by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.US) }

    val shown = entries.filter { (filter == null || it.category == filter) && (!onlyErrors || !it.success) }
    // گروه‌بندی موارد تکراری پشت‌سرهم
    val grouped = mutableListOf<Pair<com.seteditplus.log.LogEntry, Int>>()
    for (e in shown) {
        val last = grouped.lastOrNull()
        if (last != null && last.first.title == e.title && last.first.category == e.category && last.first.success == e.success)
            grouped[grouped.lastIndex] = last.first to last.second + 1
        else grouped.add(e to 1)
    }

    HubScaffold(
        title = "گزارش فعالیت", onBack = onBack,
        actions = { IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "پاک کردن") } }
    ) { padding ->
        HubList(padding) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("همه (${entries.size})") })
                    FilterChip(selected = onlyErrors, onClick = { onlyErrors = !onlyErrors }, label = { Text("فقط خطاها") })
                    LogCategory.entries.forEach { c ->
                        FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(c.label) })
                    }
                }
            }
            if (grouped.isEmpty()) item { Text("هنوز فعالیتی ثبت نشده.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(grouped.size) { i ->
                val (e, count) = grouped[i]
                TimelineRow(e, count, fmt.format(Date(e.time)), isLast = i == grouped.lastIndex)
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("پاک کردن گزارش؟") },
        confirmButton = { TextButton(onClick = { ActivityLog.clear(); confirmClear = false }) { Text("پاک کن") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("لغو") } }
    )
}

@Composable
private fun TimelineRow(e: com.seteditplus.log.LogEntry, count: Int, time: String, isLast: Boolean) {
    val color = if (e.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            if (!isLast) Box(Modifier.width(2.dp).height(64.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(8.dp))
        HubCard(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.category.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (count > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text("×$count", style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 6.dp))
                }
            }
            Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.detail.isNotBlank()) Text(e.detail, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

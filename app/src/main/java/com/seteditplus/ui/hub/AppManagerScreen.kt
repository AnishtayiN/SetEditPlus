package com.seteditplus.ui.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seteditplus.priv.Privileged
import com.seteditplus.priv.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppManagerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val apps = remember { mutableStateListOf<AppItem>() }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var onlyDisabled by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var detail by remember { mutableStateOf<AppItem?>(null) }
    var bulkConfirm by remember { mutableStateOf<Pair<String, (String) -> ShellResult>?>(null) }

    suspend fun reload() {
        loading = true
        val l = withContext(Dispatchers.IO) { AppOps.loadApps(ctx) }
        apps.clear(); apps.addAll(l)
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    val filtered = apps.filter {
        (showSystem || !it.system) && (!onlyDisabled || !it.enabled) &&
            (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
    }

    fun bulk(label: String, op: (String) -> ShellResult) {
        val targets = selected.toList()
        scope.launch {
            val results = withContext(Dispatchers.IO) { targets.map { op(it) } }
            val ok = results.count { it.ok }
            selected.clear()
            reload()
            snackbar.showSnackbar("$label: $ok از ${targets.size} موفق")
        }
    }

    HubScaffold(
        title = if (selected.isEmpty()) "مدیریت برنامه‌ها" else "${selected.size} انتخاب‌شده",
        onBack = { if (selected.isNotEmpty()) selected.clear() else onBack() },
        snackbar = snackbar,
        actions = {
            if (selected.isNotEmpty()) {
                IconButton(onClick = { selected.clear(); selected.addAll(filtered.map { it.pkg }) }) { Icon(Icons.Rounded.SelectAll, "همه") }
                IconButton(onClick = { selected.clear() }) { Icon(Icons.Rounded.Close, "لغو") }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!Privileged.isAvailable()) {
                Text(
                    "⚠ برای اکشن‌ها دسترسی ممتاز لازم است (موتور داخلی / Shizuku / روت).",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("جستجوی نام یا پکیج…") },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("سیستمی") })
                FilterChip(selected = onlyDisabled, onClick = { onlyDisabled = !onlyDisabled }, label = { Text("غیرفعال‌ها") })
                Text("${filtered.size} برنامه", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
            }
            if (selected.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { bulk("توقف اجباری", AppOps::forceStop) }) { Text("توقف") }
                    TextButton(onClick = { bulkConfirm = "غیرفعال‌سازی" to AppOps::disable }) { Text("غیرفعال") }
                    TextButton(onClick = { bulk("فعال‌سازی", AppOps::enable) }) { Text("فعال") }
                    TextButton(onClick = { bulk("محدودیت پس‌زمینه", { p -> AppOps.backgroundRun(p, false) }) }) { Text("محدود") }
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        val isSel = app.pkg in selected
                        Surface(
                            color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) { if (isSel) selected.remove(app.pkg) else selected.add(app.pkg) }
                                    else detail = app
                                },
                                onLongClick = { if (isSel) selected.remove(app.pkg) else selected.add(app.pkg) }
                            )
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.pkg)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (!app.enabled) Text("غیرفعال", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                if (selected.isNotEmpty()) Checkbox(checked = isSel, onCheckedChange = { if (it) selected.add(app.pkg) else selected.remove(app.pkg) })
                            }
                        }
                    }
                }
            }
        }
    }

    bulkConfirm?.let { (label, op) ->
        AlertDialog(
            onDismissRequest = { bulkConfirm = null },
            title = { Text("$label ${selected.size} برنامه؟") },
            text = { Text("غیرفعال کردن برنامه‌های سیستمی حساس ممکن است باعث بوت‌لوپ یا ناپایداری شود. مطمئنی؟") },
            confirmButton = { TextButton(onClick = { bulkConfirm = null; bulk(label, op) }) { Text("بله") } },
            dismissButton = { TextButton(onClick = { bulkConfirm = null }) { Text("لغو") } }
        )
    }

    detail?.let { app ->
        AppDetailDialog(app, onDismiss = { detail = null }, onChanged = { msg ->
            scope.launch { reload(); snackbar.showSnackbar(msg) }
        })
    }
}

@Composable
fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { AppOps.icon(ctx, pkg) } }
    val bmp = icon
    if (bmp != null) Image(bmp, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
    else Icon(Icons.Rounded.Android, null, Modifier.size(40.dp))
}

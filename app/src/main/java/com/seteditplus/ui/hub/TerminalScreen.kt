package com.seteditplus.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.Privileged
import com.seteditplus.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TermLine(val text: String, val kind: Int) // 0=cmd 1=out 2=err 3=info

private val SNIPPETS = listOf(
    "id", "getprop ro.build.version.release", "settings list global | head -50",
    "pm list packages -3", "dumpsys battery", "cmd overlay list",
    "wm size", "wm density", "dumpsys deviceidle whitelist", "logcat -d -t 100",
    "top -n 1 -b | head -20", "df -h", "cmd package compile -m speed-profile -a"
)

@Composable
fun TerminalScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<TermLine>() }
    val history = remember { mutableStateListOf<String>().apply { addAll(viewModel.preferences.getTerminalHistory()) } }
    var input by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("/") }
    var running by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (lines.isEmpty()) {
            val b = withContext(Dispatchers.IO) { Privileged.best() }
            lines.add(TermLine(if (b != null) "اتصال: ${b.label}" else "⚠ هیچ دسترسی ممتازی فعال نیست — از «موتور داخلی» راه‌اندازی کن", 3))
        }
    }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }

    fun submit(cmdRaw: String) {
        val cmd = cmdRaw.trim()
        if (cmd.isEmpty() || running) return
        input = ""
        if (cmd == "clear") { lines.clear(); return }
        history.remove(cmd); history.add(cmd)
        viewModel.preferences.setTerminalHistory(history)
        lines.add(TermLine("$cwd \$ $cmd", 0))
        running = true
        scope.launch {
            val marker = "__SE_CWD__"
            val full = "cd ${Privileged.q(cwd)} 2>/dev/null; $cmd; __e=\$?; echo; echo $marker\$(pwd); exit \$__e"
            val r = withContext(Dispatchers.IO) { Privileged.exec(full, 120_000, log = LogCategory.SHELL) }
            var out = r.out
            val idx = out.lastIndexOf(marker)
            if (idx >= 0) {
                cwd = out.substring(idx + marker.length).trim().ifEmpty { cwd }
                out = out.substring(0, idx).trimEnd()
            }
            if (out.isNotEmpty()) lines.add(TermLine(out, 1))
            if (r.err.isNotBlank()) lines.add(TermLine(r.err.trimEnd(), 2))
            if (!r.ok) lines.add(TermLine("[exit ${r.exitCode}]", 3))
            running = false
        }
    }

    HubScaffold(
        title = "ترمینال ممتاز",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Rounded.History, "تاریخچه") }
            DropdownMenu(expanded = showHistory, onDismissRequest = { showHistory = false }) {
                if (history.isEmpty()) DropdownMenuItem(text = { Text("خالی") }, onClick = { showHistory = false })
                history.reversed().take(20).forEach { h ->
                    DropdownMenuItem(text = { Text(h, fontFamily = FontFamily.Monospace) }, onClick = { input = h; showHistory = false })
                }
            }
            IconButton(onClick = { copyToClipboard(ctx, lines.joinToString("\n") { it.text }) }) { Icon(Icons.Rounded.ContentCopy, "کپی همه") }
            IconButton(onClick = { lines.clear() }) { Icon(Icons.Rounded.DeleteSweep, "پاک کردن") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .background(Color(0xFF0D1117), RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    items(lines) { l ->
                        Text(
                            l.text,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, textDirection = TextDirection.Ltr),
                            color = when (l.kind) {
                                0 -> Color(0xFF7EE787)
                                2 -> Color(0xFFFF7B72)
                                3 -> Color(0xFF79C0FF)
                                else -> Color(0xFFE6EDF3)
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    if (running) item { Text("…", color = Color(0xFF79C0FF)) }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SNIPPETS.forEach { s -> AssistChip(onClick = { input = s }, label = { Text(s, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }) }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("فرمان…") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { submit(input) })
                )
                IconButton(onClick = { submit(input) }, enabled = !running) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "اجرا", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

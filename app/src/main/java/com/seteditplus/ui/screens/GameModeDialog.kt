package com.seteditplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seteditplus.data.GameMode
import com.seteditplus.data.GamePreset
import com.seteditplus.viewmodel.SettingsViewModel

/**
 * دیالوگ حالت بازی: انتخاب بازی، اعمال پروفایل و لانچ.
 */
@Composable
fun GameModeDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(GameMode.PRESETS.first()) }
    val busy by viewModel.gameBusy
    val active by viewModel.gameActivePreset

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "حالت بازی",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (active != null) {
                    Text(
                        text = "پروفایل «${active!!.label}» فعاله. وقتی از بازی برگشتی، دکمهٔ بازگردانی را بزن.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.stopGameMode()
                            onDismiss()
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بازگردانی تنظیمات")
                    }
                } else {
                    Text(
                        text = "پروفایل اعمال می‌شود، بازی باز می‌شود و تا وقتی به این اپ برگردی تنظیمات فعال می‌مانند.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GameMode.PRESETS.forEach { preset ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected.id == preset.id,
                                onClick = { selected = preset }
                            )
                            Column {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (preset.packageName != null)
                                        preset.packageName
                                    else
                                        "فقط بهینه‌سازی سراسری",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "تغییرها با خروج بازگردانی می‌شوند؛ اگر یادت رفت، ری‌استارت گوشی هم همه را به حالت اول برمی‌گرداند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (active == null) {
                Row {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Button(
                        onClick = {
                            viewModel.startGameMode(selected) { _ -> onDismiss() }
                        },
                        enabled = !busy
                    ) {
                        Text("شروع")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("بستن") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") }
        }
    )
}

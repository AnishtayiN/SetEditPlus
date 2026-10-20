package com.seteditplus.data

import android.content.Context
import android.util.Log
import com.seteditplus.adb.AdbEngine
import com.seteditplus.engine.EngineClient
import com.seteditplus.priv.Privileged
import com.seteditplus.priv.ShellResult
import com.seteditplus.shizuku.ShizukuBridge

/**
 * Repository that reads/writes Android Settings via:
 * Root (su) • Shizuku/Shizuku+/Sui (UserService) • موتور داخلی • حالت پل • حالت مستقیم
 */
class SettingsRepository(
    private val context: Context,
    private val mode: PermissionMode
) {

    fun getAll(table: SettingsTable): List<SettingsEntry> = when (mode) {
        PermissionMode.PROVIDER -> ProviderBridge.getAll(context, table)
        PermissionMode.DIRECT -> {
            val direct = DirectSettings.getAll(context, table)
            if (direct.isEmpty() && Privileged.isAvailable()) parseSettingsList(Privileged.exec(cmd("list", table)).out)
            else direct
        }
        else -> parseSettingsList(shell(cmd("list", table)).out)
    }

    fun get(table: SettingsTable, key: String): String? = when (mode) {
        PermissionMode.PROVIDER -> ProviderBridge.get(context, table, key)
        PermissionMode.DIRECT -> DirectSettings.get(context, table, key)
        else -> {
            val r = shell(cmd("get", table, key)).out.trim()
            if (r == "null" || r.isEmpty()) null else r
        }
    }

    fun put(table: SettingsTable, key: String, value: String): Boolean = when (mode) {
        PermissionMode.PROVIDER -> ProviderBridge.put(context, table, key, value)
        PermissionMode.DIRECT -> DirectSettings.put(context, table, key, value) ||
            (Privileged.isAvailable() && Privileged.exec(cmd("put", table, key, value)).ok)
        else -> shellOk(cmd("put", table, key, value))
    }

    fun delete(table: SettingsTable, key: String): Boolean = when (mode) {
        PermissionMode.PROVIDER -> ProviderBridge.delete(context, table, key)
        PermissionMode.DIRECT -> DirectSettings.delete(context, table, key) ||
            (Privileged.isAvailable() && Privileged.exec(cmd("delete", table, key)).ok)
        else -> shellOk(cmd("delete", table, key))
    }

    private fun cmd(op: String, table: SettingsTable, vararg args: String): String =
        (listOf("settings", op, table.name.lowercase()) + args.map { Privileged.q(it) }).joinToString(" ")

    private fun shellOk(command: String): Boolean {
        val r = shell(command)
        if (!r.ok) Log.w("SettingsRepository", "failed: $command -> ${r.text}")
        return r.ok && !r.text.contains("Exception") && !r.text.contains("Permission Denial")
    }

    private fun shell(command: String): ShellResult = when (mode) {
        PermissionMode.ROOT -> Privileged.execRoot(command)
        PermissionMode.SHIZUKU -> ShizukuBridge.exec(command)
        PermissionMode.ENGINE -> when {
            EngineClient.isAlive() -> EngineClient.exec(command)
            AdbEngine.isConnected() -> AdbEngine.exec(command)
            else -> Privileged.exec(command)
        }
        else -> Privileged.exec(command)
    }

    private fun parseSettingsList(raw: String): List<SettingsEntry> {
        val result = mutableListOf<SettingsEntry>()
        var currentKey: String? = null
        var currentValue: StringBuilder? = null
        for (line in raw.lines()) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                if (currentKey != null && currentValue != null) result.add(SettingsEntry(currentKey, currentValue.toString()))
                currentKey = line.substring(0, idx)
                currentValue = StringBuilder(line.substring(idx + 1))
            } else if (currentValue != null) {
                currentValue.append("\n").append(line)
            }
        }
        if (currentKey != null && currentValue != null) result.add(SettingsEntry(currentKey, currentValue.toString().trimEnd()))
        return result.sortedBy { it.key.lowercase() }
    }
}

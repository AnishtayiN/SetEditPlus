package com.seteditplus.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri

/**
 * حالت پل: نوشتن از طریق اپ Settings Database Provider (نسخهٔ AnishtayiN)
 * بدون روت و بدون شیزوکو — فقط یک pm grant به اپ پل.
 */
object ProviderBridge {

    const val PROVIDER_AUTHORITY = "com.anishtayin.provider.SettingsDatabaseProvider"
    private const val WRITE_SECURE_PATH = "/writeSecurePermission"

    private fun base(table: SettingsTable): Uri =
        Uri.parse("content://" + PROVIDER_AUTHORITY + "/" + table.name.lowercase())

    fun isAvailable(context: Context): Boolean =
        try {
            context.contentResolver.query(
                Uri.parse("content://" + PROVIDER_AUTHORITY + WRITE_SECURE_PATH),
                null, null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getInt(0) == 1 else false
            } ?: false
        } catch (e: Exception) {
            false // اپ پل نصب نیست یا پرمیشن BRIDGE ندارد
        }

    fun getAll(context: Context, table: SettingsTable): List<SettingsEntry> =
        try {
            context.contentResolver.query(base(table), null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex("name")
                val valueIdx = c.getColumnIndex("value")
                val out = mutableListOf<SettingsEntry>()
                while (c.moveToNext()) {
                    val k = if (nameIdx >= 0) c.getString(nameIdx) else continue
                    val v = if (valueIdx >= 0) c.getString(valueIdx) else ""
                    out.add(SettingsEntry(k, v))
                }
                out.sortedBy { it.key.lowercase() }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    fun get(context: Context, table: SettingsTable, key: String): String? =
        try {
            context.contentResolver.query(
                base(table), null, "name = ?", arrayOf(key), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val vi = c.getColumnIndex("value")
                    if (vi >= 0) c.getString(vi) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }

    fun put(context: Context, table: SettingsTable, key: String, value: String): Boolean =
        try {
            val cv = ContentValues(2).apply {
                put("name", key)
                put("value", value)
            }
            context.contentResolver.insert(base(table), cv)
            true
        } catch (e: Exception) {
            false
        }

    fun delete(context: Context, table: SettingsTable, key: String): Boolean =
        try {
            context.contentResolver.delete(base(table), "name = ?", arrayOf(key)) > 0
        } catch (e: Exception) {
            false
        }
}

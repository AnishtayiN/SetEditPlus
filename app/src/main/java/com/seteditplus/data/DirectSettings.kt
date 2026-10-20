package com.seteditplus.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * حالت مستقیم: اپ خودش WRITE_SECURE_SETTINGS دارد و با API رسمی می‌نویسد.
 * کلیدهای خصوصی جدول system (که اندروید برای اپ‌ها قفل کرده) در صورت وجود به موتور/شیزوکو/روت واگذار می‌شوند.
 */
object DirectSettings {

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun uri(table: SettingsTable) = Uri.parse(table.contentUri)

    fun getAll(context: Context, table: SettingsTable): List<SettingsEntry> = try {
        context.contentResolver.query(uri(table), arrayOf("name", "value"), null, null, null)?.use { c ->
            val out = ArrayList<SettingsEntry>(c.count)
            val ni = c.getColumnIndex("name")
            val vi = c.getColumnIndex("value")
            while (c.moveToNext()) {
                val k = if (ni >= 0) c.getString(ni) ?: continue else continue
                out.add(SettingsEntry(k, if (vi >= 0) c.getString(vi) ?: "" else ""))
            }
            out.sortedBy { it.key.lowercase() }
        } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    fun get(context: Context, table: SettingsTable, key: String): String? = try {
        val cr = context.contentResolver
        when (table) {
            SettingsTable.SYSTEM -> Settings.System.getString(cr, key)
            SettingsTable.SECURE -> Settings.Secure.getString(cr, key)
            SettingsTable.GLOBAL -> Settings.Global.getString(cr, key)
        }
    } catch (_: Throwable) {
        null
    }

    fun put(context: Context, table: SettingsTable, key: String, value: String): Boolean = try {
        val cr = context.contentResolver
        when (table) {
            SettingsTable.SYSTEM -> Settings.System.putString(cr, key, value)
            SettingsTable.SECURE -> Settings.Secure.putString(cr, key, value)
            SettingsTable.GLOBAL -> Settings.Global.putString(cr, key, value)
        }
    } catch (_: Throwable) {
        false
    }

    fun delete(context: Context, table: SettingsTable, key: String): Boolean = try {
        val method = "DELETE_" + table.name.lowercase()
        context.contentResolver.call(Uri.parse("content://settings"), method, key, Bundle())
        true
    } catch (_: Throwable) {
        try {
            context.contentResolver.delete(Uri.withAppendedPath(uri(table), key), null, null) > 0
        } catch (_: Throwable) {
            false
        }
    }
}

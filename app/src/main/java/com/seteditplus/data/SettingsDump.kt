package com.seteditplus.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * خروجی کامل دیتابیس تنظیمات (همان کار اسکریپت setedit-dump.sh) — از داخل اپ.
 * در حالت پل، خواندن از طریق اپ Settings Database Provider انجام می‌شود؛ بدون روت.
 */
object SettingsDump {

    fun export(context: Context, repo: SettingsRepository): String? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "setedit-all-$stamp.txt"
        val sb = StringBuilder()
        sb.appendLine("==============================")
        sb.appendLine(" SetEdit Full Settings Dump")
        sb.appendLine(" date    : ${Date()}")
        sb.appendLine(" android : ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
        sb.appendLine(" device  : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("==============================")

        for (table in SettingsTable.entries) {
            sb.appendLine()
            sb.appendLine("########## ${table.name} ##########")
            val items = try { repo.getAll(table) } catch (e: Exception) { emptyList() }
            if (items.isEmpty()) {
                sb.appendLine("(empty or unreachable)")
            } else {
                items.sortedBy { it.key.lowercase() }.forEach { e ->
                    sb.appendLine("${e.key}=${e.value}")
                }
                sb.appendLine("(count: ${items.size})")
            }
        }

        // getprop — بهترین تلاش؛ بعضی پراپرتی‌ها برای اپ‌ها فیلتر می‌شوند
        try {
            val p = ProcessBuilder("getprop").start()
            val props = p.inputStream.bufferedReader().readText()
            p.waitFor()
            sb.appendLine()
            sb.appendLine("########## BONUS: system properties (getprop) ##########")
            sb.appendLine(props)
        } catch (e: Exception) {
            sb.appendLine()
            sb.appendLine("########## getprop unavailable: ${e.message} ##########")
        }

        sb.appendLine()
        sb.appendLine("########## END ##########")

        return saveToDownloads(context, fileName, sb.toString())
    }

    private fun saveToDownloads(context: Context, fileName: String, content: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val f = File(dir, fileName)
                f.writeText(content)
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }
}

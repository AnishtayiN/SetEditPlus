package com.seteditplus.log

import android.content.Context
import com.seteditplus.SetEditApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class LogCategory(val label: String) {
    SETTING("تنظیمات"), ENGINE("موتور"), SHELL("ترمینال"), APPS("برنامه‌ها"),
    DNS("DNS"), TWEAK("ترفند"), GAME("بازی"), SYSTEM("سیستم")
}

data class LogEntry(
    val time: Long,
    val category: LogCategory,
    val title: String,
    val detail: String,
    val success: Boolean
)

/** گزارش فعالیت دائمی (تا ۵۰۰ مورد آخر) — مثل Activity Log در Shizuku+. */
object ActivityLog {
    private const val PREFS = "activity_log"
    private const val KEY = "entries"
    private const val MAX = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> get() { ensureLoaded(); return _entries }
    private var loaded = false

    private fun prefs() = SetEditApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val list = mutableListOf<LogEntry>()
        try {
            val arr = JSONArray(prefs().getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    LogEntry(
                        o.getLong("t"),
                        runCatching { LogCategory.valueOf(o.getString("c")) }.getOrDefault(LogCategory.SYSTEM),
                        o.optString("ti"), o.optString("d"), o.optBoolean("s", true)
                    )
                )
            }
        } catch (_: Throwable) {}
        _entries.value = list
    }

    @Synchronized
    fun add(category: LogCategory, title: String, detail: String = "", success: Boolean = true) {
        try {
            ensureLoaded()
            val e = LogEntry(System.currentTimeMillis(), category, title, detail.take(2000), success)
            val list = (listOf(e) + _entries.value).take(MAX)
            _entries.value = list
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("t", it.time).put("c", it.category.name).put("ti", it.title)
                    .put("d", it.detail).put("s", it.success))
            }
            prefs().edit().putString(KEY, arr.toString()).apply()
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        prefs().edit().remove(KEY).apply()
    }
}

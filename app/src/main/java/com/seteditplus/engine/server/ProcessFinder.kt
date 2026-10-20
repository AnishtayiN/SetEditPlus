package com.seteditplus.engine.server

import java.io.File

/** پیدا کردن PID پروسهٔ اصلی اپ از /proc (شل و روت هر دو به همهٔ پروسه‌ها دسترسی دارند). */
internal object ProcessFinder {

    fun findPid(processName: String, hint: Int): Int {
        if (hint > 0 && nameOf(hint) == processName) return hint
        val dirs = File("/proc").list() ?: return -1
        for (d in dirs) {
            val pid = d.toIntOrNull() ?: continue
            if (nameOf(pid) == processName) return pid
        }
        return -1
    }

    private fun nameOf(pid: Int): String? = try {
        val bytes = File("/proc/$pid/cmdline").readBytes()
        val end = bytes.indexOf(0.toByte()).let { if (it < 0) bytes.size else it }
        String(bytes, 0, end)
    } catch (_: Throwable) {
        null
    }
}

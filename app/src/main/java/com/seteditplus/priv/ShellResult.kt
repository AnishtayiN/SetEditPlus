package com.seteditplus.priv

data class ShellResult(val exitCode: Int, val out: String, val err: String) {
    val ok: Boolean get() = exitCode == 0
    val text: String get() = buildString {
        append(out)
        if (err.isNotBlank()) { if (isNotEmpty() && !endsWith("\n")) append('\n'); append(err) }
    }.trimEnd()

    companion object {
        fun fail(msg: String) = ShellResult(-1, "", msg)
    }
}

enum class BackendType(val label: String) {
    ENGINE("موتور داخلی"), SHIZUKU("Shizuku / Shizuku+"), ROOT("Root"), ADB("ADB بی‌سیم")
}

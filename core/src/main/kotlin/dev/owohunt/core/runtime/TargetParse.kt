package dev.owohunt.core.runtime

const val DISCORD_PKG = "com.discord"

private val escapeDots = Regex.escapeReplacement(DISCORD_PKG)

/**
 * Cek apakah Discord foreground dari output `dumpsys window`.
 * Contoh baris: mCurrentFocus=Window{abc123 u0 com.discord/com.discord.main.MainActivity}
 */
fun isDiscordForeground(dumpsysOutput: String): Boolean {
    val focus = Regex("mCurrentFocus=Window\\{[^}]*?\\s([\\w.]+)/(\\S+?)\\}")
        .find(dumpsysOutput)
    if (focus != null) return focus.groupValues[1] == DISCORD_PKG
    // fallback longgar kalau format berubah
    return Regex("\\b$escapeDots\\b").containsMatchIn(dumpsysOutput)
}

/**
 * Cek apakah Discord terlihat (termasuk split-screen) dari output
 * `dumpsys window windows`. Focused boleh di pane lain, yang penting
 * jendela Discord ada di layar (isOnScreen=true).
 */
fun isDiscordVisible(dumpsysWindowsOutput: String): Boolean {
    val blocks = Regex("(?=Window #\\d+)").split(dumpsysWindowsOutput)
    for (block in blocks) {
        if (!Regex("com\\.discord/").containsMatchIn(block)) continue
        if (block.contains("isOnScreen=true")) return true
    }
    return false
}

private val COMPOSER_HINTS = listOf("message_input", "composer", "message_composer", "input")

/** Attribute regex satu kali reuse. */
private val ATTR = Regex("<node\\b[^>]*>")

/**
 * Cari resource-id composer (kotak pesan) dari XML uiautomator.
 * Heuristik: node dengan resource-id com.discord:id/* yang mengandung
 * hint umum composer dan bersifat EditText / focusable.
 */
fun parseComposerId(uixml: String): String? {
    val nodes = ATTR.findAll(uixml).map { it.value }.toList()
    for (node in nodes) {
        val id = Regex("resource-id=\"([^\"]+)\"").find(node)?.groupValues?.get(1) ?: ""
        if (!id.startsWith("com.discord:id/")) continue
        val lower = id.lowercase()
        if (COMPOSER_HINTS.none { lower.contains(it) }) continue
        val className = Regex("class=\"([^\"]+)\"").find(node)?.groupValues?.get(1) ?: ""
        val focusable = node.contains("focusable=\"true\"")
        val editable = className.contains("EditText") || className.contains("RichEditText")
        if (editable || focusable) return id
    }
    // fallback: node EditText focusable pertama di com.discord
    for (node in nodes) {
        val id = Regex("resource-id=\"([^\"]+)\"").find(node)?.groupValues?.get(1) ?: ""
        if (!id.startsWith("com.discord:id/")) continue
        val className = Regex("class=\"([^\"]+)\"").find(node)?.groupValues?.get(1) ?: ""
        if (className.contains("EditText") && node.contains("focusable=\"true\"")) {
            return id
        }
    }
    return null
}

package dev.owohunt.core.runtime

private val VERIFICATION_PATTERNS = listOf(
    Regex("human\\s*verification", RegexOption.IGNORE_CASE),
    Regex("verify\\s*yourself", RegexOption.IGNORE_CASE),
    Regex("owobot\\.com/captcha", RegexOption.IGNORE_CASE),
    Regex("complete\\s+the\\s+verification", RegexOption.IGNORE_CASE),
    Regex("verification\\s+(page|link|button)", RegexOption.IGNORE_CASE),
)

private val ZERO_WIDTH = setOf(
    '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD',
)

/**
 * Buang zero-width/invisible char — pesan verifikasi OwO
 * sering diselipkan char tak terlihat supaya filter sederhana tidak cocok.
 */
fun stripZeroWidth(text: String): String = text.filter { it !in ZERO_WIDTH }

/**
 * Cek apakah teks layar (dari uiautomator dump) memuat pesan verifikasi
 * human OwO. Tag/mention ikut terbaca sebagai teks biasa — tidak menghalangi.
 */
fun looksLikeVerification(screenText: String): Boolean {
    val cleaned = stripZeroWidth(screenText)
    return VERIFICATION_PATTERNS.any { it.containsMatchIn(cleaned) }
}

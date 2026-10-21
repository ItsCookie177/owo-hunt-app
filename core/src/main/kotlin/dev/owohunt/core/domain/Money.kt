package dev.owohunt.core.domain

/**
 * Deteksi command berbau uang/cowoncy (OwO Wiki — kategori Gambling/Economy).
 * Gambling terima nominal: slots, coinflip, lottery, blackjack.
 */
private val MONEY_WORDS = setOf(
    "slots", "slot", "s", "cf", "coinflip", "coin", "flip",
    "lottery", "lotto", "bet", "blackjack", "bj", "21",
)

private val PREFIX = setOf("owo", "w", "!owo", "/")

/** true kalau teks command mengandung kata kunci judi/uang → tampilkan input jumlah koin. */
fun isMoneyCommand(text: String): Boolean {
    val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words.any { it !in PREFIX && it in MONEY_WORDS }
}

/** Saran tambah cepat: gambling (nominal) + economy (penghasil koin). */
data class MoneySuggestion(val text: String, val money: Boolean)

val MONEY_SUGGESTIONS: List<MoneySuggestion> = listOf(
    MoneySuggestion("owo slots", true),
    MoneySuggestion("owo coinflip head", true),
    MoneySuggestion("owo lottery", true),
    MoneySuggestion("owo blackjack", true),
    MoneySuggestion("owo daily", false),
    MoneySuggestion("owo quest", false),
    MoneySuggestion("owo sell all", false),
    MoneySuggestion("owo cowoncy", false),
)

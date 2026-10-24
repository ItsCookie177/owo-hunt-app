package dev.owohunt.core.domain

import kotlinx.serialization.Serializable

@Serializable
data class CommandDef(
    val id: String,
    val text: String,
    val enabled: Boolean = true,
    val maxPerSession: Int? = null,
    /** Jumlah koin untuk cmd uang (owo slots/cf/…) — di-append ke text saat kirim. */
    val amount: Long? = null,
)

@Serializable
data class Selectors(val composerId: String = "")

@Serializable
data class AppConfig(
    val minDelaySec: Long = 22,
    val maxDelaySec: Long = 48,
    val maxSessionMin: Long = 10,
    val breakEveryCmd: Int = 12,
    val breakMinSec: Long = 60,
    val breakMaxSec: Long = 120,
    val startGraceSec: Long = 10,
    val autoStopOnForegroundLoss: Boolean = true,
    /** Acak urutan pengambilan command tiap siklus. */
    val shuffleOrder: Boolean = false,
    val rotation: List<CommandDef> = listOf(),
    val selectors: Selectors = Selectors(),
    val lanToken: String = "",
)

/** Override delay saat start (UI panel). */
data class DelayOverride(
    val minDelaySec: Long? = null,
    val maxDelaySec: Long? = null,
)

enum class StopMode { NORMAL, EMERGENCY }

/** Teks final saat kirim: cmd uang → "text amount" (owo slots 100). */
fun commandText(cmd: CommandDef): String {
    val base = cmd.text.trim()
    return if (cmd.amount == null) base else "$base ${cmd.amount}"
}

/** Jeda acak antar command: uniform dalam [minSec, maxSec] ms. */
fun computeDelayMs(
    minSec: Long,
    maxSec: Long,
    random: () -> Double,
): Long {
    val lo = minOf(minSec, maxSec)
    val hi = maxOf(minSec, maxSec)
    return Math.round((lo + random() * (hi - lo)) * 1000)
}

enum class Status { IDLE, RUNNING, ERROR, CAPTCHA }

data class Stats(
    val status: Status = Status.IDLE,
    val startedAt: Long? = null,
    val lastSentAt: Long? = null,
    val totalSent: Int = 0,
    val perCommand: Map<String, Int> = emptyMap(),
    val nextCommandId: String? = null,
    val nextAt: Long? = null,
    val lastError: String? = null,
    val captchaAt: Long? = null,
)

/** Jendela pemulihan setelah captcha terdeteksi: 10 menit. */
const val CAPTCHA_WINDOW_MS: Long = 10 * 60 * 1000

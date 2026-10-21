package dev.owohunt.core

import kotlinx.serialization.Serializable

@Serializable
data class CommandDef(
    val id: String,
    val text: String,
    val enabled: Boolean = true,
    val maxPerSession: Int? = null,
    val amount: Long? = null,
)

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
    val shuffleOrder: Boolean = false,
    val rotation: List<CommandDef> = listOf(),
)

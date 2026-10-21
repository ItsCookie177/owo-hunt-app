package dev.owohunt.core.runtime

import dev.owohunt.core.domain.Status
import dev.owohunt.core.domain.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mesin status sesi + statistik (port dari session.ts).
 * Semua waktu epoch ms (ISO date dihasilkan UI/service).
 */
class Session(private val now: () -> Long = System::currentTimeMillis) {
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    val status: Status get() = _stats.value.status

    fun begin(nowMs: Long = now()) {
        _stats.value = Stats(status = Status.RUNNING, startedAt = nowMs)
    }

    fun markNext(id: String?, nextAtMs: Long? = null) {
        val s = _stats.value
        _stats.value = s.copy(
            nextCommandId = id,
            nextAt = if (nextAtMs != null) nextAtMs else s.nextAt,
        )
    }

    fun markSent(id: String) {
        val s = _stats.value
        _stats.value = s.copy(
            totalSent = s.totalSent + 1,
            perCommand = s.perCommand + (id to (s.perCommand[id] ?: 0) + 1),
            lastSentAt = now(),
        )
    }

    fun markError(msg: String) {
        _stats.value = _stats.value.copy(status = Status.ERROR, lastError = msg)
    }

    /** Status khusus: verifikasi human terdeteksi — sesi berhenti, jendela 10 menit dimulai. */
    fun markCaptcha(msg: String) {
        _stats.value = _stats.value.copy(
            status = Status.CAPTCHA,
            lastError = msg,
            captchaAt = now(),
        )
    }

    /** Verifikasi hilang dari layar — kunci dilepas, sesi siap diulang. */
    fun clearCaptcha() {
        if (_stats.value.status != Status.CAPTCHA) return
        _stats.value = _stats.value.copy(
            status = Status.IDLE,
            lastError = null,
            captchaAt = null,
        )
    }

    fun end(status: Status = Status.IDLE) {
        val s = _stats.value
        _stats.value = s.copy(
            status = status,
            nextCommandId = null,
            nextAt = null,
            lastSentAt = null,
            captchaAt = if (status == Status.CAPTCHA) s.captchaAt else null,
        )
    }

    fun snapshot(): Stats = _stats.value
}

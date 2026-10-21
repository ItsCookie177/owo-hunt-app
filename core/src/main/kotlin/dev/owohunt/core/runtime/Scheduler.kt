package dev.owohunt.core.runtime

import dev.owohunt.core.domain.CAPTCHA_WINDOW_MS
import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.DelayOverride
import dev.owohunt.core.domain.StopMode
import dev.owohunt.core.domain.Status
import dev.owohunt.core.domain.commandText
import dev.owohunt.core.domain.computeDelayMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

data class SchedulerDeps(
    val adapter: AutomationAdapter,
    val configSupplier: () -> AppConfig,
    val session: Session,
    val logger: RingLogger,
    val notifier: Notifier = NoopNotifier,
    /** Delay/sleep kirim kontrol untuk test. Default real delay. */
    val sleepFn: suspend (Long) -> Unit = { delay(it) },
    val now: () -> Long = System::currentTimeMillis,
    val random: () -> Double = Math::random,
    /** Timpa durasi grace stagger (untuk test). */
    val firstDelayMs: Long? = null,
    /** Interval cek ulang verifikasi saat sesi terkunci (default 20 dtk). */
    val captchaCheckMs: Long = 20_000,
)

data class Picked(val cmd: CommandDef, val offset: Int)

private fun isEligible(cmd: CommandDef, counts: Map<String, Int>): Boolean {
    if (!cmd.enabled) return false
    val max = cmd.maxPerSession
    return max == null || (counts[cmd.id] ?: 0) < max
}

private fun nextCommand(
    rotation: List<CommandDef>,
    counts: Map<String, Int>,
    fromIdx: Int,
): Picked? {
    val n = rotation.size
    for (i in 0 until n) {
        val cmd = rotation[(fromIdx + i) % n]
        if (isEligible(cmd, counts)) return Picked(cmd, i)
    }
    return null
}

/** Indeks eligible, diacak Fisher-Yates (pakai deps.random → test deterministik). */
private fun eligibleShuffled(
    rotation: List<CommandDef>,
    counts: Map<String, Int>,
    random: () -> Double,
): MutableList<Int> {
    val idxs = rotation.indices.filter { isEligible(rotation[it], counts) }.toMutableList()
    for (i in idxs.size - 1 downTo 1) {
        val j = (random() * (i + 1)).toInt()
        val tmp = idxs[i]
        idxs[i] = idxs[j]
        idxs[j] = tmp
    }
    return idxs
}

/**
 * Ambil berikutnya dari antrean acak; antrean habis → isi ulang shuffle.
 * Skip cmd yang kehabisan limit/disabled sejak antrean dibuat.
 */
private fun pickShuffled(
    rotation: List<CommandDef>,
    counts: Map<String, Int>,
    queue: MutableList<Int>,
    random: () -> Double,
): Picked? {
    while (true) {
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            if (isEligible(rotation[i], counts)) return Picked(rotation[i], i)
        }
        val fresh = eligibleShuffled(rotation, counts, random)
        if (fresh.isEmpty()) return null
        queue.addAll(fresh)
    }
}

/** Peek kandidat berikutnya tanpa mengonsumsi antrean (untuk countdown). */
private fun peekShuffled(
    rotation: List<CommandDef>,
    counts: Map<String, Int>,
    queue: List<Int>,
): Picked? {
    val i = queue.firstOrNull { isEligible(rotation[it], counts) }
    if (i != null) return Picked(rotation[i], i)
    val first = rotation.firstOrNull { isEligible(it, counts) }
    return first?.let { Picked(it, 0) }
}

/**
 * Port 1:1 dari scheduler.ts web — coroutine loop dengan target waktu
 * absolut nextSendAt (dump/focus check tidak menumpuk di atas delay).
 */
class Scheduler(private val deps: SchedulerDeps) {

    private val controller = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var captchaWatch: Job? = null
    private var captchaExpiredNotified = false

    val isRunning: Boolean
        get() = deps.session.status == Status.RUNNING

    private suspend fun captchaStillPresent(): Boolean {
        val dump = try {
            deps.adapter.dumpUi()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return dump != null && looksLikeVerification(dump)
    }

    private fun startCaptchaWatch() {
        if (captchaWatch?.isActive == true) return
        captchaWatch = controller.launch {
            while (isActive) {
                val session = deps.session
                if (session.status != Status.CAPTCHA) break
                val at = session.snapshot().captchaAt
                if (at != null && !captchaExpiredNotified && deps.now() - at > CAPTCHA_WINDOW_MS) {
                    captchaExpiredNotified = true
                    deps.logger.error(
                        "jendela verifikasi 10 menit habis — pantau akun (risiko ban)",
                    )
                    runCatching {
                        deps.notifier.notify(
                            "OwO Macro: waktu verifikasi habis",
                            "Jendela 10 menit habis — pantau akunmu di owobot.com",
                        )
                    }
                }
                if (!captchaStillPresent()) {
                    session.clearCaptcha()
                    stopCaptchaWatch()
                    deps.logger.info(
                        "verifikasi human selesai — kunci dilepas, tekan Start untuk lanjut",
                    )
                    runCatching {
                        deps.notifier.notify(
                            "OwO Macro: verifikasi selesai",
                            "Kunci dilepas — tekan Start untuk lanjut sesi",
                        )
                    }
                    break
                }
                delay(deps.captchaCheckMs)
            }
        }
    }

    private fun stopCaptchaWatch() {
        captchaWatch?.cancel()
        captchaWatch = null
        captchaExpiredNotified = false
    }

    @Throws(SchedulerStartException::class)
    suspend fun start(delayOverride: DelayOverride? = null) {
        val session = deps.session
        if (session.status == Status.RUNNING) {
            throw SchedulerStartException("sesi sedang berjalan")
        }
        if (session.status == Status.CAPTCHA) {
            if (captchaStillPresent()) {
                throw SchedulerStartException(
                    "verifikasi human masih di layar — selesaikan dulu di owobot.com/captcha",
                )
            }
            // Dump gagal/tak ada: tidak bisa buktikan masih ada → izinkan;
            // deteksi per-tick tetap jaga sebelum kirim pertama.
            session.clearCaptcha()
            stopCaptchaWatch()
        }
        if (!deps.adapter.isAvailable()) {
            throw SchedulerStartException("adapter UI automation tidak tersedia")
        }
        // Preflight: Discord cukup terlihat (split-screen OK).
        // Cek focus strict dijalankan per tick sebelum tiap kirim.
        val visible = deps.adapter.checkVisible()
        if (!visible) {
            throw SchedulerStartException(
                "Discord tidak terlihat — buka Discord di channel OwO dulu lalu Start lagi",
            )
        }

        val stored = deps.configSupplier()
        val config = stored.copy(
            minDelaySec = delayOverride?.minDelaySec ?: stored.minDelaySec,
            maxDelaySec = delayOverride?.maxDelaySec ?: stored.maxDelaySec,
        )
        if (config.maxDelaySec < config.minDelaySec) {
            throw SchedulerStartException("maxDelaySec harus >= minDelaySec")
        }

        stopCaptchaWatch()
        session.begin()
        // Layar tetap nyala selama sesi (timeout disimpan, dipulihkan di akhir).
        try {
            deps.adapter.keepScreenAwake()
        } catch (e: Exception) {
            deps.logger.warn("keep screen awake gagal: $e")
        }
        deps.logger.info(
            "session started (delay ${config.minDelaySec}-${config.maxDelaySec}s, adapter ${deps.adapter.name})",
        )
        loopJob = controller.launch {
            try {
                loop(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deps.logger.error("scheduler crash: $e")
                deps.session.markError("$e")
                deps.session.end(Status.ERROR)
            }
        }
    }

    suspend fun stop(mode: StopMode = StopMode.NORMAL) {
        val job = loopJob ?: return
        job.cancel()
        if (mode == StopMode.EMERGENCY) {
            deps.logger.warn("emergency stop")
            try {
                deps.adapter.clearComposer()
            } catch (e: Exception) {
                deps.logger.warn("clear composer gagal: $e")
            }
        } else {
            deps.logger.info("stopped by user")
        }
        runCatching { loopJob?.cancelAndJoin() }
        loopJob = null
        if (deps.session.status == Status.RUNNING) deps.session.end()
    }

    private suspend fun loop(config: AppConfig) {
        val adapter = deps.adapter
        val session = deps.session
        val logger = deps.logger
        val now = deps.now
        val random = deps.random

        val counts = mutableMapOf<String, Int>()
        val deadline = now() + config.maxSessionMin * 60_000
        // Stagger: grace pindah-fokus + acak 0-8 dtk tambahan, anti pola serempak.
        val firstDelay = deps.firstDelayMs
            ?: (config.startGraceSec * 1000 + Math.round(random() * 8000))
        var failures = 0
        var idx = 0
        // Antrean acak tiap siklus (shuffleOrder) — kosong = belum diisi.
        val queue = mutableListOf<Int>()
        // Target waktu kirim berikutnya — cek focus/dump yang makan waktu
        // di depan tidak lagi menumpuk di atas delay (interval tetap akurat).
        var nextSendAt = 0L

        try {
            if (firstDelay > 0) {
                logger.info(
                    "kirim pertama dalam ~${Math.round(firstDelay / 1000.0)} dtk — pindah fokus ke Discord sekarang",
                )
                deps.sleepFn(firstDelay)
            }

            while (true) {
                // Deteksi verifikasi human di layar; dump gagal = lewati cek, jangan stop.
                val dumped = try {
                    adapter.dumpUi()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // uiautomator bisa flaky (animasi/lock) — skip cek tick ini
                }
                if (dumped != null && looksLikeVerification(dumped)) {
                    logger.error(
                        "VERIFIKASI HUMAN terdeteksi di layar — auto-stop. Selesaikan manual dalam 10 menit: owobot.com/captcha",
                    )
                    session.markCaptcha(
                        "verifikasi human terdeteksi — auto-stop, selesaikan dalam 10 menit",
                    )
                    runCatching {
                        deps.notifier.notify(
                            "OwO Macro: VERIFIKASI HUMAN",
                            "Sisa waktu 10 menit — sekarang di owobot.com/captcha",
                        )
                    }
                    startCaptchaWatch()
                    return
                }

                val picked = if (config.shuffleOrder) {
                    pickShuffled(config.rotation, counts, queue, random)
                } else {
                    nextCommand(config.rotation, counts, idx)
                }
                if (picked == null) {
                    logger.info("tidak ada command tersisa (limit habis/disabled) — sesi selesai")
                    return
                }
                if (!config.shuffleOrder) {
                    idx = (idx + picked.offset + 1) % config.rotation.size
                }
                session.markNext(picked.cmd.id)
                val text = commandText(picked.cmd)

                if (config.autoStopOnForegroundLoss && !adapter.checkForeground()) {
                    logger.warn("Discord kehilangan foreground — auto-stop")
                    return
                }
                if (now() > deadline) {
                    logger.warn(
                        "session cap ${config.maxSessionMin} menit tercapai — auto-stop",
                    )
                    return
                }

                // Micro-break: ditambahkan ke target berikutnya, bukan sleep terpisah.
                val totalSent = counts.values.sum()
                if (config.breakEveryCmd > 0 && totalSent > 0 && totalSent % config.breakEveryCmd == 0) {
                    val breakMs = computeDelayMs(
                        config.breakMinSec,
                        config.breakMaxSec,
                        random,
                    )
                    nextSendAt += breakMs
                    logger.info(
                        "micro-break ~${Math.round(breakMs / 1000.0)} dtk ($totalSent command terkirim)",
                    )
                }

                val wait = nextSendAt - now()
                if (wait > 0) {
                    deps.sleepFn(wait)
                }

                try {
                    adapter.sendCommand(text)
                    counts[picked.cmd.id] = (counts[picked.cmd.id] ?: 0) + 1
                    failures = 0
                    session.markSent(picked.cmd.id)
                    logger.info("sent: $text")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += 1
                    logger.error("gagal kirim \"$text\" ($failures/3): $e")
                    if (failures >= 3) {
                        session.markError("3x gagal kirim berturut-turut: $e")
                        return
                    }
                }

                val upcoming = if (config.shuffleOrder) {
                    peekShuffled(config.rotation, counts, queue)
                } else {
                    nextCommand(config.rotation, counts, idx)
                }
                nextSendAt = now() + computeDelayMs(
                    config.minDelaySec,
                    config.maxDelaySec,
                    random,
                )
                session.markNext(upcoming?.cmd?.id, nextSendAt)
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    adapter.restoreScreenTimeout()
                } catch (_: Exception) {
                    // best effort
                }
                if (session.status == Status.RUNNING) session.end()
            }
        }
    }
}

class SchedulerStartException(message: String) : Exception(message)

/** Helper test: tunggu hingga kondisi terpenuhi (polling 5 ms). */
suspend fun waitFor(cond: () -> Boolean, timeoutMs: Long = 2000) {
    val start = System.currentTimeMillis()
    while (!cond()) {
        if (System.currentTimeMillis() - start > timeoutMs) error("waitFor timeout")
        delay(5)
    }
}

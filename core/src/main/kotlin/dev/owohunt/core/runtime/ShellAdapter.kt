package dev.owohunt.core.runtime

import kotlinx.coroutines.delay

/**
 * Adapter berbasis shell (port logika gabungan AdbAdapter + ShizukuAdapter web).
 * Semua perintah & parsing 1:1 dari web; runner yang beda (adb/rish/Shizuku)
 * dibawa lewat [ShellRunner].
 */
open class ShellAutomationAdapter(
    private val runner: ShellRunner,
    override val name: String = "shell",
    /** Retry restart-once saat runner mati/timeout (paritas web shizuku). */
    restartOnce: Boolean = false,
    private val dumpAttempts: Int = 3,
    private val dumpRetryDelayMs: Long = 1000,
    private val sleepFn: suspend (Long) -> Unit = { delay(it) },
) : AutomationAdapter {

    private var restartUsed = false
    private var savedTimeout: String? = null

    private suspend fun run(cmd: String, timeoutMs: Long = 15_000): ShellResult {
        val res = runner.exec(cmd, timeoutMs)
        if (res.exitCode != 0) {
            throw ShellFailure(cmd, res.exitCode, res.stdout)
        }
        return res
    }

    private suspend fun runWithRestart(cmd: String, timeoutMs: Long = 15_000): ShellResult {
        try {
            return run(cmd, timeoutMs)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (restartUsed || !restartOnce) throw e
            val msg = e.message ?: ""
            if (!Regex("berhenti|tidak tersedia|timeout", RegexOption.IGNORE_CASE).containsMatchIn(msg)) throw e
            restartUsed = true
            return run(cmd, timeoutMs)
        }
    }


    override suspend fun isAvailable(): Boolean = try {
        val out = run("echo ok", 10_000)
        out.stdout.contains("ok")
    } catch (_: Exception) {
        false
    }

    override suspend fun checkForeground(): Boolean {
        val out = runWithRestart(
            "dumpsys window 2>/dev/null | grep -E \"mCurrentFocus|mFocusedApp\"",
        )
        return isDiscordForeground(out.stdout)
    }

    override suspend fun checkVisible(): Boolean {
        val out = runWithRestart("dumpsys window windows 2>/dev/null", 20_000)
        return isDiscordVisible(out.stdout)
    }

    override suspend fun sendCommand(text: String) {
        val parts = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (i in parts.indices) {
            runWithRestart("input text ${shellQuote(parts[i])}")
            if (i < parts.size - 1) {
                runWithRestart("input text keyevent SPACE") // placeholder diganti di bawah
            }
        }
        runWithRestart("input keyevent 66") // ENTER
    }

    override suspend fun clearComposer() {
        try {
            runWithRestart("input keyevent 111") // ESC
            repeat(3) { runWithRestart("input keyevent 67") } // DEL
        } catch (e: Exception) {
            // best effort
        }
    }

    override suspend fun dumpUi(): String? {
        var lastErr: Exception? = null
        for (i in 0 until dumpAttempts) {
            try {
                val out = runWithRestart(
                    "rm -f /sdcard/owo_ui.xml; uiautomator dump /sdcard/owo_ui.xml 2>&1; cat /sdcard/owo_ui.xml 2>/dev/null",
                    30_000,
                )
                if (out.stdout.contains("<hierarchy") || out.stdout.contains("<node")) {
                    return out.stdout
                }
                lastErr = IllegalStateException(
                    "dump tidak berisi hierarchy: ${out.stdout.take(120)}",
                )
            } catch (e: Exception) {
                lastErr = e
            }
            sleepFn(dumpRetryDelayMs)
        }
        throw lastErr ?: IllegalStateException("uiautomator dump gagal")
    }

    override suspend fun keepScreenAwake() {
        if (savedTimeout != null) return // sudah aktif
        val out = runWithRestart("settings get system screen_off_timeout")
        val value = out.stdout.trim()
        savedTimeout = if (Regex("^\\d+$").matches(value)) value else "30000"
        // 15 menit — menutup maxSessionMin default + margin
        runWithRestart("settings put system screen_off_timeout 900000")
    }

    override suspend fun restoreScreenTimeout() {
        val original = savedTimeout ?: return
        savedTimeout = null
        runWithRestart("settings put system screen_off_timeout $original")
    }
}

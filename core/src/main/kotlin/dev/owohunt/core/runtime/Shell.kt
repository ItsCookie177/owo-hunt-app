package dev.owohunt.core.runtime

/** Hasil eksekusi shell. */
data class ShellResult(val stdout: String, val exitCode: Int)

/**
 * Runner shell yang dipakai adapter Android (Shizuku/rish).
 * Implementasi Android di modul :automation.
 */
interface ShellRunner {
    /**
     * Eksekusi satu perintah.
     * - exit != 0 → throw ShellFailure
     * - timeout → ShellTimeout
     */
    suspend fun exec(cmd: String, timeoutMs: Long = 15_000): ShellResult
}

class ShellFailure(cmd: String, exitCode: Int, detail: String) :
    Exception("perintah gagal (exit $exitCode): ${cmd.take(80)} — ${detail.take(200)}")

class ShellTimeout(cmd: String) :
    Exception("timeout menjalankan: ${cmd.take(80)}")

/** Kutip argumen shell dengan aman ('owo slots' dll). */
fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/**
 * Protokol sentinel dipakai sesi shell persisten (rish):
 * kirim `<cmd>; echo "__OWO_EXIT_<id>:$?"`, baris balik berakhir `:<code>`.
 * Fungsi ini mengurai baris sentinel → pasangan (stdout, exitCode).
 */
fun parseExitLine(afterLine: String): Int {
    val m = Regex(":(\\d+)\\s*$").find(afterLine)
    return m?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

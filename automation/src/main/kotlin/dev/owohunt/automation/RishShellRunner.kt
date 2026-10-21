package dev.owohunt.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.owohunt.core.runtime.ShellResult
import dev.owohunt.core.runtime.ShellRunner
import dev.owohunt.core.runtime.ShellFailure
import dev.owohunt.core.runtime.ShellTimeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runner via biner `rish` (diekspor dari aplikasi Shizuku).
 * Satu proses per perintah (paritas AdbAdapter); env RISH_APPLICATION_ID
 * membuat rish memakai Shizuku aplikasi ini sebagai host.
 */
class RishShellRunner(
    private val rishPath: String,
    private val applicationId: String,
) : ShellRunner {

    init {
        require(File(rishPath).exists()) { "rish tidak ditemukan: $rishPath" }
    }

    override suspend fun exec(cmd: String, timeoutMs: Long): ShellResult =
        withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(rishPath, "sh", "-c", cmd)
            val env = pb.environment()
            env["RISH_APPLICATION_ID"] = applicationId
            env["CLASSPATH"] = "" // hindari warisan Termux
            val p = pb.start()

            val stdoutBuf = StringBuilder()
            val errBuf = StringBuilder()
            val outThread = Thread {
                p.inputStream.bufferedReader().useLines { l -> l.forEach { stdoutBuf.appendLine(it) } }
            }
            val errThread = Thread {
                p.errorStream.bufferedReader().useLines { l -> l.forEach { errBuf.appendLine(it) } }
            }
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()

            val finished = withTimeoutOrNull(timeoutMs) {
                p.waitFor()
            }
            if (finished == null) {
                p.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                throw ShellTimeout(cmd)
            }
            outThread.join(500)
            errThread.join(500)
            val code = p.exitValue()
            if (code != 0) {
                throw ShellFailure(cmd, code, errBuf.toString().ifBlank { stdoutBuf.toString() })
            }
            ShellResult(stdoutBuf.toString(), code)
        }
}

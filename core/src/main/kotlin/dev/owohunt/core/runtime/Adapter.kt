package dev.owohunt.core.runtime

/**
 * Abstraksi UI automation (port dari adapters/types.ts web).
 * Implementasi Android (Shizuku) di modul :automation.
 */
interface AutomationAdapter {
    val name: String

    suspend fun isAvailable(): Boolean

    /** Discord focus input (strict) — dipakai sebelum tiap kirim. */
    suspend fun checkForeground(): Boolean

    /** Discord terlihat di layar (boleh split-screen, fokus di pane lain). */
    suspend fun checkVisible(): Boolean = checkForeground()

    suspend fun sendCommand(text: String)

    suspend fun clearComposer()

    /** Dump UI hierarchy (uiautomator). Null/throw = dump tidak tersedia. */
    suspend fun dumpUi(): String?

    /** Tahan layar nyala selama sesi (simpan timeout asli dulu). */
    suspend fun keepScreenAwake() {}

    /** Kembalikan timeout layar seperti semula. */
    suspend fun restoreScreenTimeout() {}
}

/** Notifikasi OS (dipakai saat captcha terdeteksi / jendela habis). */
interface Notifier {
    suspend fun notify(title: String, content: String)
}

object NoopNotifier : Notifier {
    override suspend fun notify(title: String, content: String) = Unit
}

/** Kontroler opsi test (port dari MockAdapter web). */
open class MockAdapter(
    var available: Boolean = true,
    var foreground: Boolean = true,
    var failAllSends: Boolean = false,
    var screenText: String = "",
    var dumpFailure: Boolean = false,
) : AutomationAdapter {
    override val name = "mock"

    val sent = mutableListOf<String>()
    var cleared = 0
    var wakeKept = 0
    var wakeRestored = 0

    override suspend fun isAvailable() = available
    open override suspend fun checkForeground(): Boolean = foreground
    override suspend fun checkVisible() = foreground

    override suspend fun sendCommand(text: String) {
        if (failAllSends) throw IllegalStateException("mock: send gagal")
        sent += text
    }

    override suspend fun clearComposer() {
        cleared += 1
    }

    override suspend fun dumpUi(): String? {
        if (dumpFailure) throw IllegalStateException("mock: dump gagal")
        return screenText
    }

    override suspend fun keepScreenAwake() {
        wakeKept += 1
    }

    override suspend fun restoreScreenTimeout() {
        wakeRestored += 1
    }
}

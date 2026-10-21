package dev.owohunt.core.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Port paritas dari adb.test.ts + shizuku.test.ts web (fake runner). */
class ShellAdapterTest {

    /** Runner fake: rekam perintah, jawab dari daftar hasil. */
    private class FakeRunner : ShellRunner {
        val cmds = mutableListOf<String>()
        private val queue = ArrayDeque<(String) -> ShellResult>()
        var default: (String) -> ShellResult = { ShellResult("", 0) }
        var throwOnNext: IOException? = null

        fun push(result: (String) -> ShellResult) = queue.add(result)

        override suspend fun exec(cmd: String, timeoutMs: Long): ShellResult {
            cmds.add(cmd)
            throwOnNext?.let { throw it.also { _ -> throwOnNext = null } }
            return if (queue.isNotEmpty()) queue.removeFirst()(cmd) else default(cmd)
        }
    }

    private fun adapter(
        runner: FakeRunner,
        restartOnce: Boolean = false,
        dumpAttempts: Int = 1,
        dumpRetryDelayMs: Long = 0,
        sleepFn: suspend (Long) -> Unit = {},
    ) = ShellAutomationAdapter(
        runner = runner,
        name = "shell",
        restartOnce = restartOnce,
        dumpAttempts = dumpAttempts,
        dumpRetryDelayMs = dumpRetryDelayMs,
        sleepFn = sleepFn,
    )

    @Test
    fun `shellQuote aman untuk argumen biasa`() {
        assertEquals("'owo'", shellQuote("owo"))
        assertEquals("'owo slots'", shellQuote("owo slots"))
    }

    @Test
    fun `quote apostrof di-escape`() {
        assertEquals("'it('\\''s'", shellQuote("it's"))
    }

    @Test
    fun `parseExitLine ambil kode exit`() {
        assertEquals(0, parseExitLine("__OWO_EXIT_ab:0"))
        assertEquals(3, parseExitLine("__OWO_EXIT_ab:3"))
        assertEquals(7, parseExitLine("foo __OWO_EXIT_cd:7 "))
        assertEquals(0, parseExitLine("no code here"))
    }

    @Test
    fun `sendCommand pecah kata, spasi di sela, enter di akhir`() = runBlocking {
        val r = FakeRunner()
        adapter(r).sendCommand("owo slots")
        assertEquals(
            listOf(
                "input text 'owo'",
                "input keyevent 62",
                "input text 'slots'",
                "input keyevent 66",
            ),
            r.cmds,
        )
        Unit
    }

    @Test
    fun `sendCommand dengan apostrof di-escape`() = runBlocking {
        val r = FakeRunner()
        adapter(r).sendCommand("owo hunt")
        assertEquals("input text 'owo'", r.cmds[0])
        Unit
    }

    @Test
    fun `clearComposer ESC dan 3x DEL`() = runBlocking {
        val r = FakeRunner()
        adapter(r).clearComposer()
        assertEquals(
            listOf("input keyevent 111", "input keyevent 67", "input keyevent 67", "input keyevent 67"),
            r.cmds,
        )
        Unit
    }

    @Test
    fun `isAvailable true ketika ok`() = runBlocking {
        assertTrue(adapter(FakeRunner()).isAvailable())
        Unit
    }

    @Test
    fun `isAvailable false ketika exit 1`() = runBlocking {
        val r = FakeRunner().apply { default = { ShellResult("", 1) } }
        assertFalse(adapter(r).isAvailable())
        Unit
    }

    @Test
    fun `dumpUi valid langsung balik`() = runBlocking {
        val xml = "<hierarchy><node text=\'a\'/></hierarchy>"
        val r = FakeRunner().apply { default = { ShellResult(xml, 0) } }
        assertEquals(xml, adapter(r).dumpUi())
        Unit
    }

    @Test
    fun `dumpUi invalid diulang lalu gagal dengan error terakhir`() = runBlocking {
        val sleeps = mutableListOf<Long>()
        val r = FakeRunner().apply { default = { ShellResult("empty", 0) } }
        try {
            adapter(r, dumpAttempts = 3, dumpRetryDelayMs = 10, sleepFn = { sleeps.add(it) }).dumpUi()
            fail("harusnya gagal")
        } catch (e: Exception) {
            assertTrue((e.message ?: "").contains("dump tidak berisi hierarchy"))
            assertEquals(3, r.cmds.size)
            assertEquals(listOf(10L, 10L, 10L), sleeps)
        }
        Unit
    }

    @Test
    fun `screen timeout disimpan lalu dipulihkan`() = runBlocking {
        val r = FakeRunner(
            // urutan: get → put 900000 → restore
        ).apply {
            push { ShellResult("32000\n", 0) }
            push { ShellResult("", 0) }
            push { ShellResult("", 0) }
        }
        val a = adapter(r)
        a.keepScreenAwake()
        assertEquals(listOf("settings get system screen_off_timeout", "settings put system screen_off_timeout 900000"), r.cmds)
        a.restoreScreenTimeout()
        assertEquals("settings put system screen_off_timeout 32000", r.cmds.last())
        r.cmds.clear()
        a.restoreScreenTimeout() // sudah kosong — no-op
        assertTrue(r.cmds.isEmpty())
        Unit
    }

    @Test
    fun `exit non-nol melempar ShellFailure`() = runBlocking {
        val r = FakeRunner().apply { default = { ShellResult("muh error", 3) } }
        try {
            adapter(r).checkForeground()
            fail("harusnya gagal")
        } catch (e: ShellFailure) {
            assertTrue((e.message ?: "").contains("exit 3"))
        }
        Unit
    }

    @Test
    fun `timeout boleh restart 1x saat restartOnce`() = runBlocking {
        val r = FakeRunner().apply {
            throwOnNext = ShellTimeout("dumpsys")
            default = { ShellResult("mCurrentFocus=Window{a u0 com.discord/com.discord.main.MainActivity}", 0) }
        }
        val a = adapter(r, restartOnce = true)
        assertTrue(a.checkForeground())
        // 2 eksekusi: attempt pertama timeout, attempt kedua sukses
        assertEquals(2, r.cmds.size)
        Unit
    }

    @Test
    fun `timeout tidak restart saat restartOnce mati`() = runBlocking {
        val r = FakeRunner().apply { throwOnNext = ShellTimeout("dumpsys") }
        val a = adapter(r, restartOnce = false)
        try {
            a.checkForeground()
            fail("harusnya gagal")
        } catch (_: ShellTimeout) {
            // selesai
        }
        assertEquals(1, r.cmds.size)
        Unit
    }

    @Test
    fun `checkForeground parsing dipakai`() = runBlocking {
        val r = FakeRunner().apply {
            default = { ShellResult("mCurrentFocus=Window{a u0 com.discord/com.discord.main.MainActivity}", 0) }
        }
        assertTrue(adapter(r).checkForeground())
        Unit
    }

    @Test
    fun `checkVisible parsing dipakai`() = runBlocking {
        val r = FakeRunner().apply {
            default = { ShellResult("Window #1 Window{a u0 com.discord/.Main}: isOnScreen=true", 0) }
        }
        assertTrue(adapter(r).checkVisible())
        Unit
    }
}

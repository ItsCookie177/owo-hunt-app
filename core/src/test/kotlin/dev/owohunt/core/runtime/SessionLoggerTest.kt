package dev.owohunt.core.runtime

import dev.owohunt.core.domain.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerTest {
    @Test
    fun `level tertulis di entri`() {
        val logger = RingLogger()
        logger.info("a")
        logger.warn("b")
        logger.error("c")
        assertEquals(
            listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            logger.entries.map { it.level },
        )
    }

    @Test
    fun `ring buffer dibatasi`() {
        val logger = RingLogger(maxEntries = 5)
        repeat(7) { logger.info("m$it") }
        assertEquals(5, logger.entries.size)
        assertEquals(listOf("m2", "m3", "m4", "m5", "m6"), logger.entries.map { it.msg })
    }

    @Test
    fun `clear mengosongkan`() {
        val logger = RingLogger()
        logger.info("x")
        logger.clear()
        assertTrue(logger.entries.isEmpty())
    }

    @Test
    fun `stream mengirim realtime`() = runBlocking {
        val logger = RingLogger()
        val received = CompletableDeferred<LogEntry>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            received.complete(logger.stream.first())
        }
        logger.warn("hai")
        val entry = received.await()
        assertEquals("hai", entry.msg)
        assertEquals(LogLevel.WARN, entry.level)
    }
}

class SessionTest {
    @Test
    fun `transisi status begin - sent - end`() {
        val session = Session()
        assertEquals(Status.IDLE, session.status)
        session.begin()
        assertEquals(Status.RUNNING, session.status)
        session.markNext("hunt")
        session.markSent("hunt")
        session.markSent("hunt")
        session.markSent("cf")
        val s = session.snapshot()
        assertEquals(3, s.totalSent)
        assertEquals(2, s.perCommand["hunt"])
        assertEquals(1, s.perCommand["cf"])
        assertNotNull(s.lastSentAt)
        session.end()
        assertEquals(Status.IDLE, session.status)
        assertNull(session.snapshot().nextCommandId)
        assertNull(session.snapshot().lastSentAt)
    }

    @Test
    fun `captcha latch dan lepas kunci`() {
        val session = Session()
        session.begin()
        session.markCaptcha("terdeteksi")
        assertEquals(Status.CAPTCHA, session.status)
        assertNotNull(session.snapshot().captchaAt)
        // clearCaptcha saat tidak captcha = no-op
        session.end()
        session.clearCaptcha()
        assertEquals(Status.IDLE, session.status)
        assertNull(session.snapshot().captchaAt)
        session.begin()
        session.markCaptcha("lagi")
        session.clearCaptcha()
        assertEquals(Status.IDLE, session.status)
        assertNull(session.snapshot().lastError)
    }

    @Test
    fun `markError menyalakan status error`() {
        val session = Session()
        session.markError("boom")
        assertEquals(Status.ERROR, session.status)
        assertEquals("boom", session.snapshot().lastError)
    }

    private fun assertNotNull(v: Any?) = assertTrue(v != null)
}

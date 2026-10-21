package dev.owohunt.core.runtime

import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.Status
import dev.owohunt.core.domain.StopMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port 1:1 dari scheduler.test.ts web. */
class SchedulerTest {

    private fun testConfig(
        rotation: List<CommandDef>? = null,
        overrides: AppConfig.() -> AppConfig = { this },
    ): AppConfig {
        val base = listOf(
            CommandDef("hunt", "owo hunt", maxPerSession = null),
            CommandDef("battle", "owo battle", maxPerSession = null),
        )
        return AppConfig(
            breakEveryCmd = 0,
            rotation = rotation ?: base,
        ).overrides()
    }

    private class Harness(
        val scheduler: Scheduler,
        val session: Session,
        val logger: RingLogger,
        val adapter: MockAdapter,
        val sleeps: MutableList<Long> = mutableListOf(),
        val nowHold: LongArray? = null,
    )

    private fun makeScheduler(
        config: AppConfig,
        adapter: MockAdapter = MockAdapter(),
        random: (() -> Double)? = null,
        captchaCheckMs: Long? = null,
        realDelay: Boolean = false,
        fakeNow: LongArray? = null,
        onSleepCustom: (suspend (Long) -> Unit)? = null,
    ): Harness {
        val session = Session()
        val logger = RingLogger()
        val sleeps = mutableListOf<Long>()
        val scheduler = Scheduler(
            SchedulerDeps(
                adapter = adapter,
                configSupplier = { config },
                session = session,
                logger = logger,
                sleepFn = onSleepCustom ?: { ms ->
                    sleeps.add(ms)
                    if (realDelay) delay(ms)
                },
                now = { fakeNow?.get(0) ?: System.currentTimeMillis() },
                random = random ?: { Math.random() },
                firstDelayMs = 0,
                captchaCheckMs = captchaCheckMs ?: 20_000,
            ),
        )
        return Harness(scheduler, session, logger, adapter, sleeps, fakeNow)
    }

    @Test
    fun `rotasi berurutan sampai semua limit habis`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("hunt", "owo hunt", maxPerSession = 3),
                    CommandDef("cf", "owo cf", maxPerSession = 2),
                ),
            ),
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(
            listOf("owo hunt", "owo cf", "owo hunt", "owo cf", "owo hunt"),
            h.adapter.sent,
        )
        assertEquals(5, h.session.snapshot().totalSent)
        assertEquals(3, h.session.snapshot().perCommand["hunt"])
        assertEquals(2, h.session.snapshot().perCommand["cf"])
    }

    @Test
    fun `skip command yang disabled`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("hunt", "owo hunt", maxPerSession = 2),
                    CommandDef("battle", "owo battle", enabled = false),
                ),
            ),
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(listOf("owo hunt", "owo hunt"), h.adapter.sent)
    }

    @Test
    fun `delay tiap tick dalam rentang min-max akurat tanpa overhead`() = runBlocking {
        val fakeNow = longArrayOf(0)
        val session = Session()
        val logger = RingLogger()
        val sleeps = mutableListOf<Long>()
        val scheduler = Scheduler(
            SchedulerDeps(
                adapter = MockAdapter(),
                configSupplier = {
                    testConfig(
                        listOf(CommandDef("hunt", "owo hunt", maxPerSession = 3)),
                    ) { copy(minDelaySec = 20, maxDelaySec = 40) }
                },
                session = session,
                logger = logger,
                sleepFn = { ms ->
                    sleeps.add(ms)
                    fakeNow.set(0, fakeNow[0] + ms)
                },
                now = { fakeNow[0] },
                random = { 0.25 }, // 20 + 0.25*20 = 25s
                firstDelayMs = 0,
            ),
        )
        scheduler.start()
        waitFor { session.status == Status.IDLE }
        // kirim pertama langsung; sisanya tepat 25s (cek focus/dump tidak menumpuk)
        assertEquals(listOf(25_000L, 25_000L), sleeps)
        Unit
    }

    @Test
    fun `micro-break terpicu tiap N command`() = runBlocking {
        val fakeNow = longArrayOf(0)
        val session = Session()
        val logger = RingLogger()
        val sleeps = mutableListOf<Long>()
        val scheduler = Scheduler(
            SchedulerDeps(
                adapter = MockAdapter(),
                configSupplier = {
                    testConfig(
                        listOf(CommandDef("hunt", "owo hunt", maxPerSession = 4)),
                    ) { copy(breakEveryCmd = 2, breakMinSec = 60, breakMaxSec = 60) }
                },
                session = session,
                logger = logger,
                sleepFn = { ms ->
                    sleeps.add(ms)
                    fakeNow.set(0, fakeNow[0] + ms)
                },
                now = { fakeNow[0] },
                random = { 0.0 }, // delay = min (22s) tiap saat
                firstDelayMs = 0,
            ),
        )
        scheduler.start()
        waitFor { session.status == Status.IDLE }
        // tick2: 22s; tick3: break 60s + 22s = 82s; tick4: 22s; tick5 limit habis
        assertEquals(listOf(22_000L, 82_000L, 22_000L), sleeps)
        assertTrue(logger.entries.any { Regex("micro-break").containsMatchIn(it.msg) })
        Unit
    }

    @Test
    fun `layar dipertahankan nyala saat sesi dan dipulihkan setelahnya`() = runBlocking {
        val adapter = MockAdapter()
        val h = makeScheduler(
            testConfig(listOf(CommandDef("hunt", "owo hunt", maxPerSession = 1))),
            adapter = adapter,
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(1, adapter.wakeKept)
        assertEquals(1, adapter.wakeRestored)
    }

    @Test
    fun `nextAt terisi untuk countdown akurat di dashboard`() = runBlocking {
        val h = makeScheduler(
            testConfig {
                copy(minDelaySec = 15, maxDelaySec = 20)
            },
            realDelay = true,
        )
        h.scheduler.start()
        waitFor { h.session.snapshot().nextAt != null }
        val nextAt = h.session.snapshot().nextAt!!
        val delta = nextAt - System.currentTimeMillis()
        assertTrue("delta=$delta", delta > 14_000)
        assertTrue("delta=$delta", delta <= 20_000)
        h.scheduler.stop(StopMode.NORMAL)
        assertNull(h.session.snapshot().nextAt)
    }

    @Test
    fun `verifikasi human di layar - latch captcha plus auto-stop`() = runBlocking {
        val adapter = MockAdapter(
            screenText = "Hey @kamu please complete human verification at owobot.com/captcha",
        )
        val h = makeScheduler(testConfig(), adapter = adapter)
        h.scheduler.start()
        waitFor { h.session.status == Status.CAPTCHA }
        assertTrue(h.adapter.sent.isEmpty())
        assertTrue(
            Regex("verifikasi human")
                .containsMatchIn(h.session.snapshot().lastError ?: ""),
        )
        assertTrue(h.session.snapshot().captchaAt != null)
        assertTrue(
            h.logger.entries.any {
                it.level == LogLevel.ERROR && it.msg.contains("VERIFIKASI")
            },
        )
    }

    @Test
    fun `start ditolak selama verifikasi masih di layar`() = runBlocking {
        val adapter = MockAdapter(screenText = "please verify at owobot.com/captcha")
        val h = makeScheduler(testConfig(), adapter = adapter)
        h.scheduler.start()
        waitFor { h.session.status == Status.CAPTCHA }
        try {
            h.scheduler.start()
            fail("harusnya ditolak")
        } catch (e: SchedulerStartException) {
            assertTrue(
                Regex("verifikasi human masih").containsMatchIn(e.message ?: ""),
            )
        }
        assertEquals(Status.CAPTCHA, h.session.status)
        Unit
    }

    @Test
    fun `verifikasi selesai - watcher buka kunci otomatis`() = runBlocking {
        val adapter = MockAdapter(screenText = "verify yourself at owobot.com/captcha now")
        val h = makeScheduler(testConfig(), adapter = adapter, captchaCheckMs = 15)
        h.scheduler.start()
        waitFor { h.session.status == Status.CAPTCHA }
        adapter.screenText = "owo hunt\now battle"
        waitFor { h.session.status == Status.IDLE }
        assertNull(h.session.snapshot().captchaAt)
        assertNull(h.session.snapshot().lastError)
    }

    @Test
    fun `dumpUi gagal bukan alasan stop`() = runBlocking {
        val adapter = MockAdapter(dumpFailure = true)
        val h = makeScheduler(
            testConfig(listOf(CommandDef("hunt", "owo hunt", maxPerSession = 1))),
            adapter = adapter,
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(listOf("owo hunt"), h.adapter.sent)
    }

    @Test
    fun `start saat sudah running ditolak`() = runBlocking {
        val h = makeScheduler(
            testConfig(listOf(CommandDef("hunt", "owo hunt", maxPerSession = null))),
            realDelay = true,
        )
        h.scheduler.start()
        try {
            h.scheduler.start()
            fail("harusnya ditolak")
        } catch (e: SchedulerStartException) {
            assertEquals("sesi sedang berjalan", e.message)
        }
        h.scheduler.stop(StopMode.NORMAL)
        Unit
    }

    @Test
    fun `preflight gagal kalau adapter tidak tersedia`() = runBlocking {
        val h = makeScheduler(testConfig(), adapter = MockAdapter(available = false))
        try {
            h.scheduler.start()
            fail("harusnya ditolak")
        } catch (e: SchedulerStartException) {
            assertEquals("adapter UI automation tidak tersedia", e.message)
        }
        assertEquals(Status.IDLE, h.session.status)
        Unit
    }

    @Test
    fun `preflight gagal kalau Discord tidak terlihat`() = runBlocking {
        val h = makeScheduler(testConfig(), adapter = MockAdapter(foreground = false))
        try {
            h.scheduler.start()
            fail("harusnya ditolak")
        } catch (e: SchedulerStartException) {
            assertTrue((e.message ?: "").contains("terlihat"))
        }
        assertEquals(Status.IDLE, h.session.status)
        Unit
    }

    @Test
    fun `auto-stop saat Discord kehilangan foreground`() = runBlocking {
        var calls = 0
        val adapter = object : MockAdapter() {
            override suspend fun checkForeground(): Boolean {
                calls += 1
                return if (calls > 1) false else super.checkForeground()
            }
        }
        val h = makeScheduler(testConfig(), adapter = adapter, random = { 0.5 })
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(listOf("owo hunt"), h.adapter.sent)
        assertTrue(
            h.logger.entries.any {
                it.level == LogLevel.WARN && it.msg.contains("foreground")
            },
        )
    }

    @Test
    fun `3x gagal kirim - status error`() = runBlocking {
        val h = makeScheduler(
            testConfig(listOf(CommandDef("hunt", "owo hunt", maxPerSession = null))),
            adapter = MockAdapter(failAllSends = true),
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.ERROR }
        assertTrue(
            Regex("3x gagal").containsMatchIn(h.session.snapshot().lastError ?: ""),
        )
    }

    @Test
    fun `emergency stop memanggil clearComposer dan berhenti`() = runBlocking {
        val adapter = MockAdapter()
        val h = makeScheduler(testConfig(), adapter = adapter, realDelay = true)
        h.scheduler.start()
        waitFor { adapter.sent.size == 1 }
        h.scheduler.stop(StopMode.EMERGENCY)
        assertEquals(Status.IDLE, h.session.status)
        assertEquals(1, adapter.cleared)
        assertTrue(h.logger.entries.any { it.msg == "emergency stop" })
    }

    @Test
    fun `session cap memicu auto-stop`() = runBlocking {
        val fakeNow = longArrayOf(0)
        val session = Session()
        val logger = RingLogger()
        val adapter = MockAdapter()
        val scheduler = Scheduler(
            SchedulerDeps(
                adapter = adapter,
                configSupplier = {
                    testConfig { copy(maxSessionMin = 1) }
                },
                session = session,
                logger = logger,
                sleepFn = { fakeNow.set(0, fakeNow[0] + 120_000) },
                now = { fakeNow[0] },
                random = { 0.5 },
                firstDelayMs = 0,
            ),
        )
        scheduler.start()
        waitFor { session.status == Status.IDLE }
        assertEquals(listOf("owo hunt", "owo battle"), adapter.sent)
        assertTrue(logger.entries.any { it.msg.contains("session cap") })
        Unit
    }

    @Test
    fun `stop saat idle no-op`() = runBlocking {
        val adapter = MockAdapter()
        val h = makeScheduler(testConfig(), adapter = adapter)
        h.scheduler.stop(StopMode.NORMAL)
        assertEquals(0, adapter.cleared)
        Unit
    }

    @Test
    fun `cmd uang amount ikut terkirim ke Discord`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("cf", "owo coinflip head", maxPerSession = 1, amount = 100L),
                    CommandDef("slots", "owo slots", maxPerSession = 1, amount = null),
                ),
            ),
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(
            listOf("owo coinflip head 100", "owo slots"),
            h.adapter.sent,
        )
    }

    @Test
    fun `shuffleOrder urutan acak deterministik plus isi ulang tiap siklus`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("hunt", "owo hunt", maxPerSession = 2),
                    CommandDef("battle", "owo battle", maxPerSession = 2),
                    CommandDef("cf", "owo cf", maxPerSession = 2),
                ),
            ) { copy(shuffleOrder = true) },
            random = { 0.0 },
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        // Fisher-Yates dgn random=0 → [battle, cf, hunt], diulang tiap siklus
        assertEquals(
            listOf(
                "owo battle", "owo cf", "owo hunt",
                "owo battle", "owo cf", "owo hunt",
            ),
            h.adapter.sent,
        )
    }

    @Test
    fun `shuffleOrder skip disabled limit habis di antrean lalu selesai`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("a", "owo a", maxPerSession = 1),
                    CommandDef("b", "owo b", enabled = false),
                    CommandDef("c", "owo c", maxPerSession = 1),
                ),
            ) { copy(shuffleOrder = true) },
            random = { 0.0 },
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(listOf("owo c", "owo a"), h.adapter.sent)
        assertEquals(2, h.session.snapshot().totalSent)
    }

    @Test
    fun `shuffleOrder mati round-robin urut seperti biasa`() = runBlocking {
        val h = makeScheduler(
            testConfig(
                listOf(
                    CommandDef("hunt", "owo hunt", maxPerSession = 1),
                    CommandDef("cf", "owo cf", maxPerSession = 1),
                ),
            ),
            random = { 0.0 },
        )
        h.scheduler.start()
        waitFor { h.session.status == Status.IDLE }
        assertEquals(listOf("owo hunt", "owo cf"), h.adapter.sent)
    }
}

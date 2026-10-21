package dev.owohunt.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test
    fun `config default + rotasi minimal valid`() {
        val errs = validateConfig(
            AppConfig(rotation = listOf(CommandDef("hunt", "owo hunt"))),
        )
        assertEquals(emptyList<String>(), errs)
    }

    @Test
    fun `batches di luar rentang ditolak`() {
        val errs = validateConfig(
            AppConfig(
                minDelaySec = 0,
                maxSessionMin = 200,
                startGraceSec = 1,
                rotation = listOf(CommandDef("hunt", "owo hunt")),
            ),
        )
        assertTrue(errs.any { it.contains("minDelaySec") })
        assertTrue(errs.any { it.contains("maxSessionMin") })
        assertTrue(errs.any { it.contains("startGraceSec") })
    }

    @Test
    fun `maxDelay lebih kecil dari minDelay ditolak`() {
        val errs = validateConfig(
            AppConfig(
                minDelaySec = 48,
                maxDelaySec = 22,
                rotation = listOf(CommandDef("hunt", "owo hunt")),
            ),
        )
        assertTrue(errs.contains("maxDelaySec harus >= minDelaySec"))
    }

    @Test
    fun `override delay di luar rentang ditolak`() {
        assertTrue(validateDelayOverride(DelayOverride(minDelaySec = 1)).isNotEmpty())
        assertTrue(validateDelayOverride(DelayOverride(maxDelaySec = 700)).isNotEmpty())
        assertEquals(
            emptyList<String>(),
            validateDelayOverride(DelayOverride(minDelaySec = 10, maxDelaySec = 20)),
        )
        assertTrue(
            validateDelayOverride(DelayOverride(minDelaySec = 40, maxDelaySec = 20))
                .any { it.contains("maxDelaySec harus >= minDelaySec") },
        )
    }

    @Test
    fun `amount habis di rentang 1 sampai 10 miliar`() {
        val errs = validateConfig(
            AppConfig(
                rotation = listOf(
                    CommandDef("a", "owo slots", amount = 0L),
                    CommandDef("b", "owo slots", amount = 10_000_000_001L),
                    CommandDef("c", "owo slots", amount = 999L),
                ),
            ),
        )
        assertEquals(2, errs.size)
        assertTrue(errs[0].contains("rotation[0]"))
        assertTrue(errs[1].contains("rotation[1]"))
    }

    @Test
    fun `rotation kosong ditolak`() {
        val errs = validateConfig(AppConfig())
        assertTrue(errs.any { it.contains("rotation minimal 1") })
    }

    @Test
    fun `maxPerSession negatif ditolak`() {
        val errs = validateConfig(
            AppConfig(
                rotation = listOf(
                    CommandDef("hunt", "owo hunt", maxPerSession = 0),
                ),
            ),
        )
        assertTrue(errs.any { it.contains("maxPerSession") })
    }
}

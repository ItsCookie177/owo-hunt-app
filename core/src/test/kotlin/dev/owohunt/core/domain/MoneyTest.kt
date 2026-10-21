package dev.owohunt.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test
    fun `kenali command gambling terima nominal koin`() {
        val texts = listOf(
            "owo slots", "owo s 100", "owo cf", "owo coinflip head",
            "owo lottery", "owo lotto buy", "owo blackjack", "w bj", "owo bet",
        )
        texts.forEach { assertTrue(it, isMoneyCommand(it)) }
    }

    @Test
    fun `bukan command uang`() {
        val texts = listOf(
            "owo hunt", "owo battle", "owo search", "owo daily",
            "owo quest", "owo sell all", "owo zoo", "owo sacrifice",
        )
        texts.forEach { assertFalse(it, isMoneyCommand(it)) }
    }

    @Test
    fun `prefix owo/w tidak dihitung sebagai kata uang`() {
        assertTrue(isMoneyCommand("owo flip")) // flip = alias coinflip
        assertFalse(isMoneyCommand("w h"))
        assertFalse(isMoneyCommand(""))
    }

    @Test
    fun `daftar saran mengandung gambling dan economy`() {
        val texts = MONEY_SUGGESTIONS.map { it.text }
        assertTrue(texts.any { isMoneyCommand(it) })
        assertTrue(MONEY_SUGGESTIONS.any { it.money })
    }
}

class CommandTextTest {
    @Test
    fun `tanpa amount = teks apa adanya`() {
        assertEquals("owo slots", commandText(CommandDef("x", "owo slots")))
        assertEquals(
            "owo hunt",
            commandText(CommandDef("x", "  owo hunt  ")),
        )
    }

    @Test
    fun `amount di-append sebagai nominal koin`() {
        assertEquals(
            "owo slots 1000",
            commandText(CommandDef("x", "owo slots", amount = 1000L)),
        )
        assertEquals(
            "owo coinflip head 100",
            commandText(CommandDef("x", "owo coinflip head", amount = 100L)),
        )
    }
}

class ComputeDelayTest {
    @Test
    fun `random 0 = batas bawah`() {
        assertEquals(22_000L, computeDelayMs(22, 48) { 0.0 })
    }

    @Test
    fun `random 1 = batas atas`() {
        assertEquals(48_000L, computeDelayMs(22, 48) { 1.0 })
    }

    @Test
    fun `random 0.5 = tengah`() {
        assertEquals(35_000L, computeDelayMs(22, 48) { 0.5 })
    }

    @Test
    fun `min + max tetap aman`() {
        assertEquals(22_000L, computeDelayMs(48, 22) { 0.0 })
    }
}

package dev.owohunt.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaTest {
    @Test
    fun `membuang char zero-width`() {
        assertEquals(
            "abcdef",
            stripZeroWidth("a\u200Bb\u200Cc\u200Dd\u2060e\uFEFFf"),
        )
    }

    @Test
    fun `membiarkan teks normal`() {
        assertEquals("owo hunt", stripZeroWidth("owo hunt"))
    }

    @Test
    fun `deteksi human verification`() {
        assertTrue(
            looksLikeVerification(
                "Hey @user please complete human verification to keep playing!",
            ),
        )
    }

    @Test
    fun `deteksi link captcha OwO`() {
        assertTrue(looksLikeVerification("verify at owobot.com/captcha now"))
    }

    @Test
    fun `deteksi meski disisipi zero-width char`() {
        assertTrue(looksLikeVerification("complete hum\u200Ban verif\u200Bication"))
    }

    @Test
    fun `deteksi verify yourself`() {
        assertTrue(looksLikeVerification("Please verify yourself"))
    }

    @Test
    fun `tidak false-positive teks biasa`() {
        assertFalse(looksLikeVerification("owo hunt\now battle"))
        assertFalse(looksLikeVerification("selamat verify ya"))
        assertFalse(looksLikeVerification(""))
    }
}

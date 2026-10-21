package dev.owohunt.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class TypesTest {
    @Test
    fun `default config values`() {
        val c = AppConfig()
        assertThat(c.minDelaySec).isEqualTo(22L)
        assertThat(c.maxDelaySec).isEqualTo(48L)
    }
}

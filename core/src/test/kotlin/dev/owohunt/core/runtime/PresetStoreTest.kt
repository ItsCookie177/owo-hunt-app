package dev.owohunt.core.runtime

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Port 1:1 dari presets.test.ts web. */
class PresetStoreTest {

    private fun tempPresetPath(): Path {
        val dir = Files.createTempDirectory("owo-preset-")
        return dir.resolve("presets.json")
    }

    private fun publicConfig() = defaultConfig().copy(lanToken = "")

    @Test
    fun `simpan baca ulang dari disk lalu hapus`() {
        val file = tempPresetPath()
        val s = PresetStore(file.toFile())
        val preset = s.save("harian", publicConfig())
        assertEquals("harian", preset.name)
        assertEquals(4, preset.config.rotation.size)

        val reloaded = PresetStore(file.toFile())
        assertEquals(1, reloaded.list().size)
        assertEquals(22L, reloaded.list()[0].config.minDelaySec)

        assertTrue(reloaded.remove("harian"))
        assertFalse(reloaded.remove("harian"))
        assertEquals(0, PresetStore(file.toFile()).list().size)
    }

    @Test
    fun `nama sama tetap satu entri overwrite`() {
        val file = tempPresetPath()
        val s = PresetStore(file.toFile())
        s.save("a", publicConfig())
        s.save("a", publicConfig().copy(minDelaySec = 30))
        val list = s.list()
        assertEquals(1, list.size)
        assertEquals(30L, list[0].config.minDelaySec)
    }

    @Test
    fun `nama kosong dan config tidak valid ditolak`() {
        val file = tempPresetPath()
        val s = PresetStore(file.toFile())
        try {
            s.save("   ", publicConfig())
            fail("harusnya gagal")
        } catch (_: IllegalArgumentException) {
        }
        try {
            s.save("x", publicConfig().copy(minDelaySec = 1))
            fail("harusnya gagal")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, s.list().size)
        assertFalse(file.toFile().exists())
    }

    @Test
    fun `preset rusak di disk dilewati file lain tetap dipakai`() {
        val file = tempPresetPath()
        val s = PresetStore(file.toFile())
        s.save("ok", publicConfig())
        // Suntik entri rusak manual di awal array "presets"
        val raw = file.toFile().readText()
        val idx = raw.indexOf("[")
        val broken = raw.substring(0, idx + 1) +
            "{ \"name\": \"rusak\", \"savedAt\": \"x\", \"config\": null }," +
            raw.substring(idx + 1)
        file.toFile().writeText(broken)
        val reloaded = PresetStore(file.toFile())
        assertEquals(listOf("ok"), reloaded.list().map { it.name })
        Unit
    }
}

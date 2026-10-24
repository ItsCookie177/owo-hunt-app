package dev.owohunt.core.runtime

import dev.owohunt.core.domain.AppConfig
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port 1:1 dari config.test.ts web. */
class ConfigStoreTest {

    private fun tempConfigPath(): Path {
        val dir = Files.createTempDirectory("owo-cfg-")
        return dir.resolve("config.json")
    }

    @Test
    fun `membuat config default dan token saat file belum ada`() {
        val p = tempConfigPath()
        val s = ConfigStore(p.toFile())
        val cfg = s.get()
        assertEquals(22L, cfg.minDelaySec)
        assertEquals(48L, cfg.maxDelaySec)
        assertEquals(4, cfg.rotation.size)
        assertTrue(cfg.lanToken.length >= 8)
        val cf = cfg.rotation.first { it.id == "cf" }
        assertEquals("owo coinflip head", cf.text)
        assertEquals(null, cf.amount)
        assertEquals(null, cf.maxPerSession)
        val slots = cfg.rotation.first { it.id == "slots" }
        assertEquals(null, slots.amount)
        assertEquals(null, slots.maxPerSession)
        val onDisk = p.toFile().readText()
        assertTrue(onDisk.contains("minDelaySec"))
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<AppConfig>(onDisk)
        assertEquals(22L, parsed.minDelaySec)
        assertTrue(parsed.lanToken.length >= 8)
    }

    @Test
    fun `config lama intervalSec jitterSec dimigrasi dengan default field baru`() {
        val p = tempConfigPath()
        p.toFile().writeText(
            """
            {
              "intervalSec": 30,
              "jitterSec": 2,
              "rotation": [
                { "id": "hunt", "text": "owo hunt", "enabled": true, "maxPerSession": null }
              ],
              "selectors": { "composerId": "" },
              "lanToken": "lamacantoken123"
            }
            """.trimIndent(),
        )
        val s = ConfigStore(p.toFile())
        assertEquals(22L, s.get().minDelaySec)
        assertEquals("lamacantoken123", s.get().lanToken)
    }

    @Test
    fun `update menyimpan ke disk dan mempertahankan token`() {
        val p = tempConfigPath()
        val s = ConfigStore(p.toFile())
        val token = s.get().lanToken
        s.update(s.get().copy(minDelaySec = 30, maxDelaySec = 60))
        assertEquals(30L, s.get().minDelaySec)
        assertEquals(60L, s.get().maxDelaySec)
        assertEquals(token, s.get().lanToken)
        val onDisk = p.toFile().readText()
        assertTrue(onDisk.contains("minDelaySec"))
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<AppConfig>(onDisk)
        assertEquals(30L, parsed.minDelaySec)
        assertEquals(token, parsed.lanToken)
    }

    @Test
    fun `load ulang membaca nilai yang sama`() {
        val p = tempConfigPath()
        val s = ConfigStore(p.toFile())
        s.update(s.get().copy(maxSessionMin = 25))
        val s2 = ConfigStore(p.toFile())
        assertEquals(25L, s2.get().maxSessionMin)
        assertEquals(s.get().lanToken, s2.get().lanToken)
    }

    @Test
    fun `file rusak fallback default dengan token baru`() {
        val p = tempConfigPath()
        p.toFile().writeText("{ not json")
        val s = ConfigStore(p.toFile())
        assertEquals(22L, s.get().minDelaySec)
        assertTrue(s.get().lanToken.length >= 8)
    }

    @Test
    fun `file tidak valid menurut skema fallback default`() {
        val p = tempConfigPath()
        p.toFile().writeText("""{"minDelaySec": 1}""")
        val warns = mutableListOf<String>()
        val s = ConfigStore(p.toFile(), warn = { warns += it })
        assertEquals(22L, s.get().minDelaySec)
        assertTrue(warns.isNotEmpty())
    }
}

package dev.owohunt.core.runtime

import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.validateConfig
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class PresetFile(val presets: List<Preset>)

@Serializable
data class Preset(
    val name: String,
    val savedAt: String,
    val config: AppConfig,
)

/**
 * Port PresetStore web — file `presets.json` `{ presets: [...] }`,
 * entri rusak dilewati, config divalidasi sebelum disimpan.
 */
class PresetStore(
    private val file: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val warn: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var presets: List<Preset> = load()

    fun list(): List<Preset> = presets.sortedByDescending { it.savedAt }

    fun save(name: String, config: AppConfig): Preset {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) throw IllegalArgumentException("nama preset kosong")
        // Validasi konfigurasi dulu — preset gagal tidak boleh merusak config aktif.
        val cleanConfig = config.copy(lanToken = "")
        val errs = validateConfig(cleanConfig)
        if (errs.isNotEmpty()) throw IllegalArgumentException(errs.first())
        val preset = Preset(clean, Instant.ofEpochMilli(nowMs()).toString(), cleanConfig)
        presets = listOf(preset) + presets.filter { it.name != clean }
        persist()
        return preset
    }

    fun remove(name: String): Boolean {
        val before = presets.size
        presets = presets.filter { it.name != name }
        if (presets.size == before) return false
        persist()
        return true
    }

    private fun load(): List<Preset> {
        if (!file.exists()) return emptyList()
        return try {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val arr = root["presets"]?.jsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (name.isBlank()) return@mapNotNull null
                    val savedAt = obj["savedAt"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    if (savedAt.isBlank()) return@mapNotNull null
                    val cfgEl = obj["config"] ?: return@mapNotNull null
                    val cfg = json.decodeFromJsonElement<AppConfig>(cfgEl)
                    if (validateConfig(cfg).isNotEmpty()) return@mapNotNull null
                    Preset(name, savedAt, cfg)
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            warn("[presets] presets.json gagal dibaca, mulai kosong")
            emptyList()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(PresetFile(presets)) + "\n")
        tmp.renameTo(file)
    }
}

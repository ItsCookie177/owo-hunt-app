package dev.owohunt.core.runtime

import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.validateConfig
import java.io.File
import java.security.SecureRandom
import kotlinx.serialization.json.Json

object Tokens {
    private val sr = SecureRandom()
    /** 8 byte acak → 16 karakter hex (paritas randomBytes(8).toString('hex')). */
    fun randomHex(nBytes: Int = 8): String =
        (0 until nBytes).joinToString("") { "%02x".format(sr.nextInt(256)) }
}

fun defaultConfig(): AppConfig = AppConfig(
    rotation = listOf(
        CommandDef(id = "hunt", text = "owo hunt"),
        CommandDef(id = "battle", text = "owo battle"),
        CommandDef(id = "cf", text = "owo coinflip head"),
        CommandDef(id = "slots", text = "owo slots"),
    ),
)

/**
 * Port ConfigStore web — file `config.json` dengan format sama
 * (bisa dipindah-pindah antara web dan APK), tulis atomik via .tmp + rename.
 */
class ConfigStore(
    private val file: File,
    private val randomHex: () -> String = { Tokens.randomHex() },
    private val warn: (String) -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private var config: AppConfig = load()

    fun get(): AppConfig = config

    fun token(): String = config.lanToken

    fun update(input: AppConfig): AppConfig {
        val errs = validateConfig(input)
        if (errs.isNotEmpty()) throw IllegalArgumentException(errs.first())
        config = input.copy(lanToken = ensureToken(config.lanToken))
        write(config)
        return config
    }

    private fun ensureToken(current: String): String =
        if (current.length >= 8) current else randomHex()

    private fun load(): AppConfig {
        val fallback = defaultConfig()

        if (!file.exists()) {
            val cfg = fallback.copy(lanToken = randomHex())
            write(cfg)
            return cfg
        }
        return try {
            val parsed = json.decodeFromString<AppConfig>(file.readText())
            val cfg = parsed.copy(lanToken = ensureToken(parsed.lanToken))
            val errs = validateConfig(cfg)
            if (errs.isNotEmpty()) {
                warn("[config] config.json tidak valid, pakai default: ${errs.first()}")
                val def = fallback.copy(lanToken = randomHex())
                write(def)
                def
            } else {
                cfg
            }
        } catch (_: Exception) {
            warn("[config] config.json gagal dibaca, pakai default")
            val def = fallback.copy(lanToken = randomHex())
            write(def)
            def
        }
    }

    private fun write(c: AppConfig) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(c) + "\n")
        tmp.renameTo(file)
    }
}

package dev.owohunt.app

import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.DelayOverride
import dev.owohunt.core.runtime.AutomationAdapter
import dev.owohunt.core.runtime.ConfigStore
import dev.owohunt.core.runtime.LogEntry
import dev.owohunt.core.runtime.Preset
import dev.owohunt.core.runtime.PresetStore
import dev.owohunt.core.runtime.RingLogger
import dev.owohunt.core.runtime.Scheduler
import dev.owohunt.core.runtime.SchedulerDeps
import dev.owohunt.core.runtime.Session
import dev.owohunt.core.domain.Stats
import dev.owohunt.core.domain.StopMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runtime wiring per aplikasi: store persisten + scheduler (adapter Shizuku/mock)
 * + state UI. Paritas web: configStore/presetStore/scheduler langsung.
 */
class AppViewModel(
    configPath: File,
    presetsPath: File,
    val adapter: AutomationAdapter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val configStore = ConfigStore(configPath)
    val presetStore = PresetStore(presetsPath, warn = {})

    val session = Session()
    val logger = RingLogger()

    private val _config = MutableStateFlow(configStore.get())
    val config: StateFlow<AppConfig> = _config

    val stats: StateFlow<Stats> = session.stats

    private val _logs = MutableStateFlow(logger.entries)
    val logs: StateFlow<List<LogEntry>> = _logs

    val scheduler = Scheduler(
        SchedulerDeps(
            adapter = adapter,
            configSupplier = { _config.value },
            session = session,
            logger = logger,
        ),
    )

    init {
        logger.info("runtime siap (adapter: ${adapter.name})")
        scope.launch {
            logger.stream.collect { _logs.value = logger.entries }
        }
        // Poll log kalau balik dari background (sharedFlow flush) — jaga simple.
    }

    fun presets(): List<Preset> = presetStore.list()

    fun doStart(override: DelayOverride? = null): Result<Unit> {
        return try {
            scheduler.start(override)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun doStop(emergency: Boolean = false) {
        scope.launch {
            scheduler.stop(if (emergency) StopMode.EMERGENCY else StopMode.NORMAL)
        }
    }

    fun updateConfig(c: AppConfig): Result<AppConfig> {
        return try {
            val saved = configStore.update(c)
            _config.value = saved
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun savePreset(name: String): Result<Preset> = try {
        val p = presetStore.save(name, _config.value.copy(lanToken = ""))
        logger.info("preset tersimpan: ${p.name}")
        Result.success(p)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun deletePreset(name: String): Boolean {
        val ok = presetStore.remove(name)
        if (ok) logger.info("preset dihapus: $name")
        return ok
    }

    fun loadPreset(name: String) {
        val p = presetStore.list().firstOrNull { it.name == name } ?: return
        updateConfig(p.config)
    }
}

package dev.owohunt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.owohunt.core.domain.AppConfig
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.MONEY_SUGGESTIONS
import dev.owohunt.core.domain.isMoneyCommand
import dev.owohunt.core.runtime.LogEntry
import dev.owohunt.core.runtime.LogLevel
import dev.owohunt.core.runtime.Status
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> RotationScreen(vm)
                2 -> PresetScreen(vm)
                3 -> StatsScreen(vm)
                else -> LogScreen(vm)
            }
        }
        NavigationBar(showDivider = true) {
            TABS.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = t.icon,
                    label = t.label,
                )
            }
        }
    }
}

private fun statusText(s: Status): String = when (s) {
    Status.IDLE -> "Idle"
    Status.RUNNING -> "Berjalan"
    Status.CAPTCHA -> "Verifikasi human"
    Status.ERROR -> "Error"
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun countdown(vm: AppViewModel): String {
    val stats by vm.stats.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            nowMs = System.currentTimeMillis()
        }
    }
    val nextAt = stats.nextAt ?: return "-"
    val s = ((nextAt - nowMs).coerceAtLeast(0) / 1000).toInt()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    val stats by vm.stats.collectAsState()
    val countdown = countdown(vm)
    var actionError by remember { mutableStateOf<String?>(null) }
    val running = stats.status == Status.RUNNING

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Text("Status")
            Spacer(Modifier.height(4.dp))
            Text(statusText(stats.status))
            if (running) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Komando berikutnya dalam $countdown",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            (stats.lastError ?: actionError)?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!running) {
                    Button(
                        onClick = {
                            val r = vm.doStart()
                            actionError = r.exceptionOrNull()?.message
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Mulai") }
                    Button(
                        onClick = { vm.doStop(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Berhenti") }
                } else {
                    Button(
                        onClick = { vm.doStop(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Berhenti") }
                    Button(
                        onClick = { vm.doStop(true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Darurat") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsCard(vm, config)

        Spacer(Modifier.height(12.dp))
        Text(
            "Adapter: ${vm.adapter.name}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun slider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text("$value$unit", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = min.toFloat()..max.toFloat(),
    )
}

@Composable
private fun SettingsCard(vm: AppViewModel, config: AppConfig) {
    var draft by remember(config) { mutableStateOf(config) }
    var msg by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Text("Pengaturan")
        Spacer(Modifier.height(8.dp))

        slider("Delay min", draft.minDelaySec.toInt(), 5, 600, "s") {
            draft = draft.copy(minDelaySec = it.toLong())
        }
        slider("Delay maks", draft.maxDelaySec.toInt(), 5, 600, "s") {
            draft = draft.copy(maxDelaySec = it.toLong())
        }
        slider("Durasi sesi", draft.maxSessionMin.toInt(), 1, 120, "m") {
            draft = draft.copy(maxSessionMin = it.toLong())
        }
        slider("Break tiap", draft.breakEveryCmd, 0, 100, " cmd") {
            draft = draft.copy(breakEveryCmd = it)
        }
        slider("Break min", draft.breakMinSec.toInt(), 5, 600, "s") {
            draft = draft.copy(breakMinSec = it.toLong())
        }
        slider("Break maks", draft.breakMaxSec.toInt(), 5, 600, "s") {
            draft = draft.copy(breakMaxSec = it.toLong())
        }
        slider("Grace mulai", draft.startGraceSec.toInt(), 5, 120, "s") {
            draft = draft.copy(startGraceSec = it.toLong())
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Auto stop saat Discord hilang", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.autoStopOnForegroundLoss,
                onCheckedChange = { draft = draft.copy(autoStopOnForegroundLoss = it) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Acak urutan command", modifier = Modifier.weight(1f))
            Switch(
                checked = draft.shuffleOrder,
                onCheckedChange = { draft = draft.copy(shuffleOrder = it) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val r = vm.updateConfig(draft)
                    msg = if (r.isSuccess) "Tersimpan" else "Error: ${r.exceptionOrNull()?.message}"
                },
                modifier = Modifier.weight(1f),
            ) { Text("Simpan pengaturan") }
        }
        msg?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
fun RotationScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    var draft by remember(config) { mutableStateOf(config) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Rotasi command")
        Spacer(Modifier.height(8.dp))
        draft.rotation.forEachIndexed { idx, cmd ->
            CommandCard(
                cmd = cmd,
                onCmd = { c ->
                    draft = draft.copy(
                        rotation = draft.rotation.toMutableList().apply { set(idx, c) },
                    )
                },
                onRemove = {
                    draft = draft.copy(rotation = draft.rotation.toMutableList().apply { removeAt(idx) })
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("Tambah cepat")
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MONEY_SUGGESTIONS.chunked(2).forEach { pairIn ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairIn.forEach { sug ->
                            Button(onClick = {
                                draft = draft.copy(
                                    rotation = draft.rotation +
                                        CommandDef(id = "cmd${draft.rotation.size + 1}", text = sug.text),
                                )
                            }) { Text("+ ${sug.text}") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val r = vm.updateConfig(draft)
                msg = if (r.isSuccess) "Tersimpan" else "Error: ${r.exceptionOrNull()?.message}"
            },
        ) { Text("Simpan rotasi") }
        msg?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun numField(label: String, value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(2.dp))
        TextField(
            value = value?.toString() ?: "",
            onValueChange = { s -> onChange(s.trim().toLongOrNull()) },
            label = "",
        )
    }
}

@Composable
private fun CommandCard(
    cmd: CommandDef,
    onCmd: (CommandDef) -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cmd.id, modifier = Modifier.weight(1f))
            Switch(
                checked = cmd.enabled,
                onCheckedChange = { onCmd(cmd.copy(enabled = it)) },
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRemove) { Text("Hapus") }
        }
        Spacer(Modifier.height(6.dp))
        TextField(
            value = cmd.text,
            onValueChange = { onCmd(cmd.copy(text = it)) },
            label = "command",
        )
        if (isMoneyCommand(cmd.text)) {
            Spacer(Modifier.height(4.dp))
            numField("Nominal koin", cmd.amount) { onCmd(cmd.copy(amount = it)) }
        }
        Spacer(Modifier.height(4.dp))
        numField("Maks per sesi (kosong = tanpa batas)", cmd.maxPerSession?.toLong()) {
            onCmd(cmd.copy(maxPerSession = it?.toInt()))
        }
    }
}

@Composable
fun PresetScreen(vm: AppViewModel) {
    var presets by remember { mutableStateOf(vm.presets()) }
    var name by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val config by vm.config.collectAsState()
    val current = config

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Preset")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "nama preset",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                val r = vm.savePreset(name)
                msg = if (r.isSuccess) "Preset '${r.getOrNull()?.name}' tersimpan" else "Error: ${r.exceptionOrNull()?.message}"
                if (r.isSuccess) {
                    name = ""
                    presets = vm.presets()
                }
            }) { Text("Simpan") }
        }
        Spacer(Modifier.height(12.dp))
        if (presets.isEmpty()) {
            Text("Belum ada preset", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        presets.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name)
                        Text(
                            "${p.config.rotation.size} command — delay ${p.config.minDelaySec}-${p.config.maxDelaySec}s",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Button(onClick = {
                        vm.loadPreset(p.name)
                        msg = "Preset '${p.name}' dimuat"
                    }) { Text("Muat") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.deletePreset(p.name)
                        presets = vm.presets()
                    }) { Text("Hapus") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        msg?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Statistik")
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Row { Text("Status"); Spacer(Modifier.width(8.dp)); Text(statusText(stats.status)) }
            Spacer(Modifier.height(6.dp))
            Row { Text("Total terkirim"); Spacer(Modifier.width(8.dp)); Text("${stats.totalSent}") }
            stats.startedAt?.let {
                Spacer(Modifier.height(6.dp))
                Row { Text("Mulai"); Spacer(Modifier.width(8.dp)); Text(timeFmt.format(Date(it))) }
            }
            stats.lastSentAt?.let {
                Spacer(Modifier.height(6.dp))
                Row { Text("Kirim terakhir"); Spacer(Modifier.width(8.dp)); Text(timeFmt.format(Date(it))) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Per command")
        Spacer(Modifier.height(8.dp))
        if (stats.perCommand.isEmpty()) {
            Text("Belum ada data", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        stats.perCommand.entries.forEach { (id, n) ->
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(id, modifier = Modifier.weight(1f))
                    Text("$n", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LogScreen(vm: AppViewModel) {
    val logs by vm.logs.collectAsState()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(logs.asReversed()) { e ->
            Text("${fmt(e)} ${levelTag(e.level)} ${e.msg}")
        }
        if (logs.isEmpty()) {
            item { Text("Log kosong", color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
    }
}

private fun fmt(e: LogEntry): String = timeFmt.format(Date(e.ts))

private fun levelTag(l: LogLevel): String = when (l) {
    LogLevel.INFO -> "i"
    LogLevel.WARN -> "w"
    LogLevel.ERROR -> "E"
}

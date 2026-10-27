package dev.owohunt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.owohunt.core.domain.AppConfig
import dev.owohunt.automation.ShizukuStatus
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    val config0 = config

    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    var presetAdd by remember { mutableStateOf(false) }
    var selPreset by remember { mutableStateOf<String?>(null) }
    var shizukuOpen by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(vm.presets()) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SmallTitle("Jeda")
        Card {
            ArrowPreference(
                title = "Delay min–maks",
                summary = "${config0.minDelaySec}s – ${config0.maxDelaySec}s",
                onClick = { sheet = SheetKind.DELAY },
            )
            ArrowPreference(
                title = "Durasi sesi",
                summary = "${config0.maxSessionMin} mnt",
                onClick = { sheet = SheetKind.SESSION },
            )
        }

        SmallTitle("Anti-detect")
        Card {
            ArrowPreference(
                title = "Micro-break setiap",
                summary = "${config0.breakEveryCmd} command",
                onClick = { sheet = SheetKind.BREAK_EVERY },
            )
            ArrowPreference(
                title = "Break durasi",
                summary = "${config0.breakMinSec}s – ${config0.breakMaxSec}s",
                onClick = { sheet = SheetKind.BREAK_LEN },
            )
            ArrowPreference(
                title = "Grace mulai",
                summary = "${config0.startGraceSec}s",
                onClick = { sheet = SheetKind.GRACE },
            )
        }

        SmallTitle("Perilaku")
        Card {
            SwitchPreference(
                checked = config0.autoStopOnForegroundLoss,
                onCheckedChange = { vm.updateConfig(config0.copy(autoStopOnForegroundLoss = it)) },
                title = "Auto stop saat Discord hilang",
            )
            SwitchPreference(
                checked = config0.shuffleOrder,
                onCheckedChange = { vm.updateConfig(config0.copy(shuffleOrder = it)) },
                title = "Acak urutan command",
            )
        }

        SmallTitle("Preset")
        Card {
            ArrowPreference(
                title = "Simpan preset sekarang",
                summary = "dari config aktif",
                onClick = { presetAdd = true },
            )
            presets.forEach { p ->
                ArrowPreference(
                    title = p.name,
                    summary = "${p.config.rotation.size} command — delay ${p.config.minDelaySec}-${p.config.maxDelaySec}s",
                    onClick = { selPreset = p.name },
                )
            }
        }

        SmallTitle("Adapter")
        Card {
            ArrowPreference(
                title = "Adapter aktif",
                summary = vm.adapter.name,
                onClick = null,
            )
        }

        SmallTitle("Diagnostik")
        Card {
            ArrowPreference(
                title = "Wizard Shizuku",
                summary = "status koneksi · izin · rish · probe shell",
                onClick = { shizukuOpen = true },
            )
        }

        SmallTitle("Selector")
        Card {
            ArrowPreference(
                title = "composerId",
                summary = config0.selectors.composerId.ifBlank { "belum diatur (probe Diagnostik)" },
                onClick = null,
            )
        }

        msg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }

OverlayBottomSheet(
        show = selPreset != null,
        title = selPreset,
        onDismissRequest = { selPreset = null },
    ) {
        val sel = selPreset
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (sel != null) {
                            vm.loadPreset(sel)
                            msg = "Preset '$sel' dimuat"
                        }
                        selPreset = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Muat") }
                Button(
                    onClick = {
                        if (sel != null) {
                            vm.deletePreset(sel)
                            presets = vm.presets()
                        }
                        selPreset = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Hapus") }
            }
        }
    }

    OverlayBottomSheet(
        show = sheet != null,
        title = when (sheet) {
            SheetKind.DELAY -> "Jeda antar command"
            SheetKind.SESSION -> "Durasi sesi"
            SheetKind.BREAK_EVERY -> "Break tiap N command"
            SheetKind.BREAK_LEN -> "Durasi break"
            else -> "Grace mulai"
        },
        onDismissRequest = { sheet = null },
    ) {
        sheet?.let { kind ->
            ConfigSheetBody(vm, config0, kind) { sheet = null }
        }
    }

    OverlayBottomSheet(
        show = shizukuOpen,
        title = "Diagnostik Shizuku",
        onDismissRequest = { shizukuOpen = false },
    ) {
        ShizukuWizardBody(vm)
    }

    OverlayDialog(
        show = presetAdd,
        title = "Simpan preset",
        summary = "Nama preset untuk config aktif",
        onDismissRequest = { presetAdd = false },
    ) {
        var name by remember { mutableStateOf("") }
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "nama preset",
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val r = vm.savePreset(name)
                    msg = if (r.isSuccess) "Preset tersimpan" else "Error: ${r.exceptionOrNull()?.message}"
                    if (r.isSuccess) presets = vm.presets()
                    presetAdd = false
                },
                modifier = Modifier.weight(1f),
            ) { Text("Simpan") }
            Button(
                onClick = { presetAdd = false },
                modifier = Modifier.weight(1f),
            ) { Text("Batal") }
        }
    }
}

enum class SheetKind { DELAY, SESSION, BREAK_EVERY, BREAK_LEN, GRACE }

@Composable
private fun ConfigSheetBody(
    vm: AppViewModel,
    base: AppConfig,
    kind: SheetKind,
    onDone: () -> Unit,
) {
    var msg by remember { mutableStateOf<String?>(null) }

    var delayStart by remember { mutableFloatStateOf(base.minDelaySec.toFloat()) }
    var delayEnd by remember { mutableFloatStateOf(base.maxDelaySec.toFloat()) }
    var session by remember { mutableFloatStateOf(base.maxSessionMin.toFloat()) }
    var breakEvery by remember { mutableFloatStateOf(base.breakEveryCmd.toFloat()) }
    var breakStart by remember { mutableFloatStateOf(base.breakMinSec.toFloat()) }
    var breakEnd by remember { mutableFloatStateOf(base.breakMaxSec.toFloat()) }
    var grace by remember { mutableFloatStateOf(base.startGraceSec.toFloat()) }

    Column {
        when (kind) {
            SheetKind.DELAY -> {
                Text("Range delay (detik): ${delayStart.toInt()} – ${delayEnd.toInt()}")
                Spacer(Modifier.height(6.dp))
                RangeSlider(
                    value = delayStart..delayEnd,
                    onValueChange = { range ->
                        delayStart = range.start
                        delayEnd = range.endInclusive
                    },
                    valueRange = 5f..600f,
                )
            }
            SheetKind.SESSION -> {
                Text("Durasi sesi (mnt): ${session.toInt()}")
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = session,
                    onValueChange = { session = it },
                    valueRange = 1f..120f,
                )
            }
            SheetKind.BREAK_EVERY -> {
                Text("Break tiap ${breakEvery.toInt()} command")
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = breakEvery,
                    onValueChange = { breakEvery = it },
                    valueRange = 0f..100f,
                )
            }
            SheetKind.BREAK_LEN -> {
                Text("Break durasi (detik): ${breakStart.toInt()} – ${breakEnd.toInt()}")
                Spacer(Modifier.height(6.dp))
                RangeSlider(
                    value = breakStart..breakEnd,
                    onValueChange = { range ->
                        breakStart = range.start
                        breakEnd = range.endInclusive
                    },
                    valueRange = 5f..600f,
                )
            }
            SheetKind.GRACE -> {
                Text("Grace mulai (detik): ${grace.toInt()}")
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = grace,
                    onValueChange = { grace = it },
                    valueRange = 5f..120f,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val updated = when (kind) {
                        SheetKind.DELAY -> base.copy(minDelaySec = delayStart.toLong(), maxDelaySec = delayEnd.toLong())
                        SheetKind.SESSION -> base.copy(maxSessionMin = session.toLong())
                        SheetKind.BREAK_EVERY -> base.copy(breakEveryCmd = breakEvery.toInt())
                        SheetKind.BREAK_LEN -> base.copy(breakMinSec = breakStart.toLong(), breakMaxSec = breakEnd.toLong())
                        SheetKind.GRACE -> base.copy(startGraceSec = grace.toLong())
                    }
                    val r = vm.updateConfig(updated)
                    if (r.isSuccess) {
                        onDone()
                    } else {
                        msg = r.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Simpan") }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
            ) { Text("Batal") }
        }
        msg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun ShizukuWizardBody(vm: AppViewModel) {
    val ctx = LocalContext.current
    var steps by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    var probeOut by remember { mutableStateOf<String?>(null) }

    var importMsg by remember { mutableStateOf<String?>(null) }
    val refresh: () -> Unit = {
        steps = listOf(
            "Shizuku terpasang" to ShizukuStatus.isInstalled(ctx),
            "Shizuku berjalan (binder hidup)" to ShizukuStatus.pingBinder(),
            "Izin diberikan ke OwO Macro" to ShizukuStatus.isGranted(),
            "Biner rish tersedia di filesDir" to ShizukuStatus.rishExists(ctx),
        )
    }
    LaunchedEffect(Unit) { refresh() }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val pickRish = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importMsg = if (ShizukuStatus.importFile(ctx, uri, "rish")) "rish diimpor" else "gagal impor rish"
            refresh()
        }
    }
    val pickLib = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importMsg = if (ShizukuStatus.importFile(ctx, uri, "librish.so")) "librish.so diimpor" else "gagal impor librish.so"
            refresh()
        }
    }
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    Column {
        steps.forEach { (label, ok) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            color = if (ok) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(label, color = MiuixTheme.colorScheme.onSurface)
            }
        }

        if (!ShizukuStatus.isGranted() && ShizukuStatus.pingBinder()) {
            Button(
                onClick = { ShizukuStatus.request(1004) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("Minta izin Shizuku") }
        }
        if (!ShizukuStatus.rishExists(ctx)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Ekspor rish dari aplikasi Shizuku ke Download, lalu impor dua berkas ini ke app:",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { pickRish.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Pilih file 'rish'") }
                Button(
                    onClick = { pickLib.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Pilih 'librish.so'") }
            }
        }
        importMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { vm.probeShell { probeOut = it } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Probe shell") }

        probeOut?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

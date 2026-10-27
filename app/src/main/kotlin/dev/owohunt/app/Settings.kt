package dev.owohunt.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Baris "Slider + Dialog + Arrow": judul kiri, nilai kanan (tap → salahs
 * OverlayDialog utk input angka tepat), slider full-width di bawahnya.
 */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                valueText,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onEdit),
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun RangeRow(
    title: String,
    valueText: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onValueChangeFinished: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                valueText,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onEdit),
            )
        }
        Spacer(Modifier.height(4.dp))
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    monet: Boolean,
    onMonet: (Boolean) -> Unit,
    wizardOpen: Boolean,
    onWizardToggle: (Boolean) -> Unit,
) {
    val config by vm.config.collectAsState()
    val config0 = config

    var presetAdd by remember { mutableStateOf(false) }
    var selPreset by remember { mutableStateOf<String?>(null) }
    var presets by remember { mutableStateOf(vm.presets()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var editKind by remember { mutableStateOf<SheetEditKind?>(null) }

    // state lokal slider (persist via updateConfig saat selesai)
    var delayStart by remember(config0.minDelaySec) { mutableFloatStateOf(config0.minDelaySec.toFloat()) }
    var delayEnd by remember(config0.maxDelaySec) { mutableFloatStateOf(config0.maxDelaySec.toFloat()) }
    var session by remember(config0.maxSessionMin) { mutableFloatStateOf(config0.maxSessionMin.toFloat()) }
    var breakEvery by remember(config0.breakEveryCmd) { mutableFloatStateOf(config0.breakEveryCmd.toFloat()) }
    var breakStart by remember(config0.breakMinSec) { mutableFloatStateOf(config0.breakMinSec.toFloat()) }
    var breakEnd by remember(config0.breakMaxSec) { mutableFloatStateOf(config0.breakMaxSec.toFloat()) }
    var grace by remember(config0.startGraceSec) { mutableFloatStateOf(config0.startGraceSec.toFloat()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SmallTitle("Jeda")
        Card {
            RangeRow(
                title = "Delay min–maks",
                valueText = "${delayStart.toInt()}s – ${delayEnd.toInt()}s",
                value = delayStart..delayEnd,
                valueRange = 5f..600f,
                onValueChange = { r ->
                    delayStart = r.start
                    delayEnd = r.endInclusive
                },
                onValueChangeFinished = {
                    vm.updateConfig(
                        config0.copy(minDelaySec = delayStart.toLong(), maxDelaySec = delayEnd.toLong()),
                    )
                },
                onEdit = { editKind = SheetEditKind.DELAY },
            )
            SliderRow(
                title = "Durasi sesi",
                valueText = "${session.toInt()} mnt",
                value = session,
                valueRange = 1f..120f,
                onValueChange = { session = it },
                onValueChangeFinished = {
                    vm.updateConfig(config0.copy(maxSessionMin = session.toLong()))
                },
                onEdit = { editKind = SheetEditKind.SESSION },
            )
        }

        SmallTitle("Anti-detect")
        Card {
            SliderRow(
                title = "Micro-break setiap",
                valueText = "setiap ${breakEvery.toInt()} cmd",
                value = breakEvery,
                valueRange = 0f..100f,
                onValueChange = { breakEvery = it },
                onValueChangeFinished = {
                    vm.updateConfig(config0.copy(breakEveryCmd = breakEvery.toInt()))
                },
                onEdit = { editKind = SheetEditKind.BREAK_EVERY },
            )
            RangeRow(
                title = "Break durasi",
                valueText = "${breakStart.toInt()}s – ${breakEnd.toInt()}s",
                value = breakStart..breakEnd,
                valueRange = 5f..600f,
                onValueChange = { r ->
                    breakStart = r.start
                    breakEnd = r.endInclusive
                },
                onValueChangeFinished = {
                    vm.updateConfig(
                        config0.copy(breakMinSec = breakStart.toLong(), breakMaxSec = breakEnd.toLong()),
                    )
                },
                onEdit = { editKind = SheetEditKind.BREAK_LEN },
            )
            SliderRow(
                title = "Grace mulai",
                valueText = "${grace.toInt()}s",
                value = grace,
                valueRange = 5f..120f,
                onValueChange = { grace = it },
                onValueChangeFinished = {
                    vm.updateConfig(config0.copy(startGraceSec = grace.toLong()))
                },
                onEdit = { editKind = SheetEditKind.GRACE },
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

        SmallTitle("Tampilan")
        Card {
            SwitchPreference(
                checked = monet,
                onCheckedChange = onMonet,
                title = "Tema Monet (warna sistem)",
                summary = "aksen ikut wallpaper; matikan utk skema bawaan Miuix",
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
                onClick = { onWizardToggle(true) },
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

    // Sheet dialog utk input angka tepat (dipanggil dgn tahan nilai slider)
    OverlayDialog(
        show = editKind != null,
        title = when (editKind) {
            SheetEditKind.DELAY -> "Delay antar command (detik)"
            SheetEditKind.SESSION -> "Durasi sesi (menit)"
            SheetEditKind.BREAK_EVERY -> "Break tiap N command"
            SheetEditKind.BREAK_LEN -> "Break durasi (detik)"
            else -> "Grace mulai (detik)"
        },
        onDismissRequest = { editKind = null },
    ) {
        EditSheetBody(vm, config0, editKind) { editKind = null }
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
        show = wizardOpen,
        title = "Diagnostik Shizuku",
        onDismissRequest = { onWizardToggle(false) },
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

enum class SheetEditKind { DELAY, SESSION, BREAK_EVERY, BREAK_LEN, GRACE }

@Composable
private fun EditSheetBody(
    vm: AppViewModel,
    base: AppConfig,
    kind: SheetEditKind?,
    onDone: () -> Unit,
) {
    if (kind == null) return
    var txt by remember(kind) {
        mutableStateOf(
            when (kind) {
                SheetEditKind.DELAY -> "${base.minDelaySec},${base.maxDelaySec}"
                SheetEditKind.SESSION -> "${base.maxSessionMin}"
                SheetEditKind.BREAK_EVERY -> "${base.breakEveryCmd}"
                SheetEditKind.BREAK_LEN -> "${base.breakMinSec},${base.breakMaxSec}"
                SheetEditKind.GRACE -> "${base.startGraceSec}"
            },
        )
    }
    var err by remember { mutableStateOf<String?>(null) }

    Column {
        Text(
            if (kind == SheetEditKind.DELAY || kind == SheetEditKind.BREAK_LEN) {
                "format: min,maks"
            } else {
                "nilai"
            },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = txt,
            onValueChange = { txt = it },
            label = "angka",
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val r = runCatching {
                    val parts = txt.split(",").map { it.trim().toLong() }
                    when (kind) {
                        SheetEditKind.DELAY -> base.copy(minDelaySec = parts[0], maxDelaySec = parts[1])
                        SheetEditKind.SESSION -> base.copy(maxSessionMin = parts[0])
                        SheetEditKind.BREAK_EVERY -> base.copy(breakEveryCmd = parts[0].toInt())
                        SheetEditKind.BREAK_LEN -> base.copy(breakMinSec = parts[0], breakMaxSec = parts[1])
                        SheetEditKind.GRACE -> base.copy(startGraceSec = parts[0])
                    }
                }
                val updated = r.getOrNull()
                if (updated == null) {
                    err = "format tidak valid"
                } else {
                    val res = vm.updateConfig(updated)
                    if (res.isSuccess) onDone() else err = res.exceptionOrNull()?.message
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) { Text("Simpan") }
        err?.let {
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
                verticalAlignment = Alignment.CenterVertically,
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

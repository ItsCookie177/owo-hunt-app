package dev.owohunt.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.MONEY_SUGGESTIONS
import dev.owohunt.core.domain.isMoneyCommand
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DangerRed = Color(0xFFE53935)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RotationScreen(vm: AppViewModel, pad: PaddingValues) {
    val config by vm.config.collectAsState()
    val config0 = config

    var editing by remember { mutableStateOf(-1) } // -1 = tutup; -2 = tambah baru; >=0 idx

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenContentPadding(pad)),
    ) {
        SmallTitle(text = "Rotasi command")
        Card {
            if (config0.rotation.isEmpty()) {
                BasicComponent(
                    title = "Belum ada command",
                    summary = "tambahkan lewat daftar quick add di bawah",
                )
            }
            config0.rotation.forEachIndexed { idx, cmd ->
                BasicComponent(
                    title = cmd.text.ifBlank { cmd.id },
                    summary = listOfNotNull(
                        if (isMoneyCommand(cmd.text) && cmd.amount != null) "nominal taruhan ${cmd.amount}" else null,
                        if (cmd.maxPerSession != null) "maks ${cmd.maxPerSession}x/sesi" else null,
                        if (!cmd.enabled) "nonaktif" else null,
                    ).joinToString(" · ").ifBlank { "tanpa batas" },
                    onClick = { editing = idx },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hanya tampil command yang BELUM ada di rotasi aktif
        val available = config0.rotation.map { it.text }.let { existing ->
            MONEY_SUGGESTIONS.filter { sug -> sug.text !in existing }
        }
        if (available.isNotEmpty()) {
            SmallTitle(text = "Tambah cepat")
            Card {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    available.forEach { sug ->
                        Text(
                            text = "+ ${sug.text}",
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    vm.updateConfig(
                                        config0.copy(
                                            rotation = config0.rotation +
                                                CommandDef(id = "cmd${System.currentTimeMillis() % 100000}", text = sug.text),
                                        ),
                                    )
                                }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    // Editor command via bottom sheet (tap item rotasi)
    OverlayBottomSheet(
        show = editing != -1,
        title = "Edit command",
        onDismissRequest = { editing = -1 },
    ) {
        if (editing >= 0 && editing < config0.rotation.size) {
            CommandSheetBody(
                vm = vm,
                initial = config0.rotation[editing],
                onDone = { editing = -1 },
            )
        } else {
            editing = -1
        }
    }
}

@Composable
private fun CommandSheetBody(
    vm: AppViewModel,
    initial: CommandDef,
    onDone: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var hasAmount by remember { mutableStateOf(initial.amount != null) }
    var amount by remember { mutableFloatStateOf(initial.amount?.toFloat() ?: 100f) }
    var unlimited by remember { mutableStateOf(initial.maxPerSession == null) }
    var maxPer by remember { mutableFloatStateOf((initial.maxPerSession ?: 10).toFloat().coerceIn(1f, 100f)) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.navigationBarsPadding()) {
        BasicComponent(
            title = "Aktif",
            summary = if (enabled) "command dijalankan" else "command dilewati",
            endActions = {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            },
        )
        BasicComponent(
            title = "Command",
            summary = text.ifBlank { "ketik di bawah" },
        )
        TextField(
            value = text,
            onValueChange = { text = it },
            label = "command",
        )
        if (isMoneyCommand(text)) {
            BasicComponent(
                title = "Nominal taruhan",
                summary = if (hasAmount) "${amount.toInt()} owoncy" else "tanpa nominal",
                endActions = {
                    Switch(checked = hasAmount, onCheckedChange = { hasAmount = it })
                },
            )
            if (hasAmount) {
                Slider(
                    value = amount,
                    onValueChange = { amount = it },
                    valueRange = 1f..1_000_000f,
                )
            }
        }
        BasicComponent(
            title = "Maks per sesi",
            summary = if (unlimited) "tanpa batas" else "maks ${maxPer.toInt()}x",
            endActions = {
                Switch(checked = !unlimited, onCheckedChange = { unlimited = !it })
            },
        )
        if (!unlimited) {
            Slider(
                value = maxPer,
                onValueChange = { maxPer = it },
                valueRange = 1f..100f,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val amountVal: Long? = if (isMoneyCommand(text) && hasAmount) amount.toLong() else null
                    val maxVal: Int? = if (!unlimited) maxPer.toInt() else null
                    val updated = initial.copy(
                        text = text.trim(),
                        enabled = enabled,
                        amount = amountVal,
                        maxPerSession = maxVal,
                    )
                    val cfg = vm.config.value
                    val list = cfg.rotation.toMutableList()
                    val i = list.indexOfFirst { it.id == initial.id }
                    val r = runCatching {
                        if (i >= 0) list[i] = updated else list.add(updated)
                        vm.updateConfig(cfg.copy(rotation = list))
                    }
                    if (r.isSuccess) {
                        onDone()
                    } else {
                        msg = r.exceptionOrNull()?.message
                    }
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) { Text("Simpan") }
            Button(
                onClick = {
                    val cfg = vm.config.value
                    val list = cfg.rotation.toMutableList()
                    val i = list.indexOfFirst { it.id == initial.id }
                    if (i >= 0) list.removeAt(i)
                    vm.updateConfig(cfg.copy(rotation = list))
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(color = DangerRed, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) { Text("Hapus") }
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

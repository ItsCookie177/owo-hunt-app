package dev.owohunt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import dev.owohunt.core.domain.CommandDef
import dev.owohunt.core.domain.MONEY_SUGGESTIONS
import dev.owohunt.core.domain.isMoneyCommand
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RotationScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    val config0 = config

    var editing by remember { mutableStateOf(-1) } // -1 = tutup; -2 = tambah baru; >=0 idx
    var msg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SmallTitle("Rotasi command")
        Card {
            if (config0.rotation.isEmpty()) {
                Text("Belum ada command", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            config0.rotation.forEachIndexed { idx, cmd ->
                BasicComponent(
                    title = cmd.text.ifBlank { cmd.id },
                    summary = listOfNotNull(
                        if (cmd.amount != null) "nominal ${cmd.amount}" else null,
                        if (cmd.maxPerSession != null) "maks ${cmd.maxPerSession}x/sesi" else "tanpa batas",
                        if (!cmd.enabled) "nonaktif" else null,
                    ).joinToString(" — ").ifBlank { null },
                    onClick = { editing = idx }

                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SmallTitle("Tambah cepat")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MONEY_SUGGESTIONS.chunked(2).forEach { pairIn ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairIn.forEach { sug ->
                            Button(onClick = {
                                vm.updateConfig(config0.copy(
                                    rotation = config0.rotation +
                                        CommandDef(id = "cmd${config0.rotation.size + 1}", text = sug.text),
                                ))
                            }) { Text("+ ${sug.text}") }
                        }
                    }
                }
            }
        }

        msg?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(200.dp))
    }

    // Editor command via bottom sheet (tap item rotasi / tombol tambah)
    OverlayBottomSheet(
        show = editing != -1,
        title = if (editing == -2) "Command baru" else "Edit command",
        onDismissRequest = { editing = -1 },
    ) {
        if (editing != -1) {
            val cmd = if (editing >= 0 && editing < config0.rotation.size) {
                config0.rotation[editing]
            } else {
                CommandDef(id = "", text = "")
            }
            CommandSheetBody(
                vm = vm,
                initial = cmd,
                isNew = editing == -2,
                onDone = { editing = -1 },
            )
        }
    }
}

@Composable
private fun CommandSheetBody(
    vm: AppViewModel,
    initial: CommandDef,
    isNew: Boolean,
    onDone: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var hasAmount by remember { mutableStateOf(initial.amount != null) }
    var amount by remember { mutableFloatStateOf(initial.amount?.toFloat() ?: 100f) }
    var unlimited by remember { mutableStateOf(initial.maxPerSession == null) }
    var maxPer by remember { mutableStateOf(initial.maxPerSession?.toString() ?: "") }
    var msg by remember { mutableStateOf<String?>(null) }

    Column {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = "command",
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Aktif", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        if (isMoneyCommand(text)) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Nominal ${amount.toInt()}", modifier = Modifier.weight(1f))
                Switch(checked = hasAmount, onCheckedChange = { hasAmount = it })
            }
            if (hasAmount) {
                Slider(
                    value = amount,
                    onValueChange = { amount = it },
                    valueRange = 1f..1_000_000f,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Maks per sesi: tanpa batas", modifier = Modifier.weight(1f))
            Switch(checked = !unlimited, onCheckedChange = { unlimited = !it })
        }
        if (!unlimited) {
            Spacer(Modifier.height(4.dp))
            TextField(
                value = maxPer,
                onValueChange = { maxPer = it },
                label = "maks per sesi",
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val amountVal: Long? = if (isMoneyCommand(text) && hasAmount) amount.toLong() else null
                    val maxVal: Int? = if (!unlimited) maxPer.toIntOrNull() else null
                    val updated = initial.copy(
                        id = if (initial.id.isBlank()) "cmd${System.currentTimeMillis() % 10000}" else initial.id,
                        text = text.trim(),
                        enabled = enabled,
                        amount = amountVal,
                        maxPerSession = maxVal,
                    )
                    val cfg = vm.config.value
                    val list = cfg.rotation.toMutableList()
                    val insertIdx = if (isNew) list.size else list.indexOfFirst { it.id == initial.id }
                    if (insertIdx >= 0) list.set(insertIdx, updated) else list.add(updated)
                    val r = vm.updateConfig(cfg.copy(rotation = list))
                    if (r.isSuccess) {
                        onDone()
                    } else {
                        msg = r.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (isNew) "Tambah" else "Simpan") }
            if (!isNew) {
                Button(
                    onClick = {
                        val cfg = vm.config.value
                        val list = cfg.rotation.toMutableList()
                        val i = list.indexOfFirst { it.id == initial.id }
                        if (i >= 0) list.removeAt(i)
                        vm.updateConfig(cfg.copy(rotation = list))
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Hapus") }
            }
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

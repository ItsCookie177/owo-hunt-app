package dev.owohunt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import dev.owohunt.core.domain.Status
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val config by vm.config.collectAsState()
    val stats by vm.stats.collectAsState()
    val countdown = countdownText(vm)
    var actionError by remember { mutableStateOf<String?>(null) }
    var showCaptcha by remember { mutableStateOf(false) }
    val running = stats.status == Status.RUNNING
    val captcha = stats.status == Status.CAPTCHA
    if (showCaptcha != captcha) showCaptcha = captcha

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Kartu Status — Tilt
        Card(
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            BasicComponent(
                title = "Adapter",
                summary = vm.adapter.name,
            )
            BasicComponent(
                title = "Discord foreground",
                summary = if (vm.adapter.foreground) "ya" else "tidak",
            )
            BasicComponent(
                title = "Status macro",
                summary = statusText(stats.status),
            )
        }

        // Kartu Sesi — Tilt
        Card(
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            BasicComponent(
                title = "Komando berikutnya",
                summary = "dalam $countdown",
            )
            if (running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        val startedAt = stats.startedAt ?: 0L
                        val total = config.maxSessionMin * 60_000
                        if (total <= 0) 0f else (((System.currentTimeMillis() - startedAt)).toFloat() / total).coerceIn(0f, 1f)
                    },
                )
            }
        }

        // Kartu Statistik — Tilt
        Card(
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            BasicComponent(
                title = "Total terkirim",
                summary = "${stats.totalSent}",
            )
            stats.perCommand.entries.forEach { (id, n) ->
                BasicComponent(
                    title = id,
                    summary = "$n kirim",
                )
            }
        }

        (stats.lastError ?: actionError)?.let { msg ->
            Text(msg, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }

        // Aksi utama
        if (!running) {
            val context = LocalContext.current
            Button(
                onClick = {
                    vm.doStart { r ->
                        actionError = r.exceptionOrNull()?.message
                        if (r.isSuccess) HuntService.start(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Mulai") }
        } else {
            Button(
                onClick = { vm.doStop(false) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Berhenti") }
            Button(
                onClick = { vm.doStop(true) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Darurat") }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Dialog captcha (paritas web)
    OverlayDialog(
        show = showCaptcha,
        title = "Perlu verifikasi human",
        summary = "Selesaikan captcha di Discord — jendela 10 menit",
        onDismissRequest = { showCaptcha = false },
    ) {
        val context = LocalContext.current
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://owobot.com/captcha")))
                    }
                    showCaptcha = false
                },
                modifier = Modifier.weight(1f),
            ) { Text("Buka owobot.com/captcha") }
            Button(
                onClick = { showCaptcha = false },
                modifier = Modifier.weight(1f),
            ) { Text("Tutup") }
        }
    }
}

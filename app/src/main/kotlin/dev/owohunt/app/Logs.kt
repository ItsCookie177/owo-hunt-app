package dev.owohunt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.owohunt.core.runtime.LogEntry
import dev.owohunt.core.runtime.LogLevel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val WarnYellow = Color(0xFFF5C518)
private val ErrorRed = Color(0xFFE53935)

@Composable
fun LogScreen(vm: AppViewModel, pad: PaddingValues) {
    val logs by vm.logs.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    val filtered = remember(filter, logs, query) {
        val level = when (filter) {
            1 -> LogLevel.INFO
            2 -> LogLevel.WARN
            3 -> LogLevel.ERROR
            else -> null
        }
        val list = if (level == null) vm.logs.value else vm.logs.value.filter { it.level == level }
        val hit = if (query.isBlank()) list else list.filter { it.msg.contains(query, ignoreCase = true) }
        hit
    }
    // auto-scroll ke bawah (entri terbaru di atas? no — terbaru ditambah bawah)
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) state.animateScrollToItem(filtered.lastIndex)
    }

    LazyColumn(
        Modifier
            .fillMaxSize(),
        state = state,
        contentPadding = screenContentPadding(pad),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            InputField(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = "Cari log",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
        item {
            TabRowWithContour(
                tabs = listOf("Semua", "Info", "Warn", "Error"),
                selectedTabIndex = filter,
                onTabSelected = { filter = it },
            )
        }
        if (filtered.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = MiuixIcons.ListView,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Belum ada aktivitas", color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "log macro akan muncul di sini",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        items(filtered) { e ->
            Text(
                text = "${fmt(e)} ${levelTag(e.level)} ${e.msg}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = when (e.level) {
                    LogLevel.WARN -> WarnYellow
                    LogLevel.ERROR -> ErrorRed
                    else -> MiuixTheme.colorScheme.onSurface
                },
            )
        }
    }
}

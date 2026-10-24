package dev.owohunt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.owohunt.core.runtime.LogLevel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LOG_FILTERS = listOf("Semua", "Info", "Warn", "Error")

@Composable
fun LogScreen(vm: AppViewModel) {
    val logs by vm.logs.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    val filtered = remember(filter, logs) {
        val level = when (filter) {
            1 -> LogLevel.INFO
            2 -> LogLevel.WARN
            3 -> LogLevel.ERROR
            else -> null
        }
        val list = if (level == null) vm.logs.value else vm.logs.value.filter { it.level == level }
        list.asReversed()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabRow(
                    tabs = LOG_FILTERS,
                    selectedTabIndex = filter,
                    onTabSelected = { filter = it },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.logger.clear() }) { Text("Bersihkan") }
            }
        }
        if (filtered.isEmpty()) {
            item { Text("Log kosong", color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
        items(filtered) { e ->
            Text("${fmt(e)} ${levelTag(e.level)} ${e.msg}")
        }
    }
}

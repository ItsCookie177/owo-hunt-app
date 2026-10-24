package dev.owohunt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.RotateLeft
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = Runtime.get(applicationContext)
        requestNotificationPermissionIfNeeded()
        setContent {
            MiuixTheme(
                controller = ThemeController(
                    ColorSchemeMode.System,
                    isDark = isSystemInDarkTheme(),
                ),
            ) {
                AppRoot(vm)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}

data class Tab(val label: String, val icon: ImageVector)

val TABS = listOf(
    Tab("Dashboard", MiuixIcons.Home),
    Tab("Rotasi", MiuixIcons.RotateLeft),
    Tab("Pengaturan", MiuixIcons.Settings),
    Tab("Log", MiuixIcons.ListView),
)

/** Padding bawah semua screen — ruang floating bottom bar. */
val ScreenBottomPadding = PaddingValues(bottom = 110.dp)

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(0)
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp),
        ) {
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> RotationScreen(vm)
                2 -> SettingsScreen(vm)
                else -> LogScreen(vm)
            }
        }
        FloatingNavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TABS.forEachIndexed { i, t ->
                FloatingNavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = t.icon,
                    label = t.label,
                )
            }
        }
    }
}

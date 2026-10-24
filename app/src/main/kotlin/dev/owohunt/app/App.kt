package dev.owohunt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import dev.owohunt.core.runtime.AutomationAdapter
import dev.owohunt.core.runtime.MockAdapter
import java.io.File
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.RotateLeft
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = AppViewModel(
            configPath = File(filesDir, "config.json"),
            presetsPath = File(filesDir, "presets.json"),
            adapter = mockAdapter(),
        )
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

    private fun mockAdapter(): AutomationAdapter = MockAdapter()
}

data class Tab(val label: String, val icon: ImageVector)

val TABS = listOf(
    Tab("Dashboard", MiuixIcons.Home),
    Tab("Rotasi", MiuixIcons.RotateLeft),
    Tab("Presets", MiuixIcons.Playlist),
    Tab("Statistik", MiuixIcons.Tasks),
    Tab("Log", MiuixIcons.ListView),
)

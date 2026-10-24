package dev.owohunt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
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
    Tab("Presets", MiuixIcons.Playlist),
    Tab("Statistik", MiuixIcons.Tasks),
    Tab("Log", MiuixIcons.ListView),
)

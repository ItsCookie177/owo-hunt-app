package dev.owohunt.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.app.Application
import android.os.Bundle
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.RotateLeft
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Crash logger: stack trace ke filesDir + MediaStore Downloads (tanpa izin khusus). */
class OwoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = "=== crash $ts ===\n" + Log.getStackTraceString(e) + "\n"
                try { File(filesDir, "crash.txt").appendText(text) } catch (_: Throwable) {}
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val cv = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "owohunt-crash.txt")
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            cv,
                        )
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                            cv.clear()
                            cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            contentResolver.update(uri, cv, null, null)
                        }
                    }
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
            previous?.uncaughtException(thread, e)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

/** Padding bawah semua screen — ruang floating bottom bar + inset. */
val ScreenBottomPadding = PaddingValues(bottom = 110.dp)

@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(0)
    }
    // miuix-blur minSdk 33 (RuntimeShader); di bawah itu fallback solid.
    val blurOk = Build.VERSION.SDK_INT >= 33
    val backdrop = rememberLayerBackdrop()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            // Konten screen — dapat clearance atas (header) & bawah (floating bar)
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 60.dp, bottom = 118.dp),
            ) {
                when (tab) {
                    0 -> DashboardScreen(vm)
                    1 -> RotationScreen(vm)
                    2 -> SettingsScreen(vm)
                    else -> LogScreen(vm)
                }
            }

            // Header halaman: progressive blur strip (kaca atas)
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .then(
                        if (blurOk) {
                            Modifier.progressiveTextureBlur(
                                backdrop = backdrop,
                                shape = RectangleShape,
                                blurRadius = 18f,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(
                        if (blurOk) {
                            MiuixTheme.colorScheme.background.copy(alpha = 0.4f)
                        } else {
                            MiuixTheme.colorScheme.surface
                        },
                    ),
            ) {
                TopAppBar(
                    title = "OwO Macro",
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                )
            }

            // Floating bar — bawah, blur, tab aktif primary
            FloatingNavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .then(
                        if (blurOk) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(percent = 50),
                                blurRadiusX = 18f,
                                blurRadiusY = 18f,
                            )
                        } else {
                            Modifier
                        },
                    ),
                color = if (blurOk) {
                    MiuixTheme.colorScheme.surface.copy(alpha = 0.65f)
                } else {
                    MiuixTheme.colorScheme.surface
                },
                shadowElevation = if (blurOk) 0.dp else FloatingNavigationBarDefaults.ShadowElevation,
            ) {
                TABS.forEachIndexed { i, t ->
                    FloatingNavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = t.icon,
                        label = t.label,
                        colors = NavigationBarDefaults.navigationBarItemColors(
                            unselectedContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            selectedContentColor = MiuixTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

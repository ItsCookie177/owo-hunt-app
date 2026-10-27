package dev.owohunt.app

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.RotateLeft
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val prefs = remember { getSharedPreferences("ui", Context.MODE_PRIVATE) }
            var monet by rememberSaveable { mutableStateOf(prefs.getBoolean("monet", false)) }
            MiuixTheme(
                controller = ThemeController(
                    colorSchemeMode = if (monet) {
                        ColorSchemeMode.MonetSystem
                    } else {
                        ColorSchemeMode.System
                    },
                    isDark = isSystemInDarkTheme(),
                ),
            ) {
                AppRoot(vm, monet) { on ->
                    monet = on
                    prefs.edit().putBoolean("monet", on).apply()
                }
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
fun AppRoot(
    vm: AppViewModel,
    monet: Boolean,
    onMonet: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var wizardOpen by remember { mutableStateOf(false) }
    // miuix-blur butuh API 33+ (RuntimeShader); di bawah itu fallback solid.
    val blurOk = Build.VERSION.SDK_INT >= 33
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            // Header collapsing + progressive blur strip (judul "OwO Macro" + aksi probe)
            Box(
                Modifier
                    .fillMaxWidth()
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
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = { tab = 2; wizardOpen = true },
                        ) {
                            Icon(
                                MiuixIcons.Tasks,
                                contentDescription = "Diagnostik",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            // Konten screen — yang direkam utk blur (blur bar di luar node ini,
            // hindari siklus render-node yang bikin stack overflow di hwui)
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .padding(bottom = 118.dp),
                ) {
                    when (tab) {
                        0 -> DashboardScreen(vm)
                        1 -> RotationScreen(vm)
                        2 -> SettingsScreen(vm, monet, onMonet, wizardOpen) { wizardOpen = it }
                        else -> LogScreen(vm)
                    }
                }
            }

            // Floating bar — posisi dipaksa di Box wrapper sendiri (align internal
            // FloatingNavigationBar tidak mewarisi BottomCenter dari Caller), ada
            // jarak kiri-kanan biar tidak mepet tepi layar
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
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
            ) {
                FloatingNavigationBar(
                    modifier = Modifier,
                    color = if (blurOk) {
                        MiuixTheme.colorScheme.surface.copy(alpha = 0.65f)
                    } else {
                        MiuixTheme.colorScheme.surface
                    },
                    shadowElevation = if (blurOk) 0.dp else FloatingNavigationBarDefaults.ShadowElevation,
                ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FloatingNavigationBarDefaults.HorizontalPadding),
                ) {
                    TABS.forEachIndexed { i, t ->
                        val sel = tab == i
                        val tint = if (sel) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tab = i }
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(
                                t.icon,
                                contentDescription = t.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                t.label,
                                color = tint,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }}}

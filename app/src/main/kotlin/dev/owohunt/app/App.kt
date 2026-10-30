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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.RotateLeft
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
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

/** Padding konten tiap screen: top dihitung Scaffold (topbar), bottom = bar melayang + inset. */
fun screenContentPadding(pad: PaddingValues): PaddingValues = PaddingValues(
    top = pad.calculateTopPadding(),
    start = 16.dp,
    end = 16.dp,
    bottom = pad.calculateBottomPadding() + 140.dp,
)

@Composable
fun AppRoot(
    vm: AppViewModel,
    monet: Boolean,
    onMonet: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var wizardOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // miuix-blur butuh API 33+ (RuntimeShader); di bawah itu fallback solid.
    val blurOk = Build.VERSION.SDK_INT >= 33
    val backdrop = rememberLayerBackdrop()
    val scrollBehavior = MiuixScrollBehavior()
    // ganti tab → bar top di-expand lagi
    LaunchedEffect(tab) { scrollBehavior.state.heightOffset = 0f }
    val navItems = remember(TABS) { TABS.map { NavigationItem(it.label, it.icon) } }
    val tabTitles = TABS.map { it.label }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            // Header collapsing + progressive blur strip (judul "OwO Macro" + aksi probe).
            // Blur di child Box (pola PageUtils example) agar scrim blurdrescia tidak
            // menutupi aksi; offscreen layer biar mask progressive tidak bocor ke konten.
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (blurOk) {
                            Modifier.graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (blurOk) {
                    Box(
                        Modifier.matchParentSize().progressiveTextureBlur(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            blurRadius = 20f,
                            gradient = ProgressiveBlur.Top.copy(curve = 2.2f, endFraction = 0.85f),
                            noiseCoefficient = 0f,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(MiuixTheme.colorScheme.background.copy(alpha = 0.72f)),
                                ),
                            ),
                        ),
                    )
                } else {
                    Box(Modifier.matchParentSize().background(MiuixTheme.colorScheme.surface))
                }
                TopAppBar(
                    title = tabTitles[tab],
                    color = Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        // Diagnostik Shizuku: patron luaran (pemeriksaan teknis)
                        IconButton(
                            onClick = { tab = 2; wizardOpen = true },
                        ) {
                            Icon(
                                MiuixIcons.Tasks,
                                contentDescription = "Diagnostik Shizuku",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        // Bersihkan log — hanya di tab Log, dengan konfirmasi
                        if (tab == 3) {
                            IconButton(
                                onClick = { confirmClear = true },
                            ) {
                                Icon(
                                    MiuixIcons.Delete,
                                    contentDescription = "Bersihkan log",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
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
            // hindari siklus render-node yang bikin stack overflow di hwui).
            // Tanpa .padding(pad) di container: konten mengalir sampai bawah layar
            // (terlihat di balik glass); inset dikatur via contentPadding tiap screen.
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                when (tab) {
                    0 -> DashboardScreen(vm, pad)
                    1 -> RotationScreen(vm, pad)
                    2 -> SettingsScreen(vm, monet, onMonet, wizardOpen, { wizardOpen = it }, pad)
                    else -> LogScreen(vm, pad)
                }
            }

            // Floating bar — kapsul liquid glass (pola IosLiquidGlassNavigationBar
            // dari example Miuix), tampilan tab + gestur daper dikelola di dalam
            IosLiquidGlassNavigationBar(
                items = navItems,
                selectedIndex = tab,
                onItemClick = { tab = it },
                backdrop = backdrop,
                isBlurActive = blurOk,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

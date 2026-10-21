package dev.owohunt.automation

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

/** Helper status/izin Shizuku (dipakai UI :app). */
object ShizukuStatus {

    fun pingBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun isGranted(): Boolean = try {
        pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun shouldRequest(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) {
        false
    }

    fun request(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    /** Path rish di filesDir aplikasi (user menaruh hasil ekspor Shizuku di sini). */
    fun rishPath(context: Context): String =
        File(context.filesDir, "rish").absolutePath

    fun rishExists(context: Context): Boolean {
        val f = File(rishPath(context))
        return f.exists() && f.canExecute()
    }
}

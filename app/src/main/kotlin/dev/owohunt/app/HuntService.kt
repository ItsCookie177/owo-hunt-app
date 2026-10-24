package dev.owohunt.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.owohunt.core.domain.Status
import dev.owohunt.core.domain.Stats
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

class HuntService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    private val vm by lazy { Runtime.get(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                vm.doStop(emergency = false)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(
                    NOTIF_ID_STATUS,
                    statusNotification(vm.stats.value, ""),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
                )
            }
        }
        if (watchJob == null) {
            watchJob = scope.launch { watch() }
        }
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private suspend fun watch() {
        val job = currentCoroutineContext()[Job] ?: return
        // Update: begitu stats berubah + tiap 15 dtk untuk countdown.
        var lastStats = vm.stats.value
        while (job.isActive) {
            val s = vm.stats.value
            val countdown = countdownText(s)
            val statusChanged = s.status != lastStats.status
            lastStats = s
            notifyStatus(statusNotification(s, countdown))
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (s.status == Status.CAPTCHA) {
                notifyCaptcha(captchaNotification(countdown))
            } else {
                nm.cancel(NOTIF_ID_CAPTCHA)
            }
            if (s.status == Status.IDLE || s.status == Status.ERROR) {
                stopForegroundCompat()
                stopSelf()
                return
            }
            delay(15_000)
        }
    }

    private fun countdownText(s: Stats): String {
        val nextAt = s.nextAt ?: return "-"
        val sec = ((nextAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt()
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }

    private fun notifyStatus(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_STATUS, n)
    }

    private fun notifyCaptcha(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_CAPTCHA, n)
    }

    private fun statusNotification(s: Stats, countdown: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HuntService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("OwO Hunt — ${statusLabel(s.status)}")
            .setContentText("Berikutnya: $countdown — total ${s.totalSent} kirim")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Berhenti", stop)
            .build()
    }

    private fun captchaNotification(countdown: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HuntService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_CAPTCHA)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Perlu verifikasi human")
            .setContentText("Sisa $countdown — selesaikan captcha di Discord")
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVibrate(LongArray(1) { 400 })
            .addAction(0, "Hentikan", stop)
            .build()
        return n
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Status macro", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Status berjalan + countdown command berikutnya"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTCHA, "Verifikasi human", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Captcha terdeteksi — butuh intervensi"
                enableVibration(true)
                vibrationPattern = LongArray(1) { 400L }
            },
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_CAPTCHA = "captcha"
        const val NOTIF_ID_STATUS = 1
        const val NOTIF_ID_CAPTCHA = 2
        const val ACTION_STOP = "dev.owohunt.app.STOP"

        fun start(context: Context) {
            val i = Intent(context, HuntService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, HuntService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, i)
        }

        fun statusLabel(s: Status): String = when (s) {
            Status.IDLE -> "Idle"
            Status.RUNNING -> "Berjalan"
            Status.CAPTCHA -> "Verifikasi human"
            Status.ERROR -> "Error"
        }
    }
}


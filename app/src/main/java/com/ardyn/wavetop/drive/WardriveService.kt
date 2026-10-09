package com.ardyn.wavetop.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ardyn.wavetop.R
import com.ardyn.wavetop.MainActivity
import com.ardyn.wavetop.engine.SurveyEngine
import com.ardyn.wavetop.engine.WardriveStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a wardrive running with the screen off or WaveTop in the background. Android only
 * lets an app keep GPS and full-rate Wi-Fi scanning in the background from a foreground
 * service, which must show a notification — this one shows elapsed time and counts, with
 * a Stop action. The engine does the recording; this just holds the process in the foreground.
 */
class WardriveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine by lazy { SurveyEngine.get(this) }
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stopWardrive()
            return START_NOT_STICKY
        }
        val status = engine.state.value.wardrive
        if (status == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(status), type)
        } catch (e: RuntimeException) {
            engine.log("Background recording unavailable (${e.javaClass.simpleName}); keep WaveTop open", warning = true)
            stopSelf()
            return START_NOT_STICKY
        }
        ticker?.cancel()
        ticker = scope.launch {
            // Refresh the notification text every few seconds; stop when the wardrive ends.
            while (isActive) {
                val current = engine.state.value.wardrive ?: break
                notify(current)
                delay(5_000)
            }
            stopSelf()
        }
        // If the process is killed, the drive's file is already on disk up to the last flush;
        // restarting the service couldn't resume it, so don't ask to be restarted.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(status: WardriveStatus) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(status))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: the service still runs, the text just doesn't update.
        }
    }

    private fun notification(status: WardriveStatus) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wardrive)
            .setContentTitle("Wardrive · ${status.name}")
            .setContentText(
                "${elapsed(System.currentTimeMillis() - status.startedMs)} · " +
                    "${status.wifiDevices} Wi-Fi · ${status.bluetoothDevices} BT · ${status.geotagged} geotagged",
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                0,
                "Stop & save",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, WardriveService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wardrive", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a WaveTop wardrive is recording"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "wavetop_wardrive"
        private const val NOTIFICATION_ID = 0x5741
        private const val ACTION_STOP = "com.ardyn.wavetop.action.STOP_WARDRIVE"

        fun elapsed(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
        }
    }
}

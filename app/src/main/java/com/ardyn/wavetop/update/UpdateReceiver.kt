package com.ardyn.wavetop.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ardyn.wavetop.BuildConfig
import com.ardyn.wavetop.MainActivity
import com.ardyn.wavetop.R

/**
 * Two jobs:
 *  - [ACTION_INSTALL_RESULT]: Android's package installer reporting on an update WaveTop started.
 *    If it needs the user to confirm, show its dialog; otherwise pass the outcome to the Updates screen.
 *  - [Intent.ACTION_MY_PACKAGE_REPLACED]: runs in the *new* version after an update. Android doesn't
 *    reopen an app it just replaced, so post a notification the user can tap to get back in.
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL_RESULT -> onInstallResult(context, intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> notifyUpdated(context)
        }
    }

    private fun onInstallResult(context: Context, intent: Intent) {
        val updater = Updater.get(context)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    updater.reportInstallResult(false, "Android didn't ask to confirm the install.")
                } else {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> updater.reportInstallResult(true, null)
            else -> updater.reportInstallResult(
                false,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: describe(status),
            )
        }
    }

    private fun describe(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "blocked by the device"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "it conflicts with the installed app (different signing key?)"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "not compatible with this device"
        PackageInstaller.STATUS_FAILURE_INVALID -> "the APK is invalid"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "not enough storage"
        else -> "error $status"
    }

    private fun notifyUpdated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Tells you when WaveTop has finished updating"
                    },
                )
            }
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wavetop)
            .setContentTitle("WaveTop updated to ${BuildConfig.VERSION_NAME}")
            .setContentText("Tap to open WaveTop.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, note)
        } catch (_: SecurityException) {
            // Notifications not allowed; the new version is installed either way.
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.ardyn.wavetop.action.INSTALL_RESULT"
        private const val CHANNEL_ID = "wavetop_updates"
        private const val NOTIFICATION_ID = 0x5754
    }
}

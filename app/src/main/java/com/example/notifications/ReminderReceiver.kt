package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.config.AppConfig

/**
 * Fires strictly neutral, discreet local notifications titled "Daily Log".
 * Never mentions cannabis, weed, quitting, or health conditions in notification content.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_DAILY
        showNeutralNotification(context, kind)
    }

    companion object {
        const val CHANNEL_ID = "daily_log_reminders"
        const val EXTRA_KIND = "reminder_kind"
        const val KIND_DAILY = "daily"
        const val KIND_WEEKLY = "weekly"
        const val KIND_PEAK = "peak"
        const val KIND_MILESTONE = "milestone"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun showNeutralNotification(context: Context, kind: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            ensureChannel(context)

            val body = when (kind) {
                KIND_WEEKLY -> context.getString(R.string.notification_weekly_body)
                KIND_PEAK -> context.getString(R.string.notification_peak_body)
                KIND_MILESTONE -> context.getString(R.string.notification_milestone_body)
                else -> context.getString(R.string.notification_checkin_body)
            }

            val notificationId = when (kind) {
                KIND_WEEKLY -> 1002
                KIND_PEAK -> 1003
                KIND_MILESTONE -> 1004
                else -> 1001
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(AppConfig.APP_NAME_PUBLIC)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, notification)
        }
    }
}

package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.model.ReminderPrefs
import java.util.Calendar

/**
 * Very low-battery local notification scheduler using inexact repeating alarms.
 * No polling loops, no background services, no wake locks.
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun rescheduleAll(prefs: ReminderPrefs, peakCravingHour: Int?) {
        cancelAll()
        if (!prefs.enabled) return

        ReminderReceiver.ensureChannel(appContext)

        // 1. Daily check-in reminder at chosen time
        scheduleDaily(
            requestCode = REQ_DAILY,
            kind = ReminderReceiver.KIND_DAILY,
            hour = prefs.dailyHour,
            minute = prefs.dailyMinute
        )

        // 2. Weekly summary reminder (Sunday at chosen time)
        if (prefs.weeklySummary) {
            scheduleWeekly(
                requestCode = REQ_WEEKLY,
                kind = ReminderReceiver.KIND_WEEKLY,
                dayOfWeek = Calendar.SUNDAY,
                hour = prefs.dailyHour,
                minute = prefs.dailyMinute
            )
        }

        // 3. Heads-up 30 minutes before computed peak craving hour
        if (prefs.peakHourHeadsUp && peakCravingHour != null) {
            val totalMinutes = ((peakCravingHour * 60) - 30 + 1440) % 1440
            scheduleDaily(
                requestCode = REQ_PEAK,
                kind = ReminderReceiver.KIND_PEAK,
                hour = totalMinutes / 60,
                minute = totalMinutes % 60
            )
        }
    }

    fun notifyMilestoneReached(prefs: ReminderPrefs) {
        if (prefs.enabled && prefs.milestoneAlerts) {
            ReminderReceiver.showNeutralNotification(appContext, ReminderReceiver.KIND_MILESTONE)
        }
    }

    fun cancelAll() {
        listOf(REQ_DAILY to ReminderReceiver.KIND_DAILY, REQ_WEEKLY to ReminderReceiver.KIND_WEEKLY, REQ_PEAK to ReminderReceiver.KIND_PEAK)
            .forEach { (code, kind) ->
                alarmManager.cancel(buildPendingIntent(code, kind))
            }
    }

    private fun scheduleDaily(requestCode: Int, kind: String, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            buildPendingIntent(requestCode, kind)
        )
    }

    private fun scheduleWeekly(requestCode: Int, kind: String, dayOfWeek: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY * 7L,
            buildPendingIntent(requestCode, kind)
        )
    }

    private fun buildPendingIntent(requestCode: Int, kind: String): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQ_DAILY = 4101
        private const val REQ_WEEKLY = 4102
        private const val REQ_PEAK = 4103
    }
}

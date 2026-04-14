package com.example.recall_ai.service.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.recall_ai.data.local.entity.Reminder
import com.example.recall_ai.receiver.ReminderAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderAlarmScheduler"

/**
 * Schedules and cancels exact alarms via [AlarmManager].
 *
 * On API 31+ checks [AlarmManager.canScheduleExactAlarms] and falls back to
 * [setAndAllowWhileIdle] (±9 min window) if the user hasn't granted
 * SCHEDULE_EXACT_ALARM permission.
 */
@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: Reminder) {
        val triggerAt = reminder.triggerAtMillis ?: return
        val pi = buildPendingIntent(reminder.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission not granted — using inexact alarm for id=${reminder.id}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        Log.i(TAG, "Alarm scheduled: id=${reminder.id} title='${reminder.title}' triggerAt=$triggerAt")
    }

    fun cancel(reminderId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            Log.i(TAG, "Alarm cancelled: id=$reminderId")
        }
    }

    private fun buildPendingIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

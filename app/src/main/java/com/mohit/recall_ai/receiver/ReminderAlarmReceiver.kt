package com.mohit.recall_ai.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mohit.recall_ai.MainActivity
import com.mohit.recall_ai.data.local.dao.ReminderDao
import com.mohit.recall_ai.data.local.entity.ReminderStatus
import com.mohit.recall_ai.service.notification.NotificationChannelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ReminderAlarmReceiver"
private const val NOTIFICATION_ID_BASE = 5000

/**
 * Fires when an alarm scheduled by [ReminderAlarmScheduler] triggers.
 * Reads the Reminder from Room, marks it FIRED, and posts a high-priority notification.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderDao: ReminderDao

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0L) {
            Log.w(TAG, "Received alarm with no reminder ID — ignoring")
            return
        }

        Log.i(TAG, "Alarm fired for reminder id=$reminderId")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderDao.getById(reminderId)
                if (reminder == null) {
                    Log.w(TAG, "Reminder $reminderId not found in DB — deleted?")
                    return@launch
                }

                reminderDao.updateStatus(reminderId, ReminderStatus.FIRED)

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val contentPi = PendingIntent.getActivity(
                    context, reminderId.toInt(), openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(reminder.title)
                    .setContentText(reminder.description ?: "Recall AI Reminder")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(contentPi)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build()

                nm.notify(NOTIFICATION_ID_BASE + reminderId.toInt(), notification)
                Log.i(TAG, "Notification posted for reminder '${reminder.title}'")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle alarm for reminder $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

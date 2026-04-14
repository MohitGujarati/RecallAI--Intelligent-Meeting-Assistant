package com.example.recall_ai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.recall_ai.data.local.dao.ReminderDao
import com.example.recall_ai.data.local.entity.ReminderStatus
import com.example.recall_ai.service.reminder.ReminderAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BootRescheduleReceiver"

/**
 * Re-schedules all pending alarms after a device reboot.
 * AlarmManager alarms are lost on reboot — this receiver restores them.
 */
@AndroidEntryPoint
class BootRescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderDao: ReminderDao
    @Inject lateinit var alarmScheduler: ReminderAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed — rescheduling pending alarms")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pending = reminderDao.getPendingAlarmsList()
                Log.i(TAG, "Found ${pending.size} pending alarms to reschedule")

                val now = System.currentTimeMillis()
                for (reminder in pending) {
                    val triggerAt = reminder.triggerAtMillis
                    if (triggerAt != null && triggerAt > now) {
                        alarmScheduler.schedule(reminder)
                        Log.d(TAG, "Rescheduled: id=${reminder.id} '${reminder.title}'")
                    } else {
                        // Missed alarm — fire notification immediately
                        reminderDao.updateStatus(reminder.id, ReminderStatus.FIRED)
                        Log.w(TAG, "Missed alarm: id=${reminder.id} '${reminder.title}' — marked FIRED")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

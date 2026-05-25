package com.mohit.recall_ai.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the creation of all notification channels for the app.
 *
 * ── Why two channels? ─────────────────────────────────────────────────
 *
 *  CHANNEL_RECORDING (IMPORTANCE_LOW):
 *    The persistent foreground notification. IMPORTANCE_LOW means:
 *    - Shown in status bar and notification shade
 *    - NO sound, NO vibration, NO heads-up popup
 *    - Correct for ongoing background work the user explicitly started
 *
 *  CHANNEL_ALERTS (IMPORTANCE_DEFAULT):
 *    Transient informational alerts (source changed, silence warning).
 *    IMPORTANCE_DEFAULT means:
 *    - CAN make a sound (but we set silent sound in notification itself)
 *    - Shows heads-up popup when app is in background
 *    - Auto-dismissed after a few seconds via setTimeoutAfter()
 *    Separating this lets users independently mute recording alerts
 *    without hiding the ongoing recording notification, and vice versa.
 *
 * ── Idempotency ───────────────────────────────────────────────────────
 * createChannels() is safe to call multiple times. If a channel with the
 * same ID already exists, Android ignores the call — except for the name
 * and description which can be updated after creation.
 *
 * ── User control ──────────────────────────────────────────────────────
 * Once created, the user can individually mute, block, or change the
 * importance of each channel from Settings. We never override that.
 */
@Singleton
class NotificationChannelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Persistent foreground recording notification */
        const val CHANNEL_RECORDING = "recall_recording"

        /** Transient alerts: source changes, silence warnings, errors */
        const val CHANNEL_ALERTS = "recall_alerts"

        /** High-priority reminders & alarms set by the AI assistant */
        const val CHANNEL_REMINDERS = "recall_reminders"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        createRecordingChannel()
        createAlertsChannel()
        createRemindersChannel()
    }

    private fun createRecordingChannel() {
        val channel = NotificationChannel(
            CHANNEL_RECORDING,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while Recall is actively recording audio"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)   // Explicitly silent — no default sound
        }
        nm.createNotificationChannel(channel)
    }

    private fun createAlertsChannel() {
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            "Recording Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts about microphone, storage, and audio source changes"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)   // Silent — visual only
        }
        nm.createNotificationChannel(channel)
    }

    private fun createRemindersChannel() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Reminders & Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications from AI-detected reminders"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
    }

    /** Returns true if the user has blocked the recording channel */
    fun isRecordingChannelBlocked(): Boolean {
        val channel = nm.getNotificationChannel(CHANNEL_RECORDING)
        return channel?.importance == NotificationManager.IMPORTANCE_NONE
    }

    /** Returns true if overall notifications are disabled for this app */
    fun areNotificationsEnabled(): Boolean = nm.areNotificationsEnabled()
}
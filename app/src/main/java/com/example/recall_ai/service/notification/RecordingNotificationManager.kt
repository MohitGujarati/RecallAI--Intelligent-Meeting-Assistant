package com.example.recall_ai.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RecordingNotifMgr"

/**
 * The single authority for all notification rendering in Recall.
 *
 * ── Contract ─────────────────────────────────────────────────────────
 * RecordingService calls exactly ONE method: [applyState].
 * All other methods are private. The mapping
 *   [NotificationState] → [android.app.Notification]
 * lives entirely inside this class.
 *
 * ── Two-channel strategy ─────────────────────────────────────────────
 *
 *   CHANNEL_RECORDING (IMPORTANCE_LOW):
 *     Persistent foreground notification. Updated on every timer tick
 *     and on every state change. Cannot be swiped away while service runs.
 *
 *   CHANNEL_ALERTS (IMPORTANCE_DEFAULT):
 *     Transient informational notifications (source changed, silence).
 *     Shown on a SEPARATE notification ID so they don't replace the
 *     persistent recording indicator. Auto-dismissed after [ALERT_TIMEOUT_MS].
 *
 * ── Android 16 Live Updates ──────────────────────────────────────────
 * On API 36+, the recording notification uses the Live Update mechanism
 * via [LiveUpdateCompat]. The OS drives the lock-screen Chronometer
 * autonomously — we do NOT call notify() for timer ticks on API 36+.
 * [NotificationTimerManager.isOsDriven] guards the timer polling loop.
 *
 * ── Notification IDs ─────────────────────────────────────────────────
 *   NOTIFICATION_ID_RECORDING = 1001  (foreground, persistent)
 *   NOTIFICATION_ID_ALERT     = 1002  (transient, auto-dismiss)
 */
@Singleton
class RecordingNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelManager: NotificationChannelManager,
    private val actionFactory: NotificationActionFactory
) {

    companion object {
        const val NOTIFICATION_ID          = 1001   // kept for backward compat with service
        const val NOTIFICATION_ID_RECORDING = 1001
        const val NOTIFICATION_ID_ALERT     = 1002

        private const val ALERT_TIMEOUT_MS = 4_000L
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── Initialisation ───────────────────────────────────────────────────

    /**
     * Creates notification channels. Safe to call multiple times — Android
     * ignores duplicate channel creation after the first call.
     */
    fun createNotificationChannel() {
        channelManager.createChannels()
    }

    // ── Public API: single entry point ───────────────────────────────────

    /**
     * Translates a [NotificationState] to the correct notification and posts it.
     * RecordingService calls this on every state transition.
     *
     * Returns the resulting [Notification] — needed for [Service.startForeground].
     */
    fun applyState(state: NotificationState): Notification {
        Log.d(TAG, "applyState: ${state::class.simpleName}")

        return when (state) {
            is NotificationState.Recording ->
                buildRecording(state).also { postRecording(it) }

            is NotificationState.PausedPhoneCall ->
                buildPausedPhoneCall(state).also { postRecording(it) }

            is NotificationState.PausedAudioFocus ->
                buildPausedAudioFocus(state).also { postRecording(it) }

            is NotificationState.SilenceWarning ->
                buildSilenceWarning(state).also {
                    postRecording(it)
                    postAlert(buildSilenceAlert(state))
                }

            is NotificationState.AudioSourceChanged ->
                buildAudioSourceAlert(state).also { postAlert(it) }
                    .let { buildRecordingNoOp() }   // recording notification unchanged

            is NotificationState.LowStorageWarning ->
                buildLowStorageWarning(state).also { postRecording(it) }

            is NotificationState.StoppedLowStorage ->
                buildLowStorageStopped().also { postAlert(it) }
                    .let { buildRecordingNoOp() }

            is NotificationState.Error ->
                buildError(state).also { postAlert(it) }
                    .let { buildRecordingNoOp() }

            is NotificationState.Stopped ->
                // Service calls STOP_FOREGROUND_REMOVE itself; nothing to post
                buildRecordingNoOp()
        }
    }

    /**
     * Builds the initial foreground notification for [Service.startForeground].
     * Called once per session before the first [applyState].
     *
     * On API 36+: returns a native [Notification] with Live Update enabled.
     * On older:   returns a [NotificationCompat] with Chronometer.
     */
    fun buildForStartForeground(sessionStartMs: Long): Notification {
        // Try native API-36 path first
        val nativeActions = if (Build.VERSION.SDK_INT >= 36) {
            listOf(
                LiveUpdateCompat.buildNativeAction(
                    android.R.drawable.ic_media_pause, "Pause",
                    actionFactory.pausePendingIntent()
                ),
                LiveUpdateCompat.buildNativeAction(
                    android.R.drawable.ic_delete, "Stop",
                    actionFactory.stopPendingIntent()
                )
            )
        } else emptyList()

        val nativeNotif = LiveUpdateCompat.buildNativeLiveNotification(
            context           = context,
            channelId         = NotificationChannelManager.CHANNEL_RECORDING,
            sessionStartMs    = sessionStartMs,
            contentTitle      = "Recall · Recording",
            contentText       = "00:00",
            contentIntent     = actionFactory.openAppPendingIntent(),
            actions           = nativeActions
        )

        if (nativeNotif != null) {
            Log.d(TAG, "Built native API-36 foreground notification")
            return nativeNotif
        }

        // Pre-API-36 compat path
        Log.d(TAG, "Built compat foreground notification (API ${Build.VERSION.SDK_INT})")
        return buildRecording(
            NotificationState.Recording(
                elapsedSeconds = 0L,
                sessionStartMs = sessionStartMs
            )
        )
    }

    /** Updates the ongoing notification without rebuilding from a full state change */
    fun update(notification: Notification) {
        nm.notify(NOTIFICATION_ID_RECORDING, notification)
    }

    // ── Backward-compat helpers (called from service for timer ticks) ────

    fun buildRecordingNotification(elapsedSeconds: Long): Notification =
        buildRecording(
            NotificationState.Recording(
                elapsedSeconds = elapsedSeconds,
                sessionStartMs = System.currentTimeMillis() - (elapsedSeconds * 1_000L)
            )
        )

    fun buildPausedPhoneCallNotification(): Notification =
        buildPausedPhoneCall(NotificationState.PausedPhoneCall(0L))

    fun buildPausedAudioFocusNotification(): Notification =
        buildPausedAudioFocus(NotificationState.PausedAudioFocus(0L))

    fun buildSilenceWarningNotification(elapsedSeconds: Long): Notification =
        buildSilenceWarning(
            NotificationState.SilenceWarning(elapsedSeconds,
                System.currentTimeMillis() - (elapsedSeconds * 1_000L))
        )

    fun buildAudioSourceChangedNotification(sourceName: String): Notification =
        buildAudioSourceAlert(NotificationState.AudioSourceChanged(sourceName))

    fun buildLowStorageNotification(): Notification = buildLowStorageStopped()

    fun buildLowStorageWarningNotification(availableBytes: Long): Notification =
        buildLowStorageWarning(
            NotificationState.LowStorageWarning(
                availableMb    = availableBytes / 1024 / 1024,
                elapsedSeconds = 0L,
                sessionStartMs = System.currentTimeMillis()
            )
        )

    // ── Private builders ─────────────────────────────────────────────────

    private fun buildRecording(state: NotificationState.Recording): Notification {
        val builder = recordingBase()
            .setContentTitle("Recall · Recording")
            .setContentText(formatDuration(state.elapsedSeconds))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(actionFactory.pauseAction())
            .addAction(actionFactory.stopAction())

        LiveUpdateCompat.applyLiveTimer(builder, state.sessionStartMs, state.elapsedSeconds)
        return builder.build()
    }

    private fun buildPausedPhoneCall(state: NotificationState.PausedPhoneCall): Notification =
        recordingBase()
            .setContentTitle("Recall · Paused")
            .setContentText("Paused – Phone call  ·  ${formatDuration(state.elapsedSeconds)}")
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(actionFactory.stopAction())
            .also { LiveUpdateCompat.applyFrozenTimer(it, state.elapsedSeconds) }
            .build()

    private fun buildPausedAudioFocus(state: NotificationState.PausedAudioFocus): Notification =
        recordingBase()
            .setContentTitle("Recall · Paused")
            .setContentText("Paused – Audio focus lost  ·  ${formatDuration(state.elapsedSeconds)}")
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(actionFactory.resumeAction())
            .addAction(actionFactory.stopAction())
            .also { LiveUpdateCompat.applyFrozenTimer(it, state.elapsedSeconds) }
            .build()

    private fun buildSilenceWarning(state: NotificationState.SilenceWarning): Notification {
        val builder = recordingBase()
            .setContentTitle("Recall · Recording")
            .setContentText("No audio detected – Check microphone")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSubText(formatDuration(state.elapsedSeconds))
            .addAction(actionFactory.stopAction())

        LiveUpdateCompat.applyLiveTimer(builder, state.sessionStartMs, state.elapsedSeconds)
        return builder.build()
    }

    private fun buildSilenceAlert(state: NotificationState.SilenceWarning): Notification =
        alertBase()
            .setContentTitle("No audio detected")
            .setContentText("Check your microphone connection")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .build()

    private fun buildAudioSourceAlert(state: NotificationState.AudioSourceChanged): Notification =
        alertBase()
            .setContentTitle("Recall")
            .setContentText("Microphone switched to ${state.sourceName}")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .build()

    private fun buildLowStorageWarning(state: NotificationState.LowStorageWarning): Notification {
        val builder = recordingBase()
            .setContentTitle("Recall · Low Storage")
            .setContentText("${state.availableMb} MB remaining – recording may stop soon")
            .setSmallIcon(android.R.drawable.stat_notify_sdcard_usb)
            .setOngoing(true)
            .setOnlyAlertOnce(false)   // alert user even if they've seen it
            .addAction(actionFactory.stopAction())

        LiveUpdateCompat.applyLiveTimer(builder, state.sessionStartMs, state.elapsedSeconds)
        return builder.build()
    }

    private fun buildLowStorageStopped(): Notification =
        alertBase()
            .setContentTitle("Recall – Stopped")
            .setContentText("Recording stopped – Low storage")
            .setSmallIcon(android.R.drawable.stat_notify_sdcard_usb)
            .setOngoing(false)
            .build()

    private fun buildError(state: NotificationState.Error): Notification =
        alertBase()
            .setContentTitle("Recall – Error")
            .setContentText(state.message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .build()

    /** Returns the current foreground notification unchanged — used for states that
     *  only post an alert without modifying the main recording notification */
    private fun buildRecordingNoOp(): Notification =
        recordingBase()
            .setContentTitle("Recall · Recording")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    // ── Base builders ────────────────────────────────────────────────────

    private fun recordingBase(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_RECORDING)
            .setContentIntent(actionFactory.openAppPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun alertBase(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, NotificationChannelManager.CHANNEL_ALERTS)
            .setContentIntent(actionFactory.openAppPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

    // ── Post helpers ─────────────────────────────────────────────────────

    private fun postRecording(notification: Notification) {
        nm.notify(NOTIFICATION_ID_RECORDING, notification)
    }

    private fun postAlert(notification: Notification) {
        nm.notify(NOTIFICATION_ID_ALERT, notification)
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
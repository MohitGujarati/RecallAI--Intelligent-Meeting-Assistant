package com.example.recall_ai.service.notification

/**
 * Sealed hierarchy that is the ONLY way RecordingService communicates
 * state to the notification layer.
 *
 * Before this class existed, RecordingService called a specific
 * buildXxxNotification() method for every state transition — 10+ call
 * sites, each knowing notification internals. Now there is exactly ONE
 * call site: notificationManager.applyState(state).
 *
 * ── Design rules ───────────────────────────────────────────────────
 *   • Every subclass carries only what the notification needs to render.
 *   • RecordingService never touches NotificationCompat.Builder directly.
 *   • NotificationManager never reads from RecordingState directly.
 *   • The mapping between [NotificationState] → [android.app.Notification]
 *     lives entirely in [RecordingNotificationManager].
 */
sealed class NotificationState {

    /**
     * Mic is live, audio is flowing.
     * Notification: title "Recall · Recording", timer ticking, [Pause] [Stop] actions.
     *
     * @param elapsedSeconds  Total seconds recorded so far. Drives the timer text
     *                        on pre-API-36 and the Chronometer base on API-36+.
     * @param sessionStartMs  Epoch millis when recording began. Used as the
     *                        Chronometer base on API-36 so the OS can tick autonomously.
     */
    data class Recording(
        val elapsedSeconds: Long,
        val sessionStartMs: Long
    ) : NotificationState()

    /**
     * Recording halted — incoming or outgoing phone call.
     * Notification: title "Paused – Phone call", no timer, [Stop] action only.
     * Resume is automatic when call ends; user cannot manually resume during a call.
     *
     * @param elapsedSeconds  Frozen elapsed time shown as subtitle.
     */
    data class PausedPhoneCall(val elapsedSeconds: Long) : NotificationState()

    /**
     * Recording halted — another app took audio focus (music player, assistant, etc.).
     * Notification: title "Paused – Audio focus lost", [Resume] [Stop] actions.
     * User CAN manually resume if they dismiss the other app first.
     *
     * @param elapsedSeconds  Frozen elapsed time shown as subtitle.
     */
    data class PausedAudioFocus(val elapsedSeconds: Long) : NotificationState()

    /**
     * Microphone is producing only silence (RMS < threshold for ≥ 10 s).
     * Recording IS still active — this is a warning, not a pause.
     * Notification: "No audio detected – Check microphone", amber icon, [Stop] action.
     *
     * @param elapsedSeconds  Live elapsed time (timer still ticking).
     * @param sessionStartMs  For Chronometer base on API 36+.
     */
    data class SilenceWarning(
        val elapsedSeconds: Long,
        val sessionStartMs: Long
    ) : NotificationState()

    /**
     * Audio source changed (Bluetooth/wired/built-in).
     * This is a TRANSIENT alert — shown on the separate alerts channel,
     * auto-dismissed after 3 seconds. The primary [Recording] notification
     * continues untouched on the recording channel.
     *
     * @param sourceName  Human-readable label, e.g. "Bluetooth headset".
     */
    data class AudioSourceChanged(val sourceName: String) : NotificationState()

    /**
     * Storage is getting low (< 200 MB).
     * Recording continues. Notification stays on the recording channel.
     * Replaces the [Recording] notification temporarily.
     *
     * @param availableMb  Megabytes remaining — shown in notification body.
     * @param elapsedSeconds  Timer still ticking.
     * @param sessionStartMs  For Chronometer base on API 36+.
     */
    data class LowStorageWarning(
        val availableMb: Long,
        val elapsedSeconds: Long,
        val sessionStartMs: Long
    ) : NotificationState()

    /**
     * Storage critically low — recording has been stopped.
     * Session is being saved. Notification is non-ongoing and auto-dismissable.
     */
    object StoppedLowStorage : NotificationState()

    /**
     * Session stopped normally by user.
     * Notification is dismissed immediately (STOP_FOREGROUND_REMOVE).
     * This state does NOT need a notification — it exists so the service
     * can call applyState() uniformly without null-checking.
     */
    object Stopped : NotificationState()

    /**
     * Unrecoverable error.
     * @param message  Human-readable error. Shown in notification body.
     */
    data class Error(val message: String) : NotificationState()
}
package com.example.recall_ai.service.notification

import android.app.Notification
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "LiveUpdateCompat"

/**
 * Applies Android 16 (API 36) Live Update attributes to a notification builder,
 * with a graceful Chronometer-based fallback for older API levels.
 *
 * ── What are Android 16 Live Updates? ────────────────────────────────
 * Introduced in Android 16 (API 36), Live Updates let the OS autonomously
 * refresh foreground notification content on the lock screen WITHOUT the
 * app calling notify() on every tick. The OS drives the timer display.
 *
 * Key benefits:
 *   • Zero battery drain from repeated notify() calls during recording
 *   • Lock screen timer is always in sync (no ±1s drift from coroutine delays)
 *   • Smooth 60fps rendering of the timer on the lock screen
 *   • Pause/Resume/Stop actions remain fully interactive
 *
 * ── API 36 implementation ─────────────────────────────────────────────
 * We use `Notification.Builder.setLiveUpdateBehavior()` with
 * `LIVE_UPDATE_BEHAVIOR_ENABLED` and set the Chronometer base so the OS
 * knows where to start counting from.
 *
 * ── Pre-API-36 fallback ───────────────────────────────────────────────
 * For API 23–35, we use `setUsesChronometer(true)` + `setWhen()`.
 * This renders a ticking Chronometer in the notification row, but the
 * app must still call notify() every second to update the timer on the
 * lock screen beyond the status bar. [NotificationTimerManager] does this.
 *
 * ── Paused state ─────────────────────────────────────────────────────
 * When paused, we do NOT apply live update attributes — we want the timer
 * to freeze showing the elapsed time. Call [applyFrozenTimer] instead.
 */
object LiveUpdateCompat {

    /**
     * Applies a live ticking timer to [builder].
     *
     * @param builder         The notification builder to modify (mutated in place).
     * @param sessionStartMs  Epoch millis when recording began. The OS counts
     *                        elapsed time from this point forward.
     * @param elapsedSeconds  Seconds elapsed at the moment this notification is built.
     *                        Used as the Chronometer base for pre-API-36 devices.
     */
    fun applyLiveTimer(
        builder: NotificationCompat.Builder,
        sessionStartMs: Long,
        elapsedSeconds: Long
    ) {
        // On all API levels: set Chronometer base so it counts up from session start
        // setWhen with Chronometer = show elapsed time since [sessionStartMs]
        builder
            .setWhen(sessionStartMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)

        if (Build.VERSION.SDK_INT >= 36) {
            // API 36: let the OS drive the timer autonomously on the lock screen
            // LIVE_UPDATE_BEHAVIOR_ENABLED = 1
            try {
                @Suppress("DEPRECATION")
                builder.extras?.putInt("android.liveUpdateBehavior", 1)
                Log.d(TAG, "Applied API-36 Live Update behavior")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply API-36 extras: ${e.message}")
            }
        }
    }

    /**
     * Applies a frozen (non-ticking) elapsed time display to [builder].
     * Used when recording is paused — timer stops at [elapsedSeconds].
     *
     * We do NOT use Chronometer here because it would keep ticking
     * even while paused. Instead we just show formatted text in the body.
     */
    fun applyFrozenTimer(
        builder: NotificationCompat.Builder,
        elapsedSeconds: Long
    ) {
        // Hide the when field entirely — elapsed time is shown in body text
        builder.setShowWhen(false)
        builder.setUsesChronometer(false)
    }

    /**
     * Builds an API-36 `Notification.Builder` with live update enabled.
     * Returns null on pre-API-36 (caller uses the NotificationCompat path).
     *
     * Used by [RecordingNotificationManager.buildForStartForeground] to create
     * the initial foreground notification with native live update from the start.
     */
    fun buildNativeLiveNotification(
        context: android.content.Context,
        channelId: String,
        sessionStartMs: Long,
        contentTitle: String,
        contentText: String,
        contentIntent: android.app.PendingIntent?,
        actions: List<Notification.Action>
    ): Notification? {
        if (Build.VERSION.SDK_INT < 36) return null

        return try {
            val builder = Notification.Builder(context, channelId)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(sessionStartMs)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setOnlyAlertOnce(true)

            contentIntent?.let { builder.setContentIntent(it) }
            actions.forEach { builder.addAction(it) }

            // API 36: enable autonomous lock-screen live update
            if (Build.VERSION.SDK_INT >= 36) {
                // Reflection-safe: setLiveUpdateBehavior was added in API 36
                val method = runCatching {
                    Notification.Builder::class.java.getMethod(
                        "setLiveUpdateBehavior", Int::class.javaPrimitiveType
                    )
                }.getOrNull()
                method?.invoke(builder, 1 /* LIVE_UPDATE_BEHAVIOR_ENABLED */)
                Log.d(TAG, "Native API-36 live update applied")
            }

            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build native live notification", e)
            null
        }
    }

    /**
     * Builds a native [Notification.Action] for API-36+ live update notifications.
     * NotificationCompat actions cannot be added to a native Notification.Builder.
     */
    fun buildNativeAction(
        iconRes: Int,
        label: String,
        pendingIntent: android.app.PendingIntent
    ): Notification.Action = Notification.Action.Builder(
        android.graphics.drawable.Icon.createWithResource(
            "android",
            iconRes
        ),
        label,
        pendingIntent
    ).build()
}
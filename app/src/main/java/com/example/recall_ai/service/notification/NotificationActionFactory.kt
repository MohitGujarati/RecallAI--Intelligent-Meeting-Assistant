package com.example.recall_ai.service.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.recall_ai.MainActivity
import com.example.recall_ai.service.RecordingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds all [NotificationCompat.Action]s and [PendingIntent]s used in notifications.
 *
 * ── Why a dedicated factory? ──────────────────────────────────────────
 * PendingIntent request codes must be unique across the entire app.
 * Scattering them across multiple build methods risks collisions that
 * cause the wrong intent to fire. Centralising them here makes the
 * allocation explicit and auditable.
 *
 * ── PendingIntent flags ───────────────────────────────────────────────
 * FLAG_UPDATE_CURRENT: If a matching PendingIntent already exists,
 *   replace its extras with the new ones (correct for most cases).
 * FLAG_IMMUTABLE: Required on API 31+. The PendingIntent cannot be
 *   modified by the recipient. Correct for notification actions.
 *
 * ── Request code allocation ───────────────────────────────────────────
 *   100 → Open app (activity)
 *   101 → Stop recording (service)
 *   102 → Pause recording (service)
 *   103 → Resume recording (service)
 */
@Singleton
class NotificationActionFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // ── Actions ──────────────────────────────────────────────────────────

    fun stopAction(): NotificationCompat.Action = NotificationCompat.Action.Builder(
        android.R.drawable.ic_delete,
        "Stop",
        stopPendingIntent()
    ).build()

    fun pauseAction(): NotificationCompat.Action = NotificationCompat.Action.Builder(
        android.R.drawable.ic_media_pause,
        "Pause",
        pausePendingIntent()
    ).build()

    fun resumeAction(): NotificationCompat.Action = NotificationCompat.Action.Builder(
        android.R.drawable.ic_media_play,
        "Resume",
        resumePendingIntent()
    ).build()

    // ── PendingIntents ───────────────────────────────────────────────────

    fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 100, intent, flags)
    }

    fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(context, 101, RecordingService.stopIntent(context), flags)

    fun pausePendingIntent(): PendingIntent =
        PendingIntent.getService(context, 102, RecordingService.pauseIntent(context), flags)

    fun resumePendingIntent(): PendingIntent =
        PendingIntent.getService(context, 103, RecordingService.resumeIntent(context), flags)
}
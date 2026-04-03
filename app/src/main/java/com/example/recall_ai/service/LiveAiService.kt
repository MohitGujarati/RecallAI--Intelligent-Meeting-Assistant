package com.example.recall_ai.service


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.recall_ai.reposotory.LiveAiRepository
import com.example.recall_ai.service.interruption.AudioFocusHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

private const val TAG = "LiveAiService"
private const val CHANNEL_ID = "live_ai_channel"
private const val NOTIFICATION_ID = 2002

/**
 * Foreground service to maintain the active WebSocket and Microphone lock
 * even if the user backgrounds the app.
 */
@AndroidEntryPoint
class LiveAiService : Service() {

    @Inject lateinit var repository: LiveAiRepository
    @Inject lateinit var recordingStateHolder: RecordingStateHolder
    @Inject lateinit var audioFocusHandler: AudioFocusHandler
    @Inject lateinit var liveAiStateHolder: LiveAiStateHolder

    // SupervisorJob prevents one coroutine failure from crashing the entire service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_INTERRUPT = "ACTION_INTERRUPT"
        const val EXTRA_MEETING_ID = "EXTRA_MEETING_ID"

        fun startIntent(context: Context, meetingId: Long): Intent {
            return Intent(context, LiveAiService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEETING_ID, meetingId)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, LiveAiService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun interruptIntent(context: Context): Intent {
            return Intent(context, LiveAiService::class.java).apply {
                action = ACTION_INTERRUPT
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val meetingId = intent.getLongExtra(EXTRA_MEETING_ID, -1L)
                Log.i(TAG, "ACTION_START meetingId=$meetingId")
                if (meetingId != -1L) handleStart(meetingId)
                else Log.e(TAG, "Missing EXTRA_MEETING_ID — cannot start")
            }
            ACTION_STOP -> handleStop()
            ACTION_INTERRUPT -> repository.interruptAi()
            else -> Log.w(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun handleStart(meetingId: Long) {
        Log.i(TAG, "┌─ handleStart() ─────────────────────────────────")
        Log.i(TAG, "│ meetingId=$meetingId")

        // EXCLUSIVITY GUARD: Prevent silent audio/mic clashing with RecordingService
        if (recordingStateHolder.isActive) {
            Log.w(TAG, "│ ❌ RecordingService is active — cannot start Live AI")
            Log.i(TAG, "└────────────────────────────────────────────────")
            Toast.makeText(this, "Please stop the active recording first.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        if (liveAiStateHolder.isActive) {
            Log.d(TAG, "│ ⚠️ Live AI already active, ignoring start command.")
            Log.i(TAG, "└────────────────────────────────────────────────")
            return
        }

        // Request Audio Focus as a Voice Call
        Log.d(TAG, "│ Requesting audio focus…")
        if (!audioFocusHandler.requestFocus(this)) {
            Log.e(TAG, "│ ❌ Failed to acquire audio focus")
            Log.i(TAG, "└────────────────────────────────────────────────")
            liveAiStateHolder.emit(LiveAiState.Error(meetingId, "Could not acquire audio focus. Is another call active?"))
            stopSelf()
            return
        }
        Log.d(TAG, "│ ✅ Audio focus acquired")

        Log.d(TAG, "│ Starting foreground service…")
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d(TAG, "│ ✅ Foreground started")

        Log.i(TAG, "│ Calling repository.startSession()…")
        Log.i(TAG, "└────────────────────────────────────────────────")
        repository.startSession(meetingId, serviceScope)
    }

    private fun handleStop() {
        Log.i(TAG, "Stopping Live AI Service")
        repository.stopSession()
        audioFocusHandler.abandonFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.stopSession()
        audioFocusHandler.abandonFocus()
        serviceScope.cancel()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live AI Assistant",
                NotificationManager.IMPORTANCE_LOW // Low to prevent constant popping
            )
            manager.createNotificationChannel(channel)
        }

        // Tap notification to go back to app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingLaunch = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)

        // Stop button on the notification
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recall AI is listening")
            .setContentText("Tap to open or stop the session.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Default mic icon
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
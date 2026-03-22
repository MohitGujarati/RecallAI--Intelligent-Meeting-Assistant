package com.example.recall_ai.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.local.entity.PauseReason
import com.example.recall_ai.model.RecordingState
import com.example.recall_ai.model.TranscriptionMode
import com.example.recall_ai.service.audio.AudioRecorder
import com.example.recall_ai.service.audio.ChunkManager
import com.example.recall_ai.service.notification.NotificationTimerManager
import com.example.recall_ai.service.notification.RecordingNotificationManager
import com.example.recall_ai.service.recognition.ContinuousRecognitionManager
import com.example.recall_ai.worker.SummaryWorker
import com.example.recall_ai.worker.TranscriptionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RecordingService"

/**
 * Foreground Service that supports two transcription pipelines:
 *
 *   AI mode     → AudioRecord + ChunkManager → 30-s WAV chunks → Gemini/Whisper
 *   Native mode → ContinuousRecognitionManager (Android SpeechRecognizer)
 *
 * The mode is selected by the user via a toggle on RecordingScreen and
 * passed to this service as an Intent extra (EXTRA_TRANSCRIPTION_MODE).
 *
 * The two modes are MUTUALLY EXCLUSIVE — SpeechRecognizer and AudioRecord
 * both hold an exclusive mic lock and cannot run simultaneously.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var audioRecorder:       AudioRecorder
    @Inject lateinit var chunkManager:        ChunkManager
    @Inject lateinit var sessionManager:      RecordingSessionManager
    @Inject lateinit var notificationManager: RecordingNotificationManager
    @Inject lateinit var notificationTimer:   NotificationTimerManager
    @Inject lateinit var stateHolder:         RecordingStateHolder
    @Inject lateinit var continuousRecognitionManager: ContinuousRecognitionManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var chunkPipelineJob: Job? = null
    private var uiTimerJob:       Job? = null

    private var meetingId:        Long    = -1L
    private var sessionStartTime: Long    = 0L
    private var elapsedSeconds:   Long    = 0L
    private var chunkCount:       Int     = 0
    private var isPaused:         Boolean = false

    /** Which pipeline is active for the current session. */
    private var transcriptionMode: TranscriptionMode = TranscriptionMode.NATIVE

    // ── Lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager.createNotificationChannel()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        // Read mode from the start intent (ignored for stop/pause/resume)
        if (intent?.action == ACTION_START) {
            val modeName = intent.getStringExtra(EXTRA_TRANSCRIPTION_MODE)
            transcriptionMode = try {
                TranscriptionMode.valueOf(modeName ?: TranscriptionMode.NATIVE.name)
            } catch (_: IllegalArgumentException) {
                TranscriptionMode.NATIVE
            }
            Log.i(TAG, "Transcription mode: $transcriptionMode")
        }

        when (intent?.action) {
            ACTION_START  -> handleStart()
            ACTION_STOP   -> handleStop()
            ACTION_PAUSE  -> handlePause()
            ACTION_RESUME -> handleResume()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (transcriptionMode == TranscriptionMode.AI) {
            stopChunkPipeline()
        } else {
            continuousRecognitionManager.stop()
        }
        serviceScope.cancel()
        Log.d(TAG, "onDestroy")
    }

    // ── Handlers ──────────────────────────────────────────────────────

    private fun handleStart() {
        if (stateHolder.isActive) {
            Log.w(TAG, "Already active — ignoring duplicate start")
            return
        }

        sessionStartTime = System.currentTimeMillis()
        isPaused         = false
        elapsedSeconds   = 0L
        chunkCount       = 0

        serviceScope.launch {
            meetingId = sessionManager.createSession(sessionStartTime)
            Log.i(TAG, "Session created: meetingId=$meetingId  mode=$transcriptionMode")

            startForeground(
                RecordingNotificationManager.NOTIFICATION_ID,
                notificationManager.buildForStartForeground(sessionStartTime)
            )

            requestAudioFocusSilently()

            stateHolder.emit(RecordingState.Recording(meetingId))

            when (transcriptionMode) {
                TranscriptionMode.AI     -> startChunkPipeline()
                TranscriptionMode.NATIVE -> {
                    val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    continuousRecognitionManager.start(meetingId, ioScope)
                }
            }

            startTimer()
            startUiTimer()
        }
    }

    private fun handleStop() {
        if (meetingId == -1L) { stopSelf(); return }
        serviceScope.launch { finalizeAndStop(MeetingStatus.STOPPED) }
    }

    private fun handlePause() {
        if (isPaused || meetingId == -1L) return
        isPaused = true

        when (transcriptionMode) {
            TranscriptionMode.AI     -> stopChunkPipeline()
            TranscriptionMode.NATIVE -> continuousRecognitionManager.pause()
        }

        notificationTimer.pause()
        uiTimerJob?.cancel()
        uiTimerJob = null
        serviceScope.launch {
            sessionManager.onPaused(meetingId, PauseReason.NONE)
            stateHolder.emit(RecordingState.Paused(meetingId, PauseReason.NONE, elapsedSeconds * 1000L))
            notificationManager.update(notificationManager.buildPausedAudioFocusNotification())
        }
        Log.i(TAG, "Paused (user)")
    }

    private fun handleResume() {
        if (!isPaused || meetingId == -1L) return
        isPaused = false
        serviceScope.launch {
            sessionManager.onResumed(meetingId)
            stateHolder.emit(RecordingState.Recording(meetingId, elapsedSeconds * 1000L, chunkCount))
            notificationManager.update(notificationManager.buildRecordingNotification(elapsedSeconds))
        }

        when (transcriptionMode) {
            TranscriptionMode.AI     -> startChunkPipeline()
            TranscriptionMode.NATIVE -> continuousRecognitionManager.resume()
        }

        startTimer()
        startUiTimer()
        Log.i(TAG, "Resumed (user)")
    }

    // ── Chunk pipeline (AI mode only) ─────────────────────────────────

    private fun startChunkPipeline() {
        stopChunkPipeline()
        val pcmFlow = audioRecorder.pcmFlow()

        chunkPipelineJob = serviceScope.launch {
            Log.d(TAG, "AudioRecord pipeline started")
            try {
                chunkManager.processAudioStream(pcmFlow, meetingId, sessionStartTime)
                    .collect { savedChunk ->
                        chunkCount = savedChunk.chunkIndex + 1
                        sessionManager.onChunkSaved(savedChunk, elapsedSeconds)

                        Log.i(TAG, "Chunk ${savedChunk.chunkIndex} saved → enqueuing transcription")
                        TranscriptionWorker.enqueue(
                            context   = this@RecordingService,
                            chunkId   = savedChunk.chunkId,
                            meetingId = savedChunk.meetingId
                        )

                        val current = stateHolder.state.value
                        if (current is RecordingState.Recording) {
                            stateHolder.emit(current.copy(chunkCount = chunkCount))
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Chunk pipeline error: ${e.message}", e)
            }
        }
    }

    private fun stopChunkPipeline() {
        chunkPipelineJob?.cancel()
        chunkPipelineJob = null
        Log.d(TAG, "Chunk pipeline stopped")
    }

    // ── Timer ─────────────────────────────────────────────────────────

    private fun startTimer() {
        notificationTimer.start(
            scope          = serviceScope,
            sessionStartMs = sessionStartTime
        ) { elapsed ->
            elapsedSeconds = elapsed
            if (!NotificationTimerManager.isOsDriven) {
                notificationManager.update(
                    notificationManager.buildRecordingNotification(elapsed)
                )
            }
            val current = stateHolder.state.value
            if (current is RecordingState.Recording) {
                stateHolder.emit(current.copy(elapsedMs = elapsed * 1_000L))
            }
        }
    }

    // ── UI Timer (state updates for Compose) ─────────────────────────

    /**
     * Dedicated timer that updates the stateHolder every second.
     *
     * BUG-FIX: On API 36+ the NotificationTimerManager exits early
     * (the OS drives the notification timer autonomously). However,
     * RecordingScreen's Compose UI still needs stateHolder emissions
     * to update the timer display. This coroutine ensures that happens
     * on ALL API levels.
     */
    private fun startUiTimer() {
        uiTimerJob?.cancel()
        uiTimerJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1_000L
                elapsedSeconds = elapsed
                val current = stateHolder.state.value
                if (current is RecordingState.Recording) {
                    stateHolder.emit(current.copy(elapsedMs = elapsed * 1_000L))
                }
            }
        }
    }

    // ── Finalize ──────────────────────────────────────────────────────

    private suspend fun finalizeAndStop(status: MeetingStatus) {
        when (transcriptionMode) {
            TranscriptionMode.AI     -> stopChunkPipeline()
            TranscriptionMode.NATIVE -> continuousRecognitionManager.stop()
        }
        notificationTimer.stop()

        // Native mode: all transcripts are already saved by ContinuousRecognitionManager
        // with placeholder chunks marked COMPLETED. Mark the meeting COMPLETED directly
        // and enqueue the Gemini SummaryWorker.
        // AI mode: meeting stays STOPPED — TranscriptionWorker.checkMeetingCompletion()
        // will mark it COMPLETED after all chunks are transcribed.
        val finalStatus = if (transcriptionMode == TranscriptionMode.NATIVE) {
            MeetingStatus.COMPLETED
        } else {
            status
        }

        sessionManager.finalizeSession(
            meetingId           = meetingId,
            endTime             = System.currentTimeMillis(),
            status              = finalStatus,
            finalElapsedSeconds = elapsedSeconds
        )

        // Enqueue Gemini summary immediately for native mode
        if (transcriptionMode == TranscriptionMode.NATIVE) {
            SummaryWorker.enqueue(this@RecordingService, meetingId)
            Log.i(TAG, "Native mode — enqueued SummaryWorker for meeting $meetingId")
        }

        stateHolder.emit(RecordingState.Stopped(meetingId))
        Log.i(TAG, "Finalized meetingId=$meetingId status=$finalStatus mode=$transcriptionMode")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Audio focus — silent request, never act on loss ───────────────

    private fun requestAudioFocusSilently() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = android.media.AudioFocusRequest.Builder(
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { /* never pause — user controls recording */ }
                .setAcceptsDelayedFocusGain(false)
                .build()
            am.requestAudioFocus(req)
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed (non-fatal): ${e.message}")
        }
    }

    // ── Intent factory ────────────────────────────────────────────────

    companion object {
        const val ACTION_START  = "com.example.recall_ai.RECORDING_START"
        const val ACTION_STOP   = "com.example.recall_ai.RECORDING_STOP"
        const val ACTION_PAUSE  = "com.example.recall_ai.RECORDING_PAUSE"
        const val ACTION_RESUME = "com.example.recall_ai.RECORDING_RESUME"

        const val EXTRA_TRANSCRIPTION_MODE = "extra_transcription_mode"

        fun startIntent(context: Context, mode: TranscriptionMode = TranscriptionMode.NATIVE) =
            Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRANSCRIPTION_MODE, mode.name)
            }

        fun stopIntent(context: Context) =
            Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }

        fun pauseIntent(context: Context) =
            Intent(context, RecordingService::class.java).apply { action = ACTION_PAUSE }

        fun resumeIntent(context: Context) =
            Intent(context, RecordingService::class.java).apply { action = ACTION_RESUME }
    }
}
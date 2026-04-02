package com.example.recall_ai.reposotory


import android.util.Log
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.SummaryDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import com.example.recall_ai.data.remote.api.GeminiLiveClient
import com.example.recall_ai.service.LiveAiState
import com.example.recall_ai.service.LiveAiStateHolder
import com.example.recall_ai.service.audio.AudioRecorder
import com.example.recall_ai.service.audio.AudioTrackManager
import com.example.recall_ai.utils.LiveAiContextPromptBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

private const val TAG = "LiveAiRepository"

/**
 * Orchestrates the Live AI feature.
 * NOTE: This is NOT a @Singleton. A fresh instance is created by Hilt for each LiveAiService
 * lifecycle to ensure GeminiLiveClient and AudioTrackManager don't leak across sessions.
 */
class LiveAiRepository @Inject constructor(
    private val geminiLiveClient: GeminiLiveClient,
    private val audioTrackManager: AudioTrackManager,
    private val audioRecorder: AudioRecorder,
    private val meetingDao: MeetingDao,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val stateHolder: LiveAiStateHolder
) {
    companion object {
        /** Sentinel meetingId — triggers global context mode (all meetings) */
        const val GLOBAL_MEETING_ID = 0L
    }

    private var repositoryJob: Job? = null

    // Dedicated single thread for audio playback — avoids contention
    // with Dispatchers.IO shared pool which causes choppy audio
    private val audioPlaybackDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LiveAI-AudioPlayback").apply { priority = Thread.MAX_PRIORITY }
    }.asCoroutineDispatcher()

    /**
     * Starts the bidirectional audio pipeline and the Smart Context injection.
     * @param meetingId The meeting to contextualize.
     * @param scope The service's SupervisorScope.
     */
    fun startSession(meetingId: Long, scope: CoroutineScope) {
        if (stateHolder.isActive) {
            Log.w(TAG, "startSession() called but already active — skipping")
            return
        }

        Log.i(TAG, "┌─ startSession() ───────────────────────────────")
        Log.i(TAG, "│ meetingId : $meetingId")
        Log.i(TAG, "└────────────────────────────────────────────────")

        stateHolder.emit(LiveAiState.Connecting(meetingId))

        repositoryJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Build context — either single-meeting or global
                val initialContext: String
                val durationSeconds: Long

                if (meetingId == GLOBAL_MEETING_ID) {
                    // ── GLOBAL MODE: aggregate all summaries ──────────────
                    Log.d(TAG, "[1/4] GLOBAL MODE — fetching all summaries…")
                    val allSummaries = summaryDao.getAllCompletedSummaries()
                    Log.d(TAG, "[1/4] Found ${allSummaries.size} completed summaries")
                    initialContext = LiveAiContextPromptBuilder.buildGlobalPrompt(allSummaries)
                    durationSeconds = 0L   // no smart-context swap for global
                } else {
                    // ── SINGLE-MEETING MODE (existing behavior) ──────────
                    Log.d(TAG, "[1/4] Fetching meeting data from DB…")
                    val meeting = meetingDao.getById(meetingId)
                        ?: throw IllegalArgumentException("Meeting $meetingId not found in DB")
                    val existingSummary = summaryDao.getByMeetingId(meetingId)

                    Log.d(TAG, "[1/4] Meeting: title='${meeting.title}', duration=${meeting.durationSeconds}s")
                    Log.d(TAG, "[1/4] Summary available: ${existingSummary != null} (${existingSummary?.summary?.length ?: 0} chars)")

                    initialContext = LiveAiContextPromptBuilder.buildInitialPrompt(
                        title = meeting.title,
                        defaultSummary = existingSummary?.summary
                    )
                    durationSeconds = meeting.durationSeconds
                }

                Log.d(TAG, "[1/4] Context prompt built: ${initialContext.length} chars")

                // 2. Start Hardware & Network
                Log.d(TAG, "[2/4] Starting AudioTrackManager (24kHz output)…")
                audioTrackManager.start()

                Log.d(TAG, "[2/4] Connecting to Gemini Live WebSocket…")
                geminiLiveClient.connect(initialContext)
                Log.i(TAG, "[2/4] ✅ WebSocket connected and setup confirmed!")

                stateHolder.emit(LiveAiState.Listening(meetingId))
                Log.i(TAG, "[2/4] State → Listening")

                // 3. Launch parallel streams
                Log.d(TAG, "[3/4] Launching parallel audio pipelines…")
                launchAudioInput()
                launchAudioOutput(meetingId)
                launchNetworkEvents(meetingId)
                Log.i(TAG, "[3/4] ✅ All pipelines running")

                // 4. Smart Context Logic (>= 30 minutes, single-meeting only)
                if (meetingId != GLOBAL_MEETING_ID && durationSeconds >= 1800) {
                    Log.d(TAG, "[4/4] Meeting >= 30m — launching smart context swap")
                    launchSmartContextSwap(meetingId)
                } else {
                    Log.d(TAG, "[4/4] Skipping smart context swap")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start Live AI session: ${e.message}", e)
                stateHolder.emit(LiveAiState.Error(meetingId, e.localizedMessage ?: "Connection failed"))
                stopSession()
            }
        }
    }

    fun stopSession() {
        val meetingId = stateHolder.currentMeetingId ?: -1L
        Log.i(TAG, "stopSession() meetingId=$meetingId")

        repositoryJob?.cancel()
        repositoryJob = null

        geminiLiveClient.disconnect()
        audioTrackManager.stopAndRelease()
        audioPlaybackDispatcher.close()

        if (stateHolder.isActive) {
            stateHolder.emit(LiveAiState.Stopped(meetingId))
            Log.i(TAG, "State → Stopped")
        }
    }

    // ── Private Pipeline Logic ──────────────────────────────────────────

    /**
     * When true, mic audio is NOT sent to Gemini. This prevents the
     * feedback loop where the speaker's audio is picked up by the mic
     * and sent back, causing Gemini to "hear itself" and respond with
     * things like "exactly, you're right" mid-answer.
     */
    @Volatile
    private var isSpeaking = false

    private fun CoroutineScope.launchAudioInput() = launch(Dispatchers.IO) {
        Log.d(TAG, "🎙️ Audio INPUT pipeline started")
        var chunkCount = 0
        try {
            audioRecorder.pcmFlow().collect { event ->
                if (event is AudioRecorder.AudioEvent.PcmData) {
                    // Skip sending mic audio while AI is speaking to prevent echo
                    if (!isSpeaking) {
                        geminiLiveClient.sendAudio(event.bytes)
                        chunkCount++
                        if (chunkCount % 500 == 0) {
                            Log.d(TAG, "🎙️ Audio INPUT: $chunkCount chunks sent")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "🎙️ Audio INPUT error: ${e.message}", e)
            }
        }
        Log.d(TAG, "🎙️ Audio INPUT ended ($chunkCount chunks)")
    }

    /**
     * Runs on a DEDICATED thread (audioPlaybackDispatcher) to ensure
     * AudioTrack.write() never competes with other I/O operations.
     */
    private fun CoroutineScope.launchAudioOutput(meetingId: Long) = launch(audioPlaybackDispatcher) {
        Log.d(TAG, "🔊 Audio OUTPUT pipeline started (dedicated thread)")
        var chunkCount = 0
        try {
            geminiLiveClient.incomingAudioFlow.collect { pcmBytes ->
                if (!isSpeaking) {
                    isSpeaking = true
                    stateHolder.emit(LiveAiState.Speaking(meetingId))
                    Log.i(TAG, "🔊 State → Speaking (mic muted)")
                }
                audioTrackManager.write(pcmBytes)
                chunkCount++
                if (chunkCount % 200 == 0) {
                    Log.d(TAG, "🔊 Audio OUTPUT: $chunkCount chunks played")
                }
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                Log.e(TAG, "🔊 Audio OUTPUT error: ${e.message}", e)
            }
        }
        Log.d(TAG, "🔊 Audio OUTPUT ended ($chunkCount chunks)")
    }

    private fun CoroutineScope.launchNetworkEvents(meetingId: Long) = launch {
        Log.d(TAG, "📡 Network events pipeline started")
        launch {
            geminiLiveClient.turnCompleteFlow.collect {
                isSpeaking = false
                audioTrackManager.flush()  // Clear any buffered audio for clean turn switch
                Log.i(TAG, "📡 Turn complete → Listening (mic unmuted)")
                stateHolder.emit(LiveAiState.Listening(meetingId))
            }
        }
        launch {
            geminiLiveClient.errorFlow.collect { error ->
                Log.e(TAG, "📡 Network error: $error")
                stateHolder.emit(LiveAiState.Error(meetingId, error))
                stopSession()
            }
        }
    }

    private fun CoroutineScope.launchSmartContextSwap(meetingId: Long) = launch(Dispatchers.IO) {
        Log.i(TAG, "🧠 Smart context swap: generating dense context for meeting $meetingId…")
        try {
            val fullTranscript = transcriptDao.getFullTranscriptText(meetingId)
            if (!fullTranscript.isNullOrBlank()) {
                val densePrompt = LiveAiContextPromptBuilder.buildDenseContextPrompt(fullTranscript)
                geminiLiveClient.updateContext(densePrompt)
                Log.i(TAG, "🧠 Smart context swap complete (${densePrompt.length} chars)")
            } else {
                Log.w(TAG, "🧠 No transcript available for smart context swap")
            }
        } catch (e: Exception) {
            Log.e(TAG, "🧠 Smart context swap failed, continuing with default context.", e)
        }
    }
}
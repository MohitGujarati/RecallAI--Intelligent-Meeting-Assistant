package com.example.recall_ai.service.recognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import com.example.recall_ai.data.local.entity.AudioChunk
import com.example.recall_ai.data.local.entity.Transcript
import com.example.recall_ai.data.local.entity.TranscriptSource
import com.example.recall_ai.data.local.entity.TranscriptionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val TAG = "ContinuousRecognition"

/**
 * Continuous speech-to-text engine using Android's built-in [SpeechRecognizer].
 *
 * ── When is this used? ────────────────────────────────────────────────────
 * Activated by [RecordingService] when no OpenAI API key is configured
 * (OPENAI_API_KEY is blank). Acts as a zero-config, zero-cost fallback so
 * the app transcribes in real time without any external API dependency.
 *
 * ── Why not use AudioRecord + SpeechRecognizer simultaneously? ───────────
 * Both [AudioRecord] and [SpeechRecognizer] hold an exclusive lock on the
 * microphone. Running both crashes with ERROR_AUDIO on the recognizer.
 * In fallback mode, [RecordingService] skips [ChunkManager]/[AudioRecorder]
 * entirely and runs ONLY this manager.
 *
 * ── Continuous loop design ────────────────────────────────────────────────
 * Android's [SpeechRecognizer] stops after a period of silence or once it
 * believes the utterance is complete. We defeat that by immediately calling
 * [SpeechRecognizer.startListening] again in [RecognitionListener.onResults]
 * and [RecognitionListener.onError] (with a short back-off for real errors).
 *
 * The hints below extend the silence thresholds as far as the recognizer
 * implementation allows — different OEM builds honor these to varying degrees,
 * but the restart loop ensures we're always listening regardless:
 *   EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS       = 10 000 ms
 *   EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 5 000 ms
 *
 * ── Threading model ───────────────────────────────────────────────────────
 * [SpeechRecognizer] must be created and used on a thread with a [Looper].
 * ALL recognizer calls are posted to [mainHandler] (the main thread looper).
 * Room writes are dispatched to [Dispatchers.IO] via the injected ioScope.
 *
 * ── Room write strategy ───────────────────────────────────────────────────
 * [Transcript] has a mandatory FK to [AudioChunk]. In fallback mode there
 * are no real audio files, so we insert a lightweight placeholder [AudioChunk]
 * (filePath = "", fileSizeBytes = 0, status = COMPLETED) before each
 * [Transcript] row. This satisfies the FK constraint without changing the schema.
 *
 * ── Lifecycle ────────────────────────────────────────────────────────────
 *   start(meetingId, ioScope)  — call once on session start
 *   pause()                    — stops listening (phone call, audio focus loss)
 *   resume()                   — restarts after a pause
 *   stop()                     — terminal, call on session end
 */
class ContinuousRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcriptDao: TranscriptDao,
    private val audioChunkDao: AudioChunkDao,
    private val meetingDao:    MeetingDao
) {

    companion object {
        /** Brief gap between sessions so the recognizer resets cleanly. */
        private const val RESTART_DELAY_MS      = 350L

        /** Longer delay for hard errors to avoid a tight crash loop. */
        private const val ERROR_RESTART_DELAY_MS = 1_500L

        /** Busy error: wait longer before the next attempt. */
        private const val BUSY_RESTART_DELAY_MS  = 2_500L
    }

    // ── State ──────────────────────────────────────────────────────────────

    /** Main-thread handler — all SpeechRecognizer calls go here. */
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null

    private var meetingId:    Long          = -1L
    private var chunkIndex:   Int           = 0

    /** True while START or RESUME has been commanded (not stopped/paused). */
    @Volatile private var isListening: Boolean = false

    /** True once STOP has been commanded — prevents any further restarts. */
    @Volatile private var isTerminated: Boolean = false

    private lateinit var ioScope: CoroutineScope

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Begins continuous recognition for [meetingId].
     *
     * Safe to call from any thread — dispatches to main via [mainHandler].
     *
     * @param meetingId  The active meeting row ID.
     * @param ioScope    A [CoroutineScope] backed by [Dispatchers.IO] for
     *                   Room writes. Must outlive the session.
     */
    fun start(meetingId: Long, ioScope: CoroutineScope) {
        this.meetingId  = meetingId
        this.ioScope    = ioScope
        this.chunkIndex = 0
        isListening     = true
        isTerminated    = false
        mainHandler.post { createAndListen() }
        Log.i(TAG, "Started — meeting=$meetingId")
    }

    /**
     * Temporarily stops recognition. Resumable via [resume].
     * Call during phone calls, audio-focus loss, or manual user pause.
     */
    fun pause() {
        isListening = false
        mainHandler.post {
            recognizer?.stopListening()
            Log.d(TAG, "Paused")
        }
    }

    /**
     * Resumes recognition after a [pause]. No-op if already listening
     * or if [stop] has been called.
     */
    fun resume() {
        if (isTerminated) return
        isListening = true
        mainHandler.post { createAndListen() }
        Log.d(TAG, "Resumed")
    }

    /**
     * Terminal stop. Releases the recognizer and prevents any future restarts.
     * Must be called when the recording session ends.
     */
    fun stop() {
        isListening  = false
        isTerminated = true
        // Cancel any pending restart callbacks
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
            Log.i(TAG, "Stopped — $chunkIndex segments recognized")
        }
    }

    // ── Recognition loop ───────────────────────────────────────────────────

    /**
     * Creates a fresh [SpeechRecognizer] instance and starts listening.
     * Must be called on the main thread.
     *
     * We always destroy the old instance before creating a new one — reusing
     * a stopped recognizer causes ERROR_RECOGNIZER_BUSY on some OEM builds.
     */
    private fun createAndListen() {
        // Destroy the old instance
        recognizer?.apply {
            setRecognitionListener(null)
            destroy()
        }
        recognizer = null

        if (isTerminated || !isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(listener)
            sr.startListening(buildIntent())
        }
        Log.d(TAG, "Listening… (segment ${chunkIndex + 1})")
    }

    /**
     * Builds the [RecognizerIntent] with extended silence thresholds.
     *
     * These extras are treated as hints — not all recognizer implementations
     * respect them fully, hence the restart loop in [listener.onResults].
     */
    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

            // Extend silence tolerances as far as the engine allows
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                10_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                5_000L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                500L
            )
        }

    // ── RecognitionListener ────────────────────────────────────────────────

    private val listener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            if (!text.isNullOrBlank()) {
                saveTranscriptAsync(text)
            } else {
                Log.d(TAG, "Empty result — skipping write")
            }

            // Immediately restart — this is the continuous loop core
            scheduleRestart(RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            val (label, delay) = when (error) {
                // Normal: silence / no speech — restart quickly
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "no-match/timeout" to RESTART_DELAY_MS

                // Transient: recognizer busy (previous session not fully released)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "busy" to BUSY_RESTART_DELAY_MS

                // Soft network errors
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "network" to ERROR_RESTART_DELAY_MS

                // Permission / audio issues
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "no-permission" to 0L   // no point retrying without permission

                else ->
                    "error-$error" to ERROR_RESTART_DELAY_MS
            }

            Log.w(TAG, "Recognition error: $label")

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                Log.e(TAG, "Mic permission missing — stopping continuous recognition")
                return   // Terminal — do not restart
            }

            scheduleRestart(delay)
        }

        // ── Unused callbacks (required by interface) ───────────────────

        override fun onBeginningOfSpeech()              {}
        override fun onRmsChanged(rmsdB: Float)         {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech()                    {}
        override fun onPartialResults(results: Bundle?) {}
        override fun onEvent(type: Int, params: Bundle?) {}
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Posts a delayed [createAndListen] call — but only if we haven't been
     * paused or stopped in the meantime.
     */
    private fun scheduleRestart(delayMs: Long) {
        if (isTerminated || !isListening) return
        mainHandler.postDelayed({ createAndListen() }, delayMs)
    }

    /**
     * Persists one recognized segment to Room.
     *
     * Sequence (both writes in the same IO coroutine for atomicity):
     *   1. Insert a placeholder [AudioChunk] — satisfies the FK on [Transcript]
     *   2. Insert the [Transcript] row with the recognized text
     *   3. Update [Meeting.totalChunks] so the dashboard and detail screen stay live
     *
     * The placeholder [AudioChunk] has:
     *   - filePath = ""           (no audio file exists in speech mode)
     *   - fileSizeBytes = 0
     *   - transcriptionStatus = COMPLETED  (no worker needs to process it)
     *
     * The [Transcript] row has:
     *   - source = ANDROID_SPEECH
     *   - confidence = null       (Android SpeechRecognizer doesn't expose confidence)
     *   - detectedLanguage = device locale language code
     */
    private fun saveTranscriptAsync(text: String) {
        val capturedMeetingId  = meetingId
        val capturedChunkIndex = chunkIndex++   // post-increment: capture then advance

        ioScope.launch(Dispatchers.IO) {
            // 1. Placeholder chunk
            val placeholderChunk = AudioChunk(
                meetingId           = capturedMeetingId,
                chunkIndex          = capturedChunkIndex,
                filePath            = "",
                startTime           = System.currentTimeMillis(),
                durationMs          = 0L,
                overlapMs           = 0L,
                transcriptionStatus = TranscriptionStatus.COMPLETED,
                fileSizeBytes       = 0L
            )
            val chunkId = audioChunkDao.insert(placeholderChunk)

            // 2. Transcript row
            val transcript = Transcript(
                meetingId        = capturedMeetingId,
                chunkId          = chunkId,
                chunkIndex       = capturedChunkIndex,
                text             = text,
                confidence       = null,
                source           = TranscriptSource.ANDROID_SPEECH,
                detectedLanguage = Locale.getDefault().language
            )
            transcriptDao.insert(transcript)

            // 3. Keep Meeting.totalChunks live (dashboard + detail screen progress)
            meetingDao.updateProgress(
                meetingId       = capturedMeetingId,
                durationSeconds = 0L,   // timer drives this separately via RecordingService
                totalChunks     = capturedChunkIndex + 1
            )

            Log.d(
                TAG,
                "Segment ${capturedChunkIndex + 1} saved: " +
                        "\"${text.take(60)}${if (text.length > 60) "…" else ""}\""
            )
        }
    }
}
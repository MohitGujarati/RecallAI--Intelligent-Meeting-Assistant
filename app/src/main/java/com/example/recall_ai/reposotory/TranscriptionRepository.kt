package com.example.recall_ai.data.repository

import android.content.Context
import android.util.Log
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import com.example.recall_ai.data.local.entity.AudioChunk
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.local.entity.Transcript
import com.example.recall_ai.data.local.entity.TranscriptionStatus
import com.example.recall_ai.data.remote.api.TranscriptionService
import com.example.recall_ai.data.remote.dto.TranscriptionResult
import com.example.recall_ai.worker.SummaryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TranscriptionRepo"

/**
 * Orchestrates the complete lifecycle of transcribing one audio chunk.
 *
 * ── Five-step pipeline (per chunk) ───────────────────────────────────
 *
 *   Step 1  Mark chunk IN_PROGRESS in Room
 *           → prevents two workers from racing on the same chunk
 *
 *   Step 2  File guard
 *           → verify WAV exists and is non-empty before any network call
 *           → missing/corrupt file = PermanentFailure, mark FAILED immediately
 *
 *   Step 3  Build continuity prompt
 *           → fetch last sentence of chunk N-1's transcript from Room
 *           → pass as context to Whisper to reduce word-split errors at boundaries
 *
 *   Step 4  Upload to TranscriptionService
 *           → delegates to Whisper or Mock; never throws, returns sealed result
 *
 *   Step 5  Persist result
 *           Success        → insert Transcript row, mark chunk COMPLETED,
 *                            DELETE WAV (only here — file is never deleted earlier)
 *                            → check if all chunks done; if so, trigger summary
 *           RetryableError → reset to PENDING; Worker's backoff handles re-schedule
 *           PermanentError → mark FAILED; requires manual retry-all from UI
 *
 * ── File protection guarantee ────────────────────────────────────────
 * deleteChunkFile() is called only after transcriptDao.insert() and
 * audioChunkDao.updateTranscriptionStatus(COMPLETED) both succeed.
 * A crash between Step 4 and Step 5 leaves the chunk as IN_PROGRESS;
 * ProcessDeathRecoveryManager resets it to PENDING on next app launch.
 * Audio is NEVER lost to an API outage or process death.
 *
 * ── Ordered transcript assembly ──────────────────────────────────────
 * chunkIndex is stored denormalized in the Transcript entity.
 * getFullTranscriptText() uses SQL group_concat(text, ' ') ORDER BY chunkIndex.
 * No in-memory sorting needed anywhere in the codebase.
 */
@Singleton
class TranscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcriptionService: TranscriptionService,
    private val audioChunkDao: AudioChunkDao,
    private val transcriptDao: TranscriptDao,
    private val meetingDao: MeetingDao
) {

    // ── Core pipeline ────────────────────────────────────────────────────

    /**
     * Transcribes [chunkId] end-to-end.
     * Called exclusively by TranscriptionWorker.doWork().
     */
    suspend fun transcribeChunk(chunkId: Long): TranscribeChunkResult {
        val chunk = audioChunkDao.getById(chunkId)
            ?: return TranscribeChunkResult.PermanentFailure(
                "Chunk $chunkId not found — deleted while job was queued"
            )

        Log.d(TAG, "Transcribing: chunkId=$chunkId index=${chunk.chunkIndex} " +
                "meeting=${chunk.meetingId}")

        // Step 1 — claim the chunk
        audioChunkDao.updateTranscriptionStatus(chunkId, TranscriptionStatus.IN_PROGRESS)

        // Step 2 — file guard
        val audioFile = File(chunk.filePath)
        when {
            !audioFile.exists() -> {
                val msg = "WAV file missing: ${chunk.filePath}"
                Log.e(TAG, msg)
                audioChunkDao.markFailed(chunkId, msg)
                return TranscribeChunkResult.PermanentFailure(msg)
            }
            audioFile.length() < 100L -> {
                val msg = "WAV file empty/corrupt: ${audioFile.length()} bytes"
                Log.e(TAG, msg)
                audioChunkDao.markFailed(chunkId, msg)
                return TranscribeChunkResult.PermanentFailure(msg)
            }
        }

        // Step 3 — boundary continuity prompt
        val prompt = precedingChunkContext(chunk)

        // Step 4 — upload
        val result = transcriptionService.transcribe(
            audioFile = audioFile,
            language  = null,   // auto-detect; override here if user sets a language pref
            prompt    = prompt
        )

        // Step 5 — persist
        return when (result) {
            is TranscriptionResult.Success       -> onSuccess(chunk, result)
            is TranscriptionResult.RetryableError -> onRetryable(chunk, result)
            is TranscriptionResult.PermanentError -> onPermanent(chunk, result)
            else                                  -> throw IllegalStateException()
        }
    }

    // ── Step 5 handlers ─────────────────────────────────────────────────

    private suspend fun onSuccess(
        chunk: AudioChunk,
        result: TranscriptionResult.Success
    ): TranscribeChunkResult {
        Log.i(TAG, "Chunk ${chunk.id} OK: ${result.text.length} chars " +
                "| confidence=${result.confidence?.let { "%.2f".format(it) }}")

        // Insert Transcript row — REPLACE handles retry producing a new result
        transcriptDao.insert(
            Transcript(
                meetingId        = chunk.meetingId,
                chunkId          = chunk.id,
                chunkIndex       = chunk.chunkIndex,
                text             = result.text,
                confidence       = result.confidence,
                source           = result.source,
                detectedLanguage = result.detectedLanguage
            )
        )

        // Mark COMPLETED — this happens BEFORE file deletion
        audioChunkDao.updateTranscriptionStatus(chunk.id, TranscriptionStatus.COMPLETED)

        // Delete WAV — only safe to do now that the transcript is in Room
        deleteChunkFile(chunk)

        // Check if the whole meeting is done
        checkMeetingCompletion(chunk.meetingId)

        return TranscribeChunkResult.Success(transcriptLength = result.text.length)
    }

    private suspend fun onRetryable(
        chunk: AudioChunk,
        result: TranscriptionResult.RetryableError
    ): TranscribeChunkResult {
        val attempt = chunk.retryCount + 1
        Log.w(TAG, "Chunk ${chunk.id} retryable (attempt $attempt/" +
                "${AudioChunk.MAX_RETRY_COUNT}): ${result.message}")

        return if (attempt >= AudioChunk.MAX_RETRY_COUNT) {
            // Exhausted — give up without calling markFailed() so retryCount
            // stays at exactly MAX_RETRY_COUNT (not over)
            audioChunkDao.markFailed(
                chunkId = chunk.id,
                error   = "Exhausted ${AudioChunk.MAX_RETRY_COUNT} retries: ${result.message}"
            )
            Log.e(TAG, "Chunk ${chunk.id} — max retries reached, marking FAILED")
            TranscribeChunkResult.PermanentFailure("Max retries exhausted")
        } else {
            // Reset to PENDING; Worker returns Result.retry() → WorkManager backoff
            audioChunkDao.updateTranscriptionStatus(chunk.id, TranscriptionStatus.PENDING)
            TranscribeChunkResult.RetryableFailure(result.message, attempt)
        }
    }

    private suspend fun onPermanent(
        chunk: AudioChunk,
        result: TranscriptionResult.PermanentError
    ): TranscribeChunkResult {
        Log.e(TAG, "Chunk ${chunk.id} permanent error (HTTP ${result.httpCode}): ${result.message}")
        audioChunkDao.markFailed(
            chunkId = chunk.id,
            error   = "Permanent (HTTP ${result.httpCode}): ${result.message}"
        )
        return TranscribeChunkResult.PermanentFailure(result.message)
    }

    // ── Meeting completion check ─────────────────────────────────────────

    /**
     * After every successful chunk, check whether the entire meeting is done.
     * If all chunks are COMPLETED, mark the meeting COMPLETED so the summary
     * pipeline (Chapter 6) can start.
     *
     * Uses two COUNT(*) queries rather than fetching all rows — fast even
     * for meetings with 100+ chunks.
     */
    private suspend fun checkMeetingCompletion(meetingId: Long) {
        val total     = audioChunkDao.getChunkCount(meetingId)
        val completed = audioChunkDao.getCompletedChunkCount(meetingId)

        Log.d(TAG, "Meeting $meetingId: $completed/$total chunks done")

        if (total > 0 && completed == total) {
            Log.i(TAG, "Meeting $meetingId fully transcribed → marking COMPLETED, enqueuing summary")
            meetingDao.updateStatus(meetingId, MeetingStatus.COMPLETED)
            SummaryWorker.enqueue(context, meetingId)
        }
    }

    // ── Retry-all ────────────────────────────────────────────────────────

    /**
     * Resets ALL failed chunks for [meetingId] to PENDING and returns their
     * IDs so the caller can enqueue a TranscriptionWorker for each.
     *
     * retryCount is reset to 0 — the full 3-attempt budget is restored.
     */
    suspend fun retryAllFailed(meetingId: Long): List<Long> {
        val failed = audioChunkDao.getFailedChunks(meetingId)
        if (failed.isEmpty()) return emptyList()

        failed.forEach { chunk ->
            audioChunkDao.update(
                chunk.copy(
                    transcriptionStatus = TranscriptionStatus.PENDING,
                    retryCount          = 0,
                    lastError           = null,
                    updatedAt           = System.currentTimeMillis()
                )
            )
        }

        Log.i(TAG, "Reset ${failed.size} failed chunks to PENDING for meeting $meetingId")
        return failed.map { it.id }
    }

    // ── Ordered transcript assembly ──────────────────────────────────────

    /**
     * Returns the full meeting transcript assembled in correct chunk order.
     * Uses SQL group_concat(text, ' ') ORDER BY chunkIndex — no Kotlin sorting.
     */
    suspend fun getFullTranscript(meetingId: Long): String? =
        transcriptDao.getFullTranscriptText(meetingId)

    /** Live-updating Flow for the transcript screen. */
    fun observeTranscripts(meetingId: Long): Flow<List<Transcript>> =
        transcriptDao.observeTranscriptsForMeeting(meetingId)

    // ── File lifecycle ────────────────────────────────────────────────────

    /** Only called from onSuccess(), after both Room writes are confirmed. */
    private fun deleteChunkFile(chunk: AudioChunk) {
        val file = File(chunk.filePath)
        if (!file.exists()) return
        val deleted = file.delete()
        if (deleted) Log.d(TAG, "Deleted WAV: ${chunk.filePath}")
        else         Log.w(TAG, "Could not delete WAV (non-fatal): ${chunk.filePath}")
    }

    // ── Boundary continuity prompt ────────────────────────────────────────

    /**
     * Fetches the last 1–2 sentences of chunk [chunk.chunkIndex - 1]'s transcript
     * to pass as context to Whisper when transcribing [chunk].
     *
     * Returns null for chunk 0 (no predecessor) or if the previous transcript
     * doesn't exist yet (chunks can be transcribed out of order).
     */
    private suspend fun precedingChunkContext(chunk: AudioChunk): String? {
        if (chunk.chunkIndex == 0) return null

        val previousChunk = audioChunkDao
            .getChunksForMeeting(chunk.meetingId)
            .firstOrNull { it.chunkIndex == chunk.chunkIndex - 1 }
            ?: return null

        return transcriptDao
            .getByChunkId(previousChunk.id)
            ?.text
            ?.split(Regex("(?<=[.!?])\\s+"))   // split on sentence boundaries
            ?.takeLast(2)                         // last 2 sentences
            ?.joinToString(" ")
            ?.take(500)                           // WhisperRequestBuilder.PROMPT_MAX_CHARS
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Worker result — returned by transcribeChunk(), consumed by TranscriptionWorker
// ─────────────────────────────────────────────────────────────────────────────

sealed class TranscribeChunkResult {
    data class Success(val transcriptLength: Int) : TranscribeChunkResult()
    data class RetryableFailure(val reason: String, val attempt: Int) : TranscribeChunkResult()
    data class PermanentFailure(val reason: String) : TranscribeChunkResult()
}
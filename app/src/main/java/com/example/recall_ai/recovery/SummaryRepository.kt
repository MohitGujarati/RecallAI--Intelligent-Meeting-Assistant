package com.example.recall_ai.data.repository

import android.util.Log
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.dao.SummaryDao
import com.example.recall_ai.data.local.dao.TranscriptDao
import com.example.recall_ai.data.local.entity.Summary
import com.example.recall_ai.data.local.entity.SummaryStatus
import com.example.recall_ai.data.remote.api.SummaryParser
import com.example.recall_ai.data.remote.api.SummaryService
import com.example.recall_ai.data.remote.dto.SummaryGenerationResult
import com.example.recall_ai.data.remote.dto.SummaryStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SummaryRepo"

/**
 * Orchestrates LLM summary generation end-to-end.
 *
 * ── Pipeline steps ────────────────────────────────────────────────────
 *
 *   Step 1  Idempotency guard
 *           → if COMPLETED row already exists, return AlreadyComplete
 *           → safe to call multiple times (WorkManager may retry)
 *
 *   Step 2  Fetch transcript
 *           → getFullTranscriptText() returns ordered group_concat from Room
 *           → blank/null transcript → PermanentFailure (nothing to summarize)
 *
 *   Step 3  Insert/reset Summary row as GENERATING
 *           → row must exist before streaming starts so UI can observe it
 *           → reset clears old streamBuffer to prevent stale content showing
 *
 *   Step 4  Collect token stream
 *           → Token events: accumulate in memory, flush to Room every
 *             TOKEN_FLUSH_COUNT tokens or when stream ends
 *           → Complete event: parse streamBuffer → markCompleted() or markFailed()
 *           → Error event: markFailed() with user-facing message → return result
 *
 * ── Token batching ───────────────────────────────────────────────────
 * The LLM may emit 10–30 tokens/second. Writing each token individually
 * to Room would cause ~30 DB writes/second. We batch tokens in a
 * StringBuilder and flush every TOKEN_FLUSH_COUNT tokens. This reduces
 * DB writes to ~3/second while keeping the UI refresh rate reasonable.
 *
 * ── File protection guarantee ────────────────────────────────────────
 * This repository only reads transcripts — it never deletes audio files.
 * Audio lifecycle is managed entirely by TranscriptionRepository.
 *
 * ── Process death resilience ─────────────────────────────────────────
 * streamBuffer is persisted to Room on every flush. If the process dies
 * mid-stream, the buffer is preserved. ProcessDeathRecoveryManager resets
 * the row to PENDING and re-enqueues SummaryWorker, which calls this
 * method again. The old buffer is wiped in Step 3 and generation restarts.
 * We do not attempt to resume partial streams — the LLM context is lost.
 */
@Singleton
class SummaryRepository @Inject constructor(
    private val summaryService: SummaryService,
    private val summaryDao: SummaryDao,
    private val transcriptDao: TranscriptDao,
    private val meetingDao: MeetingDao
) {

    companion object {
        /** Flush accumulated tokens to Room after this many tokens */
        private const val TOKEN_FLUSH_COUNT = 10
    }

    // ── Core pipeline ────────────────────────────────────────────────────

    /**
     * Generates a summary for [meetingId].
     * Called exclusively by SummaryWorker.doWork().
     */
    suspend fun generateSummary(meetingId: Long): SummaryGenerationResult {

        // Step 1 — idempotency
        val existing = summaryDao.getActiveOrCompletedSummary(meetingId)
        if (existing?.status == SummaryStatus.COMPLETED) {
            Log.i(TAG, "Meeting $meetingId already has a COMPLETED summary — skipping")
            return SummaryGenerationResult.AlreadyComplete
        }

        // Step 2 — fetch transcript
        val transcript = transcriptDao.getFullTranscriptText(meetingId)
        if (transcript.isNullOrBlank()) {
            val msg = "No transcript available for meeting $meetingId"
            Log.e(TAG, msg)
            summaryDao.markFailed(meetingId, "No transcript found. Transcription may still be in progress.")
            return SummaryGenerationResult.PermanentFailure(msg)
        }

        val meeting = meetingDao.getById(meetingId)
        if (meeting == null) {
            val msg = "Meeting $meetingId not found — may have been deleted"
            Log.e(TAG, msg)
            return SummaryGenerationResult.PermanentFailure(msg)
        }

        Log.i(TAG, "Starting summary: meetingId=$meetingId " +
                "transcript=${transcript.length} chars")

        // Step 3 — insert or reset Summary row
        val existingRow = summaryDao.getByMeetingId(meetingId)
        if (existingRow == null) {
            summaryDao.insert(
                Summary(meetingId = meetingId, status = SummaryStatus.GENERATING)
            )
        } else {
            // Wipe old buffer and reset to GENERATING
            summaryDao.resetForRetry(meetingId)
            summaryDao.updateStatus(meetingId, SummaryStatus.GENERATING)
        }

        // Step 4 — collect token stream
        return collectStream(meetingId, transcript, meeting.title)
    }

    // ── Stream collection ────────────────────────────────────────────────

    private suspend fun collectStream(
        meetingId: Long,
        transcript: String,
        meetingTitle: String
    ): SummaryGenerationResult {

        val tokenBuffer = StringBuilder()
        var tokensSinceFlush = 0
        var result: SummaryGenerationResult = SummaryGenerationResult.Success

        try {
            summaryService.generateSummary(transcript, meetingTitle)
                .collect { event ->
                    when (event) {

                        is SummaryStreamEvent.Token -> {
                            tokenBuffer.append(event.text)
                            tokensSinceFlush++

                            // Batch flush to avoid hammering Room
                            if (tokensSinceFlush >= TOKEN_FLUSH_COUNT) {
                                flushTokenBuffer(meetingId, tokenBuffer)
                                tokensSinceFlush = 0
                            }
                        }

                        is SummaryStreamEvent.Complete -> {
                            // Flush any remaining buffered tokens
                            if (tokenBuffer.isNotEmpty()) {
                                flushTokenBuffer(meetingId, tokenBuffer)
                            }

                            Log.i(TAG, "Stream complete for meeting $meetingId — parsing...")
                            result = parseAndFinalize(meetingId)
                        }

                        is SummaryStreamEvent.Error -> {
                            // Flush tokens received so far (partial buffer preserved for debugging)
                            if (tokenBuffer.isNotEmpty()) {
                                flushTokenBuffer(meetingId, tokenBuffer)
                            }

                            val userMsg = event.userFacingMessage
                            Log.e(TAG, "Stream error for meeting $meetingId: $userMsg " +
                                    "retryable=${event.isRetryable}")

                            summaryDao.markFailed(meetingId, userMsg)

                            result = if (event.isRetryable) {
                                SummaryGenerationResult.RetryableFailure(userMsg)
                            } else {
                                SummaryGenerationResult.PermanentFailure(userMsg)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Uncaught exception during collection — treat as retryable
            val msg = "Unexpected error during summary generation: ${e.message}"
            Log.e(TAG, msg, e)
            summaryDao.markFailed(meetingId, "Summary generation failed unexpectedly. Will retry.")
            result = SummaryGenerationResult.RetryableFailure(msg)
        }

        return result
    }

    // ── Token flush ──────────────────────────────────────────────────────

    /**
     * Appends [buffer] to the Room streamBuffer and clears it.
     * Called every TOKEN_FLUSH_COUNT tokens — not on every token.
     */
    private suspend fun flushTokenBuffer(meetingId: Long, buffer: StringBuilder) {
        if (buffer.isEmpty()) return
        summaryDao.appendStreamToken(meetingId, buffer.toString())
        buffer.clear()
    }

    // ── Parse and finalize ───────────────────────────────────────────────

    /**
     * Reads the complete streamBuffer from Room, parses it into four
     * structured sections, and either calls markCompleted() or markFailed().
     */
    private suspend fun parseAndFinalize(meetingId: Long): SummaryGenerationResult {
        val row = summaryDao.getByMeetingId(meetingId)
        val buffer = row?.streamBuffer.orEmpty()

        Log.d(TAG, "Parsing buffer: ${buffer.length} chars")

        val parsed = SummaryParser.parse(buffer)

        return if (parsed != null) {
            summaryDao.markCompleted(
                meetingId   = meetingId,
                title       = parsed.title,
                summary     = parsed.summary,
                keyPoints   = parsed.keyPoints,
                actionItems = parsed.actionItems
            )
            Log.i(TAG, "Summary COMPLETED for meeting $meetingId: \"${parsed.title}\"")
            SummaryGenerationResult.Success
        } else {
            val errorMsg = "Could not parse summary response. The model may have returned unexpected formatting."
            summaryDao.markFailed(meetingId, errorMsg)
            Log.e(TAG, "Parse failed for meeting $meetingId — buffer length: ${buffer.length}")
            // Not retryable — same prompt will produce the same broken output
            SummaryGenerationResult.PermanentFailure(errorMsg)
        }
    }

    // ── Retry ────────────────────────────────────────────────────────────

    /**
     * Resets a FAILED summary to PENDING so SummaryWorker can be re-enqueued.
     * Called from the UI when the user taps "Retry Summary".
     */
    suspend fun resetForRetry(meetingId: Long) {
        summaryDao.resetForRetry(meetingId)
        Log.i(TAG, "Reset summary to PENDING for meeting $meetingId")
    }

    // ── Observe ──────────────────────────────────────────────────────────

    /** Summary screen observes this — live updates through generation and completion */
    fun observeSummary(meetingId: Long): Flow<Summary?> =
        summaryDao.observeByMeetingId(meetingId)

    suspend fun getSummary(meetingId: Long): Summary? =
        summaryDao.getByMeetingId(meetingId)
}
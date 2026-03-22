package com.example.recall_ai.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.recall_ai.data.remote.dto.SummaryGenerationResult
import com.example.recall_ai.data.repository.SummaryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val TAG = "SummaryWorker"

/**
 * WorkManager worker — generates a structured summary for one meeting.
 *
 * ── One worker per meeting (not per chunk) ───────────────────────────
 * Unlike TranscriptionWorker (one per chunk), SummaryWorker is one per
 * meeting because summary generation is a single LLM call over the
 * complete transcript. Parallelism is not applicable here.
 *
 * ── Enqueue timing ───────────────────────────────────────────────────
 * Enqueued by TranscriptionRepository.checkMeetingCompletion() the moment
 * all chunks reach COMPLETED status. The summary worker runs immediately
 * after transcription finishes — no user action required.
 *
 * ── KEEP idempotency ─────────────────────────────────────────────────
 * uniqueWorkName = "summary_{meetingId}". If:
 *   • TranscriptionRepository enqueues it and ProcessDeathRecoveryManager
 *     also enqueues it on app relaunch — KEEP makes the second call a no-op.
 *   • User manually triggers retry — UI cancels the existing job first,
 *     then re-enqueues, so a fresh run starts.
 *
 * ── Retry budget ─────────────────────────────────────────────────────
 * WorkManager retries on Result.retry() with exponential backoff:
 *   Attempt 1  → immediate
 *   Attempt 2  → 60 s delay
 *   Attempt 3  → 120 s delay
 *
 * SummaryRepository also checks retryCount on the Summary row. After
 * MAX_RETRY_ATTEMPTS the worker returns Result.failure() regardless of
 * the SummaryGenerationResult, so we don't loop forever on soft errors.
 *
 * ── Network constraint ────────────────────────────────────────────────
 * Gemini requires connectivity. The worker waits in the queue automatically
 * when offline and starts as soon as connectivity returns.
 */
@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SummaryRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        private const val BACKOFF_DELAY_S  = 60L
        private const val MAX_RETRY_ATTEMPTS = 3
        const val TAG_SUMMARY = "summary"

        /**
         * Enqueues a SummaryWorker for [meetingId]. Idempotent.
         *
         * Use ExistingWorkPolicy.KEEP — if a job already exists (e.g. worker
         * is waiting for connectivity), do not restart it.
         */
        fun enqueue(context: Context, meetingId: Long) {
            val work = OneTimeWorkRequestBuilder<SummaryWorker>()
                .setInputData(workDataOf(KEY_MEETING_ID to meetingId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_S, TimeUnit.SECONDS)
                .addTag(TAG_SUMMARY)
                .addTag(meetingTag(meetingId))
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(meetingId), ExistingWorkPolicy.KEEP, work)

            Log.d(TAG, "Enqueued summary worker for meeting=$meetingId")
        }

        /**
         * Cancels a pending/running summary worker and immediately re-enqueues.
         * Use this for manual retry so the user gets a fresh attempt now,
         * not after an existing backoff delay.
         */
        fun cancelAndReenqueue(context: Context, meetingId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(meetingId))
            enqueue(context, meetingId)
            Log.d(TAG, "Cancelled and re-enqueued summary worker for meeting=$meetingId")
        }

        /** Cancels the summary worker — call when the meeting is deleted */
        fun cancel(context: Context, meetingId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(meetingId))
            Log.d(TAG, "Cancelled summary worker for meeting $meetingId")
        }

        fun uniqueName(meetingId: Long)  = "summary_$meetingId"
        fun meetingTag(meetingId: Long)  = "summary_meeting_$meetingId"
    }

    override suspend fun doWork(): Result {
        val meetingId = inputData.getLong(KEY_MEETING_ID, -1L)

        if (meetingId < 0) {
            Log.e(TAG, "Invalid meetingId — this should never happen")
            return Result.failure(workDataOf("error" to "Invalid meetingId"))
        }

        Log.d(TAG, "doWork: meetingId=$meetingId attempt=${runAttemptCount + 1}")

        // Hard cap — don't retry beyond MAX_RETRY_ATTEMPTS regardless of result
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            val msg = "Exceeded $MAX_RETRY_ATTEMPTS retry attempts for meeting $meetingId"
            Log.e(TAG, msg)
            return Result.failure(workDataOf("error" to msg))
        }

        return when (val result = repository.generateSummary(meetingId)) {

            is SummaryGenerationResult.Success -> {
                Log.i(TAG, "Summary complete for meeting $meetingId")
                Result.success()
            }

            is SummaryGenerationResult.AlreadyComplete -> {
                Log.i(TAG, "Summary already exists for meeting $meetingId — nothing to do")
                Result.success()
            }

            is SummaryGenerationResult.RetryableFailure -> {
                Log.w(TAG, "Retryable failure (attempt ${runAttemptCount + 1}): ${result.reason}")
                Result.retry()
            }

            is SummaryGenerationResult.PermanentFailure -> {
                Log.e(TAG, "Permanent failure: ${result.reason}")
                Result.failure(workDataOf("error" to result.reason))
            }
        }
    }
}
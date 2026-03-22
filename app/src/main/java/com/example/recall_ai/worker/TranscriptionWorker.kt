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
import com.example.recall_ai.data.repository.TranscribeChunkResult
import com.example.recall_ai.data.repository.TranscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val TAG = "TranscriptionWorker"

/**
 * WorkManager worker that transcribes one audio chunk end-to-end.
 *
 * ── Why this was broken (Bug #3 root cause) ──────────────────────────────
 * The previous implementation was a stub — doWork() returned Result.success()
 * without calling TranscriptionRepository.transcribeChunk(). No transcripts
 * were ever written to Room, so the transcript tab showed nothing and the
 * summary pipeline never triggered.
 *
 * ── Fix ───────────────────────────────────────────────────────────────────
 * This is now a proper @HiltWorker. Hilt injects TranscriptionRepository,
 * which owns the full five-step transcription pipeline:
 *   Step 1  Mark chunk IN_PROGRESS (prevents duplicate workers racing)
 *   Step 2  File guard (verify WAV exists and is non-empty)
 *   Step 3  Boundary prompt (last sentence of chunk N-1 as Whisper context)
 *   Step 4  Upload to TranscriptionService (Whisper or Mock)
 *   Step 5  Persist result → insert Transcript row, mark chunk COMPLETED,
 *           delete WAV, check if all chunks done → trigger SummaryWorker
 *
 * ── One worker per chunk, not per meeting ────────────────────────────────
 * Each 30-second chunk is transcribed independently and in parallel.
 * uniqueWorkName = "transcription_chunk_{chunkId}" prevents two workers
 * from racing on the same chunk if the service enqueues twice.
 *
 * ── Retry / backoff ───────────────────────────────────────────────────────
 *   RetryableFailure → Result.retry() → WorkManager exponential backoff (30s base)
 *   PermanentFailure → Result.failure() → chunk marked FAILED in Room
 *
 * ── Network constraint ────────────────────────────────────────────────────
 * Whisper requires connectivity. Worker sits in queue when offline and starts
 * the moment connectivity returns. MockTranscriptionService ignores this
 * (it works offline), but the constraint is harmless in dev.
 */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TranscriptionRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_CHUNK_ID   = "chunk_id"
        const val KEY_MEETING_ID = "meeting_id"

        private const val BACKOFF_DELAY_SECONDS = 30L

        /**
         * Enqueues a transcription worker for [chunkId].
         *
         * uniqueWorkName = "transcription_chunk_{chunkId}" so if RecordingService
         * or ProcessDeathRecoveryManager enqueues the same chunk twice,
         * the second call is a no-op (ExistingWorkPolicy.KEEP).
         */
        fun enqueue(context: Context, chunkId: Long, meetingId: Long) {
            Log.d(TAG, "Enqueuing transcription for chunkId=$chunkId meetingId=$meetingId")

            val inputData = workDataOf(
                KEY_CHUNK_ID   to chunkId,
                KEY_MEETING_ID to meetingId
            )

            val work = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag("transcription")
                .addTag("transcription_meeting_$meetingId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "transcription_chunk_$chunkId",
                    ExistingWorkPolicy.KEEP,
                    work
                )
        }
    }

    override suspend fun doWork(): Result {
        val chunkId = inputData.getLong(KEY_CHUNK_ID, -1L)

        if (chunkId < 0L) {
            Log.e(TAG, "Invalid chunkId in input data — this should never happen")
            return Result.failure(workDataOf("error" to "Missing chunk_id"))
        }

        Log.d(TAG, "doWork: chunkId=$chunkId attempt=${runAttemptCount + 1}")

        return when (val result = repository.transcribeChunk(chunkId)) {

            is TranscribeChunkResult.Success -> {
                Log.i(TAG, "Chunk $chunkId transcribed: ${result.transcriptLength} chars")
                Result.success()
            }

            is TranscribeChunkResult.RetryableFailure -> {
                Log.w(TAG, "Chunk $chunkId retryable (attempt ${result.attempt}): ${result.reason}")
                Result.retry()
            }

            is TranscribeChunkResult.PermanentFailure -> {
                Log.e(TAG, "Chunk $chunkId permanent failure: ${result.reason}")
                Result.failure(workDataOf("error" to result.reason))
            }
        }
    }
}
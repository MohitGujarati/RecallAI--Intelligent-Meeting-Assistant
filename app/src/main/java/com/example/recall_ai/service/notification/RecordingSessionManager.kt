package com.example.recall_ai.service

import android.util.Log
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.dao.MeetingDao
import com.example.recall_ai.data.local.entity.AudioSource
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.local.entity.PauseReason
import com.example.recall_ai.service.audio.ChunkManager.SavedChunk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "RecordingSessionManager"

/**
 * Owns all Room write operations for the recording session lifecycle.
 *
 * This class is intentionally NOT a ViewModel — it runs inside the
 * Foreground Service and must survive as long as the service does.
 * It handles:
 * • Creating the Meeting row on session start
 * • Updating duration + chunk count as chunks arrive
 * • Updating pause/resume state
 * • Finalizing the Meeting row on stop
 * • Process-death recovery (resets stuck IN_PROGRESS chunks)
 *
 * All functions are suspend — callers run them inside serviceScope.
 */
class RecordingSessionManager @Inject constructor(
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao
) {

    private val dateFormatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    // ── Session lifecycle ────────────────────────────────────────────────

    /**
     * Creates a new Meeting row and returns its ID.
     * Called once per recording session.
     */
    suspend fun createSession(startTime: Long): Long {
        val title = "Meeting – ${dateFormatter.format(Date(startTime))}"
        val meeting = Meeting(
            title     = title,
            startTime = startTime,
            status    = MeetingStatus.RECORDING
        )
        val id = meetingDao.insert(meeting)
        Log.d(TAG, "Session created: id=$id title=$title")
        return id
    }

    /**
     * Called each time a chunk is saved to disk + Room.
     * Updates the Meeting's running totals so the dashboard stays fresh.
     */
    suspend fun onChunkSaved(savedChunk: SavedChunk, elapsedSeconds: Long) {
        meetingDao.updateProgress(
            meetingId       = savedChunk.meetingId,
            durationSeconds = elapsedSeconds,
            totalChunks     = savedChunk.chunkIndex + 1
        )
        Log.d(TAG, "Meeting ${savedChunk.meetingId} updated: chunks=${savedChunk.chunkIndex + 1}, elapsed=${elapsedSeconds}s")
    }

    /**
     * Called when the recording is paused (call or audio focus loss).
     */
    suspend fun onPaused(meetingId: Long, reason: PauseReason) {
        val meeting = meetingDao.getById(meetingId) ?: return
        meetingDao.update(
            meeting.copy(
                status      = MeetingStatus.PAUSED,
                pauseReason = reason,
                updatedAt   = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Meeting $meetingId paused: reason=$reason")
    }

    /**
     * Called when recording resumes after a pause.
     */
    suspend fun onResumed(meetingId: Long) {
        val meeting = meetingDao.getById(meetingId) ?: return
        meetingDao.update(
            meeting.copy(
                status      = MeetingStatus.RECORDING,
                pauseReason = PauseReason.NONE,
                updatedAt   = System.currentTimeMillis()
            )
        )
        Log.d(TAG, "Meeting $meetingId resumed")
    }

    /**
     * Called when the audio source changes (bluetooth/wired/built-in).
     */
    suspend fun onAudioSourceChanged(meetingId: Long, source: AudioSource) {
        val meeting = meetingDao.getById(meetingId) ?: return
        meetingDao.update(meeting.copy(audioSource = source, updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "Meeting $meetingId audio source: $source")
    }

    /**
     * Finalizes the session with the given terminal status.
     * Called on user stop, low storage, or error.
     */
    suspend fun finalizeSession(
        meetingId: Long,
        endTime: Long,
        status: MeetingStatus,
        finalElapsedSeconds: Long
    ) {
        // Update duration one final time before marking stopped
        val chunkCount = audioChunkDao.getChunkCount(meetingId)
        meetingDao.updateProgress(meetingId, finalElapsedSeconds, chunkCount)
        meetingDao.finalizeSession(meetingId, endTime, status)
        Log.d(TAG, "Session $meetingId finalized: status=$status, duration=${finalElapsedSeconds}s, chunks=$chunkCount")
    }

    // ── Process death recovery ───────────────────────────────────────────

    /**
     * Called on app restart to repair any state corruption caused by process death.
     *
     * Without this:
     * • Chunks stuck as IN_PROGRESS would never be retried
     * • Active sessions would stay RECORDING forever with no service
     *
     * With this:
     * • Stuck chunks → PENDING (transcription worker picks them up)
     * • Orphaned RECORDING/PAUSED meetings → STOPPED (UI shows correctly)
     */
    suspend fun recoverFromProcessDeath() {
        // Reset any chunks that were mid-flight when the process died
        val resetCount = audioChunkDao.resetStuckChunks()
        val resetCountInt = when (resetCount) {
            is Int -> resetCount
            is Long -> resetCount.toInt()
            is String -> resetCount.toIntOrNull() ?: 0
            else -> 0
        }

        if (resetCountInt > 0) {
            Log.w(TAG, "Process death recovery: reset $resetCount IN_PROGRESS chunks to PENDING")
        }

        // Find any session that was RECORDING/PAUSED with no active service
        val orphanedSession = meetingDao.getActiveSession()
        if (orphanedSession != null) {
            Log.w(TAG, "Process death recovery: orphaned session ${orphanedSession.id} found, marking STOPPED")
            meetingDao.finalizeSession(
                meetingId = orphanedSession.id,
                endTime   = System.currentTimeMillis(),
                status    = MeetingStatus.STOPPED
            )
            // Note: transcription workers will be re-enqueued by WorkManager automatically
        }
    }

    // ── Storage check ────────────────────────────────────────────────────

    fun hasEnoughStorage(availableBytes: Long): Boolean = availableBytes >= MIN_FREE_BYTES

    companion object {
        /**
         * Minimum free space required to start or continue recording.
         * 100 MB is enough for ~50 minutes of 16kHz mono WAV (≈ 2 MB/min).
         */
        private const val MIN_FREE_BYTES = 100L * 1024L * 1024L   // 100 MB
    }
}
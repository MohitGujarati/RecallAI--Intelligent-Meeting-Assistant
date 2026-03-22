package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.recall_ai.data.local.entity.AudioChunk
import com.example.recall_ai.data.local.entity.TranscriptionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {

    // ── Write ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: AudioChunk): Long

    @Update
    suspend fun update(chunk: AudioChunk)

    @Query("DELETE FROM audio_chunks WHERE id = :chunkId")
    suspend fun deleteById(chunkId: Long)

    /** Bulk delete — called after transcription succeeds and files are no longer needed */
    @Query("DELETE FROM audio_chunks WHERE meetingId = :meetingId")
    suspend fun deleteAllForMeeting(meetingId: Long)

    // ── Read — one-shot ──────────────────────────────────────────────────

    @Query("SELECT * FROM audio_chunks WHERE id = :chunkId")
    suspend fun getById(chunkId: Long): AudioChunk?

    /** All chunks for a meeting in correct playback/transcript order */
    @Query("""
        SELECT * FROM audio_chunks
        WHERE meetingId = :meetingId
        ORDER BY chunkIndex ASC
    """)
    suspend fun getChunksForMeeting(meetingId: Long): List<AudioChunk>

    /**
     * Transcription upload queue — returns PENDING chunks oldest-first.
     * WorkManager calls this to find work to do.
     */
    @Query("""
        SELECT * FROM audio_chunks
        WHERE transcriptionStatus = 'PENDING'
        ORDER BY chunkIndex ASC
    """)
    suspend fun getPendingChunks(): List<AudioChunk>

    /**
     * Used by the "retry ALL chunks" requirement:
     * Resets failed chunks back to PENDING so they re-enter the queue.
     */
    @Query("""
        SELECT * FROM audio_chunks
        WHERE meetingId = :meetingId
          AND transcriptionStatus = 'FAILED'
        ORDER BY chunkIndex ASC
    """)
    suspend fun getFailedChunks(meetingId: Long): List<AudioChunk>

    /** How many chunks exist for a meeting — used to verify completeness */
    @Query("SELECT COUNT(*) FROM audio_chunks WHERE meetingId = :meetingId")
    suspend fun getChunkCount(meetingId: Long): Int

    /** How many chunks are fully transcribed — used to trigger summary generation */
    @Query("""
        SELECT COUNT(*) FROM audio_chunks
        WHERE meetingId    = :meetingId
          AND transcriptionStatus = 'COMPLETED'
    """)
    suspend fun getCompletedChunkCount(meetingId: Long): Int

    // ── Read — reactive (Flow) ───────────────────────────────────────────

    /** Recording screen observes its own session's chunk list */
    @Query("""
        SELECT * FROM audio_chunks
        WHERE meetingId = :meetingId
        ORDER BY chunkIndex ASC
    """)
    fun observeChunksForMeeting(meetingId: Long): Flow<List<AudioChunk>>

    // ── Targeted updates ─────────────────────────────────────────────────

    @Query("""
        UPDATE audio_chunks
        SET transcriptionStatus = :status,
            updatedAt           = :now
        WHERE id = :chunkId
    """)
    suspend fun updateTranscriptionStatus(
        chunkId: Long,
        status: TranscriptionStatus,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE audio_chunks
        SET transcriptionStatus = :status,
            retryCount          = retryCount + 1,
            lastError           = :error,
            updatedAt           = :now
        WHERE id = :chunkId
    """)
    suspend fun markFailed(
        chunkId: Long,
        error: String,
        status: TranscriptionStatus = TranscriptionStatus.FAILED,
        now: Long = System.currentTimeMillis()
    )

    /** Reset all failed chunks to PENDING — implements "retry ALL" requirement */
    @Query("""
        UPDATE audio_chunks
        SET transcriptionStatus = 'PENDING',
            lastError           = NULL,
            updatedAt           = :now
        WHERE meetingId = :meetingId
          AND transcriptionStatus = 'FAILED'
    """)
    suspend fun resetFailedToPending(
        meetingId: Long,
        now: Long = System.currentTimeMillis()
    )

    /** Safety net for process death — reset stuck IN_PROGRESS back to PENDING */
    @Query("""
        UPDATE audio_chunks
        SET transcriptionStatus = 'PENDING',
            updatedAt           = :now
        WHERE transcriptionStatus = 'IN_PROGRESS'
    """)
    suspend fun resetStuckChunks(now: Long = System.currentTimeMillis())
}
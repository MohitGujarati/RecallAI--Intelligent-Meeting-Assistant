package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

import com.example.recall_ai.data.local.entity.Transcript

@Dao
interface TranscriptDao {

    // ── Write ────────────────────────────────────────────────────────────

    /**
     * REPLACE strategy handles the case where a retry produces a new
     * transcription for the same chunk — we always keep the latest result.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transcript: Transcript): Long

    @Query("DELETE FROM transcripts WHERE meetingId = :meetingId")
    suspend fun deleteAllForMeeting(meetingId: Long)

    // ── Read — one-shot ──────────────────────────────────────────────────

    /** Single chunk's transcript — used after transcription completes */
    @Query("SELECT * FROM transcripts WHERE chunkId = :chunkId LIMIT 1")
    suspend fun getByChunkId(chunkId: Long): Transcript?

    /**
     * Full ordered transcript for a meeting.
     * ORDER BY chunkIndex is the single source of truth for correct ordering.
     */
    @Query("""
        SELECT * FROM transcripts
        WHERE meetingId = :meetingId
        ORDER BY chunkIndex ASC
    """)
    suspend fun getTranscriptsForMeeting(meetingId: Long): List<Transcript>

    /**
     * Concatenated full transcript text — sent to LLM for summary generation.
     * Joining with a space preserves sentence boundaries across chunk borders.
     */
    @Query("""
        SELECT group_concat(text, ' ') FROM transcripts
        WHERE meetingId = :meetingId
        ORDER BY chunkIndex ASC
    """)
    suspend fun getFullTranscriptText(meetingId: Long): String?

    /** Number of transcript rows — compared to totalChunks to detect completeness */
    @Query("SELECT COUNT(*) FROM transcripts WHERE meetingId = :meetingId")
    suspend fun getTranscriptCount(meetingId: Long): Int

    // ── Read — reactive (Flow) ───────────────────────────────────────────

    /**
     * Transcript screen live-updates as each chunk is transcribed.
     * UI concatenates the text list in order to show the rolling transcript.
     */
    @Query("""
        SELECT * FROM transcripts
        WHERE meetingId = :meetingId
        ORDER BY chunkIndex ASC
    """)
    fun observeTranscriptsForMeeting(meetingId: Long): Flow<List<Transcript>>
}
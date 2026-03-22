package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.recall_ai.data.local.entity.Summary
import com.example.recall_ai.data.local.entity.SummaryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    // ── Write ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: Summary): Long

    @Update
    suspend fun update(summary: Summary)

    @Query("DELETE FROM summaries WHERE meetingId = :meetingId")
    suspend fun deleteByMeetingId(meetingId: Long)

    // ── Read — one-shot ──────────────────────────────────────────────────

    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId LIMIT 1")
    suspend fun getByMeetingId(meetingId: Long): Summary?

    /** WorkManager checks this to avoid double-generating */
    @Query("""
        SELECT * FROM summaries
        WHERE meetingId = :meetingId
          AND status IN ('GENERATING', 'COMPLETED')
        LIMIT 1
    """)
    suspend fun getActiveOrCompletedSummary(meetingId: Long): Summary?

    // ── Read — reactive (Flow) ───────────────────────────────────────────

    /**
     * Summary screen observes this.
     * While status == GENERATING, the UI renders streamBuffer as live text.
     * Once COMPLETED, the UI switches to the structured fields.
     */
    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId LIMIT 1")
    fun observeByMeetingId(meetingId: Long): Flow<Summary?>

    // ── Targeted streaming updates ───────────────────────────────────────

    /**
     * Called on every incoming token from the LLM stream.
     * Appends to streamBuffer and flips status to GENERATING.
     */
    @Query("""
        UPDATE summaries
        SET streamBuffer = streamBuffer || :token,
            status       = 'GENERATING',
            updatedAt    = :now
        WHERE meetingId = :meetingId
    """)
    suspend fun appendStreamToken(
        meetingId: Long,
        token: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Called once streaming finishes and structured parsing succeeds.
     */
    @Query("""
        UPDATE summaries
        SET title        = :title,
            summary      = :summary,
            actionItems  = :actionItems,
            keyPoints    = :keyPoints,
            streamBuffer = '',
            status       = 'COMPLETED',
            errorMessage = NULL,
            updatedAt    = :now
        WHERE meetingId  = :meetingId
    """)
    suspend fun markCompleted(
        meetingId: Long,
        title: String,
        summary: String,
        actionItems: String,
        keyPoints: String,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE summaries
        SET status       = 'FAILED',
            errorMessage = :error,
            retryCount   = retryCount + 1,
            updatedAt    = :now
        WHERE meetingId  = :meetingId
    """)
    suspend fun markFailed(
        meetingId: Long,
        error: String,
        now: Long = System.currentTimeMillis()
    )

    /** Retry — reset to PENDING so WorkManager re-enqueues */
    @Query("""
        UPDATE summaries
        SET status       = 'PENDING',
            errorMessage = NULL,
            streamBuffer = '',
            updatedAt    = :now
        WHERE meetingId  = :meetingId
    """)
    suspend fun resetForRetry(
        meetingId: Long,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE summaries
        SET status    = :status,
            updatedAt = :now
        WHERE meetingId = :meetingId
    """)
    suspend fun updateStatus(
        meetingId: Long,
        status: SummaryStatus,
        now: Long = System.currentTimeMillis()
    )

    /**
     * Used by ProcessDeathRecoveryManager Step 5.
     * Returns all summaries stuck as GENERATING — means the LLM stream
     * was in-flight when the process died. Each needs to be reset and
     * re-enqueued.
     */
    @Query("SELECT * FROM summaries WHERE status = 'GENERATING'")
    suspend fun getGeneratingSummaries(): List<Summary>
}
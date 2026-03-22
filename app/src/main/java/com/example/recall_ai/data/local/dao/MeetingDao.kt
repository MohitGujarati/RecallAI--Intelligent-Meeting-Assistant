package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.MeetingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {

    // ── Write ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(meeting: Meeting): Long

    @Update
    suspend fun update(meeting: Meeting)

    @Query("UPDATE meetings SET title = :title, iconEmoji = :emoji, iconColorHex = :color, updatedAt = :now WHERE id = :id")
    suspend fun updateTitleAndIcon(id: Long, title: String, emoji: String?, color: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM meetings WHERE id = :meetingId")
    suspend fun deleteById(meetingId: Long)

    // ── Read — one-shot ──────────────────────────────────────────────────

    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    suspend fun getById(meetingId: Long): Meeting?

    /** Used for process-death recovery — find the last active session */
    @Query("""
        SELECT * FROM meetings
        WHERE status IN ('RECORDING', 'PAUSED')
        ORDER BY startTime DESC
        LIMIT 1
    """)
    suspend fun getActiveSession(): Meeting?

    // ── Read — reactive (Flow) ───────────────────────────────────────────

    /** Dashboard list — all meetings newest-first */
    @Query("SELECT * FROM meetings ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Meeting>>

    /** Detail screen observes a single meeting (status, duration, etc.) */
    @Query("SELECT * FROM meetings WHERE id = :meetingId")
    fun observeById(meetingId: Long): Flow<Meeting?>

    /** Only meetings that are fully done — useful for export/sharing */
    @Query("""
        SELECT * FROM meetings
        WHERE status = 'COMPLETED'
        ORDER BY startTime DESC
    """)
    fun observeCompleted(): Flow<List<Meeting>>

    // ── Targeted updates (avoids full-object update conflicts) ───────────

    @Query("UPDATE meetings SET status = :status, updatedAt = :now WHERE id = :meetingId")
    suspend fun updateStatus(
        meetingId: Long,
        status: MeetingStatus,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE meetings
        SET durationSeconds = :durationSeconds,
            totalChunks     = :totalChunks,
            updatedAt       = :now
        WHERE id = :meetingId
    """)
    suspend fun updateProgress(
        meetingId: Long,
        durationSeconds: Long,
        totalChunks: Int,
        now: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE meetings
        SET endTime   = :endTime,
            status    = :status,
            updatedAt = :now
        WHERE id = :meetingId
    """)
    suspend fun finalizeSession(
        meetingId: Long,
        endTime: Long,
        status: MeetingStatus,
        now: Long = System.currentTimeMillis()
    )
}
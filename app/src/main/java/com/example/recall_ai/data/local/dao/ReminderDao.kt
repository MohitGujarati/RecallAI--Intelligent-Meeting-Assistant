package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.recall_ai.data.local.entity.Reminder
import com.example.recall_ai.data.local.entity.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE meetingId = :meetingId ORDER BY createdAt DESC")
    fun observeByMeetingId(meetingId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE type = 'TODO' ORDER BY createdAt DESC")
    fun observeAllTodos(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE type = 'ALARM' AND status = 'PENDING' ORDER BY triggerAtMillis ASC")
    fun observePendingAlarms(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("UPDATE reminders SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReminderStatus, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** One-shot query for boot reschedule — not a Flow. */
    @Query("SELECT * FROM reminders WHERE type = 'ALARM' AND status = 'PENDING'")
    suspend fun getPendingAlarmsList(): List<Reminder>
}

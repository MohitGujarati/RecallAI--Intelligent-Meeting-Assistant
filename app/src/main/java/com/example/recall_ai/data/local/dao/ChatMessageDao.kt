package com.example.recall_ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.recall_ai.data.local.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the chat_messages table.
 *
 * Provides observation (Flow), insertion, and in-place text updates
 * for the streaming AI response pattern.
 */
@Dao
interface ChatMessageDao {

    /** Live-updating list for the Chat tab UI */
    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId ORDER BY createdAt ASC")
    fun observeByMeetingId(meetingId: Long): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    /** Append streamed tokens to an existing AI message row */
    @Query("UPDATE chat_messages SET text = text || :token WHERE id = :messageId")
    suspend fun appendText(messageId: Long, token: String)

    @Query("DELETE FROM chat_messages WHERE meetingId = :meetingId")
    suspend fun deleteByMeetingId(meetingId: Long)
}

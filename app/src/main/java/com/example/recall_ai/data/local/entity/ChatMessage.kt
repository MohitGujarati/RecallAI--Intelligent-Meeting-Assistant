package com.example.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists one chat message (user question or AI answer) for a meeting.
 *
 * Messages are ordered by [createdAt] and rendered in the Chat tab.
 * CASCADE on meeting delete ensures cleanup.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = Meeting::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId")]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val meetingId: Long,

    /** true = user question, false = AI answer */
    val isUser: Boolean,

    val text: String,

    val createdAt: Long = System.currentTimeMillis()
)

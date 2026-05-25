package com.mohit.recall_ai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mohit.recall_ai.data.local.converter.Converters
import com.mohit.recall_ai.data.local.dao.AudioChunkDao
import com.mohit.recall_ai.data.local.dao.ChatMessageDao
import com.mohit.recall_ai.data.local.dao.MeetingDao
import com.mohit.recall_ai.data.local.dao.ReminderDao
import com.mohit.recall_ai.data.local.dao.SummaryDao
import com.mohit.recall_ai.data.local.dao.TranscriptDao
import com.mohit.recall_ai.data.local.entity.AudioChunk
import com.mohit.recall_ai.data.local.entity.ChatMessage
import com.mohit.recall_ai.data.local.entity.Meeting
import com.mohit.recall_ai.data.local.entity.Reminder
import com.mohit.recall_ai.data.local.entity.Summary
import com.mohit.recall_ai.data.local.entity.Transcript

/**
 * Single Room database for the entire app.
 *
 * Version history:
 *   1 → initial schema (Meeting, AudioChunk, Transcript, Summary)
 *   2 → added ChatMessage entity for Chat with Transcript feature
 *   3 → (current)
 *   4 → added Reminder entity for AI-driven reminders & alarms
 *
 * Migration strategy:
 *   Add a Migration object in the companion when incrementing version.
 *   Never use fallbackToDestructiveMigration() in production.
 */
@Database(
    entities = [
        Meeting::class,
        AudioChunk::class,
        Transcript::class,
        Summary::class,
        ChatMessage::class,
        Reminder::class
    ],
    version = 4,
    exportSchema = true         // keeps a schema history JSON in /schemas
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun meetingDao(): MeetingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "recall_ai.db"
    }
}
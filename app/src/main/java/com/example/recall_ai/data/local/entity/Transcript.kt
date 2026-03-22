package com.example.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the raw transcription text returned by Whisper / Gemini / Android SpeechRecognizer
 * for one AudioChunk (or one live-recognition segment in fallback mode).
 *
 * Key design decisions:
 * - One Transcript row per AudioChunk (1-to-1 after successful transcription).
 * - chunkIndex is denormalized here so queries can ORDER BY chunkIndex
 *   without a join to audio_chunks — critical for fast full-transcript assembly.
 * - Full transcript for a meeting = SELECT text FROM transcripts
 *   WHERE meetingId = ? ORDER BY chunkIndex ASC
 *
 * Fallback (ANDROID_SPEECH) mode:
 * - ContinuousRecognitionManager inserts a lightweight placeholder AudioChunk
 *   row (filePath = "", fileSizeBytes = 0) before inserting the Transcript so
 *   the foreign-key constraint is always satisfied.
 * - No WAV file is written to disk. No TranscriptionWorker is enqueued.
 * - SummaryWorker is triggered directly when the session stops.
 */
@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = Meeting::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AudioChunk::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("meetingId"),
        Index("chunkId", unique = true),        // one transcript per chunk
        Index(value = ["meetingId", "chunkIndex"])
    ]
)
data class Transcript(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val meetingId: Long,

    /** FK to the source AudioChunk */
    val chunkId: Long,

    /** Mirrors AudioChunk.chunkIndex for orderless joins */
    val chunkIndex: Int,

    /** Raw text returned by the transcription API */
    val text: String,

    /**
     * Confidence score 0.0–1.0 if the API returns it (Whisper does via segments).
     * Null if the API doesn't provide one (Android SpeechRecognizer does not).
     */
    val confidence: Float? = null,

    /** Which API produced this transcript */
    val source: TranscriptSource = TranscriptSource.WHISPER,

    /** Language code detected by API, e.g. "en", "es" */
    val detectedLanguage: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)

enum class TranscriptSource {
    WHISPER,
    GEMINI,
    MOCK,

    /**
     * Android's built-in SpeechRecognizer — used as a zero-config fallback
     * when no OPENAI_API_KEY is set. Live, on-device, continuous transcription.
     * No confidence score; language inferred from device locale.
     */
    ANDROID_SPEECH
}
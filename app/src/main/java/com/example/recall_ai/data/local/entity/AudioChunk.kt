package com.example.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents one 30-second audio segment (with ~2s overlap) of a Meeting.
 *
 * Lifecycle:
 *   Recording captures chunk → saved to disk → chunkFile set → status = PENDING
 *   Worker picks it up  → status = IN_PROGRESS
 *   Whisper/Gemini returns → status = COMPLETED, transcript row inserted
 *   On failure → retryCount++ → status = FAILED after MAX_RETRIES
 */
@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = Meeting::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE   // wipe chunks when meeting deleted
        )
    ],
    indices = [
        Index("meetingId"),
        Index(value = ["meetingId", "chunkIndex"], unique = true)
    ]
)
data class AudioChunk(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val meetingId: Long,

    /**
     * Zero-based index within the meeting.
     * Used to reconstruct transcript in correct order.
     */
    val chunkIndex: Int,

    /** Absolute path to the .m4a file on device storage */
    val filePath: String,

    /** Epoch millis when this chunk's recording window started */
    val startTime: Long,

    /** Actual recorded duration in milliseconds */
    val durationMs: Long = 30_000L,

    /**
     * Overlap with previous chunk in milliseconds (~2000ms).
     * Chunk 0 has 0 overlap. All subsequent chunks have OVERLAP_MS.
     */
    val overlapMs: Long = 0L,

    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING,

    /** Incremented on each failed transcription attempt */
    val retryCount: Int = 0,

    /** ISO-8601 error message on last failure, null otherwise */
    val lastError: String? = null,

    /** File size in bytes; used for storage accounting */
    val fileSizeBytes: Long = 0L,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CHUNK_DURATION_MS = 30_000L
        const val OVERLAP_MS = 2_000L
        const val MAX_RETRY_COUNT = 3
    }
}

enum class TranscriptionStatus {
    /** Chunk saved to disk; waiting to be uploaded */
    PENDING,

    /** Currently being sent to Whisper / Gemini */
    IN_PROGRESS,

    /** API returned text; Transcript row exists */
    COMPLETED,

    /** Failed all retries; needs manual retry */
    FAILED
}
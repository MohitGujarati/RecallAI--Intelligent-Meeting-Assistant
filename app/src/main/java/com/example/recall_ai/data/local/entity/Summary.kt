package com.example.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the LLM-generated structured summary for a completed Meeting.
 *
 * Streaming design:
 * - When WorkManager begins summary generation, a row is inserted with
 *   status = GENERATING and all text fields null/empty.
 * - As the LLM streams tokens, streamBuffer is updated incrementally.
 * - Once streaming completes the buffer is parsed into the 4 structured fields
 *   and status flips to COMPLETED.
 * - This lets the UI observe a Flow<Summary> and render tokens in real-time
 *   even across process death (WorkManager re-enqueues and re-streams).
 */
@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = Meeting::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("meetingId", unique = true)   // one summary per meeting
    ]
)
data class Summary(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val meetingId: Long,

    val status: SummaryStatus = SummaryStatus.PENDING,

    // ── Structured sections (null until streaming completes) ──────────────

    /** e.g. "Q1 Planning Sync – March 7" */
    val title: String? = null,

    /** 2–4 paragraph narrative overview */
    val summary: String? = null,

    /** Newline-separated list of action items */
    val actionItems: String? = null,

    /** Newline-separated list of key points */
    val keyPoints: String? = null,

    // ── Streaming buffer ─────────────────────────────────────────────────

    /**
     * Accumulates raw LLM token stream while status == GENERATING.
     * Wiped once parsing into structured fields succeeds.
     * Persisted so we can resume streaming display after process death.
     */
    val streamBuffer: String = "",

    // ── Error handling ───────────────────────────────────────────────────

    val errorMessage: String? = null,

    /** How many times generation has been attempted */
    val retryCount: Int = 0,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SummaryStatus {
    /** Waiting for transcript to be complete */
    PENDING,

    /** WorkManager job dispatched, LLM call in-flight */
    GENERATING,

    /** Summary fully parsed and stored in structured fields */
    COMPLETED,

    /** Generation failed; errorMessage set; retry available */
    FAILED
}
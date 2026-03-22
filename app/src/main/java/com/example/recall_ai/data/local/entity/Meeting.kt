package com.example.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single recording session.
 * Created when the user presses Record. Updated throughout the session lifecycle.
 */
@Entity(tableName = "meetings")
data class Meeting(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Auto-generated title, e.g. "Meeting – Mar 7, 2:30 PM" */
    val title: String,

    /** Epoch millis — set when recording begins */
    val startTime: Long,

    /** Epoch millis — null until session ends */
    val endTime: Long? = null,

    /** Total recorded duration in seconds (updated incrementally as chunks arrive) */
    val durationSeconds: Long = 0L,

    val status: MeetingStatus = MeetingStatus.RECORDING,

    /** Why the session is paused; NONE when not paused */
    val pauseReason: PauseReason = PauseReason.NONE,

    /** Audio source in use (built-in mic, wired, bluetooth) */
    val audioSource: AudioSource = AudioSource.BUILT_IN,

    /** Total number of 30-second chunks captured so far */
    val totalChunks: Int = 0,

    /** User-selected emoji for the meeting icon (e.g. "💡", "🎵") */
    val iconEmoji: String? = null,

    /** Hex color for icon background (e.g. "#FFF9C4") — pastel/muted */
    val iconColorHex: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MeetingStatus {
    /** Actively recording audio */
    RECORDING,

    /** Temporarily paused — see pauseReason for why */
    PAUSED,

    /** User or system stopped the session; chunks may still be transcribing */
    STOPPED,

    /** All chunks transcribed + summary generated; fully done */
    COMPLETED,

    /** Stopped due to low storage */
    STOPPED_LOW_STORAGE,

    /** Stopped due to an unrecoverable error */
    ERROR
}

enum class PauseReason {
    NONE,
    PHONE_CALL,
    AUDIO_FOCUS_LOSS
}

enum class AudioSource {
    BUILT_IN,
    WIRED_HEADSET,
    BLUETOOTH
}
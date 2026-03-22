package com.example.recall_ai.data.local.converter

import androidx.room.TypeConverter
import com.example.recall_ai.data.local.entity.AudioSource
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.local.entity.PauseReason
import com.example.recall_ai.data.local.entity.SummaryStatus
import com.example.recall_ai.data.local.entity.TranscriptSource
import com.example.recall_ai.data.local.entity.TranscriptionStatus

/**
 * Converts all domain enums to/from their String name for Room storage.
 * Using names (not ordinals) makes the DB human-readable and safe
 * against enum reordering.
 */
class Converters {

    // ── MeetingStatus ────────────────────────────────────────────────────

    @TypeConverter
    fun fromMeetingStatus(value: MeetingStatus): String = value.name

    @TypeConverter
    fun toMeetingStatus(value: String): MeetingStatus =
        MeetingStatus.valueOf(value)

    // ── PauseReason ──────────────────────────────────────────────────────

    @TypeConverter
    fun fromPauseReason(value: PauseReason): String = value.name

    @TypeConverter
    fun toPauseReason(value: String): PauseReason =
        PauseReason.valueOf(value)

    // ── AudioSource ──────────────────────────────────────────────────────

    @TypeConverter
    fun fromAudioSource(value: AudioSource): String = value.name

    @TypeConverter
    fun toAudioSource(value: String): AudioSource =
        AudioSource.valueOf(value)

    // ── TranscriptionStatus ──────────────────────────────────────────────

    @TypeConverter
    fun fromTranscriptionStatus(value: TranscriptionStatus): String = value.name

    @TypeConverter
    fun toTranscriptionStatus(value: String): TranscriptionStatus =
        TranscriptionStatus.valueOf(value)

    // ── TranscriptSource ─────────────────────────────────────────────────

    @TypeConverter
    fun fromTranscriptSource(value: TranscriptSource): String = value.name

    @TypeConverter
    fun toTranscriptSource(value: String): TranscriptSource =
        TranscriptSource.valueOf(value)

    // ── SummaryStatus ────────────────────────────────────────────────────

    @TypeConverter
    fun fromSummaryStatus(value: SummaryStatus): String = value.name

    @TypeConverter
    fun toSummaryStatus(value: String): SummaryStatus =
        SummaryStatus.valueOf(value)
}
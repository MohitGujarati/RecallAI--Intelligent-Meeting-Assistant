package com.mohit.recall_ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reminder or to-do created by the Live AI assistant (Bob) during a voice session.
 *
 * Two flavours:
 *   ALARM — has a [triggerAtMillis]; fires a system alarm/notification at that time.
 *   TODO  — no trigger time; appears as a checklist item in the Reminders UI.
 *
 * [meetingId] is nullable: null for reminders created in a global-context session
 * (meetingId == 0L), non-null for single-meeting sessions with CASCADE delete.
 */
@Entity(
    tableName = "reminders",
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
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Null for global-context reminders (Live AI with meetingId == 0L). */
    val meetingId: Long? = null,

    val type: ReminderType,

    /** Short description — "Call John", "Wake up", "Team standup". */
    val title: String,

    /** Optional longer description or context from the conversation. */
    val description: String? = null,

    /** Epoch millis when the alarm should fire. Null for TODO-type reminders. */
    val triggerAtMillis: Long? = null,

    val status: ReminderStatus = ReminderStatus.PENDING,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ReminderType { ALARM, TODO }

enum class ReminderStatus {
    PENDING,     // alarm scheduled but not yet fired / todo not yet done
    FIRED,       // alarm went off
    DISMISSED,   // user dismissed the alarm notification
    COMPLETED,   // user marked the todo as done
    CANCELLED    // user cancelled before firing
}

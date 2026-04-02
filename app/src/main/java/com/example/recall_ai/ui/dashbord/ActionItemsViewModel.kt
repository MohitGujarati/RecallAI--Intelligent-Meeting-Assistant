package com.example.recall_ai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.Summary
import com.example.recall_ai.data.repository.RecordingRepository
import com.example.recall_ai.data.repository.SummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

/** A single action item line belonging to a meeting */
data class ActionItemEntry(
    val meetingId: Long,
    val text: String
)

/** A group of action items under one meeting */
data class MeetingActionGroup(
    val meetingId: Long,
    val meetingTitle: String,
    val meetingEmoji: String?,
    val meetingColorHex: String?,
    val items: List<ActionItemEntry>
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ActionItemsViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    /**
     * Set of action-item keys that the user has checked off during this session.
     * Keyed by "$meetingId::$itemText" for uniqueness.
     * This is in-memory only — checking items is ephemeral per session.
     */
    private val _checkedItems = MutableStateFlow<Set<String>>(emptySet())
    val checkedItems: StateFlow<Set<String>> = _checkedItems.asStateFlow()

    /**
     * All action items grouped by meeting, newest meetings first.
     */
    val actionGroups: StateFlow<List<MeetingActionGroup>> = combine(
        summaryRepository.observeAllCompletedSummaries(),
        recordingRepository.observeAllMeetings()
    ) { summaries, meetings ->
        buildGroups(summaries, meetings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun toggleItem(key: String) {
        _checkedItems.value = _checkedItems.value.toMutableSet().apply {
            if (contains(key)) remove(key) else add(key)
        }
    }

    private fun buildGroups(
        summaries: List<Summary>,
        meetings: List<Meeting>
    ): List<MeetingActionGroup> {
        val meetingMap = meetings.associateBy { it.id }

        return summaries.mapNotNull { summary ->
            val meeting = meetingMap[summary.meetingId] ?: return@mapNotNull null
            val items = splitActionItems(summary.actionItems)
            if (items.isEmpty()) return@mapNotNull null

            MeetingActionGroup(
                meetingId = meeting.id,
                meetingTitle = meeting.title,
                meetingEmoji = meeting.iconEmoji,
                meetingColorHex = meeting.iconColorHex,
                items = items.map { ActionItemEntry(meeting.id, it) }
            )
        }
    }

    private fun splitActionItems(raw: String?): List<String> =
        raw.orEmpty()
            .split("\n")
            .map { it.trimStart('•', '-', '*', ' ', '\t').trim() }
            .filter { it.isNotBlank() && it != "None identified" }

    companion object {
        fun itemKey(meetingId: Long, text: String) = "$meetingId::$text"
    }
}

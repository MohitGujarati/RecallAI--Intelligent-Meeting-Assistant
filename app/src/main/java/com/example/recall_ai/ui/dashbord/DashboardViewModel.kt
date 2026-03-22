package com.example.recall_ai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.data.local.entity.Meeting
import com.example.recall_ai.data.local.entity.MeetingStatus
import com.example.recall_ai.data.repository.RecordingRepository
import com.example.recall_ai.model.RecordingState
import com.example.recall_ai.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    /**
     * All meetings newest-first.
     * The Room Flow emits on every DB change — cards update live as
     * transcription progresses (status chips animate automatically).
     */
    val meetings: StateFlow<List<Meeting>> = repository
        .observeAllMeetings()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    /**
     * Drives the FAB state:
     *   Idle / Stopped / Error  → show Record FAB
     *   Recording / Paused      → show "In progress" indicator
     */
    val recordingState: StateFlow<RecordingState> = repository
        .recordingState
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = RecordingState.Idle
        )

    /**
     * Reactive recording-active flag.
     *
     * BUG-FIX: the old `get()` property read `recordingState.value` — a snapshot
     * that Compose never re-observed. The FAB was stuck on "Recording" because
     * reading a plain property doesn't trigger recomposition.
     *
     * Now it's a StateFlow<Boolean> derived from the live recording state,
     * collected via `collectAsStateWithLifecycle()` in the composable.
     */
    val isRecordingActive: StateFlow<Boolean> = recordingState
        .map { it is RecordingState.Recording || it is RecordingState.Paused }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // ── User intents ──────────────────────────────────────────────────────

    fun deleteMeeting(meeting: Meeting) {
        viewModelScope.launch {
            repository.deleteMeeting(meeting.id)
        }
    }

    fun updateMeetingDetails(meetingId: Long, title: String, emoji: String?, color: String?) {
        viewModelScope.launch {
            repository.updateMeetingDetails(meetingId, title, emoji, color)
        }
    }
}

// ── UI helpers ────────────────────────────────────────────────────────────────

/** Human-readable status for the meeting card chip */
fun MeetingStatus.toChipLabel(): String = when (this) {
    MeetingStatus.RECORDING           -> "Recording"
    MeetingStatus.PAUSED              -> "Paused"
    MeetingStatus.STOPPED             -> "Transcribing…"
    MeetingStatus.COMPLETED           -> "Done"
    MeetingStatus.STOPPED_LOW_STORAGE -> "Stopped — Low Storage"
    MeetingStatus.ERROR               -> "Error"
}

/** True → use green chip; false → dimmed processing chip */
fun MeetingStatus.isComplete() = this == MeetingStatus.COMPLETED
fun MeetingStatus.isError()    = this == MeetingStatus.ERROR ||
        this == MeetingStatus.STOPPED_LOW_STORAGE
fun MeetingStatus.isActive()   = this == MeetingStatus.RECORDING ||
        this == MeetingStatus.PAUSED
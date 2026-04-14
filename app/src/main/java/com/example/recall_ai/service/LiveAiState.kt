package com.example.recall_ai.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the distinct states of the Live AI voice session.
 */
sealed class LiveAiState {
    object Idle : LiveAiState()
    data class Connecting(val meetingId: Long) : LiveAiState()

    // AI is waiting for user to speak or processing input
    data class Listening(val meetingId: Long) : LiveAiState()

    // AI is actively streaming audio back to the speaker
    data class Speaking(val meetingId: Long) : LiveAiState()

    // AI is executing an action (setting reminder, etc.)
    data class PerformingAction(val meetingId: Long) : LiveAiState()

    // Session gracefully ended
    data class Stopped(val meetingId: Long) : LiveAiState()

    data class Error(val meetingId: Long, val message: String) : LiveAiState()
}

/**
 * Singleton state bridge between LiveAiService and LiveAiViewModel.
 * Completely decoupled from RecordingStateHolder to prevent state contamination.
 */
@Singleton
class LiveAiStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<LiveAiState>(LiveAiState.Idle)
    val state: StateFlow<LiveAiState> = _state.asStateFlow()

    fun emit(newState: LiveAiState) {
        _state.value = newState
    }

    val isActive: Boolean
        get() = _state.value is LiveAiState.Connecting ||
                _state.value is LiveAiState.Listening ||
                _state.value is LiveAiState.Speaking ||
                _state.value is LiveAiState.PerformingAction

    val currentMeetingId: Long?
        get() = when (val s = _state.value) {
            is LiveAiState.Connecting -> s.meetingId
            is LiveAiState.Listening -> s.meetingId
            is LiveAiState.Speaking -> s.meetingId
            is LiveAiState.PerformingAction -> s.meetingId
            is LiveAiState.Stopped -> s.meetingId
            is LiveAiState.Error -> s.meetingId
            LiveAiState.Idle -> null
        }

    fun resetIfTerminal() {
        val current = _state.value
        if (current is LiveAiState.Stopped || current is LiveAiState.Error) {
            _state.value = LiveAiState.Idle
        }
    }
}
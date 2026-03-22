package com.example.recall_ai.service

import com.example.recall_ai.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton state bridge between RecordingService and ViewModels.
 *
 * The problem: Services and ViewModels live in different scopes. The service
 * can't directly talk to a ViewModel. Solutions like LocalBroadcastManager
 * or bound services add complexity. This holder is injected into BOTH the
 * service AND the ViewModel via Hilt, giving them a shared reactive channel.
 *
 * Service writes → holder → ViewModel reads (via collectAsState in Compose)
 */
@Singleton
class RecordingStateHolder @Inject constructor() {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)

    /** ViewModels observe this. Emits on every state transition. */
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Called exclusively by RecordingService on every state transition */
    fun emit(newState: RecordingState) {
        _state.value = newState
    }

    /** Convenience — is there an active session right now? */
    val isActive: Boolean
        get() = _state.value is RecordingState.Recording ||
                _state.value is RecordingState.Paused ||
                _state.value is RecordingState.SilenceWarning

    /** Extract meetingId from whatever state we're currently in */
    val currentMeetingId: Long?
        get() = when (val s = _state.value) {
            is RecordingState.Recording      -> s.meetingId
            is RecordingState.Paused         -> s.meetingId
            is RecordingState.Stopped        -> s.meetingId
            is RecordingState.SilenceWarning -> s.meetingId
            is RecordingState.StoppedLowStorage -> s.meetingId
            is RecordingState.Error          -> s.meetingId
            RecordingState.Idle              -> null
        }

    /**
     * Reset to Idle if the current state is terminal (Stopped, StoppedLowStorage, Error).
     *
     * Because this holder is a @Singleton, its state survives after a recording
     * session ends. When the user navigates away and back to RecordingScreen,
     * the ViewModel is re-created but the holder still holds Stopped → the UI
     * maps that to Finished → the mode toggle is hidden (visible only in Idle).
     *
     * Called by RecordingViewModel.init{} so the screen always starts clean.
     */
    fun resetIfTerminal() {
        val current = _state.value
        if (current is RecordingState.Stopped ||
            current is RecordingState.StoppedLowStorage ||
            current is RecordingState.Error
        ) {
            _state.value = RecordingState.Idle
        }
    }
}
package com.example.recall_ai.ui.liveai

import android.app.Application
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.service.LiveAiService
import com.example.recall_ai.service.LiveAiState
import com.example.recall_ai.service.LiveAiStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val TAG = "LiveAiViewModel"

@HiltViewModel
class LiveAiViewModel @Inject constructor(
    private val application: Application,
    private val stateHolder: LiveAiStateHolder
) : ViewModel() {

    init {
        // Clear any terminal state so navigating here fresh shows Idle
        stateHolder.resetIfTerminal()
    }

    val uiState: StateFlow<LiveAiState> = stateHolder.state.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveAiState.Idle
    )

    fun startSession(meetingId: Long) {
        Log.i(TAG, "startSession(meetingId=$meetingId)")
        val intent = LiveAiService.startIntent(application, meetingId)
        ContextCompat.startForegroundService(application, intent)
        Log.i(TAG, "startForegroundService() called")
    }

    fun stopSession() {
        Log.i(TAG, "stopSession()")
        val intent = LiveAiService.stopIntent(application)
        application.startService(intent)
    }

    /**
     * Interrupts the AI mid-speech: stops audio output and switches to listening.
     */
    fun interruptAi() {
        Log.i(TAG, "interruptAi()")
        val intent = LiveAiService.interruptIntent(application)
        application.startService(intent)
    }
}
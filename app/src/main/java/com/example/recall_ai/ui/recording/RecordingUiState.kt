package com.example.recall_ai.ui.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recall_ai.data.local.entity.AudioSource
import com.example.recall_ai.data.local.entity.PauseReason
import com.example.recall_ai.data.repository.RecordingRepository
import com.example.recall_ai.model.RecordingState
import com.example.recall_ai.model.TranscriptionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ── UI state ─────────────────────────────────────────────────────────────────

/**
 * The single state type consumed by RecordingScreen.
 *
 * Projection of RecordingState — ViewModel translates service state into
 * display-ready values so the composable never touches domain types.
 */
sealed class RecordingUiState {

    object Idle : RecordingUiState()

    /**
     * Session is active (recording or paused).
     *
     * @param meetingId          For navigation after stopping
     * @param timerText          "MM:SS" — ready for Text() call
     * @param chunkCount         30-second segments saved so far
     * @param audioSourceIcon    Active mic identifier
     * @param isPaused           True → paused state indicator
     * @param statusLabel        Human-readable sub-state description
     * @param isWarning          True → animate status label in warning colour
     * @param liveTranscripts    The latest transcript segments in order.
     *                           Populated live as each chunk is transcribed.
     *                           Empty list = no transcripts yet (normal for first 30s).
     */
    data class Active(
        val meetingId:        Long,
        val timerText:        String,
        val chunkCount:       Int,
        val audioSourceIcon:  String,
        val isPaused:         Boolean,
        val statusLabel:      String,
        val isWarning:        Boolean        = false,
        val liveTranscripts:  List<String>  = emptyList()
    ) : RecordingUiState()

    /**
     * Recording stopped. Navigate to meeting detail.
     * @param lowStorage  True → show low-storage banner before navigating.
     */
    data class Finished(
        val meetingId:  Long,
        val lowStorage: Boolean = false
    ) : RecordingUiState()

    data class Errored(val message: String) : RecordingUiState()
}

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    /**
     * Derived UI state with live transcript segments stitched in.
     *
     * ── Why flatMapLatest instead of map ────────────────────────────────
     * We need to observe TWO flows: the recording state AND the transcripts
     * for the active meeting. The meetingId is only known after recording
     * starts, so we can't set up the transcript subscription statically.
     *
     * flatMapLatest:
     *   - When state has a meetingId → subscribe to transcript flow for that id
     *   - When state changes (new session, stopped) → cancel previous subscription
     *   - No meetingId (Idle, Error) → emit from flowOf() with empty transcripts
     *
     * combine() inside the active branch merges recording state + transcript list
     * into a single RecordingUiState emission. Both Room and RecordingStateHolder
     * emit independently, so this produces a coherent UI snapshot.
     */
    init {
        // Reset singleton state to Idle so the toggle is visible on
        // subsequent visits to RecordingScreen (singleton survives
        // NavBackStackEntry destruction but ViewModel does not).
        repository.resetRecordingStateIfTerminal()
    }

    /**
     * UI state with live transcript segments stitched in.
     *
     * BUG-FIX: the previous implementation used `flowOf(recordingState)` inside
     * combine(). That creates a single-emission cold flow — timer ticks from
     * the Service never propagated, because `flowOf` had already completed.
     *
     * Fix: use `repository.recordingState` (the hot StateFlow) directly inside
     * `combine()` so every timer tick & every new transcript both trigger a new
     * UI snapshot.
     *
     * We still wrap the outer logic in flatMapLatest keyed on `activeMeetingId`
     * so the transcript subscription switches when the meeting changes.
     */
    val uiState: StateFlow<RecordingUiState> = repository.recordingState
        .map { it.activeMeetingId() }
        .distinctUntilChanged()
        .flatMapLatest { meetingId ->
            if (meetingId != null) {
                // Active session: combine state + live transcript stream
                combine(
                    repository.recordingState,
                    repository.observeTranscripts(meetingId)
                ) { state, transcripts ->
                    val texts = transcripts.map { it.text }
                    state.toUiState(texts)
                }
            } else {
                // Idle / Stopped / Error — map the live state, no transcripts
                repository.recordingState.map { state ->
                    state.toUiState(emptyList())
                }
            }
        }
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordingUiState.Idle
        )

    // ── Transcription mode toggle ────────────────────────────────────────

    private val _transcriptionMode = MutableStateFlow(TranscriptionMode.NATIVE)
    val transcriptionMode: StateFlow<TranscriptionMode> = _transcriptionMode.asStateFlow()

    fun toggleMode() {
        _transcriptionMode.value = when (_transcriptionMode.value) {
            TranscriptionMode.AI     -> TranscriptionMode.NATIVE
            TranscriptionMode.NATIVE -> TranscriptionMode.AI
        }
    }

    // ── User intents ──────────────────────────────────────────────────────

    fun startRecording() = repository.startRecording(_transcriptionMode.value)
    fun stopRecording()  = repository.stopRecording()

    // ── State mapping ─────────────────────────────────────────────────────

    private fun RecordingState.toUiState(transcripts: List<String>): RecordingUiState =
        when (this) {

            is RecordingState.Idle -> RecordingUiState.Idle

            is RecordingState.Recording -> RecordingUiState.Active(
                meetingId       = meetingId,
                timerText       = formatElapsed(elapsedMs),
                chunkCount      = chunkCount,
                audioSourceIcon = audioSource.toIcon(),
                isPaused        = false,
                statusLabel     = "Recording (${modeLabel()})",
                liveTranscripts = transcripts
            )

            is RecordingState.SilenceWarning -> RecordingUiState.Active(
                meetingId       = meetingId,
                timerText       = formatElapsed(elapsedMs),
                chunkCount      = 0,
                audioSourceIcon = AudioSource.BUILT_IN.toIcon(),
                isPaused        = false,
                statusLabel     = "No audio detected — check mic",
                isWarning       = true,
                liveTranscripts = transcripts
            )

            is RecordingState.Paused -> RecordingUiState.Active(
                meetingId       = meetingId,
                timerText       = formatElapsed(elapsedMs),
                chunkCount      = 0,
                audioSourceIcon = AudioSource.BUILT_IN.toIcon(),
                isPaused        = true,
                statusLabel     = reason.toLabel(),
                liveTranscripts = transcripts
            )

            // Note: Stopped / StoppedLowStorage / Error do NOT include transcripts.
            // These states always use the overload below that ignores the list.
            is RecordingState.Stopped           -> RecordingUiState.Finished(meetingId)
            is RecordingState.StoppedLowStorage -> RecordingUiState.Finished(meetingId, lowStorage = true)
            is RecordingState.Error             -> RecordingUiState.Errored(message)
        }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Short label for the active transcription mode. */
    private fun modeLabel(): String = when (_transcriptionMode.value) {
        TranscriptionMode.AI     -> "AI"
        TranscriptionMode.NATIVE -> "Native"
    }

    companion object {
        fun formatElapsed(ms: Long): String {
            val totalSeconds = ms / 1_000L
            val minutes      = totalSeconds / 60
            val seconds      = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    }
}

// ── Private extensions ────────────────────────────────────────────────────────

/**
 * Returns the meetingId if this is an "active" state that we should be
 * observing transcripts for. Returns null for terminal / idle states.
 */
private fun RecordingState.activeMeetingId(): Long? = when (this) {
    is RecordingState.Recording      -> meetingId
    is RecordingState.Paused         -> meetingId
    is RecordingState.SilenceWarning -> meetingId
    else                             -> null
}

private fun AudioSource.toIcon(): String = when (this) {
    AudioSource.BUILT_IN      -> "mic"
    AudioSource.WIRED_HEADSET -> "headset"
    AudioSource.BLUETOOTH     -> "bt"
}

private fun PauseReason.toLabel(): String = when (this) {
    PauseReason.NONE             -> "Paused"
    PauseReason.PHONE_CALL       -> "Paused — phone call in progress"
    PauseReason.AUDIO_FOCUS_LOSS -> "Paused — audio focus lost"
}
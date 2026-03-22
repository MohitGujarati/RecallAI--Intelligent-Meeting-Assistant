package com.example.recall_ai.model

import com.example.recall_ai.data.local.entity.PauseReason

/**
 * Sealed hierarchy representing every possible state of the recording pipeline.
 * This is the single source of truth flowing from RecordingService → ViewModel → UI.
 *
 * State machine:
 *   Idle ──startRecording()──▶ Recording
 *   Recording ──phoneCall/focusLoss──▶ Paused
 *   Paused ──callEnded/focusGained──▶ Recording
 *   Recording/Paused ──stopRecording()──▶ Stopped
 *   Any ──unrecoverable error──▶ Error
 */
sealed class RecordingState {

    /** No active session. Default state on app launch. */
    object Idle : RecordingState()

    /**
     * Microphone is active, audio is flowing, chunks are being created.
     * @param meetingId   Room ID of the active Meeting row
     * @param elapsedMs   Total millis recorded so far (drives the UI timer)
     * @param chunkCount  How many 30s chunks have been saved (shown in notification)
     * @param audioSource Current mic source — updates on headset plug/unplug
     */
    data class Recording(
        val meetingId: Long,
        val elapsedMs: Long = 0L,
        val chunkCount: Int = 0,
        val audioSource: com.example.recall_ai.data.local.entity.AudioSource =
            com.example.recall_ai.data.local.entity.AudioSource.BUILT_IN
    ) : RecordingState()

    /**
     * Recording is halted temporarily. Mic released. Session still open.
     * @param reason  Why we paused — drives notification copy and UI label
     */
    data class Paused(
        val meetingId: Long,
        val reason: PauseReason,
        val elapsedMs: Long = 0L
    ) : RecordingState()

    /**
     * Session ended (user stop, low storage, or error-induced stop).
     * Chunks may still be transcribing. Meeting row still in DB.
     */
    data class Stopped(val meetingId: Long) : RecordingState()

    /**
     * Silence has been detected for ≥ 10 seconds.
     * Treated as a warning, NOT a stop. Recording continues.
     * UI/notification shows "No audio detected - Check microphone".
     */
    data class SilenceWarning(
        val meetingId: Long,
        val elapsedMs: Long
    ) : RecordingState()

    /**
     * Storage fell below the safety threshold mid-session.
     * Recording stopped gracefully. All saved chunks preserved.
     */
    data class StoppedLowStorage(val meetingId: Long) : RecordingState()

    /** Unrecoverable error. Session terminated. */
    data class Error(val message: String, val meetingId: Long? = null) : RecordingState()
}
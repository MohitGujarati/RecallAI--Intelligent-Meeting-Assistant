package com.example.recall_ai.service.interruption

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

private const val TAG = "AudioFocusHandler"

/**
 * Manages AudioFocus acquisition and loss for the recording session.
 *
 * ── Why this was causing constant pauses (Bug #1 root cause) ─────────────
 *
 *   BEFORE (broken):
 *     .setUsage(USAGE_MEDIA)           ← media = playback; wrong for recording
 *     .setWillPauseWhenDucked(true)    ← treats EVERY duck as a full pause
 *     pausing on CAN_DUCK              ← fires for notification sounds, music, keyboard
 *
 *   Every notification sound, keyboard click, or background music player
 *   requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK. The old code paused recording
 *   on ALL of these because setWillPauseWhenDucked(true) converts duck requests
 *   into AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, and the listener paused on that.
 *
 * ── AFTER (fixed) ─────────────────────────────────────────────────────────
 *
 *   USAGE_VOICE_COMMUNICATION  → tells the OS this is a voice session.
 *                                 The OS deprioritises music/duck requests against it.
 *
 *   setWillPauseWhenDucked NOT set (defaults to false)
 *                              → duck events (notifications, music players) are
 *                                 completely ignored. We're recording — volume
 *                                 ducking is meaningless for the mic.
 *
 *   Only pause on AUDIOFOCUS_LOSS and AUDIOFOCUS_LOSS_TRANSIENT:
 *     AUDIOFOCUS_LOSS             → another app permanently owns audio (e.g. starts a
 *                                   call after PhoneCallHandler's receiver fires)
 *     AUDIOFOCUS_LOSS_TRANSIENT   → voice assistant briefly needs exclusive mic access
 *     AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK → music/video/notification duck → IGNORED
 *
 * ── Phone calls ────────────────────────────────────────────────────────────
 * Phone-call pausing is handled independently by PhoneCallHandler via
 * TelephonyManager state. AudioFocusHandler is the fallback for non-call
 * focus losses (voice assistants, etc.).
 */
class AudioFocusHandler @Inject constructor() {

    sealed class AudioFocusEvent {
        object FocusLost   : AudioFocusEvent()
        object FocusGained : AudioFocusEvent()
    }

    private val _events = MutableSharedFlow<AudioFocusEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AudioFocusEvent> = _events.asSharedFlow()

    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    /** True only when we paused because of audio focus loss (not a phone call). */
    var focusLostActive = false
        private set

    fun requestFocus(context: Context): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // VOICE_COMMUNICATION = correct usage for microphone / voice recording.
        // This signals to the OS that we are capturing voice, so it gives us
        // higher priority over media players and duck-only requests.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            Log.d(TAG, "AudioFocus change: $change")
            when (change) {
                // Permanent loss — another app fully owns audio (e.g. navigation, voice call)
                AudioManager.AUDIOFOCUS_LOSS,
                    // Transient exclusive loss — voice assistant briefly needs the mic
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (!focusLostActive) {
                        focusLostActive = true
                        _events.tryEmit(AudioFocusEvent.FocusLost)
                        Log.i(TAG, "Focus lost ($change) → pause recording")
                    }
                }

                // CAN_DUCK = music/notification wants to lower its volume alongside us.
                // Ducking is a VOLUME concept — it's irrelevant for a microphone.
                // Deliberately NOT pausing here is the key fix for Bug #1.
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Duck request received — ignoring (mic is unaffected by ducking)")
                    // intentionally empty
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (focusLostActive) {
                        focusLostActive = false
                        _events.tryEmit(AudioFocusEvent.FocusGained)
                        Log.i(TAG, "Focus gained → resume recording")
                    }
                }
            }
        }

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(listener)
            .setAcceptsDelayedFocusGain(false)
            // DO NOT call setWillPauseWhenDucked(true).
            // Default is false. Setting it true converts duck events into
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK callbacks — the old code then
            // paused on those, which is why every notification paused recording.
            .build()

        val result = audioManager!!.requestAudioFocus(focusRequest!!)
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Focus request: ${if (granted) "GRANTED" else "DELAYED/FAILED"}")
        return granted
    }

    fun abandonFocus() {
        focusRequest?.let {
            audioManager?.abandonAudioFocusRequest(it)
            Log.d(TAG, "Audio focus abandoned")
        }
        focusRequest    = null
        focusLostActive = false
    }
}
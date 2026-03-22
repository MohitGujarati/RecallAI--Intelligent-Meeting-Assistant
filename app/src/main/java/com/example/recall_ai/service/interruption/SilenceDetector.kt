package com.example.recall_ai.service.interruption

import android.util.Log
import com.example.recall_ai.service.audio.WavEncoder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import kotlin.math.sqrt

private const val TAG = "SilenceDetector"

/**
 * Processes raw PCM buffers and emits silence/audio-resumed events.
 *
 * ── Algorithm ─────────────────────────────────────────────────────────
 * Each 0.1-second PCM buffer (3,200 bytes = 1,600 samples) is evaluated:
 *   RMS = sqrt( sum(sample²) / count )
 *
 * RMS < [rmsThreshold] → silent buffer → increment [consecutiveSilentMs]
 * RMS ≥ [rmsThreshold] → audio detected → reset counter
 *
 * After [silenceWindowMs] of consecutive silence → emit [SilenceEvent.SilenceDetected]
 * When audio returns after a silence alert  → emit [SilenceEvent.AudioResumed]
 *
 * ── Why extract from AudioRecorder? ───────────────────────────────────
 * Silence detection has its own state, own thresholds, and its own events.
 * Embedding it in AudioRecorder violates SRP. As a separate class it can
 * be unit-tested with synthetic PCM data without starting AudioRecord.
 *
 * ── RMS threshold calibration ────────────────────────────────────────
 * Default = 100 / 32,767 ≈ 0.3% of max amplitude.
 * Typical room noise is 400–1,000 RMS. Speech is 2,000–15,000 RMS.
 * 100 is intentionally low — it catches only completely dead microphones
 * or recording in a near-perfect anechoic environment.
 * Raise to 300–500 if you want to treat whispering as "silence".
 */
class SilenceDetector @Inject constructor() {

    sealed class SilenceEvent {
        /**
         * Fired ONCE after [silenceWindowMs] consecutive milliseconds of silence.
         * Not re-fired until audio resumes and silence begins again.
         * @param silentForMs How long we've been silent (always ≥ silenceWindowMs)
         */
        data class SilenceDetected(val silentForMs: Long) : SilenceEvent()

        /**
         * Fired when audio resumes after a [SilenceDetected] was emitted.
         * This lets the notification clear the "No audio detected" warning.
         */
        object AudioResumed : SilenceEvent()
    }

    private val _events = MutableSharedFlow<SilenceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SilenceEvent> = _events.asSharedFlow()

    // ── Configuration ────────────────────────────────────────────────────

    /** RMS value (0–32,767) below which a buffer is considered silent */
    var rmsThreshold: Double = 100.0

    /** How many milliseconds of consecutive silence trigger [SilenceEvent.SilenceDetected] */
    var silenceWindowMs: Long = 10_000L   // 10 seconds per spec

    // ── State ────────────────────────────────────────────────────────────

    private var consecutiveSilentMs: Long = 0L
    private var alertEmitted = false
    private var totalBuffersProcessed = 0L

    // Each PCM buffer from AudioRecorder is READ_BUFFER_BYTES (3,200 bytes) = 0.1s
    private val bufferDurationMs: Long = (WavEncoder.READ_BUFFER_BYTES.toLong() * 1_000L) /
            WavEncoder.BYTES_PER_SECOND   // = 100ms

    /**
     * Feed a raw PCM buffer into the detector.
     * Must be called from a coroutine (tryEmit is safe from any thread).
     *
     * @param pcmBuffer 16-bit little-endian PCM bytes
     * @param length    number of valid bytes in [pcmBuffer]
     */
    fun processBuffer(pcmBuffer: ByteArray, length: Int) {
        totalBuffersProcessed++
        val rms = computeRms(pcmBuffer, length)

        when {
            rms < rmsThreshold -> {
                consecutiveSilentMs += bufferDurationMs

                if (consecutiveSilentMs >= silenceWindowMs && !alertEmitted) {
                    alertEmitted = true
                    _events.tryEmit(SilenceEvent.SilenceDetected(consecutiveSilentMs))
                    Log.w(TAG, "Silence detected for ${consecutiveSilentMs}ms (RMS=$rms)")
                }
            }
            else -> {
                if (alertEmitted) {
                    // Audio came back — notify and reset
                    _events.tryEmit(SilenceEvent.AudioResumed)
                    Log.d(TAG, "Audio resumed after ${consecutiveSilentMs}ms silence (RMS=$rms)")
                }
                consecutiveSilentMs = 0L
                alertEmitted = false
            }
        }
    }

    /** Resets all state. Call when a recording session ends or a new one begins. */
    fun reset() {
        consecutiveSilentMs = 0L
        alertEmitted = false
        totalBuffersProcessed = 0L
        Log.d(TAG, "Reset")
    }

    // ── Inspection (for tests + debug) ───────────────────────────────────

    /** Current consecutive silence window. Exposed for testing. */
    val currentSilenceMs: Long get() = consecutiveSilentMs

    /** Whether a silence alert is currently active. */
    val isSilenceAlertActive: Boolean get() = alertEmitted

    /** Total PCM buffers processed since last [reset]. */
    val buffersProcessed: Long get() = totalBuffersProcessed

    // ── Core math ────────────────────────────────────────────────────────

    /**
     * Computes RMS of a 16-bit little-endian PCM buffer.
     * Returns a value in [0.0, 32767.0].
     *
     * Little-endian reconstruction:
     *   sample = (high_byte << 8) | low_byte
     *   signed = sample >= 0x8000 ? sample - 0x10000 : sample
     */
    fun computeRms(buffer: ByteArray, length: Int): Double {
        if (length < 2) return 0.0

        var sumOfSquares = 0.0
        var sampleCount  = 0

        var i = 0
        while (i + 1 < length) {
            val raw    = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signed = if (raw >= 0x8000) raw - 0x10000 else raw
            sumOfSquares += signed.toDouble() * signed.toDouble()
            sampleCount++
            i += 2
        }

        return if (sampleCount > 0) sqrt(sumOfSquares / sampleCount) else 0.0
    }
}
package com.example.recall_ai.service.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.recall_ai.service.audio.WavEncoder.READ_BUFFER_BYTES
import com.example.recall_ai.service.audio.WavEncoder.SILENCE_RMS_THRESHOLD
import com.example.recall_ai.service.audio.WavEncoder.SILENCE_THRESHOLD_READS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.math.sqrt

private const val TAG = "AudioRecorder"

/**
 * Wraps Android's [AudioRecord] and exposes a cold [Flow] of raw PCM buffers.
 *
 * Configuration (matches Whisper / Gemini ASR requirements):
 *   Sample rate : 16 000 Hz
 *   Channels    : Mono
 *   Encoding    : PCM 16-bit
 *   Read buffer : 3 200 bytes = 0.1 s  (responsive silence detection)
 *
 * Usage:
 *   Collect [pcmFlow] inside a coroutine scope.
 *   Cancel the scope to stop recording cleanly.
 *   The flow handles AudioRecord lifecycle internally.
 *
 * Silence detection:
 *   Computes RMS of each 0.1s buffer.
 *   Emits [AudioEvent.SilenceDetected] after 10 consecutive silent seconds.
 *   Resets the counter as soon as audio is detected.
 */
class AudioRecorder @Inject constructor() {

    private val sampleRate  = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding    = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        .coerceAtLeast(READ_BUFFER_BYTES * 2)  // always at least 2 read-buffers large

    sealed class AudioEvent {
        /** Raw PCM bytes; always READ_BUFFER_BYTES in length */
        data class PcmData(val bytes: ByteArray, val timestampMs: Long) : AudioEvent()

        /**
         * Fired once when 10 consecutive seconds of silence detected.
         * NOT fired again until audio resumes and then silence starts again.
         */
        object SilenceDetected : AudioEvent()

        /** Audio resumed after a silence warning */
        object AudioResumed : AudioEvent()
    }

    /**
     * Cold flow — starts AudioRecord when collected, stops on cancellation.
     * Must be collected on a coroutine backed by Dispatchers.IO.
     *
     * Emits [AudioEvent.PcmData] continuously, interleaved with
     * [AudioEvent.SilenceDetected] / [AudioEvent.AudioResumed] warnings.
     */
    @SuppressLint("MissingPermission")  // RECORD_AUDIO permission checked before calling
    fun pcmFlow(): Flow<AudioEvent> = flow {

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            minBufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize. Check RECORD_AUDIO permission.")
        }

        audioRecord.startRecording()
        Log.d(TAG, "AudioRecord started (bufferSize=$minBufferSize)")

        try {
            val readBuffer   = ByteArray(READ_BUFFER_BYTES)
            var silentReads  = 0
            var silenceAlerted = false

            while (currentCoroutineContext().isActive) {
                val bytesRead = audioRecord.read(readBuffer, 0, READ_BUFFER_BYTES)

                when {
                    bytesRead > 0 -> {
                        val rms = computeRms(readBuffer, bytesRead)

                        if (rms < SILENCE_RMS_THRESHOLD) {
                            silentReads++
                            if (silentReads >= SILENCE_THRESHOLD_READS && !silenceAlerted) {
                                silenceAlerted = true
                                emit(AudioEvent.SilenceDetected)
                                Log.w(TAG, "Silence detected for 10s (RMS=$rms)")
                            }
                        } else {
                            if (silenceAlerted) {
                                // Audio came back — reset and notify
                                emit(AudioEvent.AudioResumed)
                                Log.d(TAG, "Audio resumed after silence")
                            }
                            silentReads    = 0
                            silenceAlerted = false
                        }

                        // Emit a fresh copy of the buffer (never share mutable state in flows)
                        emit(AudioEvent.PcmData(readBuffer.copyOf(bytesRead), System.currentTimeMillis()))
                    }

                    bytesRead == AudioRecord.ERROR_INVALID_OPERATION ->
                        Log.e(TAG, "Read error: ERROR_INVALID_OPERATION")

                    bytesRead == AudioRecord.ERROR_BAD_VALUE ->
                        Log.e(TAG, "Read error: ERROR_BAD_VALUE")

                    else -> Log.w(TAG, "Unexpected read result: $bytesRead")
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
            Log.d(TAG, "AudioRecord stopped and released")
        }

    }.flowOn(Dispatchers.IO)  // blocking read() must be off the main thread

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Computes RMS amplitude of a PCM 16-bit buffer.
     * Returns a value in [0, 32767].
     *
     * Each sample is stored as two little-endian bytes;
     * we reconstruct the signed short before squaring.
     */
    private fun computeRms(buffer: ByteArray, length: Int): Double {
        var sumSquares = 0.0
        var sampleCount = 0

        var i = 0
        while (i + 1 < length) {
            // PCM_16BIT is little-endian: low byte first, high byte second
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val signed = if (sample >= 0x8000) sample - 0x10000 else sample
            sumSquares += signed.toDouble() * signed.toDouble()
            sampleCount++
            i += 2
        }

        return if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0
    }
}
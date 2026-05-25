package com.mohit.recall_ai.service.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Handles real-time audio playback for the Gemini Live AI feature.
 *
 * IMPORTANT: Gemini Live audio output is 24000Hz (24kHz), 16-bit PCM, Mono.
 * This is different from our input recording rate (16kHz).
 *
 * Optimized for low-latency streaming:
 *  - Uses 4x minimum buffer to prevent underruns (choppy audio)
 *  - Uses USAGE_MEDIA to play through loudspeaker
 *  - Uses LOW_LATENCY performance mode for minimal playback delay
 */
class AudioTrackManager {

    private var audioTrack: AudioTrack? = null

    companion object {
        private const val TAG = "AudioTrackManager"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        /** Multiplier over minBufferSize — prevents underruns for streaming audio */
        private const val BUFFER_MULTIPLIER = 4
    }

    fun start() {
        if (audioTrack != null) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        // Larger buffer = fewer underruns = smoother playback
        val bufferSize = minBufferSize * BUFFER_MULTIPLIER

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioTrack?.play()
        Log.d(TAG, "AudioTrack initialized: ${SAMPLE_RATE}Hz, buffer=${bufferSize}B (${BUFFER_MULTIPLIER}x min), LOW_LATENCY mode")
    }

    /**
     * Writes incoming PCM bytes from the WebSocket directly to the audio hardware.
     * This call blocks until all bytes are written — callers should run on a
     * dedicated thread (not Dispatchers.IO shared pool).
     */
    fun write(pcmData: ByteArray) {
        val track = audioTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
        var offset = 0
        while (offset < pcmData.size) {
            val written = track.write(pcmData, offset, pcmData.size - offset)
            if (written > 0) {
                offset += written
            } else {
                Log.e(TAG, "Error writing to AudioTrack: code $written")
                break
            }
        }
    }

    /**
     * Called when the user interrupts the AI (barge-in).
     * Instantly flushes unplayed audio bytes to prevent the AI from "talking over" the user.
     */
    fun flush() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        Log.d(TAG, "AudioTrack flushed (User Barge-in)")
    }

    fun stopAndRelease() {
        try {
            audioTrack?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        } finally {
            audioTrack?.release()
            audioTrack = null
            Log.d(TAG, "AudioTrack released")
        }
    }
}
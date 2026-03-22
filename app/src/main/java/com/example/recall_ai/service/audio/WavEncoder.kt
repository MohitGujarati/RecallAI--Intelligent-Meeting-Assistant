package com.example.recall_ai.service.audio

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes raw 16-bit PCM into a valid WAV file.
 *
 * WAV layout:
 *   [RIFF header 12 bytes]
 *   [fmt  chunk  24 bytes]
 *   [data chunk  8 bytes + pcmData]
 *
 * Whisper API and Gemini both accept WAV natively, making this
 * preferable to AAC/M4A which would require MediaCodec/MediaMuxer.
 */
object WavEncoder {

    private const val SAMPLE_RATE   = 16_000   // Hz  — optimal for ASR
    private const val CHANNELS      = 1         // Mono
    private const val BITS_PER_SAMPLE = 16      // PCM_16BIT
    private const val BYTE_RATE     = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) // 32000

    /**
     * Writes a complete WAV file to [outputStream].
     * @param pcmData Raw PCM bytes captured by AudioRecord (16-bit, mono, 16 kHz)
     */
    fun writeTo(outputStream: OutputStream, pcmData: ByteArray) {
        val dataSize   = pcmData.size
        val totalSize  = 36 + dataSize   // total RIFF chunk size = 44 header - 8 + data

        outputStream.write(buildHeader(dataSize, totalSize))
        outputStream.write(pcmData)
        outputStream.flush()
    }

    /**
     * Builds a 44-byte WAV header for the given PCM data size.
     * All multi-byte fields are little-endian per the WAV spec.
     */
    fun buildHeader(dataSize: Int, riffSize: Int): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buf.put("RIFF".toByteArray())           // ChunkID
        buf.putInt(riffSize)                     // ChunkSize
        buf.put("WAVE".toByteArray())            // Format

        // fmt sub-chunk
        buf.put("fmt ".toByteArray())            // Subchunk1ID
        buf.putInt(16)                           // Subchunk1Size (16 for PCM)
        buf.putShort(1)                          // AudioFormat: PCM = 1
        buf.putShort(CHANNELS.toShort())
        buf.putInt(SAMPLE_RATE)
        buf.putInt(BYTE_RATE)
        buf.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // BlockAlign
        buf.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        buf.put("data".toByteArray())            // Subchunk2ID
        buf.putInt(dataSize)                     // Subchunk2Size

        return buf.array()
    }

    // ── Constants exposed for callers ────────────────────────────────────

    /** Bytes captured per second with our AudioRecord config */
    const val BYTES_PER_SECOND = BYTE_RATE       // 32_000

    /** Total bytes in a 30-second WAV chunk (header not included) */
    const val CHUNK_PCM_BYTES = BYTES_PER_SECOND * 30   // 960_000

    /** PCM bytes in the 2-second overlap window */
    const val OVERLAP_PCM_BYTES = BYTES_PER_SECOND * 2  // 64_000

    /**
     * How many bytes of NEW audio to accumulate before triggering a chunk save.
     * First chunk: CHUNK_PCM_BYTES (no overlap prefix yet)
     * Subsequent:  CHUNK_PCM_BYTES - OVERLAP_PCM_BYTES (we'll prepend 2s overlap)
     */
    const val CHUNK_NEW_DATA_BYTES = CHUNK_PCM_BYTES - OVERLAP_PCM_BYTES // 896_000

    /** Read buffer size: 0.1 second of audio — small enough for responsive silence detection */
    const val READ_BUFFER_BYTES = BYTES_PER_SECOND / 10   // 3_200

    /** Silence detection: 10 seconds of consecutive silent reads */
    const val SILENCE_THRESHOLD_READS = (BYTES_PER_SECOND / READ_BUFFER_BYTES) * 10   // 100 reads

    /** RMS value below which a buffer is considered silent (0–32767 scale) */
    const val SILENCE_RMS_THRESHOLD = 100.0
}
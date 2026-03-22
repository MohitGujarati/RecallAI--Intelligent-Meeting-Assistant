package com.example.recall_ai.service.audio

import android.content.Context
import android.util.Log
import com.example.recall_ai.data.local.dao.AudioChunkDao
import com.example.recall_ai.data.local.entity.AudioChunk
import com.example.recall_ai.data.local.entity.TranscriptionStatus
import com.example.recall_ai.service.audio.AudioRecorder.AudioEvent
import com.example.recall_ai.service.audio.WavEncoder.CHUNK_NEW_DATA_BYTES
import com.example.recall_ai.service.audio.WavEncoder.CHUNK_PCM_BYTES
import com.example.recall_ai.service.audio.WavEncoder.OVERLAP_PCM_BYTES
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

private const val TAG = "ChunkManager"

/**
 * Transforms a continuous stream of raw PCM [AudioEvent]s into
 * persisted 30-second WAV files with a 2-second overlap between chunks.
 *
 * ── Overlap mechanics ────────────────────────────────────────────────────
 *
 *   Chunk 0 : [0s ─────────────────── 30s]            (30s total)
 *   Chunk 1 :       [28s ─────────────────── 58s]     (2s overlap + 28s new = 30s)
 *   Chunk 2 :             [56s ─────────────────── 86s]
 *
 *   Implementation:
 *     • First chunk: accumulate 30s of PCM → save → keep last 2s as overlapBuffer
 *     • Subsequent: accumulate 28s new PCM → prepend overlapBuffer → save (= 30s total)
 *       → keep last 2s of NEW data as next overlapBuffer
 *
 * ── Output ───────────────────────────────────────────────────────────────
 *
 *   Files     : filesDir/recordings/{meetingId}/chunk_{index}.wav
 *   Room rows : AudioChunk inserted with PENDING status immediately after save
 *   Flow      : emits SavedChunk for each completed chunk (caller enqueues worker)
 */
class ChunkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioChunkDao: AudioChunkDao
) {

    data class SavedChunk(
        val chunkId: Long,
        val chunkIndex: Int,
        val filePath: String,
        val meetingId: Long
    )

    /**
     * Collects [audioFlow] and emits a [SavedChunk] each time a 30-second
     * WAV file is saved and its Room row inserted.
     *
     * @param audioFlow  Flow from AudioRecorder (caller manages start/stop)
     * @param meetingId  Room Meeting.id — used for directory path and FK
     * @param startTime  Epoch millis when recording started (for chunk timestamps)
     */
    fun processAudioStream(
        audioFlow: Flow<AudioEvent>,
        meetingId: Long,
        startTime: Long
    ): Flow<SavedChunk> = channelFlow {

        val outputDir = getOrCreateChunkDir(meetingId)

        // Accumulator for PCM bytes in the current chunk window
        val accumulator = ArrayList<ByteArray>(32)
        var accumulatedBytes = 0

        // Holds the last 2s of PCM from the previous chunk (null before first chunk saved)
        var overlapBuffer: ByteArray? = null

        var chunkIndex  = 0
        var isFirstChunk = true

        // Threshold: how many NEW bytes before we save a chunk
        fun targetBytes() = if (isFirstChunk) CHUNK_PCM_BYTES else CHUNK_NEW_DATA_BYTES

        audioFlow.collect { event ->
            if (event !is AudioEvent.PcmData) return@collect   // skip silence events

            accumulator.add(event.bytes)
            accumulatedBytes += event.bytes.size

            if (accumulatedBytes >= targetBytes()) {

                // 1. Flatten accumulated buffers into one contiguous array
                val newPcm = flattenBuffers(accumulator, accumulatedBytes)
                accumulator.clear()
                accumulatedBytes = 0

                // 2. Build final chunk data: [overlapBuffer?] + newPcm (trimmed to targetBytes)
                val chunkPcm: ByteArray = if (isFirstChunk || overlapBuffer == null) {
                    // Chunk 0: exactly CHUNK_PCM_BYTES of audio
                    newPcm.copyOf(CHUNK_PCM_BYTES)
                } else {
                    // Chunks 1+: 2s overlap prefix + 28s new = 30s total
                    val trimmedNew = newPcm.copyOf(CHUNK_NEW_DATA_BYTES)
                    overlapBuffer!! + trimmedNew   // ByteArray concatenation
                }

                // 3. Keep last 2s of NEW data as overlap for next chunk
                overlapBuffer = newPcm.copyOfRange(
                    (newPcm.size - OVERLAP_PCM_BYTES).coerceAtLeast(0),
                    newPcm.size
                )

                // 4. Save WAV to disk
                val chunkStartTime = startTime + chunkIndex.toLong() *
                        (WavEncoder.BYTES_PER_SECOND * 28).toLong() // 28s stride
                val filePath = saveChunkToDisk(outputDir, chunkIndex, chunkPcm)

                // 5. Insert Room row
                val chunkEntity = AudioChunk(
                    meetingId             = meetingId,
                    chunkIndex            = chunkIndex,
                    filePath              = filePath,
                    startTime             = chunkStartTime,
                    durationMs            = 30_000L,
                    overlapMs             = if (isFirstChunk) 0L else WavEncoder.BYTES_PER_SECOND.toLong() * 2,
                    transcriptionStatus   = TranscriptionStatus.PENDING,
                    fileSizeBytes         = File(filePath).length()
                )
                val chunkId = audioChunkDao.insert(chunkEntity)
                Log.d(TAG, "Saved chunk $chunkIndex → $filePath (id=$chunkId)")

                // 6. Notify caller
                send(SavedChunk(chunkId, chunkIndex, filePath, meetingId))

                // 7. Handle any leftover bytes from this read cycle
                val overflowBytes = newPcm.size - targetBytes()
                if (overflowBytes > 0) {
                    val overflow = newPcm.copyOfRange(targetBytes(), newPcm.size)
                    accumulator.add(overflow)
                    accumulatedBytes += overflow.size
                }

                chunkIndex++
                isFirstChunk = false
            }
        }

        // Session ended with a partial chunk (< 30s but > 0 bytes)
        if (accumulatedBytes > 0) {
            val newPcm = flattenBuffers(accumulator, accumulatedBytes)
            val chunkPcm = if (isFirstChunk || overlapBuffer == null) {
                newPcm
            } else {
                overlapBuffer!! + newPcm
            }
            val filePath = saveChunkToDisk(outputDir, chunkIndex, chunkPcm)
            val entity = AudioChunk(
                meetingId           = meetingId,
                chunkIndex          = chunkIndex,
                filePath            = filePath,
                startTime           = startTime + chunkIndex * 28_000L,
                durationMs          = (accumulatedBytes / WavEncoder.BYTES_PER_SECOND.toLong()) * 1000L,
                overlapMs           = if (isFirstChunk) 0L else 2_000L,
                transcriptionStatus = TranscriptionStatus.PENDING,
                fileSizeBytes       = File(filePath).length()
            )
            val chunkId = audioChunkDao.insert(entity)
            Log.d(TAG, "Saved final partial chunk $chunkIndex → $filePath (id=$chunkId)")
            send(SavedChunk(chunkId, chunkIndex, filePath, meetingId))
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Encodes [pcmData] as a WAV file and writes it to disk.
     * @return absolute file path of the saved WAV
     */
    private fun saveChunkToDisk(dir: File, chunkIndex: Int, pcmData: ByteArray): String {
        val file = File(dir, "chunk_${chunkIndex.toString().padStart(4, '0')}.wav")
        BufferedOutputStream(FileOutputStream(file)).use { out ->
            WavEncoder.writeTo(out, pcmData)
        }
        Log.d(TAG, "WAV written: ${file.absolutePath} (${file.length()} bytes)")
        return file.absolutePath
    }

    private fun getOrCreateChunkDir(meetingId: Long): File {
        val dir = File(context.filesDir, "recordings/$meetingId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Concatenates a list of byte arrays into one contiguous array.
     * More efficient than repeated ByteArray + ByteArray calls.
     */
    private fun flattenBuffers(buffers: List<ByteArray>, totalBytes: Int): ByteArray {
        val result = ByteArray(totalBytes)
        var offset = 0
        for (buf in buffers) {
            buf.copyInto(result, offset)
            offset += buf.size
        }
        return result
    }

    // ── Cleanup ──────────────────────────────────────────────────────────

    /** Deletes all WAV files for a meeting (call after transcription is complete) */
    fun deleteChunkFiles(meetingId: Long) {
        val dir = File(context.filesDir, "recordings/$meetingId")
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "Deleted chunk files for meeting $meetingId")
        }
    }

    /** Returns available storage in bytes — used for low-storage check */
    fun getAvailableStorageBytes(): Long =
        context.filesDir.freeSpace
}
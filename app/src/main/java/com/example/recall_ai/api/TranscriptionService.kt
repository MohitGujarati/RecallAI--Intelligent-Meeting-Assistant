package com.example.recall_ai.data.remote.api

import com.example.recall_ai.data.remote.dto.TranscriptionResult
import java.io.File

/**
 * Provider-agnostic contract for transcription backends.
 *
 * Implementations: [WhisperTranscriptionService], [GeminiTranscriptionService],
 * [MockTranscriptionService].
 *
 * Hilt selects the active implementation at compile time via [TranscriptionModule].
 * To switch providers, change the binding in that module — zero code changes
 * needed in the Worker or Repository.
 */
interface TranscriptionService {

    /**
     * Transcribes a single WAV audio file.
     *
     * This is a suspend function — implementations perform network I/O.
     * It must NOT throw. All failures must be returned as
     * [TranscriptionResult.RetryableError] or [TranscriptionResult.PermanentError].
     *
     * @param audioFile  The WAV file on local storage. Always exists when called
     *                   (Worker verifies this before dispatching).
     * @param language   BCP-47 hint (e.g. "en"). Null = auto-detect.
     * @param prompt     Optional preceding context text to improve accuracy
     *                   at chunk boundaries. Pass the last sentence of the
     *                   previous chunk's transcript.
     */
    suspend fun transcribe(
        audioFile: File,
        language: String? = null,
        prompt: String? = null
    ): TranscriptionResult
}
package com.example.recall_ai.data.remote.api

import com.example.recall_ai.data.remote.dto.TranscriptionResult
import java.io.File

/**
 * Provider-agnostic transcription contract.
 *
 * Implementations live behind this interface:
 *   WhisperTranscriptionService  → OpenAI Whisper (production)
 *   MockTranscriptionService     → deterministic fake (dev / CI)
 *
 * Hilt binds the active implementation in NetworkModule.
 * TranscriptionWorker and TranscriptionRepository only see this interface.
 *
 * Contract:
 *   • Must be a suspend function — implementations do network I/O.
 *   • Must NOT throw. All error paths return TranscriptionResult subclasses.
 *   • The audio file is guaranteed to exist when called (Repository verifies).
 *
 * @param audioFile  WAV file on local storage.
 * @param language   BCP-47 hint (e.g. "en"). Null = auto-detect.
 * @param prompt     Last sentence of previous chunk — improves boundary accuracy.
 */
interface TranscriptionClient {
    suspend fun transcribe(
        audioFile: File,
        language: String? = null,
        prompt:   String? = null
    ): TranscriptionResult
}

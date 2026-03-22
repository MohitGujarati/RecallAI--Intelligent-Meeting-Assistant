package com.example.recall_ai.model

/**
 * Selects which transcription pipeline the recording session uses.
 *
 *   AI     → AudioRecord + ChunkManager + Gemini/Whisper API
 *   NATIVE → Android SpeechRecognizer (ContinuousRecognitionManager)
 *
 * The two are mutually exclusive — SpeechRecognizer and AudioRecord
 * both hold an exclusive mic lock and cannot run simultaneously.
 */
enum class TranscriptionMode {
    /** Gemini / Whisper cloud transcription via 30-s WAV chunks. */
    AI,

    /** On-device Android SpeechRecognizer — zero API cost, real-time. */
    NATIVE
}

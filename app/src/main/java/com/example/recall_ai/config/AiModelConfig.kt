package com.example.recall_ai.config

/**
 * Centralized configuration for AI Model identifiers used across the app.
 * Change these values to update the models used by the Gemini and Whisper services.
 */
object AiModelConfig {
    /** Model used for transcribing audio chunks (e.g., gemini-1.5-flash) */
    const val GEMINI_TRANSCRIPTION_MODEL = "gemini-3-flash-preview"

    /** Model used for generating the final meeting summary */
    const val GEMINI_SUMMARY_MODEL       = "gemini-3-flash-preview"

    /** Model used for the Q&A chat feature against the transcript */
    const val GEMINI_CHAT_MODEL          = "gemini-3-flash-preview"

    /** Model used when Whisper fallback is enabled */
    const val WHISPER_MODEL              = "whisper-1"

    /** Base URL for all Gemini model endpoints */
    const val GEMINI_BASE_URL            = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Base URL for Whisper transcription endpoints */
    const val WHISPER_BASE_URL           = "https://api.openai.com/"
}

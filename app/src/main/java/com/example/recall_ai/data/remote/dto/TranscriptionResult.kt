package com.example.recall_ai.data.remote.dto

import com.example.recall_ai.data.local.entity.TranscriptSource

/**
 * Domain-level result returned by every TranscriptionService implementation.
 *
 * Three-way split is intentional:
 *
 *   Success         → text came back, write it to Room, delete the WAV
 *   RetryableError  → transient problem; WorkManager backoff will try again
 *   PermanentError  → bad file, bad key, unsupported format; retrying wastes
 *                     API credits and will keep failing — mark FAILED now
 *
 * Using Result<T> would conflate the last two. The Worker and Repository
 * never see a Whisper or Gemini class — only this type.
 */
sealed class TranscriptionResult {

    data class Success(
        val text: String,
        val confidence: Float?         = null,   // 0.0–1.0; null if API doesn't provide
        val detectedLanguage: String?  = null,   // BCP-47, e.g. "en"
        val source: TranscriptSource   = TranscriptSource.WHISPER
    ) : TranscriptionResult()

    /** Network timeout, 429 rate-limit, 5xx server errors, DNS failure */
    data class RetryableError(
        val message: String,
        val cause: Throwable? = null
    ) : TranscriptionResult()

    /** 400 Bad Request (corrupt WAV), 401 (bad API key), 413 (file too large) */
    data class PermanentError(
        val message: String,
        val httpCode: Int? = null
    ) : TranscriptionResult()
}

// ─────────────────────────────────────────────────────────────────────────────
// Whisper API DTOs
// POST https://api.openai.com/v1/audio/transcriptions  (response_format=verbose_json)
// ─────────────────────────────────────────────────────────────────────────────

data class WhisperResponse(
    val text: String,
    val language: String?              = null,
    val duration: Float?               = null,
    val segments: List<WhisperSegment>? = null
)

data class WhisperSegment(
    val id: Int,
    val start: Float,
    val end: Float,
    val text: String,
    /** Log-probability: typically –1.0 to 0.0. We convert → confidence via exp(). */
    val avg_logprob: Float?            = null
)
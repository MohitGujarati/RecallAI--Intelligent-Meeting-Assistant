package com.example.recall_ai.data.remote.dto

// ─────────────────────────────────────────────────────────────────────────────
// Stream events — emitted by SummaryService.generateSummary() during streaming
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One event in the LLM token stream.
 *
 * Token    → a piece of text; accumulate these in Room's streamBuffer
 * Complete → stream finished cleanly; parse streamBuffer into structured fields
 * Error    → something failed; isRetryable determines if WorkManager should retry
 *
 * The UI observes Room's streamBuffer directly via Flow<Summary>, so it gets
 * live updates without collecting this stream itself. The stream is only
 * consumed by SummaryRepository and never exposed to the UI layer.
 */
sealed class SummaryStreamEvent {

    /** A fragment of LLM output — typically a word or punctuation mark */
    data class Token(val text: String) : SummaryStreamEvent()

    /** Stream closed cleanly — all tokens have been received */
    object Complete : SummaryStreamEvent()

    /**
     * Stream terminated with an error.
     *
     * @param userFacingMessage  Shown in the UI summary error state.
     *                           Must be human-readable, not a stack trace.
     * @param isRetryable        True  → WorkManager should retry with backoff
     *                           False → Mark FAILED, require manual retry
     */
    data class Error(
        val userFacingMessage: String,
        val isRetryable: Boolean,
        val cause: Throwable? = null
    ) : SummaryStreamEvent()
}

// ─────────────────────────────────────────────────────────────────────────────
// Final result — returned by SummaryRepository.generateSummary()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Outcome of a complete summary generation run.
 * SummaryWorker maps this to WorkManager Result.
 */
sealed class SummaryGenerationResult {

    /** Summary COMPLETED row now exists in Room */
    object Success : SummaryGenerationResult()

    /** Already done from a previous run — no work needed */
    object AlreadyComplete : SummaryGenerationResult()

    /** Transient failure — WorkManager backoff should retry */
    data class RetryableFailure(val reason: String) : SummaryGenerationResult()

    /** Non-recoverable failure — mark FAILED, show error in UI */
    data class PermanentFailure(val reason: String) : SummaryGenerationResult()
}

// ─────────────────────────────────────────────────────────────────────────────
// Gemini API DTOs — response_format=SSE line-delimited JSON
// POST .../models/gemini-2.0-flash:streamGenerateContent?alt=sse
// ─────────────────────────────────────────────────────────────────────────────

data class GeminiStreamResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    /** "STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "" */
    val finishReason: String?
)

data class GeminiContent(
    val parts: List<GeminiPart>?,
    val role: String?
)

data class GeminiPart(val text: String?)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
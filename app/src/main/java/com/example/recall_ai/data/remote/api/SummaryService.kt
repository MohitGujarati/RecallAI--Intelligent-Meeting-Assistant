package com.example.recall_ai.data.remote.api

import com.example.recall_ai.data.remote.dto.SummaryStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic summary generation contract.
 *
 * Returns a cold Flow that, on collection, opens an LLM streaming connection
 * and emits one SummaryStreamEvent per received token/event.
 *
 * Implementations:
 *   GeminiSummaryService  → Google Gemini 2.0 Flash (production)
 *   MockSummaryService    → deterministic fake with simulated typing delay
 *
 * Contract:
 *   • The Flow must NOT throw. All error paths emit SummaryStreamEvent.Error.
 *   • The Flow always terminates — either with Complete or Error. It never hangs.
 *   • Token events contain raw LLM text fragments (words, punctuation, newlines).
 *   • The caller (SummaryRepository) is responsible for accumulating tokens
 *     into Room and parsing the final structured output.
 *
 * @param transcript    Full meeting transcript assembled from all chunks.
 * @param meetingTitle  Used in the prompt for context (e.g. "Q1 Planning Sync").
 */
interface SummaryService {
    fun generateSummary(
        transcript:   String,
        meetingTitle: String
    ): Flow<SummaryStreamEvent>
}
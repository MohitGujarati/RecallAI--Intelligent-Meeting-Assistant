package com.example.recall_ai.data.remote.api

import android.util.Log
import com.example.recall_ai.data.remote.dto.SummaryStreamEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val TAG = "MockSummaryService"

/**
 * Deterministic fake SummaryService for development and CI.
 *
 * ── What it simulates ────────────────────────────────────────────────
 * Streams a pre-built structured response word-by-word with a realistic
 * typing delay. The response uses all four XML section tags so the full
 * parsing pipeline is exercised without a real Gemini API key.
 *
 * ── Failure injection ────────────────────────────────────────────────
 * Set failAfterTokens = 50 to simulate a mid-stream dropout and verify
 * that SummaryRepository correctly marks the summary as RetryableFailure.
 * Leave at Int.MAX_VALUE (default) for normal development.
 *
 * ── Switching to production ──────────────────────────────────────────
 * Change the binding in NetworkModule.provideSummaryService()
 * from `mock` to `gemini`. Zero other changes needed.
 */
class MockSummaryService @Inject constructor() : SummaryService {

    /** Delay between each emitted word (ms). Set to 0 in unit tests. */
    var tokenDelayMs: Long = 40L

    /** Emit a RetryableError after this many tokens. Int.MAX_VALUE = never fail. */
    var failAfterTokens: Int = Int.MAX_VALUE

    private fun buildMockResponse(meetingTitle: String): String = """
<TITLE>$meetingTitle</TITLE>

<SUMMARY>
The meeting opened with a comprehensive review of last quarter's performance metrics, which showed strong growth across all key business units. The team discussed the upcoming product roadmap and aligned on priorities for the next release cycle.

Significant time was spent reviewing client feedback received over the past two weeks. Several recurring themes were identified, leading to a decision to accelerate work on the onboarding flow improvements. The team also addressed three critical technical blockers that had been escalating.

Budget allocations for the next quarter were reviewed and approved pending final executive sign-off. The meeting concluded with agreement on next steps and a follow-up session scheduled for Thursday.
</SUMMARY>

<KEY_POINTS>
• Q3 performance metrics exceeded targets across all business units
• Product roadmap was revised to prioritize the onboarding flow improvements
• Three technical blockers escalated to the platform engineering team
• Budget for next quarter approved pending executive sign-off
• Client feedback analysis revealed two recurring pain points in authentication
• Infrastructure migration to the new data center remains on schedule
</KEY_POINTS>

<ACTION_ITEMS>
• [Alex] Prepare detailed breakdown of onboarding flow improvements for design review by Friday
• [Engineering] Resolve the three platform blockers identified — target: end of sprint
• [Sarah] Send updated budget proposal to CFO for final approval
• [All] Review the client feedback report distributed during the meeting
• [Product] Schedule follow-up meeting with key stakeholders for Thursday at 2 PM
</ACTION_ITEMS>
    """.trimIndent()

    override fun generateSummary(
        transcript: String,
        meetingTitle: String
    ): Flow<SummaryStreamEvent> = flow {

        Log.d(TAG, "Mock summary starting for: \"$meetingTitle\"")

        val fullResponse = buildMockResponse(meetingTitle)
        val words = fullResponse.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }

        var tokenCount = 0

        for (word in words) {
            if (tokenCount >= failAfterTokens) {
                Log.w(TAG, "Injecting RetryableError at token $tokenCount")
                emit(SummaryStreamEvent.Error(
                    userFacingMessage = "Mock network dropout (failAfterTokens=$failAfterTokens)",
                    isRetryable = true
                ))
                return@flow
            }

            emit(SummaryStreamEvent.Token(word))
            tokenCount++

            if (tokenDelayMs > 0) delay(tokenDelayMs)
        }

        Log.d(TAG, "Mock summary complete: $tokenCount tokens")
        emit(SummaryStreamEvent.Complete)
    }
}
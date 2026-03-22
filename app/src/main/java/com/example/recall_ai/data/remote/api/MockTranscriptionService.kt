package com.example.recall_ai.data.remote.api

import android.util.Log
import com.example.recall_ai.data.local.entity.TranscriptSource
import com.example.recall_ai.data.remote.dto.TranscriptionResult
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import kotlin.random.Random

private const val TAG = "MockTranscription"

/**
 * Deterministic fake TranscriptionService for development and CI.
 *
 * ── Determinism ──────────────────────────────────────────────────────
 * Text is seeded from chunkIndex extracted from the filename, so:
 *   chunk_0000.wav → "The meeting started with a review of..."
 *   chunk_0001.wav → "Action items were assigned to team members..."
 * The assembled transcript is visibly ordered in the UI without needing
 * a real API key or network access.
 *
 * ── File validation ──────────────────────────────────────────────────
 * Mirrors the real service — missing or empty files return PermanentError.
 * This ensures validation logic in TranscriptionRepository is exercised
 * correctly in tests even with the mock active.
 *
 * ── Failure injection ────────────────────────────────────────────────
 * Set failureRate = 0.3f to simulate 30% transient failures and verify
 * that TranscriptionWorker's retry/backoff logic behaves correctly.
 * Leave at 0.0f (default) for normal development.
 *
 * ── Swapping to production ───────────────────────────────────────────
 * Change the binding in NetworkModule.provideTranscriptionService()
 * from `mock` to `whisper`. Nothing else changes.
 */
class MockTranscriptionService @Inject constructor() : TranscriptionService {

    /** Fraction of calls that return RetryableError (0.0 = never fail) */
    var failureRate: Float = 0.0f

    /** Set true to simulate slow connections (5 s delay instead of 400 ms) */
    var simulateSlowNetwork: Boolean = false

    private val sentences = listOf(
        "The meeting started with a review of last quarter's performance metrics.",
        "We discussed the product roadmap and upcoming release milestones.",
        "Action items were assigned to team members across engineering and design.",
        "Client feedback from the previous week was reviewed in detail.",
        "Budget allocations for Q3 were approved pending final executive sign-off.",
        "Several technical blockers were identified and escalated to the platform team.",
        "The design review highlighted three critical improvements to the onboarding flow.",
        "Integration testing showed promising results for the new authentication layer.",
        "Deployment timelines were adjusted to account for the upcoming holiday period.",
        "Next steps include a follow-up meeting with all key stakeholders on Thursday.",
        "The infrastructure migration to the new data center remains on schedule.",
        "Performance benchmarks showed a forty percent improvement over the previous baseline.",
        "Documentation updates are pending review from the technical writing team.",
        "Security audit findings were resolved with patches deployed last Friday.",
        "The sprint retrospective identified several process improvements for next cycle."
    )

    override suspend fun transcribe(
        audioFile: File,
        language: String?,
        prompt: String?
    ): TranscriptionResult {

        // File validation — mirrors real service behaviour
        if (!audioFile.exists()) {
            Log.e(TAG, "File not found: ${audioFile.absolutePath}")
            return TranscriptionResult.PermanentError("File not found: ${audioFile.name}")
        }
        if (audioFile.length() < 100L) {
            Log.e(TAG, "File too small: ${audioFile.length()} bytes")
            return TranscriptionResult.PermanentError(
                "File is empty or corrupt: ${audioFile.name} (${audioFile.length()} bytes)"
            )
        }

        delay(if (simulateSlowNetwork) 5_000L else 400L)

        if (failureRate > 0f && Random.nextFloat() < failureRate) {
            Log.w(TAG, "Injected RetryableError for ${audioFile.name}")
            return TranscriptionResult.RetryableError(
                "Mock network failure (failureRate=$failureRate)"
            )
        }

        val chunkIndex = extractChunkIndex(audioFile.name)
        val text = buildText(chunkIndex)

        Log.d(TAG, "Mock OK: chunk=$chunkIndex → ${text.length} chars")

        return TranscriptionResult.Success(
            text             = text,
            confidence       = 0.96f,
            detectedLanguage = language ?: "en",
            source           = TranscriptSource.MOCK
        )
    }

    // chunk_0003.wav → 3, any other name → 0
    private fun extractChunkIndex(filename: String): Int =
        Regex("_(\\d+)\\.").find(filename)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // Deterministic: chunk N gets sentences [N*3, N*3+1, N*3+2] (wraps)
    private fun buildText(chunkIndex: Int): String {
        val base = (chunkIndex * 3) % sentences.size
        return (0 until 3).joinToString(" ") { i ->
            sentences[(base + i) % sentences.size]
        }
    }
}
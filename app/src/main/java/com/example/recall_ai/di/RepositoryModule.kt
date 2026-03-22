package com.example.recall_ai.di

import com.example.recall_ai.data.remote.api.GeminiSummaryService
import com.example.recall_ai.data.remote.api.GeminiTranscriptionService
import com.example.recall_ai.data.remote.api.MockSummaryService
import com.example.recall_ai.data.remote.api.MockTranscriptionService
import com.example.recall_ai.data.remote.api.SummaryService
import com.example.recall_ai.data.remote.api.TranscriptionService
import com.example.recall_ai.data.remote.api.WhisperTranscriptionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository-layer interfaces to their active implementations.
 *
 * ── Current configuration (Gemini-powered, Whisper on hold) ──────────────
 *
 *   TranscriptionService → [GeminiTranscriptionService]
 *     Transcribes 30-second WAV chunks using Gemini's multimodal audio API.
 *     Sends each chunk as inline base64 audio via generateContent REST endpoint.
 *     No OpenAI key needed. Uses the same GEMINI_API_KEY from NetworkModule.
 *
 *   SummaryService → [GeminiSummaryService]
 *     Generates structured meeting summaries via Gemini SSE streaming.
 *     Outputs XML-tagged sections parsed by SummaryRepository into Room.
 *
 * ── Restoring Whisper (future) ────────────────────────────────────────────
 *   1. Add OPENAI_API_KEY to local.properties
 *   2. Add buildConfigField("String", "OPENAI_API_KEY", ...) in build.gradle.kts
 *   3. Restore provideOpenAiApiKey() in NetworkModule to use BuildConfig.OPENAI_API_KEY
 *   4. Change provideTranscriptionService below to return `whisper`
 *
 * ── Mock fallback (UI dev / no internet) ─────────────────────────────────
 *   Change either return statement to `mock` / `mockSummary`.
 *   Mock services run entirely offline with simulated 400ms latency.
 *
 * ── Why @Provides instead of @Binds ──────────────────────────────────────
 * @Binds requires a 1:1 interface → impl mapping at compile time.
 * @Provides receives all concrete types and selects the active one in code —
 * making environment switches a 1-line change with no extra modules or flavors.
 *
 * ── Concrete repositories need no binding ────────────────────────────────
 * [RecordingRepository], [TranscriptionRepository], and [SummaryRepository]
 * are concrete @Singleton classes with @Inject constructors — Hilt resolves
 * them automatically without any @Provides entry here.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    /**
     * Transcription backend selection.
     *
     *   [GeminiTranscriptionService]  ← ACTIVE
     *     Multimodal Gemini REST API. Accepts WAV audio inline (base64).
     *     Model: gemini-2.0-flash-preview. Requires GEMINI_API_KEY.
     *     No Whisper dependency. Works with existing WAV chunk pipeline.
     *
     *   [WhisperTranscriptionService]  ← ON HOLD (no API key)
     *     OpenAI Whisper endpoint. High accuracy, paid per-minute.
     *     Restore when OPENAI_API_KEY is available.
     *
     *   [MockTranscriptionService]
     *     Simulated transcription, 400ms delay, zero cost. Dev/offline use.
     */
    @Provides
    @Singleton
    fun provideTranscriptionService(
        gemini:  GeminiTranscriptionService,
        whisper: WhisperTranscriptionService,   // retained for future switch
        mock:    MockTranscriptionService        // retained for offline dev
    ): TranscriptionService = gemini   // ← WHISPER: change to `whisper` when key is available

    /**
     * Summary backend selection.
     *
     *   [GeminiSummaryService]  ← ACTIVE
     *     Real Gemini SSE streaming. Structured XML output parsed into Room.
     *     Model: gemini-2.0-flash-preview. Same API key as transcription.
     *
     *   [MockSummaryService]
     *     Word-by-word token simulation at 40ms/token. Full XML sections.
     *     Ideal for UI polish and screenshot tests. Zero API cost.
     */
    @Provides
    @Singleton
    fun provideSummaryService(
        gemini: GeminiSummaryService,
        mock:   MockSummaryService               // retained for offline dev
    ): SummaryService = gemini   // ← MOCK: change to `mock` for offline development
}
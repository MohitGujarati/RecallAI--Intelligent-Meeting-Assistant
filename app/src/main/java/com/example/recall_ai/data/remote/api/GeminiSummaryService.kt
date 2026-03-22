package com.example.recall_ai.data.remote.api

import android.util.Log
import com.example.recall_ai.api.GeminiHttpException
import com.example.recall_ai.config.AiModelConfig
import com.example.recall_ai.data.remote.dto.SummaryStreamEvent
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "GeminiSummaryService"

/**
 * SummaryService backed by Gemini generateContent REST endpoint.
 *
 * ── Why generateContent and NOT streamGenerateContent? ───────────────
 * The TwinAI reference implementation (which works) uses generateContent
 * — a standard one-shot request/response. streamGenerateContent (SSE)
 * requires the model endpoint to stay alive for the full stream duration
 * and is more sensitive to model deprecation and network timeouts.
 *
 * generateContent returns the full summary in one JSON response body.
 * We emit it as a single Token + Complete event — fully compatible with
 * SummaryRepository which accumulates tokens into streamBuffer and
 * parses XML sections after Complete.
 *
 * ── Constructor ───────────────────────────────────────────────────────
 * Injects OkHttpClient, Gson, and apiKey directly — same pattern as
 * GeminiTranscriptionService. No dependency on GeminiStreamingClient.
 * All three are already provided by NetworkModule → zero Hilt changes.
 *
 * ── Error classification ──────────────────────────────────────────────
 *   400 → PermanentError  (prompt malformed or too long)
 *   401/403 → PermanentError  (bad API key)
 *   429 → RetryableError  (rate limit — WorkManager backoff)
 *   5xx → RetryableError  (transient server error)
 *   Timeout / DNS → RetryableError
 *   SAFETY finish → PermanentError  (retry won't help)
 */
class GeminiSummaryService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
) : SummaryService {

    companion object {
        @Named("gemini_api_key") private val apiKey: String= AiModelConfig.GEMINI_API_KEY

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Uses generateContent — NOT streamGenerateContent
        // Builds to: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
        private val BASE_URL =
            "${AiModelConfig.GEMINI_BASE_URL}/${AiModelConfig.GEMINI_SUMMARY_MODEL}:generateContent"
    }

    override fun generateSummary(
        transcript: String,
        meetingTitle: String
    ): Flow<SummaryStreamEvent> {

        val prompt      = SummaryPromptBuilder.build(transcript, meetingTitle)
        val requestBody = SummaryPromptBuilder.buildGeminiRequestBody(prompt)

        Log.d(TAG, "Starting summary for: \"$meetingTitle\" (${transcript.length} chars)")

        return flow {
            val url = "$BASE_URL?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            // Blocking call — this runs on WorkManager's IO thread
            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val code = response.code
                val body = runCatching { response.body?.string() }.getOrNull() ?: ""
                response.close()
                Log.e(TAG, "HTTP $code: ${body.take(300)}")
                emit(classifyHttpError(code, body))
                return@flow
            }

            val bodyString = response.body?.string() ?: ""
            response.close()

            // Parse the one-shot generateContent response
            try {
                val parsed = gson.fromJson(bodyString, GeminiContentResponse::class.java)

                // Inline API error (400-level wrapped in HTTP 200)
                parsed.error?.let { err ->
                    Log.e(TAG, "Gemini inline error ${err.code}: ${err.message}")
                    emit(classifyHttpError(err.code ?: 500, err.message ?: ""))
                    return@flow
                }

                val candidate = parsed.candidates?.firstOrNull()

                // Safety / recitation block
                if (candidate?.finishReason == "SAFETY" ||
                    candidate?.finishReason == "RECITATION") {
                    Log.e(TAG, "Content blocked: finishReason=${candidate.finishReason}")
                    emit(SummaryStreamEvent.Error(
                        userFacingMessage = "Summary could not be generated — content was flagged by safety filters.",
                        isRetryable = false
                    ))
                    return@flow
                }

                val text = candidate?.content?.parts?.firstOrNull()?.text?.trim() ?: ""

                if (text.isEmpty()) {
                    Log.e(TAG, "Gemini returned empty summary text")
                    emit(SummaryStreamEvent.Error(
                        userFacingMessage = "Summary service returned an empty response. Will retry.",
                        isRetryable = true
                    ))
                    return@flow
                }

                // Emit full text as one token then signal completion
                // SummaryRepository accumulates tokens in streamBuffer → parses XML on Complete
                Log.i(TAG, "Summary received: ${text.length} chars")
                emit(SummaryStreamEvent.Token(text))
                emit(SummaryStreamEvent.Complete)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
                emit(classifyException(e))
            }

        }.catch { e ->
            Log.e(TAG, "Uncaught exception in summary flow: ${e.message}")
            emit(classifyException(e))
        }
    }

    private fun classifyHttpError(code: Int, body: String): SummaryStreamEvent.Error =
        when (code) {
            400 -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary request was rejected — transcript may be too long. $body",
                isRetryable = false

            )
            401, 403 -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary service authentication failed. Please check your API key. $body",
                isRetryable = false
            )
            429 -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary service is busy. Will retry automatically.$body",
                isRetryable = true
            )
            in 500..599 -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary service temporarily unavailable. Will retry.$body",
                isRetryable = true
            )
            else -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary service temporarily unavailable. Will retry.$body",
                isRetryable = true
            )
        }

    private fun classifyException(e: Throwable): SummaryStreamEvent.Error =
        when (e) {
            is GeminiHttpException -> classifyHttpError(e.code, e.body)
            is SocketTimeoutException -> SummaryStreamEvent.Error(
                userFacingMessage = "Summary generation timed out. Will retry automatically.$e",
                isRetryable = true,
                cause = e
            )
            is UnknownHostException -> SummaryStreamEvent.Error(
                userFacingMessage = "No internet connection. Summary will resume when online $e.",
                isRetryable = true,
                cause = e
            )
            is IOException -> SummaryStreamEvent.Error(
                userFacingMessage = "Network error during summary generation. Will retry $e.",
                isRetryable = true,
                cause = e
            )
            else -> SummaryStreamEvent.Error(
                userFacingMessage = "An unexpected error occurred generating the summary $e.",
                isRetryable = false,
                cause = e
            )
        }
}
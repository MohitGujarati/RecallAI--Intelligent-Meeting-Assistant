package com.example.recall_ai.data.remote.api

import android.util.Log
import com.example.recall_ai.data.local.entity.TranscriptSource
import com.example.recall_ai.data.remote.dto.TranscriptionResult
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "WhisperService"

/**
 * TranscriptionService backed by OpenAI Whisper.
 *
 * This class has zero OkHttp imports — all multipart construction is
 * delegated to WhisperRequestBuilder. This class does three things only:
 *
 *   1. Assemble the Retrofit call via WhisperRequestBuilder
 *   2. Map HTTP status codes → TranscriptionResult subclasses
 *   3. Extract confidence from verbose_json avg_logprob values
 *
 * ── Confidence derivation ────────────────────────────────────────────
 * Whisper's verbose_json returns per-segment avg_logprob (log-probability,
 * range approximately –3.0 to 0.0). We convert to linear probability:
 *
 *   confidence = exp(avg(avg_logprob across all segments))
 *
 *   avg_logprob = –0.2  →  confidence ≈ 0.82  (very good)
 *   avg_logprob = –0.5  →  confidence ≈ 0.61  (acceptable)
 *   avg_logprob = –1.0  →  confidence ≈ 0.37  (poor, may hallucinate)
 *
 * ── HTTP error classification ────────────────────────────────────────
 *   400 Bad Request    → PermanentError  (corrupt/empty WAV)
 *   401 Unauthorized   → PermanentError  (bad API key)
 *   413 Payload Large  → PermanentError  (file too big, won't change)
 *   429 Rate Limit     → RetryableError  (WorkManager backoff handles it)
 *   5xx Server Error   → RetryableError  (transient, retry is correct)
 *   Network failure    → RetryableError  (timeout, DNS)
 */
class WhisperTranscriptionService @Inject constructor(
    private val api: WhisperApiService,
    @Named("openai_api_key") private val apiKey: String
) : TranscriptionService {

    override suspend fun transcribe(
        audioFile: File,
        language: String?,
        prompt: String?
    ): TranscriptionResult = try {

        Log.d(TAG, "Uploading ${audioFile.name} (${audioFile.length()} bytes)")

        val response = api.transcribe(
            authorization  = WhisperRequestBuilder.authHeader(apiKey),
            file           = WhisperRequestBuilder.filePart(audioFile),
            model          = WhisperRequestBuilder.model(),
            responseFormat = WhisperRequestBuilder.responseFormat(),
            temperature    = WhisperRequestBuilder.temperature(),
            language       = WhisperRequestBuilder.language(language),
            prompt         = WhisperRequestBuilder.prompt(prompt)
        )

        if (response.isSuccessful) {
            val body = response.body()
                ?: return TranscriptionResult.PermanentError(
                    message  = "Whisper returned 200 with empty body",
                    httpCode = 200
                )

            val confidence = body.segments
                ?.mapNotNull { it.avg_logprob }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
                ?.let { Math.exp(it).toFloat().coerceIn(0f, 1f) }

            Log.i(TAG, "OK: ${body.text.length} chars | " +
                    "lang=${body.language} | " +
                    "confidence=${confidence?.let { "%.2f".format(it) } ?: "n/a"}")

            TranscriptionResult.Success(
                text             = body.text.trim(),
                confidence       = confidence,
                detectedLanguage = body.language,
                source           = TranscriptSource.WHISPER
            )

        } else {
            classifyHttpError(response.code(), response.errorBody()?.string() ?: "")
        }

    } catch (e: SocketTimeoutException) {
        Log.w(TAG, "Timeout: ${audioFile.name}")
        TranscriptionResult.RetryableError("Upload timeout", cause = e)
    } catch (e: UnknownHostException) {
        Log.w(TAG, "No connectivity")
        TranscriptionResult.RetryableError("No internet connection", cause = e)
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error transcribing ${audioFile.name}", e)
        TranscriptionResult.RetryableError("Unexpected: ${e.message}", cause = e)
    }

    private fun classifyHttpError(code: Int, body: String): TranscriptionResult {
        Log.e(TAG, "HTTP $code: $body")
        return when (code) {
            400, 401, 403, 413, 415 -> TranscriptionResult.PermanentError(
                message  = "Whisper rejected request ($code): $body",
                httpCode = code
            )
            else -> TranscriptionResult.RetryableError(
                message = "Whisper server error ($code): $body"
            )
        }
    }
}
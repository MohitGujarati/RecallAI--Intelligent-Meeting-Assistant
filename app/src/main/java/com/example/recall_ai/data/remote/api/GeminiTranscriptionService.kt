package com.example.recall_ai.data.remote.api

import android.util.Base64
import android.util.Log
import com.example.recall_ai.config.AiModelConfig
import com.example.recall_ai.data.local.entity.TranscriptSource
import com.example.recall_ai.data.remote.dto.TranscriptionResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "GeminiTranscription"

@Singleton
class GeminiTranscriptionService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
) : TranscriptionService {

    companion object {
        @Named("gemini_api_key")  private val apiKey: String=AiModelConfig.GEMINI_API_KEY
        // Reads from AiModelConfig — change model there, not here
        val TRANSCRIPTION_MODEL = AiModelConfig.GEMINI_TRANSCRIPTION_MODEL

        // Builds to: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent
        private val BASE_URL =
            "${AiModelConfig.GEMINI_BASE_URL}/$TRANSCRIPTION_MODEL:generateContent"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val TEMPERATURE = 0.0
        private const val MAX_CONTEXT_CHARS = 300
    }

    override suspend fun transcribe(
        audioFile: File,
        language: String?,
        prompt: String?
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            val audioBytes  = audioFile.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            Log.d(TAG, "Transcribing ${audioFile.name} " +
                    "(${audioBytes.size / 1024} KB, model=$TRANSCRIPTION_MODEL)")

            val requestJson = buildRequestJson(audioBase64, language, prompt)

            val httpRequest = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val code = response.code
                val body = runCatching { response.body?.string() }.getOrNull() ?: ""
                response.close()
                Log.e(TAG, "HTTP $code: ${body.take(300)}")
                return@withContext classifyHttpError(code, body)
            }

            val bodyString = response.body?.string() ?: ""
            response.close()

            parseResponse(bodyString)

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout: ${e.message}")
            TranscriptionResult.RetryableError("Transcription request timed out", e)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "DNS failure: ${e.message}")
            TranscriptionResult.RetryableError("No internet connection", e)
        } catch (e: IOException) {
            Log.e(TAG, "IO error: ${e.message}", e)
            TranscriptionResult.RetryableError("Network error: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            TranscriptionResult.RetryableError("Unexpected error during transcription", e)
        }
    }

    private fun buildRequestJson(
        audioBase64: String,
        language: String?,
        prompt: String?
    ): String {
        val langHint = if (!language.isNullOrBlank()) " Language: $language." else ""
        val contextHint = if (!prompt.isNullOrBlank())
            " Context from previous segment: \"${prompt.take(MAX_CONTEXT_CHARS)}\"." else ""

        val instruction = "Transcribe this audio recording accurately.$langHint$contextHint" +
                " Return ONLY the transcribed text — no labels, timestamps, " +
                "explanations, or any other formatting."

        val escapedInstruction = instruction
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """
{
  "contents": [{
    "parts": [
      {"text": "$escapedInstruction"},
      {"inlineData": {"mimeType": "audio/wav", "data": "$audioBase64"}}
    ]
  }],
  "generationConfig": {
    "temperature": $TEMPERATURE,
    "maxOutputTokens": 1024
  }
}
        """.trimIndent()
    }

    private fun parseResponse(bodyString: String): TranscriptionResult {
        return try {
            val parsed = gson.fromJson(bodyString, GeminiContentResponse::class.java)

            parsed.error?.let { err ->
                Log.e(TAG, "Gemini inline error ${err.code}: ${err.message}")
                return classifyHttpError(err.code ?: 500, err.message ?: "")
            }

            val candidate = parsed.candidates?.firstOrNull()

            when (candidate?.finishReason) {
                "SAFETY", "RECITATION" -> {
                    Log.w(TAG, "Content blocked by Gemini (finishReason=${candidate.finishReason})")
                    return TranscriptionResult.PermanentError(
                        "Audio chunk was blocked by Gemini safety filters",
                        httpCode = null
                    )
                }
            }

            val text = candidate?.content?.parts?.firstOrNull()?.text?.trim() ?: ""

            if (text.isBlank()) {
                Log.w(TAG, "Gemini returned blank transcription for chunk")
                TranscriptionResult.RetryableError("Gemini returned empty transcription")
            } else {
                Log.i(TAG, "Transcribed ${text.length} chars " +
                        "(\"${text.take(60)}${if (text.length > 60) "…" else ""}\")")
                TranscriptionResult.Success(
                    text             = text,
                    confidence       = null,
                    source           = TranscriptSource.GEMINI,
                    detectedLanguage = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
            TranscriptionResult.RetryableError("Failed to parse transcription response")
        }
    }

    private fun classifyHttpError(code: Int, body: String): TranscriptionResult =
        when (code) {
            400 -> TranscriptionResult.PermanentError(
                "Audio or request rejected by Gemini — chunk may be corrupt (400)",
                httpCode = code
            )
            401, 403 -> TranscriptionResult.PermanentError(
                "Invalid Gemini API key (HTTP $code). Check GEMINI_API_KEY in local.properties.",
                httpCode = code
            )
            413 -> TranscriptionResult.PermanentError(
                "Audio chunk exceeds Gemini inline data limit (413)",
                httpCode = code
            )
            429 -> TranscriptionResult.RetryableError(
                "Gemini rate limit reached — WorkManager will retry with backoff"
            )
            in 500..599 -> TranscriptionResult.RetryableError(
                "Gemini server error $code — will retry"
            )
            else -> TranscriptionResult.RetryableError(
                "HTTP $code from Gemini — will retry"
            )
        }
}

internal data class GeminiContentResponse(
    val candidates: List<GeminiContentCandidate>? = null,
    val error:      GeminiApiError?               = null
)

internal data class GeminiContentCandidate(
    val content:      GeminiContentBody? = null,
    val finishReason: String?            = null
)

internal data class GeminiContentBody(
    val parts: List<GeminiContentPart>? = null,
    val role:  String?                  = null
)

internal data class GeminiContentPart(
    val text: String? = null
)

internal data class GeminiApiError(
    val code:    Int?    = null,
    val message: String? = null,
    val status:  String? = null
)
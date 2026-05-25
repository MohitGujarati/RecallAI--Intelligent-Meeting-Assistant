package com.mohit.recall_ai.data.remote.api

import android.util.Log
import com.mohit.recall_ai.config.AiModelConfig
import com.mohit.recall_ai.data.local.entity.TranscriptSource
import com.mohit.recall_ai.data.remote.dto.TranscriptionResult
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "GeminiTranscription"

@Singleton
class GeminiTranscriptionService @Inject constructor(
    @Named("transcription_model") private val model: GenerativeModel
) : TranscriptionService {

    companion object {
        val TRANSCRIPTION_MODEL = AiModelConfig.GEMINI_TRANSCRIPTION_MODEL
        private const val MAX_CONTEXT_CHARS = 300
    }

    override suspend fun transcribe(
        audioFile: File,
        language: String?,
        prompt: String?
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        try {
            val audioBytes = audioFile.readBytes()
            val instruction = buildInstruction(language, prompt)

            Log.d(TAG, "Transcribing ${audioFile.name} " +
                    "(${audioBytes.size / 1024} KB, model=$TRANSCRIPTION_MODEL)")

            val response = model.generateContent(
                content {
                    inlineData(audioBytes, "audio/wav")
                    text(instruction)
                }
            )

            val text = response.text?.trim() ?: ""

            if (text.isBlank()) {
                Log.w(TAG, "Firebase AI returned blank transcription")
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
            Log.e(TAG, "Transcription error: ${e.message}", e)
            classifyException(e)
        }
    }

    private fun buildInstruction(language: String?, prompt: String?): String {
        val langHint    = if (!language.isNullOrBlank()) " Language: $language." else ""
        val contextHint = if (!prompt.isNullOrBlank())
            " Context from previous segment: \"${prompt.take(MAX_CONTEXT_CHARS)}\"." else ""
        return "Transcribe this audio recording accurately.$langHint$contextHint" +
                " Return ONLY the transcribed text — no labels, timestamps, " +
                "explanations, or any other formatting."
    }

    private fun classifyException(e: Exception): TranscriptionResult {
        val msg = e.message ?: ""
        return when {
            msg.contains("SAFETY", ignoreCase = true) ||
            msg.contains("blocked", ignoreCase = true) ->
                TranscriptionResult.PermanentError(
                    "Audio chunk was blocked by safety filters", httpCode = null
                )
            msg.contains("QUOTA", ignoreCase = true) ||
            msg.contains("429", ignoreCase = true) ->
                TranscriptionResult.RetryableError("Rate limit reached — will retry with backoff")
            msg.contains("401", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true) ||
            msg.contains("UNAUTHENTICATED", ignoreCase = true) ||
            msg.contains("PERMISSION", ignoreCase = true) ->
                TranscriptionResult.PermanentError(
                    "Firebase AI authentication failed (check App Check config)", httpCode = null
                )
            msg.contains("400", ignoreCase = true) ||
            msg.contains("INVALID_ARGUMENT", ignoreCase = true) ->
                TranscriptionResult.PermanentError(
                    "Audio or request rejected — chunk may be corrupt", httpCode = 400
                )
            else ->
                TranscriptionResult.RetryableError("Firebase AI error: $msg", e)
        }
    }
}

// Internal DTOs kept here — still needed by GeminiSummaryService in same package
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

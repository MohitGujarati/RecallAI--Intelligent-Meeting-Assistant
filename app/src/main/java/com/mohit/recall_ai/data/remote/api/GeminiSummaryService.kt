package com.mohit.recall_ai.data.remote.api

import android.util.Log
import com.mohit.recall_ai.data.remote.dto.SummaryStreamEvent
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "GeminiSummaryService"

/**
 * SummaryService backed by Firebase AI SDK (no API key — uses App Check).
 *
 * Uses generateContent (one-shot) matching the previous REST implementation.
 * Emits the full response as a single Token + Complete so SummaryRepository's
 * streamBuffer + XML parsing logic is unchanged.
 */
class GeminiSummaryService @Inject constructor(
    @Named("summary_model") private val model: GenerativeModel
) : SummaryService {

    override fun generateSummary(
        transcript: String,
        meetingTitle: String
    ): Flow<SummaryStreamEvent> {
        val prompt = SummaryPromptBuilder.build(transcript, meetingTitle)
        Log.d(TAG, "Starting summary for: \"$meetingTitle\" (${transcript.length} chars)")

        return flow {
            val response = model.generateContent(
                content { text(prompt) }
            )

            val text = response.text?.trim() ?: ""

            if (text.isEmpty()) {
                Log.e(TAG, "Firebase AI returned empty summary")
                emit(SummaryStreamEvent.Error(
                    userFacingMessage = "Summary service returned an empty response. Will retry.",
                    isRetryable = true
                ))
                return@flow
            }

            Log.i(TAG, "Summary received: ${text.length} chars")
            emit(SummaryStreamEvent.Token(text))
            emit(SummaryStreamEvent.Complete)

        }.catch { e ->
            Log.e(TAG, "Summary error: ${e.message}", e)
            emit(classifyException(e))
        }
    }

    private fun classifyException(e: Throwable): SummaryStreamEvent.Error {
        val msg = e.message ?: ""
        return when {
            msg.contains("SAFETY", ignoreCase = true) ||
            msg.contains("blocked", ignoreCase = true) ->
                SummaryStreamEvent.Error(
                    userFacingMessage = "Summary could not be generated — content was flagged by safety filters.",
                    isRetryable = false
                )
            msg.contains("QUOTA", ignoreCase = true) ||
            msg.contains("429", ignoreCase = true) ->
                SummaryStreamEvent.Error(
                    userFacingMessage = "Summary service is busy. Will retry automatically.",
                    isRetryable = true
                )
            msg.contains("400", ignoreCase = true) ||
            msg.contains("INVALID_ARGUMENT", ignoreCase = true) ->
                SummaryStreamEvent.Error(
                    userFacingMessage = "Summary request was rejected — transcript may be too long.",
                    isRetryable = false
                )
            msg.contains("401", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true) ||
            msg.contains("UNAUTHENTICATED", ignoreCase = true) ->
                SummaryStreamEvent.Error(
                    userFacingMessage = "Summary service authentication failed.",
                    isRetryable = false
                )
            else ->
                SummaryStreamEvent.Error(
                    userFacingMessage = "Summary service temporarily unavailable. Will retry.",
                    isRetryable = true,
                    cause = e as? Exception
                )
        }
    }
}

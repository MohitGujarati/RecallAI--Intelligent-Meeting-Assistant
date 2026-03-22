package com.example.recall_ai.data.remote.api

import android.util.Log
import com.example.recall_ai.data.local.entity.ChatMessage
import com.example.recall_ai.api.GeminiStreamingClient
import com.example.recall_ai.data.remote.dto.GeminiStreamResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val TAG = "GeminiChatService"

/**
 * Handles Q&A chat against a meeting transcript using Gemini streaming.
 *
 * Each request sends the full transcript as context, the conversation
 * history, and the new user question. Responses stream token-by-token.
 */
class GeminiChatService @Inject constructor(
    private val client: GeminiStreamingClient
) {

    /**
     * Streams the AI answer token-by-token.
     *
     * @param transcript    Full meeting transcript text
     * @param history       Previous chat messages for multi-turn context
     * @param userQuestion  The new question to answer
     * @return Flow of text tokens (each is a small piece of the answer)
     */
    fun askQuestion(
        transcript: String,
        history: List<ChatMessage>,
        userQuestion: String
    ): Flow<String> {
        val requestBody = buildRequestBody(transcript, history, userQuestion)
        Log.d(TAG, "Sending chat question: \"${userQuestion.take(60)}\"")

        return client.stream(requestBody)
            .map { response -> extractText(response) }
            .let { tokenFlow ->
                // Filter out empty strings
                flow {
                    tokenFlow.collect { text ->
                        if (text.isNotEmpty()) emit(text)
                    }
                }
            }
    }

    private fun extractText(response: GeminiStreamResponse): String {
        response.error?.let {
            Log.e(TAG, "Gemini error: ${it.code} ${it.message}")
            return ""
        }
        return response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: ""
    }

    private fun buildRequestBody(
        transcript: String,
        history: List<ChatMessage>,
        question: String
    ): String {
        val contents = buildString {
            append("[")

            // System context message with transcript
            append("""{"role":"user","parts":[{"text":${escape(buildSystemPrompt(transcript))}}]},""")
            append("""{"role":"model","parts":[{"text":"I've read the transcript. Ask me anything about it."}]},""")

            // Conversation history
            for (msg in history) {
                val role = if (msg.isUser) "user" else "model"
                append("""{"role":"$role","parts":[{"text":${escape(msg.text)}}]},""")
            }

            // Current question
            append("""{"role":"user","parts":[{"text":${escape(question)}}]}""")

            append("]")
        }

        return """
{
  "contents": $contents,
  "generationConfig": {
    "temperature": 0.4,
    "maxOutputTokens": 2048,
    "topP": 0.9
  },
  "safetySettings": [
    {"category": "HARM_CATEGORY_HARASSMENT",        "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_HATE_SPEECH",       "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
  ]
}
        """.trimIndent()
    }

    private fun buildSystemPrompt(transcript: String): String {
        val truncated = transcript.take(100_000)
        return """You are a helpful AI assistant. The user has recorded a meeting and wants to ask questions about it.

Below is the full transcript of the meeting. Answer the user's questions based ONLY on the information in this transcript. If the answer is not in the transcript, say so honestly.

Be concise and direct in your answers. Use bullet points where appropriate.

TRANSCRIPT:
$truncated"""
    }

    /** JSON-escape a string value */
    private fun escape(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}

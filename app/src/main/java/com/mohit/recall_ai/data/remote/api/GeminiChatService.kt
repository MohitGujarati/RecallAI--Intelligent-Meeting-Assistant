package com.mohit.recall_ai.data.remote.api

import android.util.Log
import com.mohit.recall_ai.data.local.entity.ChatMessage
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "GeminiChatService"

/**
 * Handles Q&A chat against a meeting transcript using Firebase AI SDK streaming.
 * No API key — uses App Check. Replaces the previous GeminiStreamingClient SSE approach.
 */
class GeminiChatService @Inject constructor(
    @Named("chat_model") private val model: GenerativeModel
) {

    fun askQuestion(
        transcript: String,
        history: List<ChatMessage>,
        userQuestion: String
    ): Flow<String> {
        Log.d(TAG, "Sending chat question: \"${userQuestion.take(60)}\"")

        return flow {
            // Build the full conversation as a list of Content objects
            val contents = buildList {
                // System context: transcript (sent as first user turn)
                add(content("user")  { text(buildSystemPrompt(transcript)) })
                add(content("model") { text("I've read the transcript. Ask me anything about it.") })

                // Conversation history
                for (msg in history) {
                    val role = if (msg.isUser) "user" else "model"
                    add(content(role) { text(msg.text) })
                }

                // Current question
                add(content("user") { text(userQuestion) })
            }

            model.generateContentStream(contents).collect { chunk ->
                val text = chunk.text ?: ""
                if (text.isNotEmpty()) emit(text)
            }

        }.catch { e ->
            Log.e(TAG, "Chat stream error: ${e::class.simpleName}: ${e.message}", e)
            emit("⚠ Error: ${e.message?.take(120)}")
        }
    }

    private fun buildSystemPrompt(transcript: String): String {
        val truncated = transcript.take(100_000)
        return """You are a helpful AI assistant. The user has recorded a meeting and wants to ask questions about it.

Below is the full transcript of the meeting. Answer the user's questions based ONLY on the information in this transcript. If the answer is not in the transcript, say so honestly.

Be concise and direct in your answers. Use bullet points where appropriate.

TRANSCRIPT:
$truncated"""
    }
}

package com.example.recall_ai.api

import android.util.Log
import android.widget.Toast
import com.example.recall_ai.config.AiModelConfig
import com.google.gson.Gson
import com.example.recall_ai.data.remote.dto.GeminiStreamResponse
import dagger.Provides
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "GeminiClient"

/**
 * SSE streaming client — used exclusively by GeminiChatService.
 * Summary now uses generateContent (GeminiSummaryService) — not this class.
 */

@Singleton
class GeminiStreamingClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val gson: Gson,


) {
    companion object {
        @Named("gemini_api_key")  private val apiKey: String=AiModelConfig.GEMINI_API_KEY
        // Chat streaming endpoint
        // Builds to: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent
        private val BASE_URL =
            "${AiModelConfig.GEMINI_BASE_URL}/${AiModelConfig.GEMINI_CHAT_MODEL}:streamGenerateContent"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun stream(requestBody: String): Flow<GeminiStreamResponse> = callbackFlow {
        val url = "$BASE_URL?alt=sse&key=$apiKey"
        Log.d("GeminiStreamingClient", "Streaming to $url")
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream")
            .build()

        val call = httpClient.newCall(request)

        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure: ${e.message}")
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val code = response.code
                    val body = runCatching { response.body?.string() }.getOrNull() ?: ""
                    Log.e(TAG, "HTTP $code: ${body.take(300)}")
                    response.close()
                    close(GeminiHttpException(code, body))
                    return
                }

                val source = response.body?.source()
                if (source == null) {
                    response.close()
                    close(IOException("Empty response body"))
                    return
                }

                try {
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        when {
                            line.isBlank()            -> { /* SSE separator */ }
                            line == "data: [DONE]"    -> break
                            line.startsWith("data: ") -> {
                                val json = line.removePrefix("data: ").trim()
                                runCatching {
                                    gson.fromJson(json, GeminiStreamResponse::class.java)
                                }.onSuccess { parsed ->
                                    trySend(parsed)
                                }.onFailure { e ->
                                    Log.w(TAG, "Failed to parse SSE line: ${e.message}")
                                }
                            }
                        }
                    }
                    close()
                } catch (e: IOException) {
                    Log.e(TAG, "Stream read error: ${e.message}")
                    close(e)
                } finally {
                    response.close()
                }
            }
        })

        awaitClose { call.cancel() }
    }
}

/** Typed exception carrying the HTTP status code for error classification */
class GeminiHttpException(val code: Int, val body: String) :
    IOException("Gemini HTTP $code: ${body.take(200)}")
package com.example.recall_ai.data.remote.api

import android.util.Base64
import android.util.Log
import com.example.recall_ai.config.AiModelConfig
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

private const val TAG = "GeminiLiveClient"

/**
 * Bidirectional WebSocket client for Gemini Live (BidiGenerateContent API).
 *
 * Mirrors the official Python reference: Get_started_LiveAPI.py
 * See: https://github.com/google-gemini/cookbook/blob/main/quickstarts/Get_started_LiveAPI.py
 *
 * ── NOT a @Singleton ─────────────────────────────────────────────────
 * LiveAiModule provides a fresh instance per service lifecycle to avoid
 * WebSocket connection leaks between sessions.
 */
class GeminiLiveClient(
    private val okHttpClient: OkHttpClient,
    private val gson:         Gson,
    private val apiKey:       String
) {
    private var webSocket: WebSocket? = null

    private val _incomingAudioFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val incomingAudioFlow: Flow<ByteArray> = _incomingAudioFlow

    private val _turnCompleteFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val turnCompleteFlow: Flow<Unit> = _turnCompleteFlow

    private val _errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorFlow: Flow<String> = _errorFlow

    /** Emitted when Gemini invokes a declared function (e.g. set_reminder). */
    data class FunctionCallEvent(
        val callId: String,
        val functionName: String,
        val args: Map<String, Any?>
    )

    private val _functionCallFlow = MutableSharedFlow<FunctionCallEvent>(extraBufferCapacity = 8)
    val functionCallFlow: Flow<FunctionCallEvent> = _functionCallFlow

    /** Completes when the server acknowledges the setup frame. */
    private var _setupComplete = CompletableDeferred<Unit>()

    private var audioChunksSent = 0

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Opens the WebSocket and sends the Setup frame.
     * Suspends until the server acknowledges setup (setupComplete) or
     * a 30-second timeout is reached.
     */
    /**
     * Opens the WebSocket and sends the Setup frame.
     * @param initialContext System prompt text.
     * @param toolDeclarations JSON-ready list from [ToolRegistry.getDeclarations].
     *                         Empty list = no function calling for this session.
     */
    suspend fun connect(
        initialContext: String,
        toolDeclarations: List<Map<String, Any?>> = emptyList()
    ) {
        _setupComplete = CompletableDeferred()
        audioChunksSent = 0

        val url = "${AiModelConfig.GEMINI_LIVE_BASE_URL}?key=$apiKey"
        Log.i(TAG, "┌─ connect() ─────────────────────────────────────")
        Log.i(TAG, "│ URL   : ${url.take(80)}…")
        Log.i(TAG, "│ Model : ${AiModelConfig.GEMINI_LIVE_MODEL}")
        Log.i(TAG, "│ Context length : ${initialContext.length} chars")
        Log.i(TAG, "│ Tools : ${toolDeclarations.size} declared")
        Log.i(TAG, "└────────────────────────────────────────────────")

        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "✅ WebSocket OPENED — HTTP ${response.code}")
                sendSetupFrame(initialContext, toolDeclarations)
            }

            // ── TEXT messages (JSON) ──────────────────────────────────
            override fun onMessage(webSocket: WebSocket, text: String) {
                val preview = if (text.length > 300) text.take(300) + "…" else text
                Log.d(TAG, "⬇ onMessage[TEXT] (${text.length} chars): $preview")
                handleIncomingMessage(text)
            }

            // ── BINARY messages ──────────────────────────────────────
            // The Gemini Live API may send responses as binary WebSocket
            // frames (the content is still JSON). OkHttp treats text vs
            // binary as separate callbacks — we MUST handle both.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                val preview = if (text.length > 300) text.take(300) + "…" else text
                Log.d(TAG, "⬇ onMessage[BINARY] (${bytes.size} bytes): $preview")
                handleIncomingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "⛔ WebSocket CLOSED: code=$code reason=$reason")
                if (!_setupComplete.isCompleted) {
                    _setupComplete.completeExceptionally(
                        Exception("WebSocket closed before setup completed: $code / $reason")
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val httpCode = response?.code ?: -1
                val body = try { response?.body?.string()?.take(500) } catch (_: Exception) { null }
                Log.e(TAG, "❌ WebSocket FAILURE: HTTP $httpCode — ${t.localizedMessage}", t)
                if (body != null) Log.e(TAG, "❌ Response body: $body")
                _errorFlow.tryEmit(t.localizedMessage ?: "Unknown WebSocket error")
                if (!_setupComplete.isCompleted) {
                    _setupComplete.completeExceptionally(t)
                }
            }
        })

        // Wait up to 30 seconds for server to confirm setup
        try {
            withTimeout(30_000L) {
                _setupComplete.await()
            }
            Log.i(TAG, "✅ Setup complete — ready to stream audio")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Setup failed or timed out: ${e.message}", e)
            disconnect()
            throw e
        }
    }

    /**
     * Streams raw 16 kHz PCM bytes from the microphone to Gemini.
     * MIME type: "audio/pcm" (matches the Python reference exactly).
     */
    fun sendAudio(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        val base64Audio = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        // v1beta uses "audio" directly, NOT the deprecated "mediaChunks"
        val payload = mapOf(
            "realtimeInput" to mapOf(
                "audio" to mapOf(
                    "mimeType" to "audio/pcm",
                    "data" to base64Audio
                )
            )
        )
        ws.send(gson.toJson(payload))

        audioChunksSent++
        if (audioChunksSent % 100 == 0) {
            Log.d(TAG, "⬆ Audio chunks sent: $audioChunksSent (${pcmBytes.size} bytes each)")
        }
    }

    /**
     * Hot-swaps the AI's context mid-session without dropping the audio stream.
     */
    fun updateContext(newContext: String) {
        val ws = webSocket ?: return
        val payload = mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf(
                        "role"  to "user",
                        "parts" to listOf(
                            mapOf("text" to "SYSTEM UPDATE: Deeper meeting context now available:\n$newContext")
                        )
                    )
                ),
                "turnComplete" to true
            )
        )
        ws.send(gson.toJson(payload))
        Log.i(TAG, "Hot-swapped Live AI context (${newContext.length} chars)")
    }

    /**
     * Sends the result of a function call back to Gemini so it can
     * continue the conversation (e.g. confirm the reminder was set).
     */
    fun sendFunctionResponse(callId: String, functionName: String, result: Map<String, Any?>) {
        val ws = webSocket ?: return
        val payload = mapOf(
            "toolResponse" to mapOf(
                "functionResponses" to listOf(
                    mapOf(
                        "id" to callId,
                        "name" to functionName,
                        "response" to result
                    )
                )
            )
        )
        ws.send(gson.toJson(payload))
        Log.i(TAG, "⬆ Sent function response for $functionName (id=$callId)")
    }

    fun disconnect() {
        Log.i(TAG, "disconnect() called — closing WebSocket")
        webSocket?.close(1000, "User ended session")
        webSocket = null
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Sends the BidiGenerateContentSetup frame.
     *
     * Structure matches exactly what the Python SDK sends over the wire:
     *
     *   setup
     *   ├── model
     *   ├── systemInstruction
     *   └── generationConfig
     *       ├── responseModalities     ["AUDIO"]
     *       ├── mediaResolution        "MEDIA_RESOLUTION_MEDIUM"
     *       ├── speechConfig           (voice = Zephyr)
     *       └── contextWindowCompression
     *           ├── triggerTokens       104857
     *           └── slidingWindow
     *               └── targetTokens   52428
     *
     * IMPORTANT: contextWindowCompression goes INSIDE generationConfig
     * (not as a sibling). The Python SDK maps it this way. Placing it
     * outside generationConfig causes the v1beta server to silently
     * ignore the setup frame.
     */
    private fun sendSetupFrame(
        contextPrompt: String,
        toolDeclarations: List<Map<String, Any?>>
    ) {
        val setupBody = mutableMapOf<String, Any?>(
            "model" to "models/${AiModelConfig.GEMINI_LIVE_MODEL}",

            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to contextPrompt))
            ),

            "generationConfig" to mapOf(
                "responseModalities" to listOf("AUDIO"),
                "mediaResolution"    to "MEDIA_RESOLUTION_MEDIUM",
                "speechConfig"       to mapOf(
                    "voiceConfig" to mapOf(
                        "prebuiltVoiceConfig" to mapOf("voiceName" to "Zephyr")
                    )
                )
            ),

            // contextWindowCompression is at the SETUP level, NOT inside
            // generationConfig. The server rejects it with code 1007 if
            // placed inside generationConfig.
            "contextWindowCompression" to mapOf(
                "triggerTokens" to 104857,
                "slidingWindow" to mapOf("targetTokens" to 52428)
            )
        )

        // Only attach tools block if there are declarations from the registry
        if (toolDeclarations.isNotEmpty()) {
            setupBody["tools"] = listOf(
                mapOf("functionDeclarations" to toolDeclarations)
            )
        }

        val setupPayload = mapOf("setup" to setupBody)

        val json = gson.toJson(setupPayload)
        Log.i(TAG, "⬆ Sending setup frame (${json.length} chars)")
        Log.d(TAG, "⬆ Setup JSON: ${json.take(500)}")
        webSocket?.send(json)
    }

    /**
     * Parses every incoming WebSocket message (handles both text and binary frames).
     */
    private fun handleIncomingMessage(jsonText: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(jsonText, Map::class.java) as Map<String, Any?>

            Log.d(TAG, "⬇ Message keys: ${root.keys}")

            // ── 0. setupComplete ─────────────────────────────────────────
            if (root.containsKey("setupComplete")) {
                Log.i(TAG, "✅ Server confirmed setupComplete!")
                if (!_setupComplete.isCompleted) {
                    _setupComplete.complete(Unit)
                }
                return
            }

            // ── 1. serverContent (audio + text + turnComplete) ────────────
            val serverContent = root["serverContent"] as? Map<*, *>
            if (serverContent != null) {
                val modelTurn = serverContent["modelTurn"] as? Map<*, *>
                val parts = modelTurn?.get("parts") as? List<*>

                parts?.filterIsInstance<Map<*, *>>()?.forEach { part ->
                    val inlineData = part["inlineData"] as? Map<*, *>
                    val base64Data = inlineData?.get("data") as? String
                    if (base64Data != null) {
                        val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val emitted = _incomingAudioFlow.tryEmit(pcmBytes)
                        Log.v(TAG, "🔊 Audio chunk received (${pcmBytes.size} bytes, emitted=$emitted)")
                    }

                    val textContent = part["text"] as? String
                    if (textContent != null) {
                        Log.d(TAG, "📝 Text from model: $textContent")
                    }

                    // ── Function call from Gemini (agent tool use) ───────
                    @Suppress("UNCHECKED_CAST")
                    val functionCall = part["functionCall"] as? Map<String, Any?>
                    if (functionCall != null) {
                        val name = functionCall["name"] as? String ?: ""
                        val args = (functionCall["args"] as? Map<String, Any?>) ?: emptyMap()
                        val callId = functionCall["id"] as? String
                            ?: "${name}_${System.currentTimeMillis()}"
                        Log.i(TAG, "🔧 Function call: $name($args) id=$callId")
                        _functionCallFlow.tryEmit(FunctionCallEvent(callId, name, args))
                    }
                }

                val turnComplete = serverContent["turnComplete"]
                if (turnComplete == true || turnComplete.toString() == "true") {
                    Log.i(TAG, "🔄 Turn complete — AI finished speaking")
                    _turnCompleteFlow.tryEmit(Unit)
                }

                return
            }

            // ── 2. toolCall (Live API sends function calls here, NOT in serverContent)
            @Suppress("UNCHECKED_CAST")
            val toolCall = root["toolCall"] as? Map<String, Any?>
            if (toolCall != null) {
                val functionCalls = toolCall["functionCalls"] as? List<*>
                functionCalls?.filterIsInstance<Map<*, *>>()?.forEach { fc ->
                    val name = fc["name"] as? String ?: ""
                    @Suppress("UNCHECKED_CAST")
                    val args = (fc["args"] as? Map<String, Any?>) ?: emptyMap()
                    val callId = fc["id"] as? String
                        ?: "${name}_${System.currentTimeMillis()}"
                    Log.i(TAG, "🔧 toolCall: $name($args) id=$callId")
                    _functionCallFlow.tryEmit(FunctionCallEvent(callId, name, args))
                }
                return
            }

            // ── 3. Error responses from API ───────────────────────────────
            val error = root["error"] as? Map<*, *>
            if (error != null) {
                val errorMsg = error["message"]?.toString() ?: "Unknown API error"
                val errorCode = error["code"]?.toString() ?: "?"
                Log.e(TAG, "❌ API Error ($errorCode): $errorMsg")
                _errorFlow.tryEmit("API Error ($errorCode): $errorMsg")
                return
            }

            // ── 4. Unrecognized message ──────────────────────────────────
            Log.w(TAG, "⚠️ Unrecognized message keys: ${root.keys}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse incoming WebSocket message", e)
            Log.e(TAG, "❌ Raw text: ${jsonText.take(300)}")
        }
    }
}
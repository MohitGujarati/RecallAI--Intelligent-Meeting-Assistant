package com.example.recall_ai.data.remote.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import com.example.recall_ai.config.AiModelConfig

/**
 * Owns all OkHttp multipart construction for Whisper requests.
 *
 * ── Layering rationale ────────────────────────────────────────────────
 *
 *   WhisperTranscriptionService → WhisperRequestBuilder  ← OkHttp types
 *                               → WhisperApiService (Retrofit)
 *                               → OkHttp → HTTP
 *
 * WhisperTranscriptionService has zero OkHttp imports. It delegates every
 * multipart construction decision here, then passes the results to the
 * Retrofit interface. This keeps the service focused on response mapping
 * and error classification only.
 *
 * ── OkHttp streaming ─────────────────────────────────────────────────
 * filePart() uses asRequestBody() which streams the file directly to the
 * network socket — the WAV bytes are never fully loaded into a ByteArray.
 * For a 30 s WAV at 16 kHz/mono/16-bit ≈ 960 KB this matters less than
 * for large files, but streaming is always safer and correct here.
 *
 * ── Whisper prompt limit ─────────────────────────────────────────────
 * Whisper's prompt parameter accepts ≈ 224 tokens (~1 000 chars) but only
 * uses the last 224 tokens. We cap at 500 chars — enough for 2–3 sentences
 * of context without ever exceeding the limit.
 */
internal object WhisperRequestBuilder {

    private val MEDIA_WAV  = "audio/wav".toMediaType()
    private val MEDIA_TEXT = "text/plain; charset=utf-8".toMediaType()

    private const val PROMPT_MAX_CHARS = 500

    /** Streams the WAV file as a multipart file part. Never loads the file into memory. */
    fun filePart(wav: File): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            name     = "file",
            filename = wav.name,
            body     = wav.asRequestBody(MEDIA_WAV)
        )

    fun model(): RequestBody          = AiModelConfig.WHISPER_MODEL.toRequestBody(MEDIA_TEXT)

    /**
     * verbose_json is required — plain "json" omits segments and avg_logprob,
     * which are the source of our per-chunk confidence scores.
     */
    fun responseFormat(): RequestBody = "verbose_json".toRequestBody(MEDIA_TEXT)

    fun temperature(): RequestBody    = "0".toRequestBody(MEDIA_TEXT)

    /**
     * BCP-47 language hint. Providing this skips Whisper's internal language
     * detection step, improving both speed and accuracy.
     * Returns null → Retrofit omits the Part entirely from the request.
     */
    fun language(value: String?): RequestBody? =
        value?.toRequestBody(MEDIA_TEXT)

    /**
     * Preceding-chunk context for boundary continuity.
     * Whisper uses this as priming, not as a literal prefix — passing
     * the last 1–2 sentences of chunk N-1 when transcribing chunk N
     * dramatically reduces hallucinations and mid-word splits at boundaries.
     * Returns null → Retrofit omits the Part entirely from the request.
     */
    fun prompt(value: String?): RequestBody? =
        value?.take(PROMPT_MAX_CHARS)?.toRequestBody(MEDIA_TEXT)

    fun authHeader(apiKey: String): String = "Bearer $apiKey"
}
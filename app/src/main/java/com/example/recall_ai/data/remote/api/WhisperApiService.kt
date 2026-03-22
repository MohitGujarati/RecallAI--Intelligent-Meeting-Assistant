package com.example.recall_ai.data.remote.api

import com.example.recall_ai.data.remote.dto.WhisperResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit interface for POST /v1/audio/transcriptions.
 *
 * ── Why OkHttp types in a Retrofit interface? ─────────────────────────
 * Retrofit's @Multipart is a thin annotation layer on top of OkHttp.
 * MultipartBody.Part and RequestBody ARE the Retrofit-defined types for
 * multipart parts — there is no higher-level Retrofit abstraction for
 * binary file uploads. This is the correct, idiomatic Retrofit pattern:
 *
 *   @Part  file: MultipartBody.Part    ← named file part with filename
 *   @Part("model") model: RequestBody  ← plain text field
 *
 * The OkHttp construction (asRequestBody, toRequestBody, toMediaType) is
 * isolated in WhisperRequestBuilder — this interface stays declarative.
 *
 * Docs: https://square.github.io/retrofit/#multipart
 *       https://platform.openai.com/docs/api-reference/audio/createTranscription
 *
 * Request fields:
 *   file            → WAV audio bytes (required)
 *   model           → "whisper-1" (required)
 *   response_format → "verbose_json" — gives segments + avg_logprob for confidence
 *   temperature     → 0 for deterministic output
 *   language        → BCP-47 hint, optional — omitted when null
 *   prompt          → preceding context, optional — omitted when null
 */
interface WhisperApiService {

    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization")      authorization:  String,
        @Part                         file:           MultipartBody.Part,
        @Part("model")                model:          RequestBody,
        @Part("response_format")      responseFormat: RequestBody,
        @Part("temperature")          temperature:    RequestBody,
        @Part("language")             language:       RequestBody? = null,
        @Part("prompt")               prompt:         RequestBody? = null
    ): Response<WhisperResponse>
}
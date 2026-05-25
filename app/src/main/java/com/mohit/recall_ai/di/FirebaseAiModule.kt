package com.mohit.recall_ai.di

import com.mohit.recall_ai.config.AiModelConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.generationConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides Firebase AI GenerativeModel instances for each service.
 * No API key needed — authentication is handled by Firebase App Check.
 *
 * GeminiLiveClient (Bob/Live AI) is NOT provided here — it still uses
 * a direct WebSocket with an API key until Firebase adds Live API support.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseAiModule {

    @Provides
    @Singleton
    @Named("transcription_model")
    fun provideTranscriptionModel(): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = AiModelConfig.GEMINI_TRANSCRIPTION_MODEL,
                generationConfig = generationConfig {
                    temperature = 0.0f
                    maxOutputTokens = 1024
                }
            )

    @Provides
    @Singleton
    @Named("summary_model")
    fun provideSummaryModel(): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = AiModelConfig.GEMINI_SUMMARY_MODEL,
                generationConfig = generationConfig {
                    temperature = 0.3f
                    maxOutputTokens = 2048
                    topP = 0.8f
                },
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT,        HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.HATE_SPEECH,       HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.NONE),
                )
            )

    @Provides
    @Singleton
    @Named("chat_model")
    fun provideChatModel(): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = AiModelConfig.GEMINI_CHAT_MODEL,
                generationConfig = generationConfig {
                    temperature = 0.4f
                    maxOutputTokens = 2048
                    topP = 0.9f
                },
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT,        HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.HATE_SPEECH,       HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.NONE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.NONE),
                )
            )
}

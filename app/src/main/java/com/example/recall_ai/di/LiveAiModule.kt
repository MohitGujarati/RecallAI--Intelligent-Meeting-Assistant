package com.example.recall_ai.di


import com.example.recall_ai.data.remote.api.GeminiLiveClient
import com.example.recall_ai.service.audio.AudioTrackManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object LiveAiModule {

    /**
     * Provides a FRESH instance of GeminiLiveClient.
     * Notice the ABSENCE of @Singleton. This directly solves Issue #1 from the architectural review.
     * The Service will inject a new instance per session, preventing WebSocket memory leaks.
     */
    @Provides
    fun provideGeminiLiveClient(
        okHttpClient: OkHttpClient,
        gson: Gson,
        @Named("gemini_api_key") apiKey: String
    ): GeminiLiveClient {
        return GeminiLiveClient(okHttpClient, gson, apiKey)
    }

    /**
     * Provides a FRESH instance of AudioTrackManager.
     * Prevents locking the hardware AudioTrack buffer across multiple sessions.
     */
    @Provides
    fun provideAudioTrackManager(): AudioTrackManager {
        return AudioTrackManager()
    }
}
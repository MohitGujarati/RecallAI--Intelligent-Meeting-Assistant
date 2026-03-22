package com.example.recall_ai.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the WorkManager [Configuration] that wires [HiltWorkerFactory]
 * into the WorkManager runtime.
 *
 * ── Why Hilt needs to own WorkManager initialization ─────────────────────
 * By default, WorkManager self-initializes via a ContentProvider that runs
 * before Application.onCreate(). That default-initialized WorkManager uses
 * the built-in WorkerFactory, which cannot inject Hilt dependencies into
 * workers — it only calls the zero-argument constructor.
 *
 * To use @HiltWorker + @AssistedInject, we must:
 *   1. Disable WorkManager's auto-initialization (done via manifest entry).
 *   2. Provide a custom Configuration that uses HiltWorkerFactory.
 *   3. Implement Configuration.Provider in the Application class so that
 *      WorkManager picks up the custom configuration on first use.
 *
 * Steps 2 and 3 are handled by this module (providing the Configuration)
 * and RecallApplication (implementing Configuration.Provider).
 *
 * ── Manifest entry required ───────────────────────────────────────────────
 * Add this inside the <application> tag of AndroidManifest.xml to disable
 * the default WorkManager ContentProvider auto-initializer:
 *
 *   <provider
 *       android:name="androidx.startup.InitializationProvider"
 *       android:authorities="${applicationId}.androidx-startup"
 *       android:exported="false"
 *       tools:node="merge">
 *       <meta-data
 *           android:name="androidx.work.WorkManagerInitializer"
 *           android:value="androidx.startup"
 *           tools:node="remove" />
 *   </provider>
 *
 * Without this, WorkManager self-initializes before Hilt is ready, and
 * HiltWorkerFactory is never used — Hilt dependencies in workers will be null.
 *
 * ── Worker registration ───────────────────────────────────────────────────
 * Workers do NOT need to be registered anywhere. HiltWorkerFactory discovers
 * @HiltWorker classes automatically via Hilt's generated component at
 * compile time. Adding a new worker requires only:
 *   • @HiltWorker annotation on the class
 *   • @AssistedInject constructor with @Assisted Context and WorkerParameters
 *
 * ── Retry and backoff configuration ──────────────────────────────────────
 * Per-worker retry policy (BackoffPolicy, initial delay) is set in each
 * worker's OneTimeWorkRequestBuilder, not here. This Configuration only
 * affects the factory and logging level.
 *
 * ── Logging level ─────────────────────────────────────────────────────────
 * Log.DEBUG in all builds during active development. Change to Log.ERROR
 * before shipping to suppress verbose WorkManager lifecycle messages in
 * production logcat.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    /**
     * Provides the WorkManager [Configuration] that activates [HiltWorkerFactory].
     *
     * Consumed by [RecallApplication.workManagerConfiguration] which implements
     * [Configuration.Provider]. WorkManager calls [Configuration.Provider.workManagerConfiguration]
     * the first time [WorkManager.getInstance] is invoked — guaranteed to happen
     * after Hilt has injected [HiltWorkerFactory] into the Application.
     *
     * The [HiltWorkerFactory] instance is injected by Hilt into [RecallApplication]:
     *
     *     @HiltAndroidApp
     *     class RecallApplication : Application(), Configuration.Provider {
     *         @Inject lateinit var workerFactory: HiltWorkerFactory
     *
     *         override val workManagerConfiguration: Configuration
     *             get() = workManagerConfiguration
     *     }
     *
     * That Application-level injection is what connects this Configuration
     * to Hilt's component tree.
     */
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        workerFactory: HiltWorkerFactory
    ): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
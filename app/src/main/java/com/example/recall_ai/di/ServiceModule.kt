package com.example.recall_ai.di

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides Android system services and the WorkManager instance as
 * application-scoped singletons.
 *
 * ── Why inject system services? ──────────────────────────────────────────
 * Classes like [AudioFocusHandler], [NotificationChannelManager], and
 * [RecordingNotificationManager] currently resolve these services inline:
 *
 *     private val audioManager =
 *         context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
 *
 * That pattern works at runtime but makes unit testing painful — you need
 * a real [Context] or a Robolectric shadow just to construct the class.
 *
 * By providing them here, any class can instead declare:
 *
 *     class AudioFocusHandler @Inject constructor(
 *         private val audioManager: AudioManager
 *     )
 *
 * and tests can supply a mock [AudioManager] directly, with no Context.
 *
 * The inline getSystemService() calls in existing classes still compile and
 * run correctly — switching to injected system services is an incremental
 * refactor, not a breaking change. These @Provides entries make that refactor
 * available whenever a class is next touched.
 *
 * ── Why provide WorkManager? ─────────────────────────────────────────────
 * [ProcessDeathRecoveryManager], [TranscriptionRepository], and the
 * UI layer all need WorkManager. The current pattern is:
 *
 *     WorkManager.getInstance(context)
 *
 * This static call has no DI seam — it's impossible to replace in tests
 * without byte-code manipulation. Providing it here means:
 *
 *   • Any class that enqueues work can declare WorkManager as a constructor
 *     parameter and receive a mock in tests.
 *   • The WorkManager is guaranteed to be the same instance as the one
 *     configured in [RecallApplication.workManagerConfiguration] because
 *     [WorkManager.getInstance] is idempotent after first initialization.
 *
 * ── WorkManager initialization order ─────────────────────────────────────
 * WorkManager is initialized by [RecallApplication] before any Hilt-injected
 * class is constructed, because Application.onCreate() runs before any
 * Activity, Service, or ViewModel is created. The @Provides below is safe
 * to call at any point after Application.onCreate() completes.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    // ── Android system services ───────────────────────────────────────────

    /**
     * Provides the system [AudioManager] for audio focus management.
     *
     * Currently used inline by [AudioFocusHandler] via context.getSystemService().
     * Providing it here makes the injected form available for the next
     * refactor pass.
     */
    @Provides
    @Singleton
    fun provideAudioManager(
        @ApplicationContext context: Context
    ): AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Provides the system [NotificationManager].
     *
     * Currently used inline by [NotificationChannelManager] and
     * [RecordingNotificationManager]. Providing it here makes the injected
     * form available for future refactoring and testing.
     */
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── WorkManager ───────────────────────────────────────────────────────

    /**
     * Provides the app's [WorkManager] instance.
     *
     * WorkManager.getInstance() is idempotent — calling it multiple times
     * always returns the same instance. Providing it here gives all callers
     * (ProcessDeathRecoveryManager, workers, ViewModels) the same object
     * through the DI graph instead of reaching for a static call.
     *
     * Any class that currently calls WorkManager.getInstance(context) can be
     * refactored to accept WorkManager as a constructor parameter at any time.
     *
     * Example migration:
     *
     *   Before:
     *     class ProcessDeathRecoveryManager @Inject constructor(
     *         @ApplicationContext private val context: Context,
     *         ...
     *     ) {
     *         fun recover() {
     *             WorkManager.getInstance(context).enqueueUniqueWork(...)
     *         }
     *     }
     *
     *   After:
     *     class ProcessDeathRecoveryManager @Inject constructor(
     *         private val workManager: WorkManager,
     *         ...
     *     ) {
     *         fun recover() {
     *             workManager.enqueueUniqueWork(...)
     *         }
     *     }
     */
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
}
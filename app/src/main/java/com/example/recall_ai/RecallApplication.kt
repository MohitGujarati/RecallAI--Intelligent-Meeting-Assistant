package com.example.recall_ai

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.example.recall_ai.service.recovery.ProcessDeathRecoveryManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RecallApplication"

/**
 * Application entry point.
 *
 * Responsibilities:
 *   1. Initialize Hilt (@HiltAndroidApp)
 *   2. Expose WorkManager [Configuration] via [Configuration.Provider]
 *      (the Configuration itself is built and owned by [WorkerModule])
 *   3. Run process-death recovery on every launch (non-blocking, background)
 *
 * ── WorkManager initialization contract ──────────────────────────────────
 * WorkManager's auto-initialization ContentProvider is disabled in the
 * manifest (see WorkerModule KDoc for the required <provider> entry).
 * WorkManager calls [workManagerConfiguration] the first time
 * [WorkManager.getInstance] is invoked — which happens after Hilt has
 * fully injected this Application, so [workManagerConfiguration] is
 * guaranteed to be non-null.
 *
 * ── Recovery scope ────────────────────────────────────────────────────────
 * [applicationScope] uses SupervisorJob so a recovery failure never crashes
 * the app or cancels other coroutines launched on the same scope.
 */
@HiltAndroidApp
class RecallApplication : Application(), Configuration.Provider {

    /**
     * Injected from [WorkerModule.provideWorkManagerConfiguration].
     * Contains the HiltWorkerFactory that lets @HiltWorker classes receive
     * Hilt-managed dependencies.
     */
    @Inject
    override lateinit var  workManagerConfiguration: Configuration

    @Inject lateinit var recoveryManager: ProcessDeathRecoveryManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")
        runProcessDeathRecovery()
    }

    /**
     * Launches recovery asynchronously so it never blocks [Application.onCreate].
     * Recovery reads from Room (disk I/O) and must not run on the main thread.
     */
    private fun runProcessDeathRecovery() {
        applicationScope.launch {
            try {
                val report = recoveryManager.recover()
                if (report.hadAnythingToRecover) {
                    Log.w(TAG, "Process death recovery completed — app was killed unexpectedly")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process death recovery failed (non-fatal)", e)
            }
        }
    }
}
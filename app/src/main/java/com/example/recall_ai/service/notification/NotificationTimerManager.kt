package com.example.recall_ai.service.notification

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationTimerMgr"

/**
 * Drives the 1-second notification timer update on pre-API-36 devices.
 *
 * ── Why not just call notify() inside RecordingService's timer loop? ──
 * RecordingService already has a timer for elapsed seconds. Calling
 * `notificationManager.update(buildRecordingNotification(seconds))`
 * inside that loop works, but couples the timer logic to notification
 * rendering in the service. This class makes the notification timer an
 * independent, testable unit.
 *
 * ── API 36 behaviour ─────────────────────────────────────────────────
 * On API 36+, Android drives the lock-screen timer autonomously via the
 * Live Update mechanism. Calling notify() every second wastes CPU and
 * battery — we skip the polling loop entirely and let the OS handle it.
 *
 * HOWEVER: the status-bar Chronometer on API 36 is also OS-driven, so
 * we still don't need to call notify() for the status bar row either.
 *
 * On API 36+ this class immediately returns from [start] without
 * launching any coroutine. The notification Chronometer set in
 * [LiveUpdateCompat.applyLiveTimer] ticks by itself.
 *
 * ── Pre-API-36 behaviour ──────────────────────────────────────────────
 * Polls every [TICK_INTERVAL_MS] and invokes [onTick] with elapsed seconds.
 * The caller (RecordingService) uses [onTick] to call
 * `notificationManager.applyState(NotificationState.Recording(elapsed))`.
 *
 * We tick every second, but the Chronometer in the notification row
 * actually already ticks smoothly — we're just ensuring the elapsed time
 * text in the body stays accurate.
 *
 * ── Drift correction ────────────────────────────────────────────────
 * Using `delay(1000)` alone accumulates drift because each coroutine
 * resume has variable latency. We track [targetMs] and adjust each
 * delay to account for how late the previous tick was.
 */
@Singleton
class NotificationTimerManager @Inject constructor() {

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L

        /**
         * True if the OS drives the timer autonomously.
         * On API 36+ we skip all notify() calls for the timer.
         */
        val isOsDriven: Boolean get() = Build.VERSION.SDK_INT >= 36
    }

    private var timerJob: Job? = null
    private var startEpochMs: Long = 0L

    /**
     * Starts the timer loop.
     *
     * @param scope          Coroutine scope (tied to the service lifetime)
     * @param sessionStartMs Epoch millis when recording began
     * @param onTick         Lambda called every second with current elapsed seconds.
     *                       On API 36+ this is NEVER called — [isOsDriven] is true.
     */
    fun start(
        scope: CoroutineScope,
        sessionStartMs: Long,
        onTick: suspend (elapsedSeconds: Long) -> Unit
    ) {
        stop()
        startEpochMs = sessionStartMs

        if (isOsDriven) {
            Log.d(TAG, "API 36+ — OS drives timer, no polling needed")
            return
        }

        timerJob = scope.launch {
            var targetMs = sessionStartMs + TICK_INTERVAL_MS
            Log.d(TAG, "Timer started (pre-API-36 polling mode)")

            while (isActive) {
                val now = System.currentTimeMillis()
                val delayMs = (targetMs - now).coerceAtLeast(0L)
                delay(delayMs)

                if (!isActive) break

                val elapsedSeconds = (System.currentTimeMillis() - sessionStartMs) / 1_000L
                onTick(elapsedSeconds)

                // Advance target by exactly one interval regardless of actual delay
                targetMs += TICK_INTERVAL_MS
            }
            Log.d(TAG, "Timer stopped")
        }
    }

    /**
     * Pauses the timer. The coroutine is cancelled but [startEpochMs] is retained
     * so [resume] can calculate correct elapsed seconds.
     */
    fun pause() {
        timerJob?.cancel()
        timerJob = null
        Log.d(TAG, "Timer paused at epoch=$startEpochMs")
    }

    /**
     * Resumes timing after a pause.
     * The elapsed seconds continue counting from [sessionStartMs] — no
     * session time is lost, the timer just catches up.
     */
    fun resume(
        scope: CoroutineScope,
        sessionStartMs: Long,
        onTick: suspend (elapsedSeconds: Long) -> Unit
    ) {
        // Resume = start fresh with the original sessionStartMs
        // Elapsed time naturally includes the pause duration as "lost" seconds.
        // We use the session start (not pause start) so the timer stays accurate
        // relative to the total wall-clock session duration stored in Room.
        start(scope, sessionStartMs, onTick)
    }

    /**
     * Stops and cleans up. Call when the session ends.
     */
    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Returns elapsed seconds since recording began (wall-clock accurate) */
    fun elapsedSeconds(): Long =
        if (startEpochMs == 0L) 0L
        else (System.currentTimeMillis() - startEpochMs) / 1_000L
}
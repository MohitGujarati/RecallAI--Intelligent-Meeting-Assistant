package com.example.recall_ai.service.interruption

import android.content.Context
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "StorageMonitor"

/**
 * Monitors device storage and emits [StorageEvent]s when thresholds are crossed.
 *
 * ── Two-threshold model ───────────────────────────────────────────────
 *
 *   WARNING_THRESHOLD (200 MB): "Running low" — warn but keep recording
 *   CRITICAL_THRESHOLD (50 MB): Stop recording immediately to avoid data loss
 *
 *   At 16kHz mono WAV, the app uses ~32 KB/s = ~1.9 MB/min.
 *   50 MB gives ~26 minutes of buffer for the transcription pipeline
 *   to clean up already-saved chunks before we truly run out.
 *
 * ── StatFs vs File.freeSpace ──────────────────────────────────────────
 * `File.freeSpace` is a convenience wrapper around StatFs but returns
 * the total free bytes INCLUDING reserved blocks. StatFs.availableBytes
 * returns bytes truly available to non-root processes — the correct value.
 *
 * ── Pre-flight check ─────────────────────────────────────────────────
 * [checkBeforeRecording] is a synchronous check called before any
 * AudioRecord is opened. Fails fast before committing resources.
 *
 * ── Mid-session monitoring ───────────────────────────────────────────
 * [startMonitoring] polls every [CHECK_INTERVAL_MS] on the provided scope.
 * Stops itself when critical threshold is crossed to avoid repeated events.
 */
class StorageMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class StorageEvent {
        /** Available bytes are low but not critical. Show a warning. */
        data class LowStorage(val availableBytes: Long) : StorageEvent()

        /** Available bytes hit critical threshold. Stop recording NOW. */
        data class CriticalStorage(val availableBytes: Long) : StorageEvent()
    }

    private val _events = MutableSharedFlow<StorageEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StorageEvent> = _events.asSharedFlow()

    private var monitorJob: Job? = null
    private var lowWarningEmitted = false   // prevent repeated low-storage warnings

    companion object {
        /** Start recording only if we have at least this much free space */
        const val MIN_START_BYTES      = 100L * 1024 * 1024   // 100 MB

        /** Emit [StorageEvent.LowStorage] warning below this level */
        const val WARNING_BYTES        = 200L * 1024 * 1024   // 200 MB

        /** Stop recording immediately below this level */
        const val CRITICAL_BYTES       = 50L * 1024 * 1024    //  50 MB

        /** How often to poll storage during an active session */
        const val CHECK_INTERVAL_MS    = 15_000L              //  15 seconds
    }

    // ── Pre-flight check ────────────────────────────────────────────────

    /**
     * Synchronous check called before starting a recording session.
     * @return a [PreflightResult] describing whether recording can start
     */
    fun checkBeforeRecording(): PreflightResult {
        val available = getAvailableBytes()
        Log.d(TAG, "Pre-flight storage check: ${available / 1024 / 1024} MB free")
        return when {
            available < CRITICAL_BYTES -> PreflightResult.Denied(
                available,
                reason = "Not enough storage to start recording. Free up at least 50 MB."
            )
            available < MIN_START_BYTES -> PreflightResult.Denied(
                available,
                reason = "Recording stopped – Low storage"
            )
            available < WARNING_BYTES -> PreflightResult.Warn(
                available,
                message = "Low storage — recording will stop if storage runs out."
            )
            else -> PreflightResult.OK(available)
        }
    }

    sealed class PreflightResult {
        data class OK(val availableBytes: Long) : PreflightResult()
        data class Warn(val availableBytes: Long, val message: String) : PreflightResult()
        data class Denied(val availableBytes: Long, val reason: String) : PreflightResult()
    }

    // ── Mid-session monitoring ───────────────────────────────────────────

    /**
     * Launches a periodic storage checker.
     * Emits [StorageEvent.LowStorage] once if we dip below WARNING_BYTES.
     * Emits [StorageEvent.CriticalStorage] and stops itself below CRITICAL_BYTES.
     */
    fun startMonitoring(scope: CoroutineScope) {
        stopMonitoring()
        lowWarningEmitted = false

        monitorJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                val available = getAvailableBytes()
                Log.d(TAG, "Storage poll: ${available / 1024 / 1024} MB free")

                when {
                    available < CRITICAL_BYTES -> {
                        Log.e(TAG, "CRITICAL storage: ${available / 1024 / 1024} MB")
                        _events.emit(StorageEvent.CriticalStorage(available))
                        stopMonitoring()   // no more polls needed — session will stop
                        return@launch
                    }
                    available < WARNING_BYTES && !lowWarningEmitted -> {
                        lowWarningEmitted = true
                        Log.w(TAG, "Low storage warning: ${available / 1024 / 1024} MB")
                        _events.emit(StorageEvent.LowStorage(available))
                    }
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Returns bytes available to this app (non-root, non-reserved).
     * Uses StatFs on the app's filesDir partition — where chunks are saved.
     */
    fun getAvailableBytes(): Long {
        return try {
            val statFs = StatFs(context.filesDir.absolutePath)
            statFs.availableBlocksLong * statFs.blockSizeLong
        } catch (e: Exception) {
            Log.e(TAG, "StatFs failed, falling back to File.freeSpace", e)
            context.filesDir.freeSpace
        }
    }

    /**
     * Returns the total size of all files inside a directory.
     * Used by tests and debug screens to show how much the session has consumed.
     */
    fun getSessionDiskUsage(meetingId: Long): Long {
        val dir = File(context.filesDir, "recordings/$meetingId")
        return dir.walkBottomUp()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
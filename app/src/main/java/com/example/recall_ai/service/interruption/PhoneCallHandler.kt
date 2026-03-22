package com.example.recall_ai.service.interruption

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

private const val TAG = "PhoneCallHandler"

/**
 * Monitors phone call state changes and emits [PhoneCallEvent]s via a [SharedFlow].
 *
 * ── Why a dedicated class? ─────────────────────────────────────────────
 * Inline BroadcastReceivers in RecordingService create an untestable god-object.
 * This class has one responsibility, is mockable in unit tests, and its
 * lifecycle is explicit: [register] when service starts, [unregister] on destroy.
 *
 * ── State machine ─────────────────────────────────────────────────────
 *   IDLE ──RINGING/OFFHOOK──▶ IN_CALL
 *   IN_CALL ──IDLE──▶ IDLE  (with 1s debounce to avoid flicker)
 *
 * ── Debounce on IDLE ──────────────────────────────────────────────────
 * Some OEMs fire multiple IDLE transitions (one for each SIM on dual-SIM
 * devices). Without debounce, we'd emit CallEnded twice and try to resume
 * an already-running recording. The [lastCallEndMs] guard ignores a second
 * IDLE event within 1 second of the first.
 */
class PhoneCallHandler @Inject constructor() {

    sealed class PhoneCallEvent {
        /** A call started (ringing or picked up). Recording should pause. */
        object CallStarted : PhoneCallEvent()

        /** All calls ended. Recording may resume. */
        object CallEnded : PhoneCallEvent()
    }

    private val _events = MutableSharedFlow<PhoneCallEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PhoneCallEvent> = _events.asSharedFlow()

    private var isRegistered = false
    private var isInCall = false
    private var lastCallEndMs = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            Log.d(TAG, "Phone state: $state (wasInCall=$isInCall)")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING,
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    if (!isInCall) {
                        isInCall = true
                        _events.tryEmit(PhoneCallEvent.CallStarted)
                        Log.i(TAG, "Call started → emit CallStarted")
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isInCall) {
                        val now = System.currentTimeMillis()
                        // Debounce: ignore duplicate IDLE within 1 second
                        if (now - lastCallEndMs < 1_000L) {
                            Log.d(TAG, "Ignoring duplicate IDLE (debounce)")
                            return
                        }
                        isInCall = false
                        lastCallEndMs = now
                        _events.tryEmit(PhoneCallEvent.CallEnded)
                        Log.i(TAG, "Call ended → emit CallEnded")
                    }
                }
            }
        }
    }

    fun register(context: Context) {
        if (isRegistered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
        Log.d(TAG, "Registered")
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        context.unregisterReceiver(receiver)
        isRegistered = false
        Log.d(TAG, "Unregistered")
    }

    /** Returns true if a call is currently active. Used to block resumption. */
    val isCallActive: Boolean get() = isInCall
}
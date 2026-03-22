package com.example.recall_ai.service.interruption

import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.recall_ai.data.local.entity.AudioSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

private const val TAG = "AudioSourceManager"

/**
 * Tracks the active microphone source and emits change events.
 *
 * ── Detection strategy ─────────────────────────────────────────────────
 * We use TWO mechanisms in parallel for maximum compatibility:
 *
 *   1. BroadcastReceiver (legacy API 24+):
 *      - ACTION_HEADSET_PLUG for wired headsets
 *      - BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED for BT
 *
 *   2. AudioDeviceCallback (API 23+):
 *      - The definitive API — fires for ALL device types
 *      - Also catches USB headsets, hearing aids, and other exotic sources
 *      - Used as the primary truth; broadcast receivers as a fallback
 *
 * ── Priority order ────────────────────────────────────────────────────
 *   Bluetooth > Wired Headset > Built-in Mic
 *   (matches Android's own routing priority)
 *
 * ── Recording continuity ──────────────────────────────────────────────
 * AudioRecord auto-routes to the best available input when the source
 * changes. We do NOT stop/restart AudioRecord. We only:
 *   a) Update our [currentSource] state
 *   b) Update the Room Meeting row via [sourceChangeEvents]
 *   c) Show a transient notification informing the user
 */
class AudioSourceManager @Inject constructor() {

    data class SourceChangedEvent(
        val from: AudioSource,
        val to: AudioSource,
        val displayName: String
    )

    private val _currentSource = MutableStateFlow(AudioSource.BUILT_IN)
    val currentSource: StateFlow<AudioSource> = _currentSource.asStateFlow()

    private val _sourceChangeEvents = MutableSharedFlow<SourceChangedEvent>(extraBufferCapacity = 8)
    val sourceChangeEvents: SharedFlow<SourceChangedEvent> = _sourceChangeEvents.asSharedFlow()

    private var audioManager: AudioManager? = null
    private var isRegistered = false

    private val deviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            Log.d(TAG, "Devices added: ${addedDevices.map { it.type }}")
            reEvaluateSource()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            Log.d(TAG, "Devices removed: ${removedDevices.map { it.type }}")
            reEvaluateSource()
        }
    }

    // Legacy BroadcastReceiver as a belt-and-suspenders fallback
    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> reEvaluateSource()
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> reEvaluateSource()
            }
        }
    }

    fun register(context: Context) {
        if (isRegistered) return

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.registerAudioDeviceCallback(deviceCallback, null)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context, headsetReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        isRegistered = true
        // Evaluate immediately on registration so current source is correct from start
        reEvaluateSource()
        Log.d(TAG, "Registered, initial source: ${_currentSource.value}")
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        runCatching { context.unregisterReceiver(headsetReceiver) }
        isRegistered = false
        Log.d(TAG, "Unregistered")
    }

    // ── Private logic ────────────────────────────────────────────────────

    /**
     * Queries the AudioManager for currently connected input devices and
     * derives the active [AudioSource] using the priority order:
     * Bluetooth > Wired > Built-in.
     */
    private fun reEvaluateSource() {
        val am = audioManager ?: return
        val inputDevices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)

        val newSource = when {
            inputDevices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ->
                AudioSource.BLUETOOTH

            inputDevices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET } ->
                AudioSource.WIRED_HEADSET

            else -> AudioSource.BUILT_IN
        }

        val previous = _currentSource.value
        if (newSource != previous) {
            _currentSource.value = newSource
            val event = SourceChangedEvent(
                from        = previous,
                to          = newSource,
                displayName = newSource.displayName
            )
            _sourceChangeEvents.tryEmit(event)
            Log.i(TAG, "Source changed: $previous → $newSource")
        }
    }
}

/** Human-readable label used in notifications */
val AudioSource.displayName: String
    get() = when (this) {
        AudioSource.BLUETOOTH     -> "Bluetooth headset"
        AudioSource.WIRED_HEADSET -> "Wired headset"
        AudioSource.BUILT_IN      -> "Built-in microphone"
    }
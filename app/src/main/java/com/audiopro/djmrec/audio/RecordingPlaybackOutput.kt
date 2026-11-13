package com.audiopro.djmrec.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.audiopro.djmrec.DjmRecApplication

/** Selects and reports Android's route for one player; routing does not prove audible output. */
internal class RecordingPlaybackOutput(
    context: Context,
    private val player: MediaPlayer,
    private val onOutputChanged: (String) -> Unit
) : AutoCloseable {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val usbAudioManager = (context.applicationContext as DjmRecApplication).usbAudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var reportedDeviceId = -1

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    private val routing = AudioRouting.OnRoutingChangedListener { reportOutput() }

    fun start() {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        active = true
        audioManager.registerAudioDeviceCallback(devices, handler)
        player.addOnRoutingChangedListener(routing, handler)
        refresh()
    }

    fun refresh() {
        if (!active) return
        val output = preferredUsbOutput()
        if (player.preferredDevice?.id != output?.id) {
            val accepted = player.setPreferredDevice(output)
            Log.i(TAG, "Preferred output: ${output?.productName ?: "system default"}, id=${output?.id}, accepted=$accepted")
        }
        reportOutput()
    }

    fun reportOutput() {
        if (!active) return
        val output = player.routedDevice
        val deviceId = output?.id ?: 0
        if (reportedDeviceId == deviceId) return
        reportedDeviceId = deviceId
        val label = when (output) {
            null -> "System default"
            else -> when (output.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
                else -> output.productName.toString().ifBlank { "System audio" }
            }
        }
        onOutputChanged(label)
        if (output != null) Log.i(TAG, "System route: $label, id=${output.id}, type=${output.type}")
    }

    private fun preferredUsbOutput(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val recorder = usbAudioManager.deviceState.value ?: return outputs.singleOrNull()
        val input = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.id == recorder.audioManagerDeviceId }
        val address = input?.address?.takeIf { it.isNotBlank() }
        outputs.singleOrNull {
            it.address == recorder.deviceName || (address != null && it.address == address)
        }?.let { return it }
        // Android often prefixes the USB product with "USB-Audio - ". Do not guess between
        // multiple devices with the same product name.
        return outputs.singleOrNull {
            recorder.productName.isNotBlank() && it.productName.toString().contains(recorder.productName, ignoreCase = true)
        }
    }

    override fun close() {
        if (!active) return
        active = false
        audioManager.unregisterAudioDeviceCallback(devices)
        player.removeOnRoutingChangedListener(routing)
    }

    private companion object {
        const val TAG = "RecordingPlaybackOutput"
    }
}

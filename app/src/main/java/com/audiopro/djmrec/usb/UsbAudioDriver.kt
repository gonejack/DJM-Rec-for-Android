package com.audiopro.djmrec.usb

/** Rebuilds CB5 system audio, optionally leaving selected inputs owned by direct capture. */
internal object UsbAudioDriver {
    init {
        System.loadLibrary("djmrec_audio")
    }

    external fun restoreSystemAudio(fd: Int, controlInterface: Int, preservedCaptureInterfaces: IntArray): Boolean
}

package com.audiopro.djmrec.usb

/**
 * UAC2 A.14: direction is in bmRequestType, not in bRequest as it was in UAC1. See https://github.com/torvalds/linux/blob/master/include/linux/usb/audio-v2.h
 */
internal object Uac2ClockControl {
    const val REQUEST_RANGE = 0x02

    fun parseSampleRates(response: ByteArray): List<Int> {
        if (response.size < 2) return emptyList()
        val count = (response[0].toInt() and 0xFF) or ((response[1].toInt() and 0xFF) shl 8)
        // Do not treat a truncated range list as a complete statement of supported rates.
        if (count == 0 || response.size < 2 + count * 12) return emptyList()
        return (0 until count)
            .flatMap { index ->
                val base = 2 + index * 12
                CaptureFormatPolicy.ratesInRange(le32(response, base), le32(response, base + 4), le32(response, base + 8))
            }
            .distinct()
    }

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

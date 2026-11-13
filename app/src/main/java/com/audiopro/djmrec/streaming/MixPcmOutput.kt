package com.audiopro.djmrec.streaming

/** Native monitoring stays stereo; identical Mix sources need only one sample per AAC frame. */
internal object MixPcmOutput {
    fun pack(stereo: ByteArray, outputChannels: Int): ByteArray {
        require(outputChannels in 1..2 && stereo.size % 4 == 0)
        if (outputChannels == 2) return stereo
        return ByteArray(stereo.size / 2).also { mono ->
            for (frame in 0 until stereo.size / 4) {
                mono[frame * 2] = stereo[frame * 4]
                mono[frame * 2 + 1] = stereo[frame * 4 + 1]
            }
        }
    }
}

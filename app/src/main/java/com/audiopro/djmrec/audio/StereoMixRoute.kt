package com.audiopro.djmrec.audio

/** Channel indices address the processed tracks in selection order: 1 L, 1 R, 2 L, 2 R… */
data class StereoMixRoute(val leftChannel: Int = 0, val rightChannel: Int = 1) {
    val outputChannelCount: Int
        get() = if (leftChannel != NONE && leftChannel == rightChannel) 1 else 2

    fun validated(trackCount: Int): StereoMixRoute {
        val channels = trackCount.coerceAtLeast(1) * 2
        return StereoMixRoute(
            leftChannel.takeIf { it == NONE || it in 0 until channels } ?: 0, rightChannel.takeIf { it == NONE || it in 0 until channels } ?: 1)
    }

    companion object {
        const val NONE = -1

        fun sourceLabel(channel: Int): String =
            when (channel) {
                NONE -> "None"
                else -> "Track ${channel / 2 + 1} ${if (channel % 2 == 0) "L" else "R"}"
            }
    }
}

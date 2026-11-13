package com.audiopro.djmrec.audio

import kotlin.math.pow

/** Read-only timeline consumed by the waveform renderer. */
interface WaveformHistory {
    val capacity: Int
    val size: Int
    fun value(index: Int, band: Int): Float
}

/**
 * One side of the monitoring Mix, including history captured before its route changed.
 * The source is an individual channel after Track gain; null represents None.
 */
class MixWaveformHistory(
    private val source: TrackWaveformHistory?,
    gainDb: Float = 0f
) : WaveformHistory {
    override val capacity: Int = source?.capacity ?: 640
    override val size: Int get() = source?.size ?: 0
    private val gain = 10f.pow(gainDb / 20f)

    override fun value(index: Int, band: Int): Float {
        val level = source?.value(index, band) ?: 0f
        val scale = if (band == 3) gain.pow(1.65f) else gain
        return (level * scale).coerceIn(0f, 1f)
    }
}

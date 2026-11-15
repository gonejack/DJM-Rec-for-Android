package com.audiopro.djmrec.audio

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

class MixWaveformHistoryTest {
    private fun history(value: Float) = TrackWaveformHistory().apply { accept(snapshot(512, value)) }

    private fun snapshot(cursor: Int, value: Float) =
        FloatArray(TrackWaveformHistory.SNAPSHOT_FLOATS) { value }
            .also {
                it[2048] = cursor.toFloat()
                it[2049] = 6.125f
            }

    @Test
    fun `changing a source redraws existing history without waiting for new audio`() {
        val first = MixWaveformHistory(history(0.2f))
        val changed = MixWaveformHistory(history(0.7f))
        assertEquals(first.size, changed.size)
        assertEquals(0.2f, first.value(20, 1))
        assertEquals(0.7f, changed.value(20, 1))
    }

    @Test
    fun `left and right preserve their own waveforms instead of sharing the louder side`() {
        val left = MixWaveformHistory(history(0.2f))
        val right = MixWaveformHistory(history(0.7f))
        assertEquals(0.2f, left.value(20, 2))
        assertEquals(0.7f, right.value(20, 2))
        val mutedRight = MixWaveformHistory(null)
        assertEquals(0, mutedRight.size)
        assertEquals(0f, mutedRight.value(20, 2))
        assertEquals(0.2f, left.value(20, 2))
    }

    @Test
    fun `selecting the same mono source for both sides does not double either lane`() {
        val source = history(0.4f)
        val left = MixWaveformHistory(source)
        val right = MixWaveformHistory(source)
        assertEquals(0.4f, left.value(20, 0))
        assertEquals(left.value(20, 0), right.value(20, 0))
    }

    @Test
    fun `half-decibel mix gain scales each band without integer truncation`() {
        val preview = MixWaveformHistory(history(0.2f), 0.5f)
        val gain = 10f.pow(0.5f / 20f)
        assertEquals(0.2f * gain, preview.value(20, 1), 1e-6f)
        assertEquals(0.2f * gain.pow(1.65f), preview.value(20, 3), 1e-6f)
        assertEquals(1f, MixWaveformHistory(history(0.8f), 12f).value(20, 1))
    }

    @Test
    fun `each lane follows new audio appended to its source`() {
        val source = history(0.1f)
        val preview = MixWaveformHistory(source)
        source.accept(snapshot(520, 0.8f))
        assertEquals(65, preview.size)
        assertEquals(0.1f, preview.value(0, 0))
        assertEquals(0.8f, preview.value(64, 0))
    }
}

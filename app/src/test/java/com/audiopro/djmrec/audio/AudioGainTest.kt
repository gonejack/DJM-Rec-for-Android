package com.audiopro.djmrec.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioGainTest {
    @Test
    fun `track and mix gain retain half-decibel values`() {
        assertEquals(0.5f, AudioGain.snap(0.5f))
        assertEquals(3.5f, AudioGain.snap(3.5f))
        assertEquals(11.5f, AudioGain.snap(11.5f))
        assertEquals(3.5f, AudioGain.snap(3.62f))
        assertEquals(4f, AudioGain.snap(3.8f))
    }

    @Test
    fun `old integer preferences and new float preferences both load`() {
        assertEquals(6f, AudioGain.fromStored(6))
        assertEquals(0.5f, AudioGain.fromStored(0.5f))
        assertEquals(12f, AudioGain.fromStored(null))
    }

    @Test
    fun `gain stays in the supported range`() {
        assertEquals(0f, AudioGain.snap(-12f))
        assertEquals(12f, AudioGain.snap(24f))
        assertEquals(0f, AudioGain.snap(Float.NaN))
    }

    @Test
    fun `all gain labels use one decimal place`() {
        assertEquals("0.0 dB", AudioGain.format(0f))
        assertEquals("+0.5 dB", AudioGain.format(0.5f))
        assertEquals("+12.0 dB", AudioGain.format(12f))
    }
}

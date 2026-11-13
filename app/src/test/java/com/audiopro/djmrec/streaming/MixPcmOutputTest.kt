package com.audiopro.djmrec.streaming

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MixPcmOutputTest {
    @Test
    fun `mono packs one signed PCM16 sample per frame in order`() {
        val stereo = byteArrayOf(0x34, 0x12, 0x34, 0x12, 0, -128, 0, -128, -1, 127, -1, 127)
        val original = stereo.copyOf()
        assertContentEquals(byteArrayOf(0x34, 0x12, 0, -128, -1, 127), MixPcmOutput.pack(stereo, 1))
        assertContentEquals(original, stereo)
    }

    @Test
    fun `stereo preserves a None side as silence`() {
        val stereo = byteArrayOf(0x34, 0x12, 0, 0, -1, 127, 0, 0)
        assertSame(stereo, MixPcmOutput.pack(stereo, 2))
        assertContentEquals(ByteArray(8), MixPcmOutput.pack(ByteArray(8), 2))
    }

    @Test
    fun `mono and stereo clocks keep the same duration for the same frame count`() {
        for (rate in listOf(44100, 48000, 96000)) {
            val mono = PcmFrameClock(rate, 1)
            val stereo = PcmFrameClock(rate, 2)
            assertEquals(stereo.next(4096, 1_000_000), mono.next(2048, 1_000_000))
            assertEquals(stereo.next(4096, 1_000_001), mono.next(2048, 1_000_001))
        }
    }

    @Test
    fun `incomplete frames and unsupported channel counts are rejected`() {
        assertFailsWith<IllegalArgumentException> { MixPcmOutput.pack(ByteArray(3), 1) }
        assertFailsWith<IllegalArgumentException> { MixPcmOutput.pack(ByteArray(4), 3) }
        assertFailsWith<IllegalArgumentException> { PcmFrameClock(48000, 1).next(3, 0) }
    }
}

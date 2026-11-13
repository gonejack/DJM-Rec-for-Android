package com.audiopro.djmrec.usb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Uac2ClockControlTest {
    @Test
    fun parsesDiscreteAndContinuousRangesWithoutInventingRates() {
        val response = range(44_100, 96_000, 0) + range(192_000, 192_000, 0)
        assertEquals(listOf(44_100, 48_000, 88_200, 96_000, 192_000),
            Uac2ClockControl.parseSampleRates(byteArrayOf(2, 0) + response))
        assertEquals(listOf(48_000, 96_000), Uac2ClockControl.parseSampleRates(
            byteArrayOf(1, 0) + range(48_000, 96_000, 48_000)))
    }

    @Test
    fun rejectsShortMalformedAndPartialRangeLists() {
        listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(0, 0),
            byteArrayOf(2, 0) + range(48_000, 48_000, 0),
            byteArrayOf(1, 0) + range(96_000, 48_000, 0),
            byteArrayOf(1, 0) + range(-1, -1, 0)).forEach {
            assertTrue(Uac2ClockControl.parseSampleRates(it).isEmpty())
        }
    }

    private fun range(min: Int, max: Int, step: Int): ByteArray =
        listOf(min, max, step).flatMap { value -> (0..3).map { (value ushr (it * 8)).toByte() } }.toByteArray()
}

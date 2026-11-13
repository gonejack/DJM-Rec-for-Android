package com.audiopro.djmrec.usb

import kotlin.test.Test
import kotlin.test.assertEquals

class UsbInputSelectionOrderTest {
    @Test
    fun `check order owns track order even when interface numbers descend`() {
        var selected = emptyList<Int>()
        for (number in listOf(3, 1, 2)) selected = UsbCaptureInputs.toggleSelection(selected, number)
        assertEquals(listOf(3, 1, 2), selected)
        assertEquals(selected, UsbCaptureInputs.restoreSelection(selected.joinToString(",")))
    }

    @Test
    fun `uncheck closes the gap and recheck appends the input`() {
        val removed = UsbCaptureInputs.toggleSelection(listOf(3, 1, 2), 1)
        assertEquals(listOf(3, 2), removed)
        assertEquals(listOf(3, 2, 1), UsbCaptureInputs.toggleSelection(removed, 1))
    }

    @Test
    fun `stored selection ignores invalid and duplicate entries without sorting`() {
        assertEquals(listOf(3, 1, 2), UsbCaptureInputs.restoreSelection("3,invalid,1,3,2,"))
    }
}

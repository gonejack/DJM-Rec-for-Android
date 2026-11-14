package com.audiopro.djmrec.usb

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CubiluxCaptureInputTest {
    private val raw = checkNotNull(javaClass.getResourceAsStream("/cubilux-cb5/usb-configuration.bin")).use { it.readBytes() }
    private val topology = UsbAudioDescriptorParser.parseTopology(raw)
    private val inputs = UsbCaptureInputs.group(topology.audioStreamingInterfaces, 0x0BDA, 0x4F26)

    @Test
    fun usesTheUnmodifiedHardwareCapture() {
        assertEquals(930, raw.size)
        assertEquals("25ca891c80134ab3245d32eec2c721d2466dc0379735a989185b568ee60283d7",
            MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun separatesThreeInputsFromTheirSixPcmFormats() {
        assertEquals(listOf(1, 2, 3), inputs.map { it.interfaceNumber })
        assertEquals(listOf("MIC1", "Line In", "MIC2"), inputs.map { it.name })
        assertEquals(6, topology.audioStreamingInterfaces.size)
        inputs.forEach { input ->
            assertEquals(listOf(1, 2), input.formats.map { it.alternateSetting })
            assertEquals(listOf(16, 24), input.formats.map { it.bitResolution })
            assertEquals(listOf(2, 3), input.formats.map { it.subframeSize })
            assertTrue(input.formats.all { it.channelCount == 2 })
        }
        assertEquals(listOf(0x81, 0x82, 0x83), inputs.map { it.preferredFormat!!.isochronousInEndpointAddress })
    }

    @Test
    fun lineAndMic2SelectTheirOwnEndpointTerminalAndClock() {
        listOf(Triple(1, 0x81, 90), Triple(2, 0x82, 186), Triple(3, 0x83, 90)).forEach { (number, endpoint, packet) ->
            val selected = checkNotNull(UsbCaptureInputs.select(inputs, number))
            assertEquals(number, selected.interfaceNumber)
            assertEquals(2, selected.alternateSetting)
            assertEquals(endpoint, selected.isochronousInEndpointAddress)
            assertEquals(packet, selected.isochronousInMaxPacketSize)
            assertEquals(0x10 + number, selected.terminalLink)
            val clock = checkNotNull(UsbCaptureInputs.clockFor(selected, topology))
            assertEquals(number, clock.id)
            assertEquals(0, clock.interfaceNumber)
            assertTrue(clock.supportsFrequencyControl)
            assertTrue(clock.supportsFrequencySet)
        }
    }

    @Test
    fun remembersInterfaceAcrossAChangedConnectionPathWithoutGuessingAMissingInput() {
        val key = UsbCaptureInputs.preferenceKey(0x0BDA, 0x4F26)
        val preferences = mutableMapOf(key to 3)
        assertEquals(3, UsbCaptureInputs.select(inputs, preferences[key])!!.interfaceNumber)
        assertNull(UsbCaptureInputs.select(inputs.filter { it.interfaceNumber != 3 }, preferences[key]))
        assertNotEquals(key, UsbCaptureInputs.preferenceKey(0x0BDA, 0x4F27))
        assertEquals(1, UsbCaptureInputs.select(inputs, null)!!.interfaceNumber)
    }

    @Test
    fun prefersUsbInterfaceNamesAndLimitsFallbackNamesToTheVerifiedModel() {
        assertEquals("External mic", UsbCaptureInputs.group(topology.audioStreamingInterfaces, 0x0BDA, 0x4F26,
            mapOf(3 to "External mic")).last().name)
        assertEquals("Input 3", UsbCaptureInputs.group(topology.audioStreamingInterfaces, 1, 2).last().name)
    }

    @Test
    fun multiInputDeviceRequiresRawUsbEvenWhenAndroidHasRegisteredIt() {
        val info = device(2)
        assertTrue(info.requiresIsoCapture)
        assertFalse(info.useAndroidCapture(true))
        assertFalse(info.useAndroidCapture(false))
        assertEquals("CB5 1/3", info.captureLabel)
        // A single stereo stream retains the ordinary Android routing policy.
        assertFalse(info.copy(captureInputs = inputs.take(1)).requiresIsoCapture)
        val multichannel = info.copy(captureInputs = emptyList(), channelCount = 8)
        assertTrue(multichannel.requiresIsoCapture)
        assertTrue(multichannel.useAndroidCapture(true))
    }

    @Test
    fun pickerDistinguishesPortsAndMultipleIdenticalConnectedDevices() {
        val line = UsbInputOption("/dev/bus/usb/001/001", "Line In", true, true, 2)
        val mic = line.copy(interfaceNumber = 3)
        val secondDevice = line.copy(deviceName = "/dev/bus/usb/001/002")
        assertNotEquals(line.selectionKey, mic.selectionKey)
        assertNotEquals(line.selectionKey, secondDevice.selectionKey)
        assertTrue(line.matches(device(2)))
        assertFalse(mic.matches(device(2)))
        assertFalse(secondDevice.matches(device(2)))
    }

    @Test
    fun selectedPortsAreAllMarkedWithACompactDeviceLabel() {
        val selected = device(1).copy(selectedInterfaceNumbers = listOf(1, 2, 3))
        assertEquals("CB5 3/3", selected.captureLabel)
        assertTrue(UsbInputOption(selected.deviceName, "MIC1", true, true, 1).matches(selected))
        assertTrue(UsbInputOption(selected.deviceName, "Line In", true, true, 2).matches(selected))
        assertTrue(UsbInputOption(selected.deviceName, "MIC2", true, true, 3).matches(selected))
        assertFalse(UsbInputOption("another-device", "MIC2", true, true, 3).matches(selected))
    }

    private fun device(number: Int): UsbAudioDeviceInfo {
        val format = checkNotNull(UsbCaptureInputs.select(inputs, number))
        return UsbAudioDeviceInfo(
            deviceName = "/dev/bus/usb/001/001", productName = "CB5", vendorId = 0x0BDA, productId = 0x4F26,
            streamingInterfaceNumber = number, activeAlternateSetting = format.alternateSetting,
            isochronousInEndpointAddress = format.isochronousInEndpointAddress!!,
            isochronousInMaxPacketSize = format.isochronousInMaxPacketSize!!,
            channelCount = format.channelCount, bitResolution = format.bitResolution, subframeSize = format.subframeSize,
            supportedSampleRates = emptyList(), audioManagerDeviceId = 42, captureInputs = inputs
        )
    }
}

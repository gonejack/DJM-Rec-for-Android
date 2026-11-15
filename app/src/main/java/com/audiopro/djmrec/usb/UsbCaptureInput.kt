package com.audiopro.djmrec.usb

/** One physical capture input. Alternate settings are formats of this input, not more inputs. */
data class UsbCaptureInput(val interfaceNumber: Int, val name: String, val formats: List<AudioStreamingInterfaceInfo>) {
    val preferredFormat: AudioStreamingInterfaceInfo?
        get() = UsbAudioDescriptorParser.selectBestStereoInterface(formats)
}

object UsbCaptureInputs {
    /** Removing keeps the remaining order; selecting again appends a new last track. */
    fun toggleSelection(selected: List<Int>, interfaceNumber: Int): List<Int> =
        if (interfaceNumber in selected) selected - interfaceNumber else selected + interfaceNumber

    fun restoreSelection(saved: String): List<Int> = saved.split(',').mapNotNull { it.toIntOrNull() }.distinct()

    fun group(formats: List<AudioStreamingInterfaceInfo>, vendorId: Int, productId: Int, interfaceNames: Map<Int, String> = emptyMap()): List<UsbCaptureInput> =
        formats
            .groupBy { it.interfaceNumber }
            .toSortedMap()
            .mapNotNull { (number, alts) ->
                if (UsbAudioDescriptorParser.selectBestStereoInterface(alts) == null) return@mapNotNull null
                val name =
                    interfaceNames[number]?.trim()?.takeIf { it.isNotEmpty() }
                        ?: if (vendorId == 0x0BDA && productId == 0x4F26) {
                            when (number) {
                                1 -> "MIC1"
                                2 -> "Line In"
                                3 -> "MIC2"
                                else -> null
                            }
                        } else null
                UsbCaptureInput(number, name ?: "Input $number", alts)
            }

    /** A remembered/explicit input must never silently resolve to a different interface. */
    fun select(inputs: List<UsbCaptureInput>, interfaceNumber: Int?): AudioStreamingInterfaceInfo? =
        if (interfaceNumber != null) inputs.firstOrNull { it.interfaceNumber == interfaceNumber }?.preferredFormat
        else UsbAudioDescriptorParser.selectBestStereoInterface(inputs.flatMap { it.formats })

    // The port preference is shared by identical models. The connected device is always selected
    // by its current USB path; a saved path must not choose an arbitrary same-model device.
    fun preferenceKey(vendorId: Int, productId: Int) = "usb_input_interface_${vendorId}_${productId}"

    fun clockFor(streaming: AudioStreamingInterfaceInfo, topology: UacTopology): ClockSourceInfo? {
        val terminal = (topology.inputTerminals + topology.outputTerminals).firstOrNull { it.id == streaming.terminalLink }
        return topology.clockSources.firstOrNull { it.id == terminal?.clockSourceId }
    }
}

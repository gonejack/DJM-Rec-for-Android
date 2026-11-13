package com.audiopro.djmrec.storage

import com.audiopro.djmrec.audio.RecordingFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackFileNamesTest {
    @Test
    fun `selected inputs share the UI track names for every supported track format`() {
        for (number in 1..3) {
            for (format in listOf(RecordingFormat.WAV, RecordingFormat.FLAC)) {
                assertEquals("Track $number.${format.extension}",
                    RecordingOutputManager.trackDisplayName(number, "Track $number", format, 1))
            }
        }
    }

    @Test
    fun `rollover keeps the same track identity and part as the mix`() {
        assertEquals("Track 1_part02.wav",
            RecordingOutputManager.trackDisplayName(1, "Track 1", RecordingFormat.WAV, 2))
        assertEquals("mix_session_part02.wav",
            RecordingOutputManager.displayName("session", RecordingFormat.WAV, 2))
    }

    @Test
    fun `mixer source labels remain sanitized`() {
        assertEquals("02 CH1 Post-fader.wav",
            RecordingOutputManager.trackDisplayName(2, "CH1 / Post-fader", RecordingFormat.WAV, 1))
    }
}

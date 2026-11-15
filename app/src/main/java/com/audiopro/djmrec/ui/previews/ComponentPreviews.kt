package com.audiopro.djmrec.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.audiopro.djmrec.audio.ChannelLevel
import com.audiopro.djmrec.audio.RecordingFormat
import com.audiopro.djmrec.audio.StereoLevels
import com.audiopro.djmrec.ui.components.ChannelPairSelector
import com.audiopro.djmrec.ui.components.FormatSelector
import com.audiopro.djmrec.ui.components.RgbWaveform
import com.audiopro.djmrec.ui.components.StereoVuMeter
import com.audiopro.djmrec.ui.theme.BackgroundDark
import com.audiopro.djmrec.ui.theme.DjmRecTheme

/**
 * IDE-only previews for the stateless building blocks. The real screens take a [com.audiopro.djmrec.ui.MainViewModel], which needs a live Application, USB host
 * and audio engine, so they cannot be rendered in the preview sandbox. These previews cover the pieces that only need plain data, which is where most visual
 * tuning happens.
 */

// Fake meter data: left at -8 dB, right at -2 dB (close to clipping).
private val PreviewLevels =
    StereoLevels(left = ChannelLevel(peakDb = -8f, rmsDb = -14f, isClipping = false), right = ChannelLevel(peakDb = -2f, rmsDb = -9f, isClipping = false))

@Preview(name = "VU meter", widthDp = 360, heightDp = 120, showBackground = true, backgroundColor = 0xFF101416)
@Composable
private fun StereoVuMeterPreview() {
    DjmRecTheme { StereoVuMeter(levels = PreviewLevels, modifier = Modifier.background(BackgroundDark).padding(16.dp)) }
}

@Preview(name = "Format selector", widthDp = 360, heightDp = 110, showBackground = true, backgroundColor = 0xFF101416)
@Composable
private fun FormatSelectorPreview() {
    DjmRecTheme {
        FormatSelector(
            selected = RecordingFormat.WAV,
            formats = RecordingFormat.entries,
            enabled = true,
            onSelect = {},
            modifier = Modifier.background(BackgroundDark).padding(16.dp))
    }
}

@Preview(name = "USB channel pairs", widthDp = 360, heightDp = 140, showBackground = true, backgroundColor = 0xFF101416)
@Composable
private fun ChannelPairSelectorPreview() {
    DjmRecTheme {
        ChannelPairSelector(selectedOffset = 2, pairCount = 5, enabled = true, onSelect = {}, modifier = Modifier.background(BackgroundDark).padding(16.dp))
    }
}

@Preview(name = "3-band waveform", widthDp = 360, heightDp = 180, showBackground = true, backgroundColor = 0xFF101416)
@Composable
private fun RgbWaveformPreview() {
    val bins = remember { previewBins() }
    DjmRecTheme { RgbWaveform(bins = bins, modifier = Modifier.padding(16.dp).fillMaxWidth().height(120.dp)) }
}

/** RgbWaveform expects groups of four floats: index 1/2/3 are low/mid/high, each in 0..1. */
private fun previewBins(): FloatArray {
    val bins = FloatArray(2048)
    for (i in 0 until 512) {
        val t = i / 512f
        val pulse = kotlin.math.sin(t * 18f).let { it * it }
        val envelope = 0.35f + 0.65f * pulse
        bins[i * 4 + 1] = envelope
        bins[i * 4 + 2] = envelope * 0.7f
        bins[i * 4 + 3] = envelope * 0.45f
    }
    return bins
}

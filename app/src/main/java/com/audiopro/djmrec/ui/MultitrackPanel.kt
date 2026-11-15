package com.audiopro.djmrec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiopro.djmrec.audio.AudioGain
import com.audiopro.djmrec.audio.ChannelLevel
import com.audiopro.djmrec.audio.MixWaveformHistory
import com.audiopro.djmrec.audio.RecordingState
import com.audiopro.djmrec.audio.StereoLevels
import com.audiopro.djmrec.audio.StereoMixRoute
import com.audiopro.djmrec.audio.TrackWaveformHistory
import com.audiopro.djmrec.audio.WaveformHistory
import com.audiopro.djmrec.ui.components.StereoVuMeter
import com.audiopro.djmrec.ui.components.TimelineRuler
import com.audiopro.djmrec.ui.components.TimelineWaveform
import com.audiopro.djmrec.ui.components.TrackMeter
import com.audiopro.djmrec.ui.theme.AccentAmber
import com.audiopro.djmrec.ui.theme.AccentRed
import com.audiopro.djmrec.ui.theme.OutlineSubtle
import com.audiopro.djmrec.ui.theme.SurfaceVariantDark
import com.audiopro.djmrec.ui.theme.TextPrimary
import com.audiopro.djmrec.ui.theme.TextSecondary

private val floorLevels = ChannelLevel(-60f, -60f, false).let { StereoLevels(it, it) }

/**
 * Advanced mode: every input pair of the device as a track lane with its own arm button, gain and live 3-band waveform, with the master (the set itself) pinned
 * on top.
 */
@Composable
fun MultitrackPanel(viewModel: MainViewModel, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    val tracks by viewModel.multitrackTracks.collectAsState()
    val levels by viewModel.trackLevels.collectAsState()
    val masterLevels by viewModel.levels.collectAsState()
    val state by viewModel.recordingState.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val elapsed by viewModel.elapsedMillis.collectAsState()
    val active by viewModel.multitrackActive.collectAsState()
    val masterGain by viewModel.recordingGainDb.collectAsState()
    val mixRoute by viewModel.mixRoute.collectAsState()
    val liveState by viewModel.liveStreamState.collectAsState()
    val independentInputs = tracks.firstOrNull()?.inputNumber != null
    val revision = viewModel.timelineRevision.collectAsState()
    val recording = state is RecordingState.Recording || state is RecordingState.Paused
    val locked = saving || recording || state is RecordingState.Preparing

    DisposableEffect(Unit) {
        viewModel.setMultitrackVisible(true)
        viewModel.setWaveformVisible(true)
        onDispose {
            viewModel.setMultitrackVisible(false)
            viewModel.setWaveformVisible(false)
        }
    }
    val timeline = viewModel.multitrackTimeline
    val (mixLeftPreview, mixRightPreview) =
        remember(mixRoute, masterGain) {
            MixWaveformHistory(timeline.channel(mixRoute.leftChannel), masterGain) to MixWaveformHistory(timeline.channel(mixRoute.rightChannel), masterGain)
        }
    MultitrackContent(
        tracks = tracks,
        trackLevels = levels,
        masterLevels = masterLevels,
        historyFor = { if (it.isMaster) timeline.master else timeline.track(it.index) },
        revision = revision,
        spanMillis = timeline.master.capacity * timeline.master.columnMillis,
        elapsedMillis = elapsed,
        recording = recording,
        locked = locked,
        gainEnabled = AudioGain.canAdjust(state, saving),
        active = active,
        masterGainDb = masterGain,
        mixRoute = mixRoute.takeIf { independentInputs },
        mixLeftHistory = if (!recording || mixRoute.leftChannel == StereoMixRoute.NONE) mixLeftPreview else timeline.mixLeft,
        mixRightHistory = if (!recording || mixRoute.rightChannel == StereoMixRoute.NONE) mixRightPreview else timeline.mixRight,
        mixSourcesLocked = locked || liveState.isActive,
        onMixSource = viewModel::setMixSource,
        onArm = viewModel::setTrackArmed,
        onSource = viewModel::setTrackSource,
        onGain = viewModel::setTrackGainDb,
        onOpenSetup = onOpenSetup,
        modifier = modifier)
}

/** Stateless multitrack view: everything it shows comes in as parameters. */
@Composable
fun MultitrackContent(
    tracks: List<MultitrackTrack>,
    trackLevels: List<StereoLevels>,
    masterLevels: StereoLevels,
    historyFor: (MultitrackTrack) -> TrackWaveformHistory,
    revision: State<Long>,
    spanMillis: Double,
    elapsedMillis: Long,
    recording: Boolean,
    locked: Boolean,
    gainEnabled: Boolean,
    active: Boolean,
    masterGainDb: Float,
    mixRoute: StereoMixRoute?,
    mixLeftHistory: WaveformHistory,
    mixRightHistory: WaveformHistory,
    mixSourcesLocked: Boolean,
    onMixSource: (left: Boolean, channel: Int) -> Unit,
    onArm: (track: Int, armed: Boolean) -> Unit,
    onSource: (track: Int, source: Int) -> Unit,
    onGain: (track: Int, gainDb: Float, persist: Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetTrack by rememberSaveable { mutableStateOf<Int?>(null) }
    if (tracks.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                "This input has a single stereo pair, so there is nothing to record next to the master. " +
                    "Advanced mode needs a mixer or interface with more than 2 channels.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary)
        }
        return
    }

    // Physical inputs already arrive in selection order, which owns their track numbers.
    val ordered = remember(tracks) { if (tracks.first().inputNumber != null) tracks else tracks.sortedByDescending { it.isMaster } }
    val armedCount = tracks.count { it.armed }
    BoxWithConstraints(modifier) {
        // Keep both microphones visible on phones; larger panels give the waveforms more height.
        val mixHeight = if (mixRoute != null) 182.dp else 0.dp
        val headingHeight = if (mixRoute != null) 20.dp else 48.dp
        val waveformHeight = ((maxHeight - headingHeight - mixHeight) / minOf(ordered.size, 2) - 56.dp).coerceIn(40.dp, 112.dp)
        Column(Modifier.fillMaxSize()) {
            if (mixRoute == null)
                Row(
                    Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TRACKS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            when {
                                !active -> "Starting input..."
                                tracks.first().inputNumber != null -> "${tracks.size} inputs selected"
                                else -> "$armedCount armed + mix"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1)
                    }
            TimelineRuler(
                spanMillis = spanMillis,
                elapsedMillis = elapsedMillis,
                recording = recording,
                modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 4.dp))
            HorizontalDivider(color = OutlineSubtle)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(ordered, key = { it.index }, contentType = { "track" }) { track ->
                    TrackRow(
                        track = track,
                        levels = if (track.isMaster) masterLevels else trackLevels.getOrNull(track.index) ?: floorLevels,
                        history = historyFor(track),
                        revision = revision,
                        masterGainDb = masterGainDb,
                        recording = recording,
                        locked = locked,
                        gainEnabled = gainEnabled,
                        waveformHeight = waveformHeight,
                        onArm = { onArm(track.index, it) },
                        onSource = { onSource(track.index, it) },
                        onOpenTrack = { if (track.isMaster) onOpenSetup() else sheetTrack = track.index })
                    HorizontalDivider(color = OutlineSubtle)
                }
            }
            if (mixRoute != null) {
                HorizontalDivider(color = OutlineSubtle)
                MixTrackRow(
                    tracks = tracks,
                    route = mixRoute,
                    levels = masterLevels,
                    leftHistory = mixLeftHistory,
                    rightHistory = mixRightHistory,
                    revision = revision,
                    gainDb = masterGainDb,
                    recording = recording,
                    enabled = gainEnabled,
                    sourcesEnabled = !mixSourcesLocked,
                    onSource = onMixSource,
                    onOpenSetup = onOpenSetup)
            }
        }
    }

    val sheetFor = sheetTrack?.let { index -> tracks.firstOrNull { it.index == index && !it.isMaster } }
    if (sheetFor != null) {
        TrackSheet(
            track = sheetFor,
            levels = trackLevels.getOrNull(sheetFor.index) ?: floorLevels,
            settingsEnabled = !locked,
            gainEnabled = gainEnabled,
            onSource = { onSource(sheetFor.index, it) },
            onGain = { value, persist -> onGain(sheetFor.index, value, persist) },
            onArm = { onArm(sheetFor.index, it) },
            onDismiss = { sheetTrack = null })
    }
}

@Composable
private fun TrackRow(
    track: MultitrackTrack,
    levels: StereoLevels,
    history: TrackWaveformHistory,
    revision: State<Long>,
    masterGainDb: Float,
    recording: Boolean,
    locked: Boolean,
    gainEnabled: Boolean,
    waveformHeight: Dp,
    onArm: (Boolean) -> Unit,
    onSource: (Int) -> Unit,
    onOpenTrack: () -> Unit
) {
    val number = track.displayNumber
    val gainText = formatGain(if (track.isMaster) masterGainDb else track.gainDb)
    val gainDescription = if (track.isMaster) "${track.title} gain $gainText, change in Recording setup" else "Track $number gain $gainText"
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ArmControl(track, number, recording, locked, onArm)
            Box(Modifier.weight(1f)) { SourceSelector(track, number, enabled = !locked, onSource = onSource) }
            GainChip(text = gainText, description = gainDescription, enabled = gainEnabled, onClick = onOpenTrack)
        }
        TrackMeter(
            levels,
            Modifier.fillMaxWidth().height(8.dp).semantics {
                contentDescription =
                    "Track $number level, peak ${maxOf(levels.left.peakDb, levels.right.peakDb).toInt()} dBFS" +
                        if (levels.left.isClipping || levels.right.isClipping) ", clipping" else ""
            })
        TimelineWaveform(
            history = history,
            revision = revision,
            dimmed = track.inputNumber == null && !track.armed && !track.isMaster,
            recording = recording,
            modifier = Modifier.fillMaxWidth().height(waveformHeight).semantics { contentDescription = "Track $number waveform, ${track.title}" })
    }
}

/** Arm toggle for a track; the master is always recorded and shows a badge instead. */
@Composable
private fun ArmControl(track: MultitrackTrack, number: Int, recording: Boolean, locked: Boolean, onArm: (Boolean) -> Unit) {
    if (track.isMaster || track.inputNumber != null) {
        if (track.inputNumber != null && !track.armed) return
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(6.dp), color = AccentRed.copy(alpha = 0.16f)) {
                Text(
                    if (track.inputNumber != null) "REC" else "MASTER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentRed,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
        return
    }
    IconToggleButton(
        checked = track.armed,
        onCheckedChange = onArm,
        enabled = !locked,
        modifier =
            Modifier.semantics {
                contentDescription = "Record track $number, ${track.title}"
                stateDescription = if (track.armed) "Armed" else "Not armed"
            }) {
            Icon(
                if (track.armed) Icons.Default.FiberManualRecord else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint =
                    when {
                        !track.armed -> TextSecondary
                        locked && !recording -> AccentRed.copy(alpha = 0.5f)
                        else -> AccentRed
                    })
        }
}

@Composable
private fun TrackTitle(track: MultitrackTrack, number: Int, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (track.inputNumber == null)
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary,
                modifier = Modifier.width(16.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Text(
            " · ${track.subtitle}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (track.routingError != null) {
            Icon(Icons.Default.WarningAmber, "Routing problem", Modifier.padding(start = 2.dp).size(16.dp), tint = AccentAmber)
        }
        if (track.sourceOptions.isNotEmpty()) {
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp), tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f))
        }
    }
}

/** Track number and source; a dropdown when the mixer lets the app route this pair. */
@Composable
private fun SourceSelector(track: MultitrackTrack, number: Int, enabled: Boolean, onSource: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val routable = track.sourceOptions.isNotEmpty()
    Box {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 32.dp)
                .clickable(enabled = routable && enabled, role = Role.Button, onClickLabel = "Change source") { expanded = true }
                .padding(start = 4.dp),
            contentAlignment = Alignment.CenterStart) {
                TrackTitle(track, number, enabled = enabled)
            }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            track.routingError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentAmber,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(240.dp))
            }
            track.sourceOptions.forEach { option ->
                val selected = option.code == track.selectedSource
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            Text(option.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    },
                    leadingIcon = { if (selected) Icon(Icons.Default.Check, "Selected") else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        expanded = false
                        if (!selected) onSource(option.code)
                    })
            }
        }
    }
}

@Composable
private fun GainChip(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVariantDark) {
                Box(Modifier.padding(horizontal = 8.dp).height(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (enabled) TextPrimary else TextSecondary,
                        maxLines = 1)
                }
            }
        }
}

/** Source (when routable) and gain for one track, with its live meter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackSheet(
    track: MultitrackTrack,
    levels: StereoLevels,
    settingsEnabled: Boolean,
    gainEnabled: Boolean,
    onSource: (Int) -> Unit,
    onGain: (Float, Boolean) -> Unit,
    onArm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(track.index) { mutableFloatStateOf(track.gainDb) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Track ${track.displayNumber}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${track.title} · ${if (track.inputNumber != null) track.subtitle else track.channels.usbLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary)
                if (!settingsEnabled)
                    Text(
                        if (gainEnabled) "Recording and source choices are locked during capture. Gain changes apply immediately."
                        else "Track settings are temporarily unavailable while preparing or saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary)
                if (track.sourceOptions.isNotEmpty()) {
                    Text("Mixer source", style = MaterialTheme.typography.titleSmall)
                    track.routingError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AccentAmber) }
                    Column(Modifier.selectableGroup()) {
                        track.sourceOptions.forEach { option ->
                            val selected = option.code == track.selectedSource
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                    selected = selected, enabled = settingsEnabled, role = Role.RadioButton) {
                                        if (!selected) onSource(option.code)
                                    },
                                verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected, onClick = null, enabled = settingsEnabled)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(option.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                        }
                    }
                    HorizontalDivider(color = OutlineSubtle)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Record this track", style = MaterialTheme.typography.titleSmall)
                        Text("Save a separate audio file. Mix input stays available.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = track.armed,
                        onCheckedChange = onArm,
                        enabled = settingsEnabled,
                        modifier = Modifier.semantics { contentDescription = "Record Track ${track.displayNumber}" })
                }
                Text("Gain", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatGain(value),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            value = 0f
                            onGain(0f, true)
                        },
                        enabled = gainEnabled) {
                            Text("Reset to 0 dB")
                        }
                }
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        onGain(it, false)
                    },
                    onValueChangeFinished = { onGain(value, true) },
                    valueRange = AudioGain.MIN_DB..AudioGain.MAX_DB,
                    steps = AudioGain.SLIDER_STEPS,
                    enabled = gainEnabled,
                    modifier = Modifier.semantics { contentDescription = "Track ${track.displayNumber} gain in decibels" })
                Text(
                    "0 dB keeps the input level exactly. Above 0 dB this track's own limiter keeps peaks under -1 dBFS; it never affects the other tracks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary)
                StereoVuMeter(levels)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                Spacer(Modifier.height(8.dp))
            }
    }
}

@Composable
private fun MixTrackRow(
    tracks: List<MultitrackTrack>,
    route: StereoMixRoute,
    levels: StereoLevels,
    leftHistory: WaveformHistory,
    rightHistory: WaveformHistory,
    revision: State<Long>,
    gainDb: Float,
    recording: Boolean,
    enabled: Boolean,
    sourcesEnabled: Boolean,
    onSource: (Boolean, Int) -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${if (route.outputChannelCount == 1) "Mono" else "Stereo"} · Go Live / video",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            GainChip(formatGain(gainDb), "Mix gain ${formatGain(gainDb)}", enabled, onOpenSetup)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MixSourceSelector("L", route.leftChannel, tracks, sourcesEnabled, { onSource(true, it) }, Modifier.weight(1f))
            MixSourceSelector("R", route.rightChannel, tracks, sourcesEnabled, { onSource(false, it) }, Modifier.weight(1f))
        }
        TrackMeter(
            levels,
            Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "Mix ${if (route.outputChannelCount == 1) "mono" else "stereo"} level" })
        MixChannelWaveform("L", leftHistory, revision, recording)
        MixChannelWaveform("R", rightHistory, revision, recording)
    }
}

@Composable
private fun MixChannelWaveform(side: String, history: WaveformHistory, revision: State<Long>, recording: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(side, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary, modifier = Modifier.width(12.dp))
        TimelineWaveform(
            history = history,
            revision = revision,
            dimmed = false,
            recording = recording,
            modifier = Modifier.weight(1f).height(32.dp).semantics { contentDescription = "Mix $side waveform" })
    }
}

@Composable
private fun MixSourceSelector(
    side: String,
    channel: Int,
    tracks: List<MultitrackTrack>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectorShape = RoundedCornerShape(8.dp)
    val selectorTextStyle = MaterialTheme.typography.labelMedium
    val selectorContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val menuItemColors = MenuDefaults.itemColors(textColor = selectorContentColor, leadingIconColor = selectorContentColor)
    BoxWithConstraints(modifier) {
        val buttonWidth = maxWidth
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            shape = selectorShape,
            color = SurfaceVariantDark,
            contentColor = selectorContentColor,
            modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "Mix $side source: ${StereoMixRoute.sourceLabel(channel)}" }) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$side ← ${StereoMixRoute.sourceLabel(channel)}", maxLines = 1, style = selectorTextStyle, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                }
            }
        DropdownMenu(
            modifier = Modifier.width(buttonWidth),
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = selectorShape,
            containerColor = SurfaceVariantDark,
            tonalElevation = 0.dp,
        ) {
            DropdownMenuItem(
                text = { Text("None", style = selectorTextStyle) },
                leadingIcon = {
                    if (channel == StereoMixRoute.NONE) Icon(Icons.Default.Check, "Selected", Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp))
                },
                colors = menuItemColors,
                onClick = {
                    expanded = false
                    onSelect(StereoMixRoute.NONE)
                },
            )
            tracks.forEach { track ->
                repeat(track.channels.width) { offset ->
                    val source = track.channels.firstChannel + offset
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(StereoMixRoute.sourceLabel(source), style = selectorTextStyle)
                                Text(track.subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        },
                        leadingIcon = { if (source == channel) Icon(Icons.Default.Check, "Selected", Modifier.size(18.dp)) else Spacer(Modifier.size(18.dp)) },
                        colors = menuItemColors,
                        onClick = {
                            expanded = false
                            onSelect(source)
                        })
                }
            }
        }
    }
}

private fun formatGain(db: Float): String = AudioGain.format(db)

package com.audiopro.djmrec.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.audiopro.djmrec.audio.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputPicker(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val inputs by viewModel.usbInputs.collectAsState()
    val device by viewModel.deviceState.collectAsState()
    val notice by viewModel.connectionNotice.collectAsState()
    val state by viewModel.recordingState.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val live by viewModel.liveStreamState.collectAsState()
    val locked = saving || live.isActive || state is RecordingState.Recording || state is RecordingState.Paused || state is RecordingState.Preparing
    LaunchedEffect(Unit) { viewModel.rescanUsbDevices() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio inputs", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = viewModel::refreshInputs) { Icon(Icons.Default.Refresh, "Refresh USB devices") }
                }
                Text(
                    if (locked) "Input stays locked while recording, connecting or streaming."
                    else "Select inputs in track order: the first checked is Track 1. Tap the device name to return here.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            notice?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (inputs.isEmpty())
                item { Text("No USB devices detected. Connect the PC/Mac audio port using a data cable and enable USB host/OTG if your phone requires it.") }
            items(inputs, key = { it.selectionKey }) { input ->
                val selected = input.matches(device)
                val canToggle = !locked && input.captureCandidate && (!selected || (input.multiSelectable && device?.selectedInterfaceNumbers?.size != 1))
                Surface(shape = RoundedCornerShape(8.dp), tonalElevation = if (selected) 4.dp else 1.dp) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = canToggle) { viewModel.selectInput(input.deviceName, input.interfaceNumber) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (input.multiSelectable) {
                                Checkbox(checked = selected, onCheckedChange = null, enabled = canToggle)
                            } else {
                                RadioButton(selected = selected, onClick = null, enabled = canToggle)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(input.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        selected && input.multiSelectable ->
                                            "Track ${(device?.selectedInterfaceNumbers?.indexOf(input.interfaceNumber) ?: -1) + 1}"
                                        selected -> "Selected"
                                        !input.captureCandidate -> "No USB audio input advertised"
                                        !input.hasPermission -> "Tap to allow USB access"
                                        else -> "Available"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                }
            }
            device?.let { input ->
                item {
                    Text(input.profileDescription, style = MaterialTheme.typography.titleSmall)
                    Text(
                        input.allInOneProfile?.setupHint
                            ?: "Check both stereo meters and listen to a short saved test before a full set. Automatic detection cannot identify an undocumented proprietary format.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
        }
    }
}

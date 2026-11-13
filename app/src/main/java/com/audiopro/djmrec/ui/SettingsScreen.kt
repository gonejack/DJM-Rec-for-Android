package com.audiopro.djmrec.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiopro.djmrec.BuildConfig
import com.audiopro.djmrec.ui.theme.TextSecondary
import androidx.core.net.toUri

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val waveform by viewModel.waveformEnabled.collectAsState()
    val smooth by viewModel.smoothWaveform.collectAsState()
    val keepScreen by viewModel.keepScreenOn.collectAsState()
    val confirm by viewModel.confirmStop.collectAsState()
    val trimSilence by viewModel.trimLeadingSilence.collectAsState()
    val limiter by viewModel.safetyLimiter.collectAsState()
    val preRecord by viewModel.preRecord.collectAsState()
    val autoStopMinutes by viewModel.silenceAutoStopMinutes.collectAsState()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Capture", style = MaterialTheme.typography.titleLarge)
        Text("Monitoring arms automatically after USB connection and permission. Recording starts only when you press Record.", color = TextSecondary)
        PreferenceSwitch("Safety limiter", "Catches loud peaks at -1 dBFS instead of clipping them, so a hot mix or +12 dB gain stays clean in the recording and the livestream.", limiter, viewModel::setSafetyLimiter)
        PreferenceSwitch("Pre-record buffer", "Keeps the last 15 seconds while the mixer is connected, so pressing Record late still captures the start of the mix.", preRecord, viewModel::setPreRecord)
        SilenceAutoStopPreference(autoStopMinutes, viewModel::setSilenceAutoStopMinutes)
        PreferenceSwitch("Trim leading silence", "Start the file at the first sound, keeping half a second of lead-in. Removes the few silent seconds before the mixer's audio arrives. Applies to the next recording.", trimSilence, viewModel::setTrimLeadingSilence)
        PreferenceSwitch("Confirm stop", "Ask before stopping from the recorder. Notification Save & close always acts immediately.", confirm, viewModel::setConfirmStop)
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { RecordingSetupControls(viewModel) }
        }
        Text("Display", style = MaterialTheme.typography.titleLarge)
        PreferenceSwitch("Live waveform", "CDJ-style layered bands: blue = low, amber = mid, white = high.", waveform, viewModel::setWaveformEnabled)
        PreferenceSwitch("Smooth waveform", "Scroll at the display frame rate. Turn off to reduce graphics work.", smooth, viewModel::setSmoothWaveform)
        PreferenceSwitch("Keep recorder screen awake", "Recording always keeps the device awake; this also keeps the screen on while monitoring. Capture continues if the screen locks.", keepScreen, viewModel::setKeepScreenOn)
        Text("Background recording", style = MaterialTheme.typography.titleLarge)
        Text("Keep the persistent notification enabled. Allow background battery use in Android settings for long sets. Force-stop, reboot or disconnecting USB still ends capture.", color = TextSecondary)
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))
        }, modifier = Modifier.fillMaxWidth()) { Text("Android app settings") }
        Text("About", style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("DJM REC", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Installed version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SilenceAutoStopPreference(minutes: Int, onChange: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto-stop after silence", style = MaterialTheme.typography.titleSmall)
            Text("Saves and stops a recording left running after the music ends, for example at the end of the night. A livestream keeps running.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 20, 30).forEach { option ->
                    FilterChip(
                        selected = minutes == option,
                        onClick = { onChange(option) },
                        label = { Text(if (option == 0) "Off" else "$option min") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().toggleable(value = value, role = Role.Switch, onValueChange = onChange).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(value, null)
        }
    }
}

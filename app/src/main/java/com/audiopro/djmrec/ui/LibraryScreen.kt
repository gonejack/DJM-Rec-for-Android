package com.audiopro.djmrec.ui

import android.content.Intent
import android.database.ContentObserver
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.audiopro.djmrec.audio.RecordingPlaybackOutput
import com.audiopro.djmrec.storage.LibraryRecording
import com.audiopro.djmrec.storage.RecordingLibrary
import com.audiopro.djmrec.storage.RecordingTreeNode
import com.audiopro.djmrec.storage.buildRecordingTree
import com.audiopro.djmrec.ui.theme.AccentGreen
import com.audiopro.djmrec.ui.theme.AccentRed
import com.audiopro.djmrec.ui.theme.BackgroundDark
import com.audiopro.djmrec.ui.theme.DjmRecTheme
import com.audiopro.djmrec.ui.theme.SurfaceDark
import com.audiopro.djmrec.ui.theme.SurfaceVariantDark
import com.audiopro.djmrec.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var expandedDirectories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var collapsedSearchDirectories by remember(query) { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf<List<LibraryRecording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<LibraryRecording?>(null) }
    var rename by remember { mutableStateOf<LibraryRecording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<LibraryRecording?>(null) }
    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<LibraryRecording?>(null) }
    var exportTarget by remember { mutableStateOf<LibraryRecording?>(null) }
    var playbackReady by remember { mutableStateOf(false) }
    var playbackFailure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        try {
            viewModel.withRecordingPlayback {
                playbackReady = true
                awaitCancellation()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            playbackFailure = failure.message ?: "Could not prepare audio playback"
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val target = exportTarget
            if (uri != null && target != null) scope.launch {
                busy = true
                withContext(Dispatchers.IO) {
                    runCatching {
                        RecordingLibrary.export(
                            context,
                            target,
                            uri
                        )
                    }
                }
                    .onSuccess { snackbarHostState.showSnackbar("Exported \"${target.displayName}\"") }
                    .onFailure {
                        error = it.message
                            ?: "Could not export a copy. Check free storage and choose a different location."
                    }
                busy = false
            }
            exportTarget = null
        }

    fun share(recording: LibraryRecording) {
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = recording.mimeType
                putExtra(Intent.EXTRA_STREAM, recording.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri(recording.displayName, recording.uri)
            }, "Share recording"))
        }.onFailure { error = "No sharing app is available" }
    }
    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) { runCatching { RecordingLibrary.list(context) } }
            .onSuccess { recordings = it }.onFailure {
                error =
                    it.message ?: "Could not load recordings. Check storage permission and refresh."
            }
        loading = false
    }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh++
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your recordings", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${recordings.size} recordings / ${sizeLabel(recordings.sumOf { it.size })}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { refresh++ }) {
                Icon(
                    Icons.Default.Refresh,
                    "Refresh recordings"
                )
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search recordings") },
            leadingIcon = { Icon(Icons.Default.Search, null) })
        if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        val visible = remember(recordings, query) {
            recordings.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                        it.relativeDirectory.contains(query, ignoreCase = true)
            }
        }
        if (visible.isEmpty() && !loading) {
            Box(
                Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank())
                        "Your saved recordings will appear here.\nRecordings in progress stay hidden."
                    else
                        "No matching recordings",
                    color = TextSecondary
                )
            }
        } else RecordingTreeList(
            recordings = visible,
            isFolderExpanded = { path ->
                if (query.isBlank()) path in expandedDirectories else path !in collapsedSearchDirectories
            },
            onToggleFolder = { path ->
                if (query.isBlank()) {
                    expandedDirectories =
                        if (path in expandedDirectories) expandedDirectories - path else expandedDirectories + path
                } else {
                    collapsedSearchDirectories =
                        if (path in collapsedSearchDirectories) collapsedSearchDirectories - path else collapsedSearchDirectories + path
                }
            },
            busy = busy,
            onFolderAction = { path, action ->
                when (action) {
                    FolderAction.RENAME -> {
                        folderToRename = path; folderName = path.substringAfterLast('/')
                    }
                    FolderAction.DELETE -> folderToDelete = path
                }
            },
            onAction = { recording, action ->
                when (action) {
                    RecordingAction.PLAY -> selected = recording
                    RecordingAction.EDIT -> {
                        selected = selected?.takeUnless { it.uri == recording.uri }
                        editing = recording
                    }
                    RecordingAction.SHARE -> share(recording)
                    RecordingAction.EXPORT -> {
                        exportTarget = recording; export.launch(recording.displayName)
                    }
                    RecordingAction.RENAME -> {
                        rename = recording; renameText = recording.title
                    }
                    RecordingAction.DELETE -> delete = recording
                }
            },
            modifier = Modifier.weight(1f)
        )
        selected?.let { recording ->
            when {
                playbackFailure != null -> Text(playbackFailure.orEmpty(), color = AccentRed)
                playbackReady -> SetPlayer(
                    recording,
                    onClose = { selected = null },
                    onError = { error = it }
                )
                else -> LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        SnackbarHost(snackbarHostState, Modifier.fillMaxWidth())
    }
    folderToRename?.let { path ->
        AlertDialog(
            onDismissRequest = { if (!busy) folderToRename = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    folderName,
                    { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = !busy && folderName.isNotBlank(), onClick = {
                    selected = selected?.takeUnless {
                        it.relativeDirectory == path || it.relativeDirectory.startsWith("$path/")
                    }
                    scope.launch {
                        busy = true
                        val requestedName = folderName
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                RecordingLibrary.renameFolder(
                                    context,
                                    path,
                                    requestedName
                                )
                            }
                        }
                        busy = false
                        result.onSuccess { newPath ->
                            folderToRename = null
                            expandedDirectories = expandedDirectories.map {
                                if (it == path || it.startsWith("$path/")) newPath + it.removePrefix(
                                    path
                                ) else it
                            }
                            refresh++
                            snackbarHostState.showSnackbar("Folder renamed")
                        }.onFailure {
                            error = it.message ?: "Could not rename this folder"
                            refresh++
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToRename = null },
                    enabled = !busy
                ) { Text("Cancel") }
            }
        )
    }
    folderToDelete?.let { path ->
        val count =
            recordings.count { it.relativeDirectory == path || it.relativeDirectory.startsWith("$path/") }
        AlertDialog(
            onDismissRequest = { if (!busy) folderToDelete = null },
            title = { Text("Delete this folder?") },
            text = {
                Text("${path.substringAfterLast('/')}\n$count recordings will be deleted. Files that cannot be moved to Trash cannot be restored.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    selected = selected?.takeUnless {
                        it.relativeDirectory == path || it.relativeDirectory.startsWith("$path/")
                    }
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) {
                            runCatching { RecordingLibrary.trashFolder(context, path) }
                        }
                        folderToDelete = null
                        busy = false
                        result.onSuccess { outcome ->
                            expandedDirectories =
                                expandedDirectories.filterNot { it == path || it.startsWith("$path/") }
                            refresh++
                            if (outcome.undoable.isNotEmpty()) {
                                val message = if (outcome.permanentlyDeleted > 0) {
                                    "${outcome.permanentlyDeleted} files permanently deleted; others moved to Trash"
                                } else "Folder moved to Trash"
                                val response = snackbarHostState.showSnackbar(
                                    message, actionLabel = "Undo", duration = SnackbarDuration.Short
                                )
                                if (response == SnackbarResult.ActionPerformed) {
                                    withContext(Dispatchers.IO) {
                                        outcome.undoable.forEach {
                                            RecordingLibrary.untrash(
                                                context,
                                                it
                                            )
                                        }
                                    }
                                    refresh++
                                }
                            } else {
                                snackbarHostState.showSnackbar("Folder deleted")
                            }
                        }.onFailure {
                            error =
                                "Could not delete folder: ${it.message ?: "some files could not be removed"}"
                            refresh++
                        }
                    }
                }) { Text("Delete", color = AccentRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToDelete = null },
                    enabled = !busy
                ) { Text("Keep") }
            }
        )
    }
    rename?.let { recording ->
        AlertDialog(
            onDismissRequest = { if (!busy) rename = null },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(
                    renameText,
                    { renameText = it },
                    label = { Text("Recording name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val requestedName = renameText
                        selected = selected?.takeUnless { it.uri == recording.uri }
                        withContext(Dispatchers.IO) {
                            runCatching {
                                RecordingLibrary.rename(
                                    context,
                                    recording,
                                    requestedName
                                )
                            }
                        }
                            .onSuccess {
                                rename = null
                                refresh++
                                snackbarHostState.showSnackbar("Renamed to \"$requestedName\"")
                            }
                            .onFailure {
                                error = it.message
                                    ?: "Could not rename this recording. Try a different name."
                            }
                        busy = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = { rename = null },
                    enabled = !busy
                ) { Text("Cancel") }
            })
    }
    delete?.let { recording ->
        AlertDialog(
            onDismissRequest = { if (!busy) delete = null },
            title = { Text("Delete this recording?") },
            text = { Text("${recording.displayName}\nYou can undo this right after deleting. Older file-based recordings are removed permanently.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        selected = selected?.takeUnless { it.uri == recording.uri }
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    RecordingLibrary.trash(
                                        context,
                                        recording
                                    )
                                }
                            }
                            busy = false
                            delete = null
                            result.onSuccess { undoable ->
                                refresh++
                                if (undoable) {
                                    val outcome = snackbarHostState.showSnackbar(
                                        message = "Moved to Trash", actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (outcome == SnackbarResult.ActionPerformed) {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                RecordingLibrary.untrash(
                                                    context,
                                                    recording
                                                )
                                            }
                                        }
                                    }
                                    refresh++
                                } else {
                                    snackbarHostState.showSnackbar("Recording deleted")
                                }
                            }.onFailure {
                                error = "Could not delete \"${recording.displayName}\": ${it.message ?: "the file is busy"}. Close other apps using it and try again."
                                refresh++
                            }
                        }
                    }
                ) { Text("Delete", color = AccentRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = { delete = null },
                    enabled = !busy
                ) { Text("Keep") }
            })
    }
    editing?.takeIf { playbackReady }?.let { recording ->
        SetEditorDialog(recording, onClose = { editing = null; refresh++ })
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Could not complete action") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}

private enum class RecordingAction { PLAY, EDIT, SHARE, EXPORT, RENAME, DELETE }
private enum class FolderAction { RENAME, DELETE }

@Composable
private fun RecordingTreeList(
    recordings: List<LibraryRecording>,
    isFolderExpanded: (String) -> Boolean,
    onToggleFolder: (String) -> Unit,
    busy: Boolean,
    onFolderAction: (String, FolderAction) -> Unit,
    onAction: (LibraryRecording, RecordingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val tree =
        remember(recordings) { buildRecordingTree(recordings, LibraryRecording::relativeDirectory) }
    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        recordingTreeRows(tree, isFolderExpanded, onToggleFolder, busy, onFolderAction, onAction)
    }
}

private fun LazyListScope.recordingTreeRows(
    nodes: List<RecordingTreeNode<LibraryRecording>>,
    isFolderExpanded: (String) -> Boolean,
    onToggleFolder: (String) -> Unit,
    busy: Boolean,
    onFolderAction: (String, FolderAction) -> Unit,
    onAction: (LibraryRecording, RecordingAction) -> Unit,
    depth: Int = 0
) {
    nodes.forEach { node ->
        when (node) {
            is RecordingTreeNode.Recording -> item(
                key = "recording:${node.value.uri}",
                contentType = "recording"
            ) {
                RecordingFileRow(node.value, depth, busy, onAction)
            }
            is RecordingTreeNode.Folder -> {
                val expanded = isFolderExpanded(node.path)
                item(key = "folder:${node.path}", contentType = "folder") {
                    RecordingFolderRow(node, expanded, depth, busy, onToggleFolder, onFolderAction)
                }
                if (expanded) {
                    recordingTreeRows(node.children, isFolderExpanded, onToggleFolder, busy, onFolderAction, onAction, depth + 1)
                }
            }
        }
    }
}

@Composable
private fun RecordingFolderRow(
    folder: RecordingTreeNode.Folder<LibraryRecording>,
    expanded: Boolean,
    depth: Int,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onAction: (String, FolderAction) -> Unit
) {
    var menu by remember(folder.path) { mutableStateOf(false) }
    var menuPosition by remember(folder.path) { mutableStateOf(Offset.Zero) }
    Surface(
        modifier = Modifier.padding(start = (depth * 20).dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .pointerInput(folder.path) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            menuPosition = down.position
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.pressed }?.let {
                                    if (!menu) menuPosition = it.position
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "${if (expanded) "Collapse" else "Expand"} ${folder.name}",
                        onLongClickLabel = "Folder actions for ${folder.name}",
                        onClick = { onToggle(folder.path) },
                        onLongClick = { if (!busy) menu = true }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(28.dp), tint = AccentGreen)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        folder.name, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${folder.recordingCount} recordings",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        null, Modifier.size(20.dp), tint = TextSecondary
                    )
                }
            }
            Box(
                Modifier.offset { IntOffset(menuPosition.x.toInt(), menuPosition.y.toInt()) }.size(1.dp)
            ) {
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menu = false
                        onAction(folder.path, FolderAction.RENAME)
                    })
                    DropdownMenuItem(text = { Text("Delete", color = AccentRed) }, onClick = {
                        menu = false
                        onAction(folder.path, FolderAction.DELETE)
                    })
                }
            }
        }
    }
}

@Composable
private fun RecordingFileRow(
    recording: LibraryRecording,
    depth: Int,
    busy: Boolean,
    onAction: (LibraryRecording, RecordingAction) -> Unit
) {
    var menu by remember(recording.uri) { mutableStateOf(false) }
    var quickMenu by remember(recording.uri) { mutableStateOf(false) }
    var menuPosition by remember(recording.uri) { mutableStateOf(Offset.Zero) }
    fun act(action: RecordingAction) {
        menu = false
        quickMenu = false
        onAction(recording, action)
    }
    Surface(
        modifier = Modifier.padding(start = (depth * 20).dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(recording.uri) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            menuPosition = down.position
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.pressed }?.let {
                                    if (!quickMenu) menuPosition = it.position
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Play ${recording.title}",
                        onLongClickLabel = "File actions for ${recording.title}",
                        onClick = { act(RecordingAction.PLAY) },
                        onLongClick = { if (!busy) quickMenu = true }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(28.dp), tint = AccentGreen)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(recording.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${recording.extension.uppercase()} / ${elapsedText(recording.duration)} / ${sizeLabel(recording.size)}",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "Actions for ${recording.title}")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Play") }, onClick = { act(RecordingAction.PLAY) })
                        DropdownMenuItem(text = { Text("Edit: trim, fades, loudness") },
                            enabled = !busy, onClick = { act(RecordingAction.EDIT) })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { act(RecordingAction.SHARE) })
                        DropdownMenuItem(text = { Text("Export a copy") },
                            enabled = !busy, onClick = { act(RecordingAction.EXPORT) })
                        DropdownMenuItem(text = { Text("Rename") },
                            enabled = !busy, onClick = { act(RecordingAction.RENAME) })
                        DropdownMenuItem(text = { Text("Delete", color = AccentRed) },
                            enabled = !busy, onClick = { act(RecordingAction.DELETE) })
                    }
                }
            }
            Box(
                Modifier.offset { IntOffset(menuPosition.x.toInt(), menuPosition.y.toInt()) }.size(1.dp)
            ) {
                DropdownMenu(expanded = quickMenu, onDismissRequest = { quickMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { act(RecordingAction.RENAME) })
                    DropdownMenuItem(text = { Text("Delete", color = AccentRed) },
                        onClick = { act(RecordingAction.DELETE) })
                }
            }
        }
    }
}

@Composable
private fun SetPlayer(recording: LibraryRecording, onClose: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(recording.uri) { MediaPlayer() }
    var ready by remember(recording.uri) { mutableStateOf(false) }
    var playing by remember(recording.uri) { mutableStateOf(false) }
    var position by remember(recording.uri) { mutableLongStateOf(0L) }
    var duration by remember(recording.uri) { mutableLongStateOf(recording.duration) }
    var output by remember(recording.uri) { mutableStateOf("System default") }
    val playbackOutput = remember(player) { RecordingPlaybackOutput(context, player) { output = it } }
    DisposableEffect(player) {
        playbackOutput.start()
        player.setOnPreparedListener {
            ready = true
            duration = it.duration.toLong()
            playbackOutput.refresh()
            it.start()
            playing = true
            playbackOutput.reportOutput()
        }
        player.setOnCompletionListener { playing = false; position = duration }
        player.setOnErrorListener { _, _, _ ->
            playing = false; ready = false; onError("This recording could not be played"); true
        }
        runCatching {
            player.setDataSource(
                context,
                recording.uri
            ); player.prepareAsync()
        }.onFailure { onError("Could not open recording") }
        onDispose { playbackOutput.close(); player.release() }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            position = runCatching { player.currentPosition.toLong() }.getOrDefault(position)
            // The first audio route can become available after onPrepared/start returns.
            playbackOutput.reportOutput()
            delay(200.milliseconds)
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariantDark) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = ready, onClick = {
                    if (playing) player.pause() else {
                        playbackOutput.refresh()
                        player.start()
                        playbackOutput.reportOutput()
                    }
                    playing = !playing
                }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause playback" else "Play recording"
                    )
                }
                Text(
                    recording.title,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close player") }
            }
            Slider(
                position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                { position = it.toLong(); player.seekTo(it.toInt()) }, enabled = ready,
                valueRange = 0f..duration.coerceAtLeast(1).toFloat()
            )
            Text(
                "${elapsedText(position)} / ${elapsedText(duration)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "System route: $output",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

private fun sizeLabel(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

@Preview(name = "Recordings - collapsed", widthDp = 360, heightDp = 480)
@Composable
private fun LibraryTreeCollapsedPreview() {
    LibraryTreePreview(expanded = false)
}

@Preview(name = "Recordings - expanded", widthDp = 360, heightDp = 480)
@Composable
private fun LibraryTreeExpandedPreview() {
    LibraryTreePreview(expanded = true)
}

@Composable
private fun LibraryTreePreview(expanded: Boolean) {
    val session = "20261114_203000"
    val recordings = listOf(
        LibraryRecording(
            "content://media/external/audio/media/1".toUri(),
            "mix_$session.wav",
            64_000_000,
            220_000,
            3
        ),
        LibraryRecording(
            "content://media/external/audio/media/2".toUri(), "Track 1.wav", 32_000_000, 220_000, 2,
            relativeDirectory = "tracks_$session"
        ),
        LibraryRecording(
            "content://media/external/audio/media/3".toUri(), "Track 2.wav", 32_000_000, 220_000, 2,
            relativeDirectory = "tracks_$session"
        ),
        LibraryRecording(
            "content://media/external/audio/media/4".toUri(),
            "mix_20261113_210000.flac",
            48_000_000,
            360_000,
            1
        )
    )
    DjmRecTheme {
        Surface(color = BackgroundDark) {
            RecordingTreeList(
                recordings, { expanded }, {}, false, { _, _ -> }, { _, _ -> },
                Modifier.fillMaxSize().padding(16.dp)
            )
        }
    }
}

package com.audiopro.djmrec.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class LibraryRecording(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val duration: Long,
    val modified: Long,
    val legacyFile: File? = null,
    val relativeDirectory: String = ""
) {
    val extension
        get() = displayName.substringAfterLast('.').lowercase()

    val title
        get() = displayName.substringBeforeLast('.')

    val mimeType
        get() =
            when (extension) {
                "flac" -> "audio/flac"
                "mp3" -> "audio/mpeg"
                else -> "audio/wav"
            }
}

/** Uses published MediaStore rows; never lists, renames or deletes an active capture. */
object RecordingLibrary {
    private const val RELATIVE_PATH = "Music/DJMRec/"
    private val extensions = setOf("wav", "flac", "mp3")

    @Suppress("DEPRECATION") // setIncludePending also supports Android 10.
    fun list(context: Context): List<LibraryRecording> {
        val supportsTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val columns = buildList {
            addAll(
                listOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.IS_PENDING,
                    MediaStore.Audio.Media.RELATIVE_PATH))
            if (supportsTrash) add(MediaStore.MediaColumns.IS_TRASHED)
        }
            .toTypedArray()
        val collection =
            if (supportsTrash) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.setIncludePending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        val queryArgs =
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("$RELATIVE_PATH%"))
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC")
                if (supportsTrash) {
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
            }
        val indexedPaths = mutableSetOf<String>()
        return buildList {
            context.contentResolver.query(collection, columns, queryArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    val path = cursor.getString(6)
                    indexedPaths += path + name
                    if (cursor.getInt(5) != 0 || (supportsTrash && cursor.getInt(7) != 0)) continue
                    if (name.substringAfterLast('.').lowercase() !in extensions) continue
                    add(
                        LibraryRecording(
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                            name,
                            cursor.getLong(2),
                            cursor.getLong(3),
                            cursor.getLong(4) * 1000,
                            relativeDirectory = path.removePrefix(RELATIVE_PATH).trim('/')))
                }
            }
            // Older versions wrote direct files. Compare full paths because different
            // sessions can contain identically named tracks. Hidden captures/trash stay hidden.
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "DJMRec")
            val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "DJMRec") }
            listOfNotNull(publicDir, privateDir).forEach { dir ->
                dir.walkTopDown()
                    .onEnter { it == dir || !it.isHidden }
                    .filter { file ->
                        file.isFile &&
                            !file.isHidden &&
                            file.extension.lowercase() in extensions &&
                            (dir != publicDir || RELATIVE_PATH + file.relativeTo(dir).invariantSeparatorsPath !in indexedPaths)
                    }
                    .forEach { file ->
                        val duration = runCatching {
                            val metadata = MediaMetadataRetriever()
                            try {
                                metadata.setDataSource(file.absolutePath)
                                metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            } finally {
                                metadata.release()
                            }
                        }
                            .getOrDefault(0L)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        add(
                            LibraryRecording(
                                uri,
                                file.name,
                                file.length(),
                                duration,
                                file.lastModified(),
                                file,
                                relativeDirectory = file.relativeTo(dir).invariantSeparatorsPath.substringBeforeLast('/', "")))
                    }
            }
        }
            .sortedByDescending { it.modified }
    }

    fun rename(context: Context, recording: LibraryRecording, input: String) {
        val name = RecordingNamePolicy.displayName(input, recording.extension)
        require(list(context).none { it.uri != recording.uri && it.relativeDirectory == recording.relativeDirectory && it.displayName.equals(name, true) }) {
            "A recording with that name already exists in this folder"
        }
        recording.legacyFile?.let { source ->
            val target = File(source.parentFile, name)
            if (source.name == name) return
            check(!target.exists() && source.renameTo(target)) { "Could not rename this set" }
            return
        }
        check(
            context.contentResolver.update(
                recording.uri, ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, name) }, "${MediaStore.Audio.Media.IS_PENDING}=0", null) == 1) {
                "Could not rename this set"
            }
    }

    fun delete(context: Context, recording: LibraryRecording) {
        recording.legacyFile?.let { file ->
            check(file.delete()) { "Could not delete this recording. Close other apps using it and try again." }
            return
        }
        check(context.contentResolver.delete(recording.uri, "${MediaStore.Audio.Media.IS_PENDING}=0", null) == 1) {
            "Could not delete this recording. Close other apps using it and try again."
        }
    }

    /**
     * Moves a recording to the system Trash so the delete can be undone (own MediaStore rows). Returns true when an undo is possible; legacy file-based
     * recordings fall back to a permanent delete. Throws when neither works.
     */
    fun trash(context: Context, recording: LibraryRecording): Boolean {
        recording.legacyFile?.let { file ->
            check(file.delete()) { "Could not delete this recording. Close other apps using it and try again." }
            return false
        }
        val trashed = runCatching {
            context.contentResolver.update(
                recording.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, "${MediaStore.Audio.Media.IS_PENDING}=0", null) == 1
        }
            .getOrDefault(false)
        if (!trashed) {
            delete(context, recording)
            return false
        }
        return true
    }

    /** Reverses [trash] while the undo snackbar is still on screen. */
    fun untrash(context: Context, recording: LibraryRecording) {
        runCatching { context.contentResolver.update(recording.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, null, null) }
    }

    fun renameFolder(context: Context, path: String, input: String): String {
        val sourcePath = path.trim('/')
        require(sourcePath.isNotEmpty()) { "Choose a folder" }
        val name = input.trim()
        require(name.isNotEmpty() && name != "." && name != "..") { "Enter a folder name" }
        require(name.length <= 100 && !name.endsWith('.') && !name.endsWith(' ')) {
            "Use a folder name of 100 characters or fewer without a trailing dot or space"
        }
        require(name.none { it.isISOControl() || it in "/\\\\:*?\\\"<>|" }) { "Name contains unsupported characters" }
        if (name == sourcePath.substringAfterLast('/')) return sourcePath
        val parent = sourcePath.substringBeforeLast('/', "")
        val targetPath = if (parent.isEmpty()) name else "$parent/$name"
        val members = folderMembers(context, sourcePath)
        require(members.isNotEmpty()) { "This folder no longer contains recordings" }
        checkNoPending(context, sourcePath)
        check(list(context).none { it.relativeDirectory == targetPath || it.relativeDirectory.startsWith("$targetPath/") }) {
            "A folder with that name already exists"
        }
        roots(context).forEach { root -> check(!File(root, targetPath).exists()) { "A folder with that name already exists" } }

        val moved = mutableListOf<() -> Unit>()
        try {
            members.forEach { recording ->
                val suffix = recording.relativeDirectory.removePrefix(sourcePath)
                val newDirectory = targetPath + suffix
                val legacyFile = recording.legacyFile
                if (legacyFile != null) {
                    val root = roots(context).firstOrNull { legacyFile.toPath().startsWith(it.toPath()) } ?: error("Recording is outside the library")
                    val target = File(File(root, newDirectory), legacyFile.name)
                    check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) { "Could not create the new folder" }
                    check(!target.exists() && legacyFile.renameTo(target)) { "Could not move ${legacyFile.name}" }
                    moved += { check(target.renameTo(legacyFile)) { "Could not restore ${legacyFile.name}" } }
                } else {
                    val values = ContentValues().apply { put(MediaStore.Audio.Media.RELATIVE_PATH, "$RELATIVE_PATH$newDirectory/") }
                    check(context.contentResolver.update(recording.uri, values, "${MediaStore.Audio.Media.IS_PENDING}=0", null) == 1) {
                        "Could not move ${recording.displayName}"
                    }
                    moved += {
                        context.contentResolver.update(
                            recording.uri,
                            ContentValues().apply { put(MediaStore.Audio.Media.RELATIVE_PATH, "$RELATIVE_PATH${recording.relativeDirectory}/") },
                            null,
                            null)
                    }
                }
            }
        } catch (failure: Exception) {
            moved.asReversed().forEach { runCatching { it() } }
            throw failure
        }
        roots(context).forEach { root -> removeEmptyDirectories(File(root, sourcePath)) }
        return targetPath
    }

    data class FolderTrashResult(val undoable: List<LibraryRecording>, val permanentlyDeleted: Int)

    fun trashFolder(context: Context, path: String): FolderTrashResult {
        val members = folderMembers(context, path)
        require(members.isNotEmpty()) { "This folder no longer contains recordings" }
        checkNoPending(context, path)
        val undoable = mutableListOf<LibraryRecording>()
        var permanentlyDeleted = 0
        try {
            // Delete legacy files last so a MediaStore failure can still be rolled back.
            members.sortedBy { it.legacyFile != null }.forEach { recording -> if (trash(context, recording)) undoable += recording else permanentlyDeleted++ }
        } catch (failure: Exception) {
            undoable.forEach { untrash(context, it) }
            throw failure
        }
        roots(context).forEach { root -> removeEmptyDirectories(File(root, path)) }
        return FolderTrashResult(undoable, permanentlyDeleted)
    }

    private fun folderMembers(context: Context, path: String): List<LibraryRecording> {
        val normalized = path.trim('/')
        require(normalized.isNotEmpty()) { "Choose a folder" }
        return list(context).filter { it.relativeDirectory == normalized || it.relativeDirectory.startsWith("$normalized/") }
    }

    @Suppress("DEPRECATION") // Required to include pending rows on Android 10.
    private fun checkNoPending(context: Context, path: String) {
        val directory = "$RELATIVE_PATH${path.trim('/')}/"
        val cursor =
            context.contentResolver.query(
                MediaStore.setIncludePending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Audio.Media.IS_PENDING}=1",
                arrayOf("$directory%"),
                null) ?: error("Could not check whether this folder is in use")
        cursor.use { check(!it.moveToFirst()) { "Wait for the recording in this folder to finish" } }
    }

    private fun roots(context: Context): List<File> =
        listOfNotNull(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "DJMRec"),
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "DJMRec") })

    private fun removeEmptyDirectories(source: File) {
        if (source.isDirectory) source.walkBottomUp().filter(File::isDirectory).forEach(File::delete)
    }

    fun export(context: Context, recording: LibraryRecording, destination: Uri) {
        check(destination != recording.uri) { "Choose a different destination" }
        context.contentResolver.openInputStream(recording.uri).use { source ->
            checkNotNull(source) { "Could not open the recording" }
            context.contentResolver.openOutputStream(destination, "wt").use { target ->
                checkNotNull(target) { "Could not open the destination" }
                source.copyTo(target)
            }
        }
    }
}

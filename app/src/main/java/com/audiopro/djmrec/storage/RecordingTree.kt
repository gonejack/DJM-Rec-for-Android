package com.audiopro.djmrec.storage

internal sealed interface RecordingTreeNode<out T> {
    data class Recording<T>(val value: T) : RecordingTreeNode<T>

    data class Folder<T>(
        val path: String,
        val children: List<RecordingTreeNode<T>>
    ) : RecordingTreeNode<T> {
        val name: String get() = path.substringAfterLast('/')
        val recordingCount: Int get() = children.sumOf {
            when (it) {
                is Recording -> 1
                is Folder -> it.recordingCount
            }
        }
    }
}

/** Keeps the input order, placing each folder where its first recording appeared. */
internal fun <T> buildRecordingTree(
    recordings: List<T>,
    directoryOf: (T) -> String
): List<RecordingTreeNode<T>> {
    fun build(entries: List<Pair<T, List<String>>>, parent: String): List<RecordingTreeNode<T>> {
        val folders = entries.filter { it.second.isNotEmpty() }.groupBy { it.second.first() }
        val addedFolders = mutableSetOf<String>()
        return buildList {
            entries.forEach { (recording, segments) ->
                if (segments.isEmpty()) {
                    add(RecordingTreeNode.Recording(recording))
                } else if (addedFolders.add(segments.first())) {
                    val name = segments.first()
                    val path = if (parent.isEmpty()) name else "$parent/$name"
                    add(RecordingTreeNode.Folder(path, build(
                        folders.getValue(name).map { (value, parts) -> value to parts.drop(1) }, path
                    )))
                }
            }
        }
    }
    return build(recordings.map { it to directoryOf(it).split('/').filter(String::isNotEmpty) }, "")
}

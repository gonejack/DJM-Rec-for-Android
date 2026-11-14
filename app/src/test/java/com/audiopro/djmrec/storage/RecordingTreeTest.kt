package com.audiopro.djmrec.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordingTreeTest {
    private data class Entry(val name: String, val directory: String = "")

    private fun tree(vararg entries: Entry) = buildRecordingTree(entries.toList(), Entry::directory)

    @Test
    fun `empty library has no folders`() {
        assertTrue(tree().isEmpty())
    }

    @Test
    fun `root recordings and track folders retain newest first order`() {
        val newest = Entry("mix_new.wav")
        val track = Entry("Track 1.wav", "tracks_session")
        val oldest = Entry("mix_old.wav")
        val rows = tree(newest, track, oldest)

        assertEquals(RecordingTreeNode.Recording(newest), rows[0])
        val folder = assertIs<RecordingTreeNode.Folder<Entry>>(rows[1])
        assertEquals("tracks_session", folder.path)
        assertEquals(listOf(RecordingTreeNode.Recording(track)), folder.children)
        assertEquals(RecordingTreeNode.Recording(oldest), rows[2])
    }

    @Test
    fun `same track names in different sessions remain independently accessible`() {
        val first = Entry("Track 1.wav", "mix_1_tracks")
        val second = Entry("Track 1.wav", "mix_10_tracks")
        val rows = tree(first, second)
        val firstFolder = assertIs<RecordingTreeNode.Folder<Entry>>(rows[0])
        val secondFolder = assertIs<RecordingTreeNode.Folder<Entry>>(rows[1])

        assertEquals("mix_1_tracks", firstFolder.path)
        assertEquals("mix_10_tracks", secondFolder.path)
        assertEquals(listOf(RecordingTreeNode.Recording(first)), firstFolder.children)
        assertEquals(listOf(RecordingTreeNode.Recording(second)), secondFolder.children)
    }

    @Test
    fun `nested folders expose every track even without a root mix`() {
        val track = Entry("Track 1.flac", "session/Tracks/")
        val mix = Entry("mix.wav", "session")
        val otherTrack = Entry("Track 2.wav", "session/Tracks/")
        val session = assertIs<RecordingTreeNode.Folder<Entry>>(tree(track, mix, otherTrack).single())
        val tracks = assertIs<RecordingTreeNode.Folder<Entry>>(session.children[0])

        assertEquals("session", session.path)
        assertEquals(3, session.recordingCount)
        assertEquals("session/Tracks", tracks.path)
        assertEquals("Tracks", tracks.name)
        assertEquals(2, tracks.recordingCount)
        assertEquals(listOf(RecordingTreeNode.Recording(track), RecordingTreeNode.Recording(otherTrack)), tracks.children)
        assertEquals(RecordingTreeNode.Recording(mix), session.children[1])
    }
}

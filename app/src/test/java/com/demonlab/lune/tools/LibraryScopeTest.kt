package com.demonlab.lune.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScopeTest {

    @Test
    fun `parses primary storage doc ids`() {
        assertEquals("primary" to "Music", LibraryScope.pathFromDocId("primary:Music"))
        assertEquals("primary" to "Music/MyMusic", LibraryScope.pathFromDocId("primary:Music/MyMusic"))
    }

    @Test
    fun `parses sd card doc ids`() {
        assertEquals("1A2B-3C4D" to "Music", LibraryScope.pathFromDocId("1A2B-3C4D:Music"))
    }

    @Test
    fun `trims separators and whitespace`() {
        assertEquals("primary" to "Music/MyMusic", LibraryScope.pathFromDocId(" primary:/Music/MyMusic/ "))
    }

    @Test
    fun `rejects ids without a usable path`() {
        assertNull(LibraryScope.pathFromDocId("Music"))
        assertNull(LibraryScope.pathFromDocId("primary:"))
        assertNull(LibraryScope.pathFromDocId(":Music"))
        assertNull(LibraryScope.pathFromDocId(""))
    }

    @Test
    fun `path boundary does not match a sibling with the same prefix`() {
        val root = "/storage/emulated/0/Music"
        assertTrue(LibraryScope.isInside("/storage/emulated/0/Music/song.mp3", root))
        assertTrue(LibraryScope.isInside("/storage/emulated/0/Music/Artist/song.mp3", root))
        assertTrue(LibraryScope.isInside(root, root))
        assertFalse(LibraryScope.isInside("/storage/emulated/0/Music2/song.mp3", root))
        assertFalse(LibraryScope.isInside("/storage/emulated/0/MyMusic/song.mp3", root))
    }

    @Test
    fun `trailing slash on the root is ignored`() {
        assertTrue(LibraryScope.isInside("/storage/emulated/0/Music/song.mp3", "/storage/emulated/0/Music/"))
        assertFalse(LibraryScope.isInside("/storage/emulated/0/Music2/song.mp3", "/storage/emulated/0/Music/"))
    }
}

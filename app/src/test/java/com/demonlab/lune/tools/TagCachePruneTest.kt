package com.demonlab.lune.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagCachePruneTest {

    private val current = setOf("/music/a/01.mp3", "/music/a/02.mp3")

    @Test
    fun testCachedFileMissingFromTheLibraryIsPruned() {
        assertEquals(
            listOf("/music/a/99.mp3"),
            staleTagCachePaths(listOf("/music/a/99.mp3", "/music/a/01.mp3"), current, "/music/a/")
        )
    }

    @Test
    fun testRowOutsideTheScopeIsKept() {
        assertTrue(staleTagCachePaths(listOf("/other/b/01.mp3"), current, "/music/a/").isEmpty())
    }

    @Test
    fun testUnscopedScanPrunesEveryMissingRow() {
        assertEquals(
            listOf("/other/b/01.mp3"),
            staleTagCachePaths(listOf("/other/b/01.mp3"), current, null)
        )
    }

    @Test
    fun testScopeBoundaryNeedsASeparator() {
        // "/music/ab/" is not inside "/music/a/".
        assertTrue(staleTagCachePaths(listOf("/music/ab/01.mp3"), current, "/music/a/").isEmpty())
    }

    @Test
    fun testEveryRowMatchingTheLibraryIsKept() {
        assertTrue(staleTagCachePaths(current.toList(), current, "/music/a/").isEmpty())
    }
}

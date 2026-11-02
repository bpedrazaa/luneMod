package com.demonlab.lune.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SortNamesTest {

    @Test
    fun testLeadingArticlesAreIgnoredForArtists() {
        assertEquals("strokes", artistSortName("The Strokes"))
        assertEquals("doors", artistSortName("The Doors"))
        assertEquals("band", artistSortName("A Band"))
        assertEquals("ocean", artistSortName("An Ocean"))
        assertEquals("strokes", artistSortName("THE STROKES"))
    }

    @Test
    fun testNamesThatOnlyLookLikeArticleAreKept() {
        assertEquals("theatre", artistSortName("Theatre"))
        assertEquals("the", artistSortName("The"))
        assertEquals("an", artistSortName("An"))
        assertEquals("a", artistSortName("A"))
    }

    @Test
    fun testOuterSpaceIsTrimmed() {
        assertEquals("strokes", artistSortName("  The   Strokes  "))
    }

    @Test
    fun testCaseSensitiveKeepsTheCasing() {
        assertEquals("Strokes", artistSortName("The Strokes", caseSensitive = true))
        assertEquals("strokes", artistSortName("The Strokes", caseSensitive = false))
    }

    @Test
    fun testArtistLetters() {
        assertEquals("S", artistLetter("The Strokes"))
        assertEquals("D", artistLetter("The Doors"))
        assertEquals("B", artistLetter("A Band"))
        assertEquals("O", artistLetter("An Ocean"))
        assertEquals("M", artistLetter("Møme"))
        assertEquals("", artistLetter("   "))
        assertEquals("", artistLetter(null))
    }

    @Test
    fun testAlbumNamesKeepTheirFirstLetter() {
        assertEquals("T", firstLetter("The Classics"))
        assertEquals("D", firstLetter("Deliverance"))
        assertEquals("M", firstLetter("Møme"))
        assertEquals("", firstLetter("  "))
        assertEquals("", firstLetter(null))
    }
}

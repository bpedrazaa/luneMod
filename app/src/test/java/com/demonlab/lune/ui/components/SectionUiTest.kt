package com.demonlab.lune.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import com.demonlab.lune.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SectionUiTest {

    private val knownSections = listOf(
        "RESUME", "MIXES", "ALL", "FAVORITES", "ALBUMS",
        "ARTISTS", "ALBUM_ARTISTS", "GENRES", "PLAYLISTS", "FOLDERS"
    )

    @Test
    fun testEveryKnownSectionHasALabel() {
        knownSections.forEach { section ->
            assertNotNull("missing label for $section", SectionUi.labelRes(section))
        }
    }

    @Test
    fun testLabelsAreDistinct() {
        val labels = knownSections.mapNotNull { SectionUi.labelRes(it) }
        assertEquals(knownSections.size, labels.size)
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun testAlbumArtistsHasItsOwnLabelAndTheArtistIcon() {
        assertEquals(R.string.tab_album_artists, SectionUi.labelRes("ALBUM_ARTISTS"))
        assertEquals(Icons.Default.Person, SectionUi.icon("ALBUM_ARTISTS"))
    }

    @Test
    fun testUnknownSectionFallsBackToTheRawKeyLook() {
        assertNull(SectionUi.labelRes("SOMETHING_ELSE"))
        assertEquals(Icons.Default.Folder, SectionUi.icon("SOMETHING_ELSE"))
    }
}

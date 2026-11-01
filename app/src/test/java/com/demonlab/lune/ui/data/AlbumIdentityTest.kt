package com.demonlab.lune.ui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumIdentityTest {

    @Test
    fun testSameTitleUnderDifferentAlbumArtistsIsTwoAlbums() {
        assertNotEquals(
            Album.idFor("Pink Floyd", "Animals"),
            Album.idFor("The Animals", "Animals")
        )
    }

    @Test
    fun testIdentityIgnoresCaseAndOuterSpace() {
        assertEquals(
            Album.idFor("Pink Floyd", "Animals"),
            Album.idFor(" pink floyd ", "Animals  ")
        )
    }

    @Test
    fun testDifferentTitlesUnderOneArtistDiffer() {
        assertNotEquals(
            Album.idFor("Opeth", "Damnation"),
            Album.idFor("Opeth", "Deliverance")
        )
    }

    @Test
    fun testIdIsDerivedFromTheKey() {
        assertEquals(
            Album.keyFor("Opeth", "Damnation").hashCode().toLong(),
            Album.idFor("Opeth", "Damnation")
        )
    }
}

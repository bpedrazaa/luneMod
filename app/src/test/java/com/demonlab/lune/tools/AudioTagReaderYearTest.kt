package com.demonlab.lune.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The year tag is not always a bare year: some taggers write a full date, and
 * some write junk. Only plausible four-digit years reach the library.
 */
class AudioTagReaderYearTest {

    @Test
    fun testPlainYear() {
        assertEquals(1975, AudioTagReader.parseYear("1975"))
        assertEquals(2024, AudioTagReader.parseYear(" 2024 "))
    }

    @Test
    fun testFullDates() {
        assertEquals(1994, AudioTagReader.parseYear("1994-03-01"))
        assertEquals(1994, AudioTagReader.parseYear("1994/03/01"))
        assertEquals(1994, AudioTagReader.parseYear("01/03/1994"))
    }

    @Test
    fun testMissingYear() {
        assertNull(AudioTagReader.parseYear(null))
        assertNull(AudioTagReader.parseYear(""))
        assertNull(AudioTagReader.parseYear("   "))
    }

    @Test
    fun testImpossibleYearsAreIgnored() {
        assertNull(AudioTagReader.parseYear("0"))
        assertNull(AudioTagReader.parseYear("999"))
        assertNull(AudioTagReader.parseYear("3000"))
        assertNull(AudioTagReader.parseYear("unknown"))
    }
    /** An empty read must not replace a cached row, so it has to report itself. */
    @Test
    fun testEmptyReadIsBlank() {
        assertTrue(AudioTagReader.Tags().isBlank())
        assertFalse(AudioTagReader.Tags(title = "A").isBlank())
        assertFalse(AudioTagReader.Tags(albumArtist = "B").isBlank())
        assertFalse(AudioTagReader.Tags(bitrate = 320000).isBlank())
    }
}

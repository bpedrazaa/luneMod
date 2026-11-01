package com.demonlab.lune.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

class Id3YearReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    private fun bigEndian(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun frame(id: String, text: String, encoding: Int = 0, charset: Charset = Charsets.ISO_8859_1): ByteArray {
        val body = byteArrayOf(encoding.toByte()) + text.toByteArray(charset)
        return id.toByteArray(Charsets.ISO_8859_1) + bigEndian(body.size) + byteArrayOf(0, 0) + body
    }

    private fun tagFile(major: Int, vararg frames: ByteArray): File {
        val body = frames.fold(ByteArray(0)) { acc, frame -> acc + frame }
        val header = "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(major.toByte(), 0, 0) + synchsafe(body.size)
        val file = folder.newFile("tag-$major-${body.size}.mp3")
        file.writeBytes(header + body)
        return file
    }

    @Test
    fun testTdrcInsideA23TagIsRead() {
        // This library tags the year with a v2.4 TDRC frame inside a v2.3 tag.
        val file = tagFile(3, frame("TDRC", "2002"))
        assertEquals(2002, Id3YearReader.readYear(file))
    }

    @Test
    fun testTyerIsRead() {
        val file = tagFile(3, frame("TYER", "1999"))
        assertEquals(1999, Id3YearReader.readYear(file))
    }

    @Test
    fun testFullDateIsReducedToItsYear() {
        val file = tagFile(4, frame("TDRC", "2004-09-21"))
        assertEquals(2004, Id3YearReader.readYear(file))
    }

    @Test
    fun testUtf16TextIsDecoded() {
        val utf16 = "\uFEFF2007"
        val file = tagFile(3, frame("TDRC", utf16, encoding = 1, charset = Charsets.UTF_16LE))
        assertEquals(2007, Id3YearReader.readYear(file))
    }

    @Test
    fun testFrameAfterOtherFramesIsFound() {
        val file = tagFile(
            3,
            frame("TIT2", "Say Hello to the Angels"),
            frame("TALB", "Turn On The Bright Lights"),
            frame("TDRC", "2002")
        )
        assertEquals(2002, Id3YearReader.readYear(file))
    }

    @Test
    fun testNoYearFrameGivesNull() {
        val file = tagFile(3, frame("TIT2", "Untitled"))
        assertNull(Id3YearReader.readYear(file))
    }

    @Test
    fun testImplausibleYearIsIgnored() {
        val file = tagFile(3, frame("TDRC", "9999"))
        assertNull(Id3YearReader.readYear(file))
    }

    @Test
    fun testNonId3FileGivesNull() {
        val file = folder.newFile("plain.mp3")
        file.writeBytes(byteArrayOf(0x49, 0x44, 0x34, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        assertNull(Id3YearReader.readYear(file))
    }

    @Test
    fun testTruncatedFrameGivesNull() {
        val full = tagFile(3, frame("TDRC", "2002"))
        val truncated = folder.newFile("truncated.mp3")
        truncated.writeBytes(full.readBytes().copyOf(full.length().toInt() - 2))
        assertNull(Id3YearReader.readYear(truncated))
    }
}

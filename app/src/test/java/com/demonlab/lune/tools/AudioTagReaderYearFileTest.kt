package com.demonlab.lune.tools

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The year must survive the whole read path, not just the parse helper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioTagReaderYearFileTest {

    private fun createDummyWavFile(file: File) {
        val sampleRate = 44100
        val channels = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * channels * (bitsPerSample / 8)
        val chunkSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(chunkSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * (bitsPerSample / 8))
        buffer.putShort((channels * (bitsPerSample / 8)).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        for (i in 0 until dataSize) {
            buffer.put(0.toByte())
        }

        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    @Test
    fun testReadsYearFromTheFileTag() {
        val tempFile = File.createTempFile("tag_year", ".wav")
        try {
            createDummyWavFile(tempFile)
            val audioFile = AudioFileIO.read(tempFile)
            audioFile.tagOrCreateAndSetDefault.setField(FieldKey.YEAR, "1975")
            audioFile.commit()

            val tags = AudioTagReader.read(tempFile, "wav")

            assertEquals(1975, tags.year)
        } finally {
            tempFile.delete()
        }
    }
}

package com.demonlab.lune.tools

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the year out of an ID3v2 TYER or TDRC frame.
 *
 * This library tags MP3s as ID3v2.3 but stores the year in a TDRC frame, which
 * is a v2.4 frame. jaudiotagger ignores it inside a v2.3 tag and the platform
 * retriever returns nothing, so the frame is parsed here.
 */
internal object Id3YearReader {

    private const val MAX_TAG_BYTES = 4 shl 20

    fun readYear(file: File): Int? = try {
        RandomAccessFile(file, "r").use { raf -> parse(raf) }
    } catch (_: Throwable) {
        null
    }

    private fun parse(raf: RandomAccessFile): Int? {
        if (raf.length() < 10) return null

        val header = ByteArray(10)
        raf.readFully(header)
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return null
        }

        val major = header[3].toInt() and 0xFF
        if (major !in 3..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = synchsafeSize(header, 6)
        if (tagSize <= 0) return null

        val body = ByteArray(minOf(tagSize, MAX_TAG_BYTES))
        raf.readFully(body)

        var pos = 0
        if (flags and 0x40 != 0) {
            pos = when {
                major == 4 -> synchsafeSize(body, 0)
                body.size >= 4 -> 4 + bigEndianInt(body, 0)
                else -> 0
            }
            if (pos < 0 || pos + 10 > body.size) return null
        }

        while (pos + 10 <= body.size) {
            val id = String(body, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val size = if (major == 4) synchsafeSize(body, pos + 4) else bigEndianInt(body, pos + 4)
            val frameStart = pos + 10
            if (size <= 0 || frameStart + size > body.size) break

            if (id == "TYER" || id == "TDRC") {
                val year = Regex("(\\d{4})")
                    .find(decodeText(body, frameStart, size))
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (year != null && year in 1000..2200) return year
            }
            pos = frameStart + size
        }
        return null
    }

    private fun decodeText(body: ByteArray, start: Int, size: Int): String {
        if (size < 1) return ""
        val text = body.copyOfRange(start + 1, start + size)
        return when (body[start].toInt() and 0xFF) {
            1 -> String(text, Charsets.UTF_16)
            2 -> String(text, Charsets.UTF_16BE)
            3 -> String(text, Charsets.UTF_8)
            else -> String(text, Charsets.ISO_8859_1)
        }
    }

    private fun synchsafeSize(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}

package com.demonlab.lune.tools

import android.media.MediaMetadataRetriever
import android.os.Build
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Reads a file's tags. The text comes from the tag library rather than the
 * platform reader, which decodes some ID3 text with the wrong charset.
 *
 * Opening a file is the expensive part, so callers persist the result in
 * [com.demonlab.lune.data.FileTagCache] and only call this for new or changed files.
 */
object AudioTagReader {

    data class Tags(
        val albumArtist: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val trackNumber: Int? = null,
        val year: Int? = null
    ) {
        /** Every field null means the read produced nothing usable. */
        fun isBlank(): Boolean = this == Tags()
    }

    fun read(file: File, extension: String): Tags {
        var albumArtist: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var bitDepth: Int? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var trackNumber: Int? = null
        var year: Int? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)

            albumArtist = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            title = extract(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM)
            genre = extract(retriever, MediaMetadataRetriever.METADATA_KEY_GENRE)
            year = parseYear(extract(retriever, MediaMetadataRetriever.METADATA_KEY_YEAR))
            trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore("/")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
            }
        } catch (_: Throwable) {
            // The jaudiotagger pass below covers formats the platform retriever rejects.
        } finally {
            runCatching { retriever.release() }
        }

        // The platform reader decodes some ID3 text with the wrong charset: a
        // UTF-16 "Ænima" can come back as "Ćnima". The tag itself is read with
        // the declared encoding here, so its text wins whenever it has any.
        readWithJaudiotagger(file)?.let { tag ->
            title = tag.title ?: title
            artist = tag.artist ?: artist
            album = tag.album ?: album
            albumArtist = tag.albumArtist ?: albumArtist
            genre = tag.genre ?: genre
            bitrate = bitrate ?: tag.bitrate
            sampleRate = sampleRate ?: tag.sampleRate
            bitDepth = bitDepth ?: tag.bitDepth
            trackNumber = trackNumber ?: tag.trackNumber
            year = year ?: tag.year
        }

        // TYER and TDRC live in ID3, so only MP3 earns the extra open.
        if (year == null && extension == "mp3") {
            year = Id3YearReader.readYear(file)
        }

        return Tags(albumArtist, bitrate, sampleRate, bitDepth, title, artist, album, genre, trackNumber, year)
    }

    private fun readWithJaudiotagger(file: File): Tags? {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader
            Tags(
                albumArtist = tagField(tag, FieldKey.ALBUM_ARTIST),
                bitrate = header?.bitRate?.toIntOrNull()?.takeIf { it > 0 }?.times(1000),
                sampleRate = header?.sampleRateAsNumber?.takeIf { it > 0 },
                bitDepth = header?.bitsPerSample?.takeIf { it > 0 },
                title = tagField(tag, FieldKey.TITLE),
                artist = tagField(tag, FieldKey.ARTIST),
                album = tagField(tag, FieldKey.ALBUM),
                genre = tagField(tag, FieldKey.GENRE),
                trackNumber = tagField(tag, FieldKey.TRACK)?.substringBefore("/")?.toIntOrNull()?.takeIf { it > 0 },
                year = parseYear(tagField(tag, FieldKey.YEAR))
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extract(retriever: MediaMetadataRetriever, key: Int): String? {
        return runCatching { retriever.extractMetadata(key) }.getOrNull()?.let(::clean)
    }

    /**
     * The year tag is usually four digits but can be a full date. Anything
     * outside a plausible range is ignored rather than shown as a year.
     */
    internal fun parseYear(raw: String?): Int? {
        val match = raw?.let { Regex("(\\d{4})").find(it) } ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        return year.takeIf { it in 1000..2200 }
    }

    private fun tagField(tag: org.jaudiotagger.tag.Tag?, key: FieldKey): String? {
        if (tag == null) return null
        return runCatching { tag.getFirst(key) }.getOrNull()?.let(::clean)
    }

    private fun clean(raw: String): String? {
        val value = CharsetUtils.sanitizeText(raw).trim()
        return value.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
    }
}

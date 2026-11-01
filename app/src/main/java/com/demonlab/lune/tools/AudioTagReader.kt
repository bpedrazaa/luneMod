package com.demonlab.lune.tools

import android.media.MediaMetadataRetriever
import android.os.Build
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Reads the tags MediaStore does not expose: album artist, plus the tags of
 * formats where MediaStore leaves title/artist/album empty (notably WAV/DSD).
 *
 * Opening a file is the expensive part, so callers persist the result in
 * [com.demonlab.lune.data.FileTagCache] and only call this for new or changed files.
 */
object AudioTagReader {

    private val hiFiExtensions = setOf("flac", "wav", "alac", "dsf", "dff", "aiff", "ape")

    // MediaMetadataRetriever does not expose these formats reliably.
    private val retrieverWeakExtensions = setOf("wav", "dsf", "dff", "aiff", "ape")

    data class Tags(
        val albumArtist: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val bitDepth: Int? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val trackNumber: Int? = null
    )

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
        var opened = false

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            opened = true

            albumArtist = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            title = extract(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = extract(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM)
            genre = extract(retriever, MediaMetadataRetriever.METADATA_KEY_GENRE)
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

        val weakFormat = extension in retrieverWeakExtensions
        val missingHiFiProps = extension in hiFiExtensions && (sampleRate == null || bitDepth == null)
        val missingBasicTags = title == null || artist == null || album == null
        val needsFallback = !opened || weakFormat || missingHiFiProps || missingBasicTags

        if (needsFallback) {
            readWithJaudiotagger(file)?.let { fallback ->
                albumArtist = albumArtist ?: fallback.albumArtist
                bitrate = bitrate ?: fallback.bitrate
                sampleRate = sampleRate ?: fallback.sampleRate
                bitDepth = bitDepth ?: fallback.bitDepth
                title = title ?: fallback.title
                artist = artist ?: fallback.artist
                album = album ?: fallback.album
                genre = genre ?: fallback.genre
                trackNumber = trackNumber ?: fallback.trackNumber
            }
        }

        return Tags(albumArtist, bitrate, sampleRate, bitDepth, title, artist, album, genre, trackNumber)
    }

    private fun readWithJaudiotagger(file: File): Tags? {
        return try {
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader
            Tags(
                albumArtist = tagField(tag, FieldKey.ALBUM_ARTIST),
                bitrate = header?.bitRate?.toIntOrNull()?.takeIf { it > 0 },
                sampleRate = header?.sampleRateAsNumber?.takeIf { it > 0 },
                bitDepth = header?.bitsPerSample?.takeIf { it > 0 },
                title = tagField(tag, FieldKey.TITLE),
                artist = tagField(tag, FieldKey.ARTIST),
                album = tagField(tag, FieldKey.ALBUM),
                genre = tagField(tag, FieldKey.GENRE),
                trackNumber = tagField(tag, FieldKey.TRACK)?.substringBefore("/")?.toIntOrNull()?.takeIf { it > 0 }
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extract(retriever: MediaMetadataRetriever, key: Int): String? {
        return runCatching { retriever.extractMetadata(key) }.getOrNull()?.let(::clean)
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

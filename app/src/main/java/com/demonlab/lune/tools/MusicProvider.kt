package com.demonlab.lune.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.demonlab.lune.R
import com.demonlab.lune.data.FileTagCache
import com.demonlab.lune.data.MusicDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UriTypeAdapter : TypeAdapter<Uri>() {
    override fun write(out: JsonWriter, value: Uri?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(reader: JsonReader): Uri? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return reader.nextString().toUri() // Modern KTX string-to-Uri converter
    }
}

data class PendingTag(
    val path: String,
    val fileSize: Long,
    val lastModified: Long,
    val extension: String
)

data class LibrarySync(
    val songs: List<Song>,
    val pendingTags: List<PendingTag>
)

class MusicProvider(private val context: Context) {
    companion object {
        // Bump when the cached Song shape changes; the next sync rebuilds it.
        const val CACHE_VERSION = 6
        // Bump when the tag reader's output changes. Rows carry the epoch they
        // were read with, so a bump reads every row once more without losing a
        // partial pass.
        const val TAG_CACHE_VERSION = 7
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "alac", "dsf", "dff", "aiff", "ape"
        )
        private val HI_FI_EXTENSIONS = setOf("flac", "wav", "alac", "dsf", "dff", "aiff", "ape")
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private val database = MusicDatabase.getDatabase(context)

    private val cacheFile = File(context.filesDir, "songs_cache.json")
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .disableHtmlEscaping()
        .create()

    fun getCachedSongs(): List<Song> {
        if (!cacheFile.exists()) return emptyList()
        if (settingsManager.libraryCacheVersion != CACHE_VERSION) {
            cacheFile.delete()
            return emptyList()
        }
        return try {
            val json = cacheFile.readText()
            val type = object : TypeToken<List<Song>>() {}.type
            gson.fromJson<List<Song>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveToCache(songs: List<Song>) {
        try {
            val hash = songs.hashCode()
            // Most syncs find nothing new; rewriting a multi-megabyte file for
            // them is pure I/O.
            if (cacheFile.exists() &&
                settingsManager.libraryCacheVersion == CACHE_VERSION &&
                settingsManager.libraryCacheHash == hash
            ) {
                return
            }
            val json = gson.toJson(songs)
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
            settingsManager.libraryCacheVersion = CACHE_VERSION
            settingsManager.libraryCacheHash = hash
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSongInCache(updatedSong: Song) {
        // A stale cache is thrown away and rebuilt by the next sync; patching it
        // would persist a list with a single song.
        if (settingsManager.libraryCacheVersion != CACHE_VERSION) return
        val songs = getCachedSongs().toMutableList()
        val idx = songs.indexOfFirst { it.id == updatedSong.id }
        if (idx >= 0) {
            songs[idx] = updatedSong
        } else {
            songs.add(updatedSong)
        }
        saveToCache(songs)
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun syncSongs(): List<Song> = syncLibrary().songs

    /**
     * Rebuilds the library from MediaStore using the persisted tag cache.
     *
     * Files whose size/mtime match a cache entry are not reopened. The recursive
     * disk scan only runs on an explicit refresh: doing it on every sync cost one
     * stat call per file.
     */
    suspend fun syncLibrary(scanForMissing: Boolean = false): LibrarySync = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext LibrarySync(emptyList(), emptyList())

        val songList = mutableListOf<Song>()
        val pendingTags = mutableListOf<PendingTag>()

        // Strict scope: only files under the folder the user picked.
        val scopeRoot = LibraryScope.resolveRoot(context)
        val scopePrefix = scopeRoot?.let { it.trimEnd('/') + "/" }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.Audio.Media.GENRE
            } else {
                MediaStore.Audio.Media.ARTIST
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.Audio.Media.BITRATE)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            projectionList.add(MediaStore.Audio.Media.SAMPLERATE)
            projectionList.add(MediaStore.Audio.Media.BITS_PER_SAMPLE)
        }
        val projection = projectionList.toTypedArray()

        // Include songs flagged by MediaStore as IS_MUSIC, or any audio file that is not a system sound (ringtone/alarm/notification)
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR (${MediaStore.Audio.Media.IS_RINGTONE} == 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} == 0 AND ${MediaStore.Audio.Media.IS_ALARM} == 0))"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val overrides = database.songOverrideDao().getAllOverrides().associateBy { it.songId }
        val tagCache = database.fileTagDao().getAll().associateBy { it.path }

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val genreColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            } else -1
            val bitrateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            } else -1
            val sampleRateColumn = if (Build.VERSION.SDK_INT >= 36) {
                cursor.getColumnIndex(MediaStore.Audio.Media.SAMPLERATE)
            } else -1
            val bitDepthColumn = if (Build.VERSION.SDK_INT >= 36) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BITS_PER_SAMPLE)
            } else -1

            while (cursor.moveToNext()) {
                val data = cursor.getString(dataColumn) ?: continue
                if (data.isBlank()) continue
                if (scopePrefix != null && !data.startsWith(scopePrefix)) continue

                val extension = data.substringAfterLast(".", "").lowercase()
                if (extension !in AUDIO_EXTENSIONS) continue

                val file = File(data)
                val exists = file.exists()
                if (exists && file.length() == 0L) continue

                val fileSize = if (exists) file.length() else 0L
                val lastModified = if (exists) file.lastModified() else 0L
                val cached = if (exists) {
                    tagCache[data]?.takeIf { it.fileSize == fileSize && it.lastModified == lastModified }
                } else null
                // A row read by an older reader is pending; rows already at the
                // current epoch keep their place, so an interrupted pass resumes.
                if (exists && (cached == null || cached.epoch != TAG_CACHE_VERSION)) {
                    pendingTags.add(PendingTag(data, fileSize, lastModified, extension))
                }

                val id = cursor.getLong(idColumn)
                var title = cursor.getString(titleColumn) ?: ""
                var artist = cursor.getString(artistColumn) ?: ""
                var album = cursor.getString(albumColumn) ?: ""
                var duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                var trackNumber = if (trackColumn != -1) cursor.getInt(trackColumn) else 0
                var genre = if (genreColumn != -1) cursor.getString(genreColumn) else null

                // If MediaStore duration is missing/zero, attempt extraction directly
                if (duration <= 0L && exists) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.release()
                    } catch (_: Throwable) {}
                }

                if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
                    title = file.nameWithoutExtension.ifBlank { "Track $id" }
                }
                if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true)) {
                    artist = context.getString(R.string.unknown_artist)
                }
                if (album.isBlank() || album.equals("<unknown>", ignoreCase = true)) {
                    album = context.getString(R.string.unknown_album)
                }

                val override = overrides[id]

                // Tags for formats MediaStore does not parse (WAV, DSD) or where it
                // returned empty values. Skipped when the user edited the song.
                if (cached != null && override == null) {
                    val forceTagValues = extension == "wav"
                    if (!cached.tagTitle.isNullOrBlank()) {
                        title = CharsetUtils.sanitizeText(cached.tagTitle)
                    }
                    // The file's own tags win: MediaStore keeps whatever its
                    // scanner decoded, which can be a different charset.
                    if (!cached.tagArtist.isNullOrBlank()) {
                        artist = CharsetUtils.sanitizeText(cached.tagArtist)
                    }
                    if (!cached.tagAlbum.isNullOrBlank()) {
                        album = CharsetUtils.sanitizeText(cached.tagAlbum)
                    }
                    if (!cached.tagGenre.isNullOrBlank()) {
                        genre = CharsetUtils.sanitizeText(cached.tagGenre)
                    }
                    // MediaStore encodes a multi-disc track as disc * 1000 + track,
                    // so the tag wins whenever the column carries that form.
                    if (cached.tagTrack != null && cached.tagTrack > 0 &&
                        (forceTagValues || trackNumber == 0 || trackNumber > 999)
                    ) {
                        trackNumber = cached.tagTrack
                    }
                }

                var coverUrl: String? = null
                var isFavorite = false
                var albumArtist = ""
                if (override != null) {
                    override.title?.let { if (it.isNotBlank()) title = it }
                    override.artist?.let { if (it.isNotBlank()) artist = it }
                    override.album?.let { if (it.isNotBlank()) album = it }
                    override.genre?.let { if (it.isNotBlank()) genre = it }
                    coverUrl = override.coverUri
                    isFavorite = override.isFavorite
                }
                if (albumArtist.isBlank()) {
                    val cachedAlbumArtist = cached?.albumArtist
                    if (!cachedAlbumArtist.isNullOrBlank()) {
                        albumArtist = CharsetUtils.sanitizeText(cachedAlbumArtist)
                    }
                }

                title = CharsetUtils.sanitizeText(title)
                artist = CharsetUtils.sanitizeText(artist)
                album = CharsetUtils.sanitizeText(album)

                // MediaStore's track column can carry disc * 1000 + track even
                // when no tag was cached.
                if (trackNumber > 999) trackNumber %= 1000
                val mediaStoreYear = if (yearColumn != -1) {
                    cursor.getInt(yearColumn).takeIf { it in 1000..2200 }
                } else null
                // The file tag wins: MediaStore's year column is empty on half the library.
                val year = cached?.year ?: mediaStoreYear

                val folderName = data.substringBeforeLast("/").substringAfterLast("/")
                val isHiFiFile = extension in HI_FI_EXTENSIONS
                val isHiFi = settingsManager.enableHiFi && isHiFiFile
                val format = when (extension) {
                    "mp3" -> "MP3"
                    "flac" -> "FLAC"
                    "wav" -> "WAV"
                    "aac", "m4a" -> "AAC"
                    "ogg" -> "OGG"
                    "opus" -> "OPUS"
                    "wma" -> "WMA"
                    "alac" -> "ALAC"
                    "dsf", "dff" -> "DSD"
                    else -> extension.uppercase()
                }

                val mediaStoreBitrate = if (bitrateColumn != -1) {
                    val value = cursor.getInt(bitrateColumn)
                    if (value > 0) value else null
                } else null

                val calculatedBitrate = if (duration > 0 && exists) {
                    ((file.length() * 8) / (duration / 1000f)).toInt()
                } else null

                val bitrate = cached?.bitrate ?: mediaStoreBitrate ?: calculatedBitrate

                val sampleRate = cached?.sampleRate
                    ?: if (sampleRateColumn != -1) cursor.getInt(sampleRateColumn).takeIf { it > 0 } else null
                val bitDepth = cached?.bitDepth
                    ?: if (bitDepthColumn != -1) cursor.getInt(bitDepthColumn).takeIf { it > 0 } else null

                var isHiRes = isHiFiFile && isHiResDetection(sampleRate, bitDepth, extension)
                if (!isHiRes && isHiFiFile && sampleRate == null && bitDepth == null && (bitrate ?: 0) >= 2304000) {
                    isHiRes = true
                }

                val contentUri: Uri = ContentUris.withAppendedId(collection, id)

                // Use the song's specific contentUri as albumArtUri so AudioThumbnailFetcher extracts
                // the per-song embedded picture rather than leaking shared MediaStore albumart (Fixes Issue #106)
                val albumArtUri = contentUri

                songList.add(
                    Song(
                        id, albumId, title, artist, album, duration, contentUri, data,
                        dateAdded, albumArtUri, genre, folderName, isHiFi, coverUrl, isFavorite, null,
                        format, bitrate, trackNumber, isHiRes, sampleRate, bitDepth, albumArtist,
                        year = year
                    )
                )
            }
        }

        // Direct disk scan fallback for audio files on storage that haven't been indexed by MediaStore yet.
        // Only on explicit refresh: the callers that sync on resume must stay cheap.
        if (scanForMissing) {
            val indexedPaths = songList.mapTo(HashSet(songList.size)) { it.path.trim() }
            val missingFiles = mutableSetOf<File>()

            fun scanDirForMissing(dir: File, maxDepth: Int = 8) {
                if (maxDepth <= 0 || !dir.exists() || !dir.canRead()) return
                val list = dir.listFiles() ?: return
                for (f in list) {
                    if (f.isDirectory) {
                        if (!f.name.startsWith(".")) {
                            scanDirForMissing(f, maxDepth - 1)
                        }
                    } else if (f.isFile && f.length() > 0L && f.extension.lowercase() in AUDIO_EXTENSIONS) {
                        val abs = f.absolutePath.trim()
                        if (!indexedPaths.contains(abs)) {
                            missingFiles.add(f)
                        }
                    }
                }
            }

            if (scopeRoot != null) {
                scanDirForMissing(File(scopeRoot))
            } else {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                if (musicDir.exists()) scanDirForMissing(musicDir)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists()) scanDirForMissing(downloadsDir)

                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                if (documentsDir.exists()) scanDirForMissing(documentsDir)

                val audioFolderUriStr = settingsManager.musicFolderUri
                if (!audioFolderUriStr.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(audioFolderUriStr)
                        val pathSegment = uri.lastPathSegment?.substringAfterLast(":")
                        if (!pathSegment.isNullOrBlank()) {
                            val customFolder = File(Environment.getExternalStorageDirectory(), pathSegment)
                            if (customFolder.exists()) scanDirForMissing(customFolder)
                        }
                    } catch (_: Exception) {}
                }
            }

            if (missingFiles.isNotEmpty()) {
                val missingPaths = mutableListOf<String>()
                for (f in missingFiles) {
                    try {
                        val resolved = SongResolver.resolveSongFromUri(context, Uri.fromFile(f))
                        if (resolved != null) {
                            songList.add(resolved)
                            missingPaths.add(f.absolutePath)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (missingPaths.isNotEmpty()) {
                    try {
                        MediaScannerConnection.scanFile(context, missingPaths.toTypedArray(), null, null)
                    } catch (_: Exception) {}
                }
            }
        }

        songList.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        // Only a full rescan walked the disk, so only then is a path missing
        // from the list a path that no longer exists.
        if (scanForMissing) {
            val currentPaths = songList.mapTo(HashSet(songList.size)) { it.path }
            val stalePaths = staleTagCachePaths(tagCache.keys, currentPaths, scopePrefix)
            if (stalePaths.isNotEmpty()) {
                stalePaths.chunked(500).forEach { chunk -> database.fileTagDao().deleteAll(chunk) }
            }
        }

        saveToCache(songList)
        LibrarySync(songList, pendingTags.distinctBy { it.path })
    }

    /**
     * Full rescan: the MediaStore pass, the missing file walk and the tag pass,
     * reporting tag progress so a caller can show it.
     */
    data class RescanResult(val songCount: Int, val tagsRead: Int)

    suspend fun refreshLibrary(onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): RescanResult =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext RescanResult(0, 0)
            val sync = syncLibrary(scanForMissing = true)
            var tagsRead = 0
            if (sync.pendingTags.isNotEmpty()) {
                val total = sync.pendingTags.size
                enrichTags(sync.pendingTags) { _, attempted ->
                    tagsRead += attempted
                    onProgress(tagsRead, total)
                }
            }
            // Rebuild from the rows just written, so the caller and the next
            // screen see the new text instead of the pre-read snapshot.
            val refreshed = if (tagsRead > 0) syncLibrary(scanForMissing = false) else sync
            // The library screen watches this to reload without waiting for its
            // own throttle.
            settingsManager.libraryRevision = settingsManager.libraryRevision + 1
            RescanResult(refreshed.songs.size, tagsRead)
        }

    /**
     * Reads tags for files with no usable cache entry and reports each batch so
     * the library can update without waiting for the whole scan.
     */
    suspend fun enrichTags(
        pending: List<PendingTag>,
        batchSize: Int = 500,
        onBatch: suspend (entries: List<FileTagCache>, attempted: Int) -> Unit
    ) {
        if (pending.isEmpty()) return
        val dao = database.fileTagDao()
        pending.chunked(batchSize).forEach { chunk ->
            val entries = withContext(Dispatchers.IO) {
                chunk.mapNotNull { pendingTag ->
                    val file = File(pendingTag.path)
                    if (!file.exists()) return@mapNotNull null
                    val tags = AudioTagReader.read(file, pendingTag.extension)
                    // A read that produced nothing must not replace a good row.
                    // Skipping leaves it on its old epoch, so it is tried again.
                    if (tags.isBlank()) return@mapNotNull null
                    FileTagCache(
                        path = pendingTag.path,
                        fileSize = pendingTag.fileSize,
                        lastModified = pendingTag.lastModified,
                        albumArtist = tags.albumArtist,
                        bitrate = tags.bitrate,
                        sampleRate = tags.sampleRate,
                        bitDepth = tags.bitDepth,
                        tagTitle = tags.title,
                        tagArtist = tags.artist,
                        tagAlbum = tags.album,
                        tagGenre = tags.genre,
                        tagTrack = tags.trackNumber,
                        year = tags.year,
                        epoch = TAG_CACHE_VERSION
                    )
                }
            }
            if (entries.isNotEmpty()) {
                withContext(Dispatchers.IO) { dao.upsertAll(entries) }
            }
            onBatch(entries, chunk.size)
        }
    }

    private fun isHiResDetection(sampleRate: Int?, bitDepth: Int?, extension: String): Boolean {
        if (extension == "dsf" || extension == "dff") return true
        val sampleRateValue = sampleRate ?: 0
        val bitDepthValue = bitDepth ?: 0
        return (sampleRateValue >= 48000 && bitDepthValue >= 24) || sampleRateValue >= 88200
    }
}

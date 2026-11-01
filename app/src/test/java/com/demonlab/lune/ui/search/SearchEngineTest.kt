package com.demonlab.lune.ui.search

import android.net.Uri
import com.demonlab.lune.data.Playlist
import com.demonlab.lune.data.PlaylistSong
import com.demonlab.lune.tools.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search runs over the whole library on every (debounced) keystroke, so these
 * tests pin its matching rules and the id-based playlist resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEngineTest {

    private var nextId = 1L

    private fun song(
        title: String,
        artist: String,
        album: String,
        albumArtist: String = "",
        folder: String = "Music",
        favorite: Boolean = false
    ): Song {
        val id = nextId++
        return Song(
            id = id,
            albumId = id,
            title = title,
            artist = artist,
            album = album,
            duration = 200_000L,
            uri = Uri.parse("content://media/external/audio/media/$id"),
            path = "/storage/emulated/0/$folder/$title.mp3",
            folderName = folder,
            albumArtist = albumArtist,
            isFavorite = favorite
        )
    }

    private fun search(
        query: String,
        songs: List<Song>,
        playlists: List<Playlist> = emptyList(),
        mappings: List<PlaylistSong> = emptyList()
    ) = computeSearchResults(
        query = query,
        allSongs = songs,
        allAlbums = emptyList(),
        allRealAlbums = emptyList(),
        allPlaylists = playlists,
        playlistMappings = mappings,
        allFolders = songs.map { it.folderName }.distinct(),
        favoritesTag = "Favorites"
    )

    private fun ids(songs: List<Song>) = songs.map { it.id }.sorted()

    @Test
    fun `matches title artist and album`() {
        val target = song("Midnight City", "M83", "Hurry Up, We're Dreaming")
        val other = song("Daylight", "Aesop Rock", "Labor Days")
        val songs = listOf(target, other)

        assertEquals(listOf(target.id), ids(search("midnight", songs).songs))
        assertEquals(listOf(target.id), ids(search("m83", songs).songs))
        assertEquals(listOf(target.id), ids(search("dreaming", songs).songs))
    }

    @Test
    fun `matches album artist even when track artists differ`() {
        val first = song("Intro", "Guest A", "Compilation Vol. 1", albumArtist = "Various Artists")
        val second = song("Outro", "Guest B", "Compilation Vol. 1", albumArtist = "Various Artists")
        val solo = song("Solo", "Guest A", "Solo Album")
        val songs = listOf(first, second, solo)

        val results = search("various artists", songs).songs

        assertEquals(listOf(first.id, second.id), ids(results))
    }

    @Test
    fun `all terms must match`() {
        val target = song("Midnight City", "M83", "Hurry Up")
        val songs = listOf(target, song("Midnight Sun", "M83", "Other"))

        assertEquals(listOf(target.id), ids(search("midnight hurry", songs).songs))
    }

    @Test
    fun `accent-insensitive matching still works`() {
        val target = song("Canción del mar", "Artista", "Álbum")
        assertEquals(listOf(target.id), ids(search("cancion", listOf(target)).songs))
    }

    @Test
    fun `resolves playlist songs through the id map`() {
        val first = song("Alpha", "A", "One")
        val second = song("Beta", "B", "Two")
        val third = song("Gamma", "C", "Three")
        val playlist = Playlist(id = 7L, name = "Road trip")
        val mappings = listOf(
            PlaylistSong(playlistId = 7L, songId = third.id),
            PlaylistSong(playlistId = 7L, songId = first.id),
            PlaylistSong(playlistId = 7L, songId = second.id)
        )

        val results = search("alpha", listOf(first, second, third), listOf(playlist), mappings)

        assertEquals(1, results.playlistResults.size)
        val matched = results.playlistResults.values.first()
        assertEquals(listOf(first.id), ids(matched))
    }

    @Test
    fun `playlist name matches are returned without song matches`() {
        val song = song("Alpha", "A", "One")
        val playlist = Playlist(id = 9L, name = "Focus")
        val results = search("focus", listOf(song), listOf(playlist), emptyList())

        assertEquals(1, results.playlistResults.size)
        assertTrue(results.playlistResults.values.first().isEmpty())
    }

    @Test
    fun `favorites and folder sections are populated`() {
        val favorite = song("Loved", "A", "One", folder = "Jazz", favorite = true)
        val plain = song("Plain", "B", "Two", folder = "Jazz")
        val songs = listOf(favorite, plain)

        val results = search("loved", songs)

        assertEquals(listOf(favorite.id), ids(results.tagResults["Jazz"].orEmpty()))
        assertEquals(listOf(favorite.id), ids(results.favoriteSongs))
        assertEquals(listOf(favorite.id), ids(results.tagResults["Favorites"].orEmpty()))
    }

    @Test
    fun `ten thousand songs stay fast and correct`() {
        val songs = (1..10_000).map { index ->
            song(
                title = "Track ${index % 500}",
                artist = "Artist ${index % 300}",
                album = "Album ${index % 800}",
                albumArtist = "VA Group G${index % 150}X",
                folder = "Folder ${index % 40}"
            )
        }
        val playlists = (1..20).map { Playlist(id = it.toLong(), name = "Playlist $it") }
        val mappings = playlists.flatMap { playlist ->
            (0 until 500).map { offset ->
                val songId = songs[((playlist.id * 37 + offset * 13) % songs.size).toInt()].id
                PlaylistSong(playlistId = playlist.id, songId = songId)
            }
        }

        val started = System.nanoTime()
        val results = search("g42x", songs, playlists, mappings)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        val expected = songs.filter { it.albumArtist.contains("g42x", ignoreCase = true) }
        assertEquals(expected.size, results.songs.size)
        println("10k-song search: ${results.songs.size} matches, ${results.playlistResults.size} playlists, ${elapsedMs}ms")
    }
}

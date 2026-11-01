package com.demonlab.lune.ui.search

import android.net.Uri
import com.demonlab.lune.data.Playlist
import com.demonlab.lune.data.PlaylistSong
import com.demonlab.lune.tools.Song
import com.demonlab.lune.ui.data.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search runs over the whole library on every (debounced) keystroke, so these
 * tests pin its matching rules, the album artist grouping and the id-based
 * playlist resolution.
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

    private fun album(albumArtist: String, name: String, songs: List<Song>) = Album(
        id = Album.idFor(albumArtist, name),
        name = name,
        artist = albumArtist,
        albumArtUri = null,
        coverUrl = null,
        songs = songs
    )

    private fun search(
        query: String,
        songs: List<Song>,
        albums: List<Album> = emptyList(),
        playlists: List<Playlist> = emptyList(),
        mappings: List<PlaylistSong> = emptyList()
    ) = computeSearchResults(
        query = query,
        allSongs = songs,
        allAlbums = albums,
        allPlaylists = playlists,
        playlistMappings = mappings
    )

    private fun ids(songs: List<Song>) = songs.map { it.id }.sorted()

    @Test
    fun `songs match their own title only`() {
        val target = song("Midnight City", "M83", "Hurry Up, We're Dreaming")
        val other = song("Daylight", "Aesop Rock", "Labor Days")
        val songs = listOf(target, other)

        assertEquals(listOf(target.id), ids(search("midnight", songs).songs))
        // The artist and the album have their own groups; they must not add songs.
        assertEquals(emptyList<Long>(), ids(search("m83", songs).songs))
        assertEquals(emptyList<Long>(), ids(search("dreaming", songs).songs))
    }

    @Test
    fun `albums match their own title only`() {
        val onAlbum = song("Dogs", "Pink Floyd", "Animals", albumArtist = "Pink Floyd")
        val otherSong = song("hi grace", "Dominic Fike", "14 minutes", albumArtist = "Dominic Fike")
        val songs = listOf(onAlbum, otherSong)
        val albums = listOf(
            album("Pink Floyd", "Animals", listOf(onAlbum)),
            album("Dominic Fike", "14 minutes", listOf(otherSong))
        )

        // "grace" appears in a song title but in no album title.
        val grace = search("grace", songs, albums = albums)
        assertEquals(emptySet<String>(), grace.albumResults.keys.map { it.name }.toSet())
        assertEquals(listOf(otherSong.id), ids(grace.songs))

        // "animals" is an album title, so the album appears even though no song matches.
        val animals = search("animals", songs, albums = albums)
        assertEquals(listOf("Animals"), animals.albumResults.keys.map { it.name })
        assertEquals(emptyList<Long>(), ids(animals.songs))
    }

    @Test
    fun `all terms must match`() {
        val target = song("Midnight City Lights", "M83", "Hurry Up")
        val songs = listOf(target, song("Midnight Sun", "M83", "Other"))

        assertEquals(listOf(target.id), ids(search("midnight city", songs).songs))
        assertEquals(emptyList<Long>(), ids(search("midnight hurry", songs).songs))
    }

    @Test
    fun `accent-insensitive matching still works`() {
        val target = song("Canción del mar", "Artista", "Álbum")
        assertEquals(listOf(target.id), ids(search("cancion", listOf(target)).songs))
    }

    @Test
    fun `album artists match their own name, not their tracks or guests`() {
        val first = song("Intro", "Guest A", "Compilation Vol. 1", albumArtist = "Various Artists")
        val second = song("Outro", "Guest B", "Compilation Vol. 1", albumArtist = "Various Artists")
        val titledGrace = song("Grace", "Someone", "Record", albumArtist = "Apocalyptica")
        val guestGrace = song("Blushin", "Arlo, Emi Grace", "Blushin", albumArtist = "Arlo")
        val songs = listOf(first, second, titledGrace, guestGrace)

        // A track titled Grace, or a featured artist named Grace, must not surface an artist.
        assertEquals(emptySet<String>(), search("grace", songs).albumArtistResults.keys.map { it.name }.toSet())

        // The artist's own name does.
        val named = search("various artists", songs)
        assertEquals(setOf("Various Artists"), named.albumArtistResults.keys.map { it.name }.toSet())
        assertEquals(listOf(first.id, second.id), ids(named.albumArtistResults.keys.first().songs))
    }

    @Test
    fun `exact album title comes before longer titles`() {
        val exact = song("Dogs", "Pink Floyd", "Grace", albumArtist = "Pink Floyd")
        val longer = song("X", "Foo Fighters", "Echoes, Silence, Patience & Grace", albumArtist = "Foo Fighters")
        val songs = listOf(exact, longer)
        val albums = listOf(
            album("Foo Fighters", "Echoes, Silence, Patience & Grace", listOf(longer)),
            album("Pink Floyd", "Grace", listOf(exact))
        )

        val results = search("grace", songs, albums = albums)

        assertEquals(
            listOf("Grace", "Echoes, Silence, Patience & Grace"),
            results.albumResults.keys.map { it.name }
        )
    }

    @Test
    fun `same album title under two artists stays two album results`() {
        val pinkFloyd = song("Dogs", "Pink Floyd", "Animals", albumArtist = "Pink Floyd")
        val theAnimals = song("The House of the Rising Sun", "The Animals", "Animals", albumArtist = "The Animals")
        val songs = listOf(pinkFloyd, theAnimals)
        val albums = listOf(
            album("Pink Floyd", "Animals", listOf(pinkFloyd)),
            album("The Animals", "Animals", listOf(theAnimals))
        )

        val results = search("animals", songs, albums = albums)

        assertEquals(2, results.albumResults.size)
        assertEquals(
            setOf("Pink Floyd", "The Animals"),
            results.albumResults.keys.map { it.artist }.toSet()
        )
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

        val results = search("alpha", listOf(first, second, third), playlists = listOf(playlist), mappings = mappings)

        assertEquals(1, results.playlistResults.size)
        val matched = results.playlistResults.values.first()
        assertEquals(listOf(first.id), ids(matched))
    }

    @Test
    fun `playlist name matches are returned without song matches`() {
        val song = song("Alpha", "A", "One")
        val playlist = Playlist(id = 9L, name = "Focus")
        val results = search("focus", listOf(song), playlists = listOf(playlist))

        assertEquals(1, results.playlistResults.size)
        assertTrue(results.playlistResults.values.first().isEmpty())
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
        val results = search("g42x", songs, playlists = playlists, mappings = mappings)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        // Only album artist names contain "g42x"; no song title does.
        assertEquals(0, results.songs.size)
        assertEquals(1, results.albumArtistResults.size)

        val byTitle = search("track 42", songs)
        // Every term must appear somewhere in the title, so 420-429 match too.
        val expectedTitles = songs.filter { song ->
            val title = song.title.lowercase()
            title.contains("track") && title.contains("42")
        }
        assertEquals(expectedTitles.size, byTitle.songs.size)

        println("10k-song search: ${byTitle.songs.size} title matches, ${results.albumArtistResults.size} album artist, ${elapsedMs}ms")
    }
}

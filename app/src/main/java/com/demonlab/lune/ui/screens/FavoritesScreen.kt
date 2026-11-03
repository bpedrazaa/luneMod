package com.demonlab.lune.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.demonlab.lune.R
import com.demonlab.lune.tools.Song
import com.demonlab.lune.ui.components.SongItem
import com.demonlab.lune.ui.data.Album
import com.demonlab.lune.ui.data.AlbumCard

/**
 * The Favorites tab: favorite albums and album artists on top, the favorite
 * songs below, grouped the way the search screen groups its results.
 */
@Composable
fun FavoritesScreen(
    songs: List<Song>,
    albums: List<Album>,
    albumArtists: List<Album>,
    currentSongId: Long?,
    isPlaying: Boolean,
    isCurrentListPlaying: Boolean,
    isShuffleActive: Boolean,
    hasBlurBackground: Boolean,
    isDarkTheme: Boolean,
    useCustomControlsColor: Boolean,
    controlsColorPalette: Int,
    bottomPadding: Dp,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongOptions: (Song) -> Unit,
    onSongFavorite: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Album) -> Unit,
    onAlbumFavorite: (Album) -> Unit,
    onArtistFavorite: (Album) -> Unit
) {
    if (songs.isEmpty() && albums.isEmpty() && albumArtists.isEmpty()) {        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val sectionColor = if (hasBlurBackground) Color.White else MaterialTheme.colorScheme.onSurface

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tab_favorites),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasBlurBackground) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (songs.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.favorites_song_count, songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onShuffleClick,
                        shape = CircleShape,
                        color = if (isShuffleActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = stringResource(R.string.option_shuffle),
                                tint = if (isShuffleActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = onPlayClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCurrentListPlaying && isPlaying) Icons.Default.Pause
                                else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (isCurrentListPlaying && isPlaying) R.string.play_state_pause
                                    else R.string.play_state_play
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.favorites_albums), sectionColor)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Box(modifier = Modifier.width(150.dp)) {
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                hasBlurBackground = hasBlurBackground,
                                category = "ALBUMS",
                                isFavorite = true,
                                onFavoriteClick = { onAlbumFavorite(album) }
                            )
                        }
                    }
                }
            }
        }

        if (albumArtists.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.favorites_album_artists), sectionColor)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(albumArtists, key = { it.id }) { artist ->
                        Box(modifier = Modifier.width(150.dp)) {
                            AlbumCard(
                                album = artist,
                                onClick = { onArtistClick(artist) },
                                hasBlurBackground = hasBlurBackground,
                                category = "ALBUM_ARTISTS",
                                isFavorite = true,
                                onFavoriteClick = { onArtistFavorite(artist) }
                            )
                        }
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.favorites_songs), sectionColor)
            }
            itemsIndexed(
                songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song_item" }
            ) { index, song ->
                SongItem(
                    isFirst = index == 0,
                    isLast = index == songs.lastIndex,
                    song = song,
                    currentlyPlaying = currentSongId == song.id,
                    isPlaying = isPlaying,
                    hasBlurBackground = hasBlurBackground,
                    useCustomControlsColor = useCustomControlsColor,
                    controlsColorPalette = controlsColorPalette,
                    onClick = { onSongClick(song) },
                    onOptionsClick = { onSongOptions(song) },
                    onFavoriteClick = onSongFavorite
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

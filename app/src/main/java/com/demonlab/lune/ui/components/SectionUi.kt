package com.demonlab.lune.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.demonlab.lune.R

/**
 * Label and icon for a section key. One map for the section menu, the bottom
 * bar and the customization pickers, so a new key cannot ship with a raw key
 * and a folder icon.
 */
object SectionUi {

    /** The section's label, or null when the key has no localised name. */
    @StringRes
    fun labelRes(section: String): Int? = when (section) {
        "RESUME" -> R.string.tab_resume
        "MIXES" -> R.string.tab_mixes
        "ALL" -> R.string.tab_all
        "FAVORITES" -> R.string.tab_favorites
        "ALBUMS" -> R.string.tab_albums_real
        "ARTISTS" -> R.string.tab_artists
        "ALBUM_ARTISTS" -> R.string.tab_album_artists
        "GENRES" -> R.string.tab_genres
        "PLAYLISTS" -> R.string.playlists
        "FOLDERS" -> R.string.tab_folders
        else -> null
    }

    /** Album artists share the artists icon; they are the same axis to browse. */
    fun icon(section: String): ImageVector = when (section) {
        "RESUME" -> Icons.Default.History
        "MIXES" -> Icons.Default.AutoAwesome
        "ALL" -> Icons.Default.LibraryMusic
        "ALBUMS" -> Icons.Default.Album
        "ARTISTS", "ALBUM_ARTISTS" -> Icons.Default.Person
        "GENRES" -> Icons.Default.Category
        "PLAYLISTS" -> Icons.AutoMirrored.Filled.QueueMusic
        "FOLDERS" -> Icons.Default.Folder
        "FAVORITES" -> Icons.Default.FavoriteBorder
        else -> Icons.Default.Folder
    }
}

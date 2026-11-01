package com.demonlab.lune.tools

import android.content.Context
import android.util.Log
import com.demonlab.lune.data.MusicDatabase
import com.demonlab.lune.data.SongOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataManager(private val context: Context) {
    private val database = MusicDatabase.getDatabase(context)

    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = database.songOverrideDao().getOverrideForSong(songId)
            val override = existing?.copy(isFavorite = isFavorite)
                ?: SongOverride(songId = songId, isFavorite = isFavorite)

            database.songOverrideDao().insertOverride(override)
            true
        } catch (e: Exception) {
            Log.e("MetadataManager", "Error updating favorite status", e)
            false
        }
    }
}

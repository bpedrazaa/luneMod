package com.demonlab.lune.tools

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Turns the SAF folder chosen by the user into a filesystem root, so the
 * library can be limited to it instead of hiding other folders after a scan.
 */
object LibraryScope {

    /** "primary:Music/MyMusic" becomes ("primary", "Music/MyMusic"). */
    fun pathFromDocId(docId: String): Pair<String, String>? {
        val separator = docId.indexOf(':')
        if (separator <= 0 || separator == docId.lastIndex) return null
        val volume = docId.substring(0, separator).trim()
        val relative = docId.substring(separator + 1).trim().trim('/')
        if (volume.isEmpty() || relative.isEmpty()) return null
        return volume to relative
    }

    /** True for the root itself and for paths below it, with a path boundary. */
    fun isInside(path: String, root: String): Boolean {
        val normalizedRoot = root.trimEnd('/')
        if (normalizedRoot.isEmpty()) return false
        return path == normalizedRoot || path.startsWith("$normalizedRoot/")
    }

    /**
     * Filesystem root of the selected folder, or null when the library is not
     * scoped: no folder chosen, "show all folders" enabled, or a tree that
     * cannot be mapped to local storage.
     */
    fun resolveRoot(context: Context): String? {
        val settings = SettingsManager.getInstance(context)
        if (settings.showAllFoldersOnStart) return null
        val uriString = settings.musicFolderUri ?: return null
        val docId = runCatching { Uri.parse(uriString).lastPathSegment }.getOrNull() ?: return null
        val (volume, relative) = pathFromDocId(Uri.decode(docId)) ?: return null
        val base = when {
            volume.equals("primary", ignoreCase = true) ->
                Environment.getExternalStorageDirectory().absolutePath
            volume.matches(Regex("[0-9A-Fa-f-]{8,}")) -> "/storage/$volume"
            else -> return null
        }
        val root = "$base/$relative"
        return root.takeIf { File(it).isDirectory }
    }
}

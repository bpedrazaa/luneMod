package com.demonlab.lune.ui.utils

import java.util.Locale

/**
 * Artist names are sorted and indexed past a leading article, so "The
 * Strokes", "A Band" and "An Ocean" file under S, B and O.
 */
fun artistSortName(name: String?, caseSensitive: Boolean = false): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    var stripped = trimmed
    for (article in listOf("the ", "an ", "a ")) {
        if (trimmed.length > article.length &&
            trimmed.substring(0, article.length).equals(article, ignoreCase = true)
        ) {
            stripped = trimmed.substring(article.length).trim()
            break
        }
    }
    return if (caseSensitive) stripped else stripped.lowercase(Locale.ROOT)
}

fun artistLetter(name: String?): String = artistSortName(name).take(1).uppercase(Locale.ROOT)

/** Album and genre names keep their first letter. */
fun firstLetter(name: String?): String = name?.trim().orEmpty().take(1).uppercase(Locale.ROOT)

package com.demonlab.lune.ui.utils

import java.util.Locale

/**
 * Artist names are sorted and indexed past a leading article, so "The
 * Strokes", "A Band", "Los Prisioneros" and "Le Tigre" file under S, B, P
 * and T. A word that merely starts with those letters ("Lana", "Elvis") is
 * left alone, because the article has to be followed by a space.
 */
private val LEADING_ARTICLES = listOf(
    "the ", "an ", "a ",
    "los ", "las ", "les ", "el ", "la ", "lo ", "le ",
    "unos ", "unas ", "una ", "un "
)

fun artistSortName(name: String?, caseSensitive: Boolean = false): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    var stripped = trimmed
    for (article in LEADING_ARTICLES) {
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

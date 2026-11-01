package com.demonlab.lune.tools

/**
 * Cache rows with no file left in the library. When the library is scoped only
 * rows under the scope are considered, so switching folders does not throw
 * away the tags of folders that are simply out of view.
 */
internal fun staleTagCachePaths(
    cachedPaths: Collection<String>,
    currentPaths: Set<String>,
    scopePrefix: String?
): List<String> = cachedPaths.filter { path ->
    path !in currentPaths && (scopePrefix == null || path.startsWith(scopePrefix))
}

package com.demonlab.lune.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.demonlab.lune.R
import com.demonlab.lune.data.Playlist
import com.demonlab.lune.data.PlaylistSong
import com.demonlab.lune.tools.PlaybackManager
import com.demonlab.lune.tools.SettingsManager
import com.demonlab.lune.tools.Song
import com.demonlab.lune.tools.normalizeForSearch
import androidx.compose.foundation.lazy.rememberLazyListState
import com.demonlab.lune.ui.components.AppBlurBackdrop
import com.demonlab.lune.ui.components.ScrollToTopPill
import com.demonlab.lune.ui.components.rememberScrollToTopVisibility
import com.demonlab.lune.ui.components.SongItem
import com.demonlab.lune.ui.components.rememberBlurSheetColors
import com.demonlab.lune.ui.data.Album
import com.demonlab.lune.ui.playlist.PlaylistPreviewCovers
import com.demonlab.lune.ui.utils.bounceClick
import com.demonlab.lune.ui.utils.fastSmoothScrollToItem
import com.demonlab.lune.ui.viewmodels.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResults(
    val songs: List<Song>,
    val albumArtistResults: Map<Album, List<Song>>,
    val albumResults: Map<Album, List<Song>>,
    val playlistResults: Map<Playlist, List<Song>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    allFolders: List<String>,
    onDismiss: () -> Unit,
    onSongClick: (Song, List<Song>, String, Long) -> Unit,
    onPlayAll: (List<Song>, Boolean) -> Unit,
    onNavigateToAlbum: (Album) -> Unit,
    onNavigateToAlbumArtist: (String) -> Unit,
    onNavigateToPlaylist: (Playlist) -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onOptionsClick: (Song) -> Unit,
    currentlyPlayingId: Long?,
    activeCategory: String?,
    activePlaylistId: Long?,
    onFavoriteClick: ((Song) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val pm = PlaybackManager.getInstance(context)
    val currentSong = pm.currentSong
    val settings = remember { SettingsManager.getInstance(context) }
    val blurColors = rememberBlurSheetColors(currentSong)

    var showFilterDialog by remember { mutableStateOf(false) }
    var filterSongs by remember { mutableStateOf(true) }
    var filterAlbumArtists by remember { mutableStateOf(true) }
    var filterAlbums by remember { mutableStateOf(true) }
    var filterPlaylists by remember { mutableStateOf(true) }
    val isSearchActive = activePlaylistId == -300L
    var searchShuffle by remember { mutableStateOf(settings.getPlaylistShuffle(-300L)) }

    if (settings.isSectionCustomizationEnabled) {
        val hiddenTabs = settings.hiddenSectionTabs
        if ("ALL" in hiddenTabs) filterSongs = false
        if ("ALBUM_ARTISTS" in hiddenTabs) filterAlbumArtists = false
        if ("ALBUMS" in hiddenTabs) filterAlbums = false
        if ("PLAYLISTS" in hiddenTabs) filterPlaylists = false
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchShuffle = pm.isShuffle
    }

    val isPlaying = pm.isPlaying
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val allSongs = remember(viewModel.filteredSongs, allFolders) {
        viewModel.filteredSongs.filter { song -> 
            allFolders.contains(song.folderName) 
        }
    }
    val allAlbums = remember(allSongs) {
        allSongs.groupBy { Album.keyFor(it.displayAlbumArtist, it.album) }
            .map { (_, songs) ->
                val albumName = songs.first().album.trim()
                val albumArtist = songs.first().displayAlbumArtist.trim()
                Album(
                    id = Album.idFor(albumArtist, albumName),
                    name = albumName,
                    artist = albumArtist,
                    albumArtUri = songs.first().albumArtUri,
                    coverUrl = songs.first().coverUrl,
                    songs = songs.sortedBy { it.title }
                )
            }.sortedBy { it.name }
    }
    val allPlaylists = viewModel.playlists
    val playlistMappings = viewModel.playlistMappings

    val sSectionSongs = stringResource(R.string.search_section_songs)
    val sTabAlbumArtists = stringResource(R.string.tab_album_artists)
    val sTabAlbumsReal = stringResource(R.string.tab_albums_real)
    val sFilterTitle = stringResource(R.string.search_filter)
    val sFilterAll = stringResource(R.string.search_filter_all)
    val sFilterPlaylist = stringResource(R.string.search_filter_playlist)
    val sFilterAlbum = stringResource(R.string.search_filter_album)
    val sFilterCancel = stringResource(R.string.search_filter_cancel)

    // Typing is debounced and matched off the main thread: on a 10k-song
    // library the per-keystroke filter was enough to drop input frames.
    var searchResults by remember { mutableStateOf(emptySearchResults()) }
    LaunchedEffect(query, allSongs, allAlbums, allPlaylists, playlistMappings) {
        val pendingQuery = query
        if (pendingQuery.isBlank()) {
            searchResults = emptySearchResults()
            return@LaunchedEffect
        }
        delay(180)
        searchResults = withContext(Dispatchers.Default) {
            computeSearchResults(
                query = pendingQuery,
                allSongs = allSongs,
                allAlbums = allAlbums,
                allPlaylists = allPlaylists,
                playlistMappings = playlistMappings
            )
        }
    }

    BackHandler(onBack = onDismiss)

    AppBlurBackdrop(
        hasBlurBackground = blurColors.hasBlur,
        isDarkTheme = blurColors.isDark,
        currentSong = currentSong,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { 
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .focusRequester(focusRequester),
                            placeholder = { 
                                Text(
                                    stringResource(R.string.search_hint), 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    color = blurColors.textSecondaryColor
                                ) 
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = blurColors.textColor,
                                unfocusedTextColor = blurColors.textColor,
                                cursorColor = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Surface(
                                shape = CircleShape,
                                color = if (blurColors.hasBlur) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack, 
                                        contentDescription = "Back",
                                        tint = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Surface(
                                shape = CircleShape,
                                color = if (blurColors.hasBlur) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = "Filter",
                                        tint = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            val listState = rememberLazyListState()
            val isScrollToTopVisible = rememberScrollToTopVisibility(listState, settings.isScrollToTopEnabled)
            val scope = rememberCoroutineScope()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                if (filterAlbumArtists && searchResults.albumArtistResults.isNotEmpty()) {
                    item {
                        Text(
                            text = sTabAlbumArtists,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    searchResults.albumArtistResults.forEach { (albumArtist, _) ->
                        item {
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = blurColors.textColor
                                ),
                                leadingContent = {
                                    AsyncImage(
                                        model = albumArtist.songs.firstOrNull()?.let { it.coverUrl ?: it.uri } ?: R.drawable.ic_launcher_foreground,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.clickable { onNavigateToAlbumArtist(albumArtist.name) }
                            ) {
                                Text(albumArtist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = blurColors.textColor)
                            }
                        }
                    }
                }

                if (filterAlbums && searchResults.albumResults.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = sTabAlbumsReal,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    searchResults.albumResults.forEach { (album, _) ->
                        item {
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = blurColors.textColor,
                                    supportingColor = blurColors.textSecondaryColor
                                ),
                                supportingContent = { Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = blurColors.textSecondaryColor) },
                                leadingContent = {
                                    AsyncImage(
                                        model = album.songs.firstOrNull()?.let { it.coverUrl ?: it.uri } ?: R.drawable.ic_launcher_foreground,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.clickable { onNavigateToAlbum(album) }
                            ) {
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = blurColors.textColor)
                            }
                        }
                    }
                }

                if (filterSongs && searchResults.songs.isNotEmpty()) {
                    item {
                        Text(
                            text = sSectionSongs,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onNavigateToFolder("ALL") }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                        )
                    }
                    if (searchResults.songs.size > 1) {
                        item {
                            val btnHeight = 52.dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val playBtnTint = if (blurColors.hasBlur) {
                                        if (blurColors.useCustomControlsColor) Color.White else Color.Black
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                    Surface(
                                        onClick = { onPlayAll(searchResults.songs, searchShuffle) },
                                        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                                        color = if (blurColors.hasBlur) blurColors.primaryTint else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(btnHeight)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow, 
                                                    contentDescription = null, 
                                                    modifier = Modifier.size(18.dp),
                                                    tint = playBtnTint
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    stringResource(R.string.search_play_all),
                                                    fontWeight = FontWeight.Bold,
                                                    color = playBtnTint
                                                )
                                            }
                                        }
                                    }
                                    val shuffleActiveTint = if (blurColors.hasBlur) {
                                        if (blurColors.useCustomControlsColor) Color.White else Color.Black
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                    val shuffleInactiveTint = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.onSurface
                                    Surface(
                                        onClick = {
                                            if (isSearchActive) {
                                                pm.toggleShuffle()
                                                searchShuffle = pm.isShuffle
                                            } else {
                                                val newState = !searchShuffle
                                                searchShuffle = newState
                                                settings.setPlaylistShuffle(-300L, newState)
                                            }
                                        },
                                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 26.dp, bottomEnd = 26.dp),
                                        color = if (searchShuffle) {
                                            if (blurColors.hasBlur) blurColors.primaryTint else MaterialTheme.colorScheme.primary
                                        } else {
                                            if (blurColors.hasBlur) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(btnHeight)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Shuffle, 
                                                    contentDescription = null, 
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (searchShuffle) shuffleActiveTint else shuffleInactiveTint
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    stringResource(R.string.search_shuffle),
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (searchShuffle) shuffleActiveTint else shuffleInactiveTint
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(searchResults.songs) { index, song ->
                        val isFirst = index == 0
                        val isLast = index == searchResults.songs.lastIndex
                        val isCurrent = song.id == currentlyPlayingId && activeCategory == "ALL"
                        SongItem(
                            isFirst = isFirst,
                            isLast = isLast,
                            song = song,
                            currentlyPlaying = isCurrent,
                            isPlaying = isPlaying && isCurrent,
                            hasBlurBackground = blurColors.hasBlur,
                            useCustomControlsColor = blurColors.useCustomControlsColor,
                            controlsColorPalette = blurColors.controlsColorPalette,
                            onClick = { onSongClick(song, allSongs, "ALL", -300L) },
                            onOptionsClick = { onOptionsClick(song) },
                            onFavoriteClick = onFavoriteClick
                        )
                    }
                }

                if (filterPlaylists && searchResults.playlistResults.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.playlists),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (blurColors.hasBlur) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    val playlistList = searchResults.playlistResults.toList()
                    if (playlistList.isNotEmpty()) {
                        for ((playlist, matchingSongs) in playlistList) {
                            item {
                                ListItem(
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                        headlineColor = blurColors.textColor
                                    ),
                                    leadingContent = {
                                        PlaylistPreviewCovers(playlistId = playlist.id, viewModel = viewModel, size = 50.dp)
                                    },
                                    modifier = Modifier.clickable { onNavigateToPlaylist(playlist) }
                                ) {
                                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = blurColors.textColor)
                                }
                            }
                            itemsIndexed(matchingSongs) { index, song ->
                                val isFirst = index == 0
                                val isLast = index == matchingSongs.lastIndex
                                val isCurrent = song.id == currentlyPlayingId && activeCategory == "PLAYLISTS" && activePlaylistId == playlist.id
                                SongItem(
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    song = song,
                                    currentlyPlaying = isCurrent,
                                    isPlaying = isPlaying && isCurrent,
                                    hasBlurBackground = blurColors.hasBlur,
                                    useCustomControlsColor = blurColors.useCustomControlsColor,
                                    controlsColorPalette = blurColors.controlsColorPalette,
                                    modifier = Modifier.padding(start = 32.dp),
                                    onClick = { 
                                        val fullPlaylistSongs = playlistMappings.filter { it.playlistId == playlist.id }
                                            .mapNotNull { mapping -> allSongs.find { s -> s.id == mapping.songId } }
                                        onSongClick(song, fullPlaylistSongs, "PLAYLISTS", playlist.id) 
                                        onNavigateToPlaylist(playlist)
                                    },
                                    onOptionsClick = { onOptionsClick(song) },
                                    onFavoriteClick = onFavoriteClick
                                )
                            }
                        }
                    }
                }

                if (query.isNotBlank() &&
                    (!filterAlbumArtists || searchResults.albumArtistResults.isEmpty()) &&
                    (!filterAlbums || searchResults.albumResults.isEmpty()) &&
                    (!filterSongs || searchResults.songs.isEmpty()) &&
                    (!filterPlaylists || searchResults.playlistResults.isEmpty())
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No results found", color = blurColors.textSecondaryColor)
                        }
                    }
                }
            }

            ScrollToTopPill(
                visible = isScrollToTopVisible.value,
                onClick = {
                    scope.launch {
                        listState.fastSmoothScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                hasBlurBackground = blurColors.hasBlur,
                isDarkTheme = blurColors.isDark
            )
        }
    }
}

    if (showFilterDialog) {
        val sectionCustomizationEnabled = settings.isSectionCustomizationEnabled
        val hiddenTabs = settings.hiddenSectionTabs
        val isAllDisabled = sectionCustomizationEnabled && "ALL" in hiddenTabs
        val isAlbumArtistsDisabled = sectionCustomizationEnabled && "ALBUM_ARTISTS" in hiddenTabs
        val isAlbumsDisabled = sectionCustomizationEnabled && "ALBUMS" in hiddenTabs
        val isPlaylistsDisabled = sectionCustomizationEnabled && "PLAYLISTS" in hiddenTabs

        val checkboxColors = CheckboxDefaults.colors(
            checkedColor = blurColors.primaryTint,
            checkmarkColor = if (blurColors.hasBlur) {
                if (blurColors.useCustomControlsColor) Color.White else Color.Black
            } else {
                MaterialTheme.colorScheme.onPrimary
            }
        )
        Dialog(
            onDismissRequest = { showFilterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AppBlurBackdrop(
                hasBlurBackground = blurColors.hasBlur,
                isDarkTheme = blurColors.isDark,
                currentSong = currentSong,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Surface(
                    color = if (blurColors.hasBlur) Color.Transparent else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = sFilterTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = blurColors.textColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isAllDisabled) { filterSongs = !filterSongs }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = filterSongs,
                                    enabled = !isAllDisabled,
                                    onCheckedChange = { if (!isAllDisabled) filterSongs = it },
                                    colors = checkboxColors
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sFilterAll,
                                    color = if (isAllDisabled) blurColors.textSecondaryColor.copy(alpha = 0.4f) else blurColors.textColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isAlbumArtistsDisabled) { filterAlbumArtists = !filterAlbumArtists }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = filterAlbumArtists,
                                    enabled = !isAlbumArtistsDisabled,
                                    onCheckedChange = { if (!isAlbumArtistsDisabled) filterAlbumArtists = it },
                                    colors = checkboxColors
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sTabAlbumArtists,
                                    color = if (isAlbumArtistsDisabled) blurColors.textSecondaryColor.copy(alpha = 0.4f) else blurColors.textColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isAlbumsDisabled) { filterAlbums = !filterAlbums }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = filterAlbums,
                                    enabled = !isAlbumsDisabled,
                                    onCheckedChange = { if (!isAlbumsDisabled) filterAlbums = it },
                                    colors = checkboxColors
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sFilterAlbum,
                                    color = if (isAlbumsDisabled) blurColors.textSecondaryColor.copy(alpha = 0.4f) else blurColors.textColor
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isPlaylistsDisabled) { filterPlaylists = !filterPlaylists }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = filterPlaylists,
                                    enabled = !isPlaylistsDisabled,
                                    onCheckedChange = { if (!isPlaylistsDisabled) filterPlaylists = it },
                                    colors = checkboxColors
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sFilterPlaylist,
                                    color = if (isPlaylistsDisabled) blurColors.textSecondaryColor.copy(alpha = 0.4f) else blurColors.textColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showFilterDialog = false },
                                modifier = Modifier.bounceClick()
                            ) {
                                Text(sFilterCancel, color = blurColors.textSecondaryColor)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    filterSongs = true
                                    filterAlbumArtists = true
                                    filterAlbums = true
                                    filterPlaylists = true
                                },
                                modifier = Modifier.bounceClick()
                            ) {
                                Text(sFilterAll, color = blurColors.primaryTint, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Exact name first, then starts-with, then contains. */
private fun relevanceRank(value: String, query: String): Int {
    val name = value.normalizeForSearch()
    val search = query.normalizeForSearch()
    return when {
        name == search -> 0
        name.startsWith(search) -> 1
        else -> 2
    }
}

private fun emptySearchResults() = SearchResults(
    songs = emptyList(),
    albumArtistResults = emptyMap(),
    albumResults = emptyMap(),
    playlistResults = emptyMap()
)

internal fun computeSearchResults(
    query: String,
    allSongs: List<Song>,
    allAlbums: List<Album>,
    allPlaylists: List<Playlist>,
    playlistMappings: List<PlaylistSong>
): SearchResults {
    val searchTerms = query.normalizeForSearch().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (searchTerms.isEmpty()) return emptySearchResults()

    // Songs match their own title only. Artist and album have their own groups.
    val matchedSongs = allSongs.filter { song ->
        val normalizedTitle = song.title.normalizeForSearch()
        searchTerms.all { term -> normalizedTitle.contains(term) }
    }
    val matchedSongIds = matchedSongs.mapTo(HashSet()) { it.id }

    // Album artists match their own name. A track title or a featured artist
    // named "Grace" must not surface the artist here; those belong in songs.
    val albumArtistResults = mutableMapOf<Album, List<Song>>()
    allSongs
        .groupBy { it.displayAlbumArtist }
        .entries
        .asSequence()
        .filter { (name, _) -> name.isNotBlank() }
        .filter { (name, _) -> searchTerms.all { term -> name.normalizeForSearch().contains(term) } }
        .sortedWith(compareBy({ relevanceRank(it.key, query) }, { it.key.normalizeForSearch() }))
        .forEach { (albumArtistName, artistSongs) ->
            albumArtistResults[
                Album(
                    id = albumArtistName.hashCode().toLong(),
                    name = albumArtistName,
                    artist = "",
                    albumArtUri = artistSongs.first().albumArtUri,
                    coverUrl = artistSongs.first().coverUrl,
                    songs = artistSongs
                )
            ] = artistSongs.filter { it.id in matchedSongIds }
        }

    fun matchAlbums(albums: List<Album>): MutableMap<Album, List<Song>> {
        val results = mutableMapOf<Album, List<Song>>()
        albums
            .filter { album -> searchTerms.all { term -> album.name.normalizeForSearch().contains(term) } }
            .sortedWith(compareBy({ relevanceRank(it.name, query) }, { it.name.normalizeForSearch() }))
            .forEach { album ->
                results[album] = album.songs.filter { it.id in matchedSongIds }
            }
        return results
    }

    val albumResults = matchAlbums(allAlbums)

    // One id lookup per mapping instead of a linear scan of the whole library.
    val songsById = allSongs.associateBy { it.id }
    val playlistResults = mutableMapOf<Playlist, List<Song>>()
    for (playlist in allPlaylists) {
        val nameMatches = searchTerms.all { term -> playlist.name.normalizeForSearch().contains(term) }
        val playlistSongs = playlistMappings.asSequence()
            .filter { it.playlistId == playlist.id }
            .mapNotNull { songsById[it.songId] }
            .toList()
        val matchingSongs = playlistSongs.filter { it.id in matchedSongIds }
        if (nameMatches || matchingSongs.isNotEmpty()) {
            playlistResults[playlist] = matchingSongs
        }
    }

    return SearchResults(
        songs = matchedSongs,
        albumArtistResults = albumArtistResults,
        albumResults = albumResults,
        playlistResults = playlistResults
    )
}

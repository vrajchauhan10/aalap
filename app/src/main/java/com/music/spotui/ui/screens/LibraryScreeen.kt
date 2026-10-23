package com.music.spotui.ui.screens

import androidx.compose.animation.togetherWith
import com.bumptech.glide.integration.compose.placeholder
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.entity.AccountModel
import com.music.spotui.R
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.preferences.isLibraryGridView
import com.music.spotui.data.preferences.setLibraryGridView
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {

    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val entries by libraryViewModel.entries.collectAsState()
    val account by libraryViewModel.account.collectAsState()
    var showAccount by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Spotify-style layout toggle: rows or a 3-column grid, persisted across runs.
    var gridView by remember { mutableStateOf(isLibraryGridView(context)) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (showAccount) {
        AccountSheet(
            account = (account as? Response.Success)?.data ?: AccountModel(),
            navController = navController,
            onDismiss = { showAccount = false },
        )
    }

    val followedArtists by libraryViewModel.followedArtists.collectAsState()
    val favouriteAlbums by libraryViewModel.favouriteAlbums.collectAsState()
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    val allEntries = (entries as? Response.Success)?.data.orEmpty()
    // Liked Songs and Downloads get their own tiles, so they're not repeated in the list.
    // Playlists made in Aalap come first, then the ones from Spotify.
    val localVersion by com.music.spotui.data.local.AalapPlaylists.version.collectAsState()
    val localPlaylists = remember(localVersion) { com.music.spotui.data.local.AalapPlaylists.asLibraryEntries(context) }
    var creatingPlaylist by remember { mutableStateOf(false) }
    if (creatingPlaylist) com.music.spotui.ui.components.PlaylistNameDialog("New playlist", onDismiss = { creatingPlaylist = false }) { name ->
        creatingPlaylist = false
        val id = com.music.spotui.data.local.AalapPlaylists.create(context, name)
        navController.navigate(com.music.spotui.ui.navigation.playlistRoute(id, name))
    }
    val hiddenVersion by com.music.spotui.data.local.HiddenPlaylists.version.collectAsState()
    val hidden = remember(hiddenVersion) { com.music.spotui.data.local.HiddenPlaylists.ids(context) }
    val listEntries = localPlaylists + allEntries.filter {
        it.spotifyId != Api.HomeCache.LIKED_SONGS_ID && it.spotifyId != Api.HomeCache.DOWNLOADS_ID && it.spotifyId !in hidden
    }.distinctBy { it.spotifyId.ifBlank { it.name } } // a playlist both saved and owned is listed once
    val playlists = listEntries.count { it.isPlaylist }
    val albums = listEntries.size - playlists

    // Pulling down only stretches and springs back: this page never refreshes on a pull.
    com.music.spotui.ui.components.AalapBounce(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(com.music.spotui.ui.theme.AppBackground)
                .statusBarsPadding()
        ) {
            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 16.dp, 16.dp, 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", fontWeight = FontWeight.Bold, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 28.sp)
                    Text(
                        // Only what you have, on one line (e.g. "1 playlist · 86 artists").
                        listOfNotNull(
                            "$playlists ${if (playlists == 1) "playlist" else "playlists"}".takeIf { playlists > 0 },
                            "$albums ${if (albums == 1) "album" else "albums"}".takeIf { albums > 0 },
                            "${followedArtists.size} ${if (followedArtists.size == 1) "artist" else "artists"}".takeIf { followedArtists.isNotEmpty() },
                        ).joinToString(" · "),
                        color = com.music.spotui.ui.theme.TextMuted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                RoundIconButton(onClick = { creatingPlaylist = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New playlist", tint = com.music.spotui.ui.theme.TextPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(
                    active = searchActive,
                    onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" },
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search library", tint = if (searchActive) com.music.spotui.ui.theme.OnSaffron else com.music.spotui.ui.theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(onClick = {
                    gridView = !gridView
                    setLibraryGridView(context, gridView)
                }) {
                    Icon(
                        painter = painterResource(if (gridView) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                        contentDescription = if (gridView) "Show as list" else "Show as grid",
                        tint = com.music.spotui.ui.theme.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(com.music.spotui.ui.theme.SurfaceHigh)
                        .clickable { showAccount = true },
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = (account as? Response.Success)?.data?.imageUrl.orEmpty()
                    if (avatar.isNotBlank()) AccountAvatar(avatar, 40.dp)
                    else Icon(Icons.Default.Person, contentDescription = "Account", tint = com.music.spotui.ui.theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
            }

            AnimatedVisibility(visible = searchActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 8.dp, 16.dp, 0.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(com.music.spotui.ui.theme.SurfaceHigh)
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = com.music.spotui.ui.theme.TextMuted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(com.music.spotui.ui.theme.Saffron),
                        modifier = Modifier.weight(1f).padding(8.dp, 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (searchQuery.isEmpty()) Text("Find in your library", color = com.music.spotui.ui.theme.TextMuted, fontSize = 15.sp)
                                inner()
                            }
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = com.music.spotui.ui.theme.TextMuted,
                            modifier = Modifier.size(18.dp).clickable { searchQuery = "" },
                        )
                    }
                }
            }

            // ── Filter chips ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp, 12.dp, 16.dp, 4.dp),
            ) {
                LibraryFilter.entries.forEach { f ->
                    val sel = f == filter
                    Text(
                        f.label,
                        color = if (sel) com.music.spotui.ui.theme.OnSaffron else com.music.spotui.ui.theme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) com.music.spotui.ui.theme.Saffron else com.music.spotui.ui.theme.SurfaceHigh)
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            when (entries) {
                is Response.Loading -> LibrarySkeleton(PaddingValues(0.dp))
                else -> {
                    // Spotify unreachable (offline, down, or the login expired): everything on the
                    // phone still shows (likes, downloads, history, Aalap playlists, artists).
                    val retryScope = androidx.compose.runtime.rememberCoroutineScope()
                    if (entries is Response.Error) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 8.dp)
                            .clip(RoundedCornerShape(14.dp)).background(com.music.spotui.ui.theme.SurfaceHigh).padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("Couldn't reach Spotify. Showing what's on your phone.", color = com.music.spotui.ui.theme.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("Try again", color = com.music.spotui.ui.theme.Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { retryScope.launch { libraryViewModel.refreshAll() } }.padding(start = 10.dp))
                    }
                    val q = searchQuery.trim()
                    // Switching filter slides the content in from the side you tapped (All →
                    // Artists moves left, back moves right); grid/list cross-fades.
                    androidx.compose.animation.AnimatedContent(
                        targetState = filter to gridView,
                        transitionSpec = {
                            val (from, fromGrid) = initialState
                            val (to, toGrid) = targetState
                            val spec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(320, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f))
                            if (from == to && fromGrid != toGrid) {
                                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) togetherWith
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                            } else {
                                val dir = if (to.ordinal >= from.ordinal) 1 else -1
                                (androidx.compose.animation.slideInHorizontally(spec) { dir * it / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240))) togetherWith
                                    (androidx.compose.animation.slideOutHorizontally(spec) { -dir * it / 6 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
                            }
                        },
                        label = "libraryFilter",
                    ) { (shownFilter, shownGrid) ->
                    val shownEntries = listEntries
                        .filter { when (shownFilter) {
                            LibraryFilter.ALL -> true
                            LibraryFilter.PLAYLISTS -> it.isPlaylist
                            LibraryFilter.ALBUMS -> !it.isPlaylist
                            LibraryFilter.ARTISTS -> false
                        } }
                        .filter { q.isBlank() || it.name.contains(q, true) || it.subtitle.contains(q, true) }
                    val shownArtists = if (shownFilter == LibraryFilter.ALL || shownFilter == LibraryFilter.ARTISTS)
                        followedArtists.filter { q.isBlank() || it.name.contains(q, true) } else emptyList()
                    LibraryContent(
                        navController = navController,
                        showTiles = shownFilter == LibraryFilter.ALL && q.isBlank(),
                        entries = shownEntries,
                        artists = shownArtists,
                        artistsAsGrid = shownFilter == LibraryFilter.ARTISTS,
                        grid = shownGrid,
                        favouriteAlbums = if (q.isBlank() && (shownFilter == LibraryFilter.ALL || shownFilter == LibraryFilter.ALBUMS)) favouriteAlbums else emptyList(),
                        favouriteAlbumsAsGrid = shownFilter == LibraryFilter.ALBUMS,
                        onSeeAll = { filter = it },
                        emptyText = when {
                            q.isNotBlank() -> "Nothing matches that."
                            shownFilter == LibraryFilter.PLAYLISTS -> "No playlists yet. Tap + at the top to make one."
                            shownFilter == LibraryFilter.ALBUMS -> "No albums yet. Save an album and it shows up here."
                            else -> "Nothing here yet."
                        },
                    )
                    }
                }
            }
        }
    }
}

private enum class LibraryFilter(val label: String) {
    ALL("All"), PLAYLISTS("Playlists"), ALBUMS("Albums"), ARTISTS("Artists"),
}

@Composable
private fun RoundIconButton(active: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) com.music.spotui.ui.theme.Saffron else com.music.spotui.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick),
    ) { content() }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AccountAvatar(url: String, size: androidx.compose.ui.unit.Dp) {
    GlideImage(
        modifier = Modifier.size(size).clip(CircleShape),
        model = url,
        contentScale = ContentScale.Crop,
        contentDescription = ""
    )
}

private fun openLibraryEntry(entry: LibraryEntry, navController: NavController) {
    if (entry.spotifyId == Api.HomeCache.LIKED_SONGS_ID) navController.navigate(Routes.Liked.route)
    else if (entry.spotifyId == Api.HomeCache.DOWNLOADS_ID) navController.navigate(Routes.Downloads.route)
    else if (entry.isPlaylist) navController.navigate(playlistRoute(entry.spotifyId, entry.name))
    else navController.navigate(albumRoute(entry.name, entry.artists))
}

/** Quick-access tiles: Liked Songs, Downloads, History, Local files. */
@Composable
private fun LibraryTiles(navController: NavController) {
    val context = LocalContext.current
    // The same count as the Liked Songs page and Profile.
    val likeVersion by com.music.spotui.data.preferences.likedVersion.collectAsState()
    val liked by androidx.compose.runtime.produceState(0, likeVersion) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.music.spotui.data.preferences.LikedLibrary.count(context) }
    }
    val downloads = remember { com.music.spotui.data.preferences.getDownloadedSongs(context).size }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTile(
                "Liked Songs", com.music.spotui.ui.components.counted(liked, "song"),
                listOf(Color(0xFFFF8A3D), Color(0xFFE0461B)),
                rememberVectorPainter(Icons.Default.Favorite), Modifier.weight(1f),
            ) { navController.navigate(Routes.Liked.route) }
            LibraryTile(
                "Downloads", com.music.spotui.ui.components.counted(downloads, "song"),
                listOf(Color(0xFFFFC56B), Color(0xFFB86E00)),
                painterResource(R.drawable.ic_download), Modifier.weight(1f),
            ) { navController.navigate(Routes.Downloads.route) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTile(
                "History", "Plays and stats",
                listOf(Color(0xFF7A5AF8), Color(0xFF3B2A8C)),
                rememberVectorPainter(Icons.Default.DateRange), Modifier.weight(1f),
            ) { navController.navigate(Routes.History.route) }
            LibraryTile(
                "Local files", "On this phone",
                listOf(Color(0xFF2BB3C0), Color(0xFF15606B)),
                painterResource(R.drawable.ic_library_big), Modifier.weight(1f),
            ) { navController.navigate(Routes.LocalFiles.route) }
        }
    }
}

@Composable
private fun LibraryTile(
    title: String, subtitle: String, colors: List<Color>,
    icon: androidx.compose.ui.graphics.painter.Painter, modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun LibrarySectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    ) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(com.music.spotui.ui.theme.Saffron))
        Spacer(Modifier.width(10.dp))
        Text(text, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The Library body: tiles, then playlists/albums as rows or a grid, then artists. */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibraryContent(
    navController: NavController,
    showTiles: Boolean,
    entries: List<LibraryEntry>,
    artists: List<com.music.spotui.data.entity.ArtistsModel>,
    artistsAsGrid: Boolean,
    grid: Boolean,
    favouriteAlbums: List<LibraryEntry> = emptyList(),
    favouriteAlbumsAsGrid: Boolean = false,
    onSeeAll: (LibraryFilter) -> Unit = {},
    emptyText: String = "Nothing matches that.",
) {
    // Long-press a playlist to remove it (Aalap playlists are deleted; Spotify ones hidden in Aalap only).
    var removing by remember { mutableStateOf<LibraryEntry?>(null) }
    removing?.let { e ->
        com.music.spotui.ui.components.RemovePlaylistDialog(e.spotifyId, e.name, onDismiss = { removing = null }, onRemoved = { removing = null })
    }
    val press: (LibraryEntry) -> Modifier = { entry ->
        Modifier.combinedClickable(
            onClick = { openLibraryEntry(entry, navController) },
            onLongClick = if (entry.isPlaylist) ({ removing = entry }) else null,
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = Modifier.fillMaxSize().background(com.music.spotui.ui.theme.AppBackground),
    ) {
        val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
        if (com.music.spotui.data.preferences.OfflineState.isOffline) {
            item(span = full) { OfflineBanner() }
        }
        if (showTiles) item(span = full) { LibraryTiles(navController) }

        // Albums by the artists you follow and play most. Grid view: square tiles like the
        // rest of the library; list view: a row on All, list rows under Albums.
        if (favouriteAlbums.isNotEmpty() && (!favouriteAlbumsAsGrid || grid)) {
            item(span = full) { LibrarySectionTitle("Albums from artists you love") }
            if (!favouriteAlbumsAsGrid && grid) {
                // All view, grid: pages of 4×4 squares, swipe for the next page.
                item(span = full) {
                    PagedGrid(favouriteAlbums) { e -> SmallAlbumTile(e, navController) }
                }
            } else if (!favouriteAlbumsAsGrid) {
                // All view, list: the first few as rows, the rest under Albums.
                items(minOf(5, favouriteAlbums.size), span = { full() }) { i -> AlbumRow(favouriteAlbums[i], navController) }
                if (favouriteAlbums.size > 5) item(span = full) { SeeAll("See all ${favouriteAlbums.size} albums") { onSeeAll(LibraryFilter.ALBUMS) } }
            } else {
                items(favouriteAlbums.size) { i -> AlbumCard(favouriteAlbums[i], navController, Modifier.padding(6.dp).padding(4.dp)) }
            }
        }

        if (entries.isNotEmpty()) {
            item(span = full) { LibrarySectionTitle("Playlists and albums") }
            items(entries.size) { i ->
                val entry = entries[i]
                if (grid) {
                    Column(
                        modifier = Modifier
                            .padding(6.dp)
                            .then(press(entry))
                            .padding(4.dp),
                    ) {
                        GlideImage(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                            model = entry.coverUri, contentScale = ContentScale.Crop, contentDescription = "",
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(entry.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(press(entry))
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                    ) {
                        GlideImage(
                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)),
                            model = entry.coverUri, contentScale = ContentScale.Crop, contentDescription = "",
                        )
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(entry.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (entry.isPlaylist) "Playlist" else "Album",
                                    color = com.music.spotui.ui.theme.Saffron, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(com.music.spotui.ui.theme.Saffron.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(entry.subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (favouriteAlbums.isNotEmpty() && favouriteAlbumsAsGrid && !grid) {
            item(span = full) { LibrarySectionTitle("From artists you love") }
            items(favouriteAlbums.size, span = { full() }) { i -> AlbumRow(favouriteAlbums[i], navController) }
        }

        if (artists.isNotEmpty() || artistsAsGrid) {
            item(span = full) { LibrarySectionTitle("Artists you follow") }
            if (!artistsAsGrid && !grid) {
                // All view, list: Add artists and the first few as rows, the rest under Artists.
                item(span = full) { AddArtistsRow(navController) }
                items(minOf(5, artists.size), span = { full() }) { i -> ArtistRow(artists[i], navController) }
                if (artists.size > 5) item(span = full) { SeeAll("See all ${artists.size} artists") { onSeeAll(LibraryFilter.ARTISTS) } }
            } else if (!artistsAsGrid) {
                // All view, grid: pages of 4×4 circles (Add artists first), swipe for the next page.
                item(span = full) {
                    PagedGrid(listOf<com.music.spotui.data.entity.ArtistsModel?>(null) + artists) { a ->
                        if (a == null) AddArtistsCircle(navController, Modifier.fillMaxWidth(), compact = true)
                        else SmallArtistTile(a, navController)
                    }
                }
            } else if (grid) {
                item { AddArtistsCircle(navController, Modifier.padding(10.dp)) }
                items(artists.size) { i -> ArtistCircle(artists[i], navController, Modifier.padding(10.dp)) }
            } else if (artistsAsGrid) {
                // List view: compact rows, not full-width circles.
                item(span = full) { AddArtistsRow(navController) }
                items(artists.size, span = { full() }) { i -> ArtistRow(artists[i], navController) }
            }
        }

        if (entries.isEmpty() && artists.isEmpty() && !showTiles) {
            item(span = full) {
                Text(emptyText, color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp, 30.dp))
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistCircle(artist: com.music.spotui.data.entity.ArtistsModel, navController: NavController, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { navController.navigate(artistRoute(artist.name, artist.id)) },
    ) {
        GlideImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(com.music.spotui.ui.theme.SurfaceHigh),
            model = artist.coverUri, contentScale = ContentScale.Crop, contentDescription = "",
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Spacer(Modifier.height(8.dp))
        Text(artist.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("Artist", color = com.music.spotui.ui.theme.TextMuted, fontSize = 11.sp)
    }
}

/**
 * Pages of up to 16 items in a 4×4 grid; swipe sideways for the next page. Dots under
 * the grid show where you are when there's more than one page.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun <T> PagedGrid(items: List<T>, perRow: Int = 4, rows: Int = 4, cell: @Composable (T) -> Unit) {
    val pages = items.chunked(perRow * rows)
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    Column {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
        ) { p ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pages[p].chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(width = if (i == pager.currentPage) 16.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == pager.currentPage) com.music.spotui.ui.theme.Saffron else com.music.spotui.ui.theme.SurfaceHigh),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SmallAlbumTile(entry: LibraryEntry, navController: NavController) {
    Column(Modifier.fillMaxWidth().clickable { openLibraryEntry(entry, navController) }) {
        GlideImage(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(com.music.spotui.ui.theme.SurfaceHigh),
            model = entry.coverUri, contentScale = ContentScale.Crop, contentDescription = entry.name,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Spacer(Modifier.height(4.dp))
        Text(entry.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SmallArtistTile(artist: com.music.spotui.data.entity.ArtistsModel, navController: NavController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(artistRoute(artist.name, artist.id)) },
    ) {
        GlideImage(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(com.music.spotui.ui.theme.SurfaceHigh),
            model = artist.coverUri, contentScale = ContentScale.Crop, contentDescription = artist.name,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Spacer(Modifier.height(5.dp))
        Text(artist.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SeeAll(text: String, onClick: () -> Unit) {
    Text(
        text, color = com.music.spotui.ui.theme.Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

/** An album as a list row: cover, name, year · artist. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumRow(entry: LibraryEntry, navController: NavController) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { openLibraryEntry(entry, navController) }.padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        GlideImage(
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(com.music.spotui.ui.theme.SurfaceHigh),
            model = entry.coverUri, contentScale = ContentScale.Crop, contentDescription = entry.name,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(entry.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An artist as a list row: small photo, name, "Artist". */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistRow(artist: com.music.spotui.data.entity.ArtistsModel, navController: NavController) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(artistRoute(artist.name, artist.id)) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        GlideImage(
            modifier = Modifier.size(58.dp).clip(CircleShape).background(com.music.spotui.ui.theme.SurfaceHigh),
            model = artist.coverUri, contentScale = ContentScale.Crop, contentDescription = artist.name,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(artist.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artist", color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp)
        }
        Text("›", color = com.music.spotui.ui.theme.TextMuted, fontSize = 22.sp)
    }
}

@Composable
private fun AddArtistsRow(navController: NavController) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Routes.ArtistPicker.route) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(58.dp).clip(CircleShape).background(com.music.spotui.ui.theme.Saffron.copy(alpha = 0.14f)),
        ) { Text("+", color = com.music.spotui.ui.theme.Saffron, fontSize = 28.sp) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Add artists", color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Suggestions from what you play", color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp)
        }
    }
}

/** First card in the artists list: opens the artist picker. */
@Composable
private fun AddArtistsCircle(navController: NavController, modifier: Modifier, compact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { navController.navigate(Routes.ArtistPicker.route) },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(com.music.spotui.ui.theme.Saffron.copy(alpha = 0.14f))
                .border(1.5.dp, com.music.spotui.ui.theme.Saffron.copy(alpha = 0.6f), CircleShape),
        ) { Text("+", color = com.music.spotui.ui.theme.Saffron, fontSize = if (compact) 26.sp else 34.sp, fontWeight = FontWeight.Light) }
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
        Text("Add artists", color = com.music.spotui.ui.theme.TextPrimary, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (!compact) Text("Suggestions", color = com.music.spotui.ui.theme.TextMuted, fontSize = 11.sp)
    }
}

/** An album cover with its name and year · artist. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumCard(entry: LibraryEntry, navController: NavController, modifier: Modifier) {
    Column(modifier = modifier.clickable { openLibraryEntry(entry, navController) }) {
        GlideImage(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(com.music.spotui.ui.theme.SurfaceHigh),
            model = entry.coverUri, contentScale = ContentScale.Crop, contentDescription = entry.name,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
        )
        Spacer(Modifier.height(6.dp))
        Text(entry.name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibrarySkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(com.music.spotui.ui.theme.AppBackground)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        repeat(8) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 6.dp)
            ) {
                Box(modifier = Modifier.size(55.dp).clip(RoundedCornerShape(4.dp)).background(com.music.spotui.ui.theme.Surface))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Box(modifier = Modifier.height(14.dp).width(160.dp).clip(RoundedCornerShape(3.dp)).background(com.music.spotui.ui.theme.Surface))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.height(11.dp).width(90.dp).clip(RoundedCornerShape(3.dp)).background(com.music.spotui.ui.theme.Surface))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    account: AccountModel,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.music.spotui.ui.theme.Surface,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 16.dp)
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(com.music.spotui.ui.theme.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if (account.imageUrl.isNotBlank()) AccountAvatar(account.imageUrl, 56.dp)
                    else Icon(Icons.Default.Person, contentDescription = null, tint = com.music.spotui.ui.theme.TextPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(com.music.spotui.data.preferences.aalapDisplayName(LocalContext.current, account.name.ifBlank { "Aalap listener" }), color = com.music.spotui.ui.theme.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (account.email.isNotBlank()) Text(account.email, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Aalap member", color = com.music.spotui.ui.theme.Saffron, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            HorizontalDivider(color = com.music.spotui.ui.theme.SurfaceHigh)

            AccountRow("Profile and settings") {
                onDismiss()
                navController.navigate(Routes.Settings.route)
            }
            AccountRow("Listening history") {
                onDismiss()
                navController.navigate(Routes.History.route)
            }
            val shareScope = androidx.compose.runtime.rememberCoroutineScope()
            AccountRow("Share Aalap") {
                shareScope.launch { runCatching { com.music.spotui.ui.share.ShareApp.share(context) } }
                onDismiss()
            }
            HorizontalDivider(color = com.music.spotui.ui.theme.SurfaceHigh)
            AccountRow("Log out", tint = Color(0xFFE57373)) {
                com.music.spotui.data.api.AccountActions.logOut(context)
                onDismiss()
                navController.navigate(Routes.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccountRow(label: String, tint: Color = com.music.spotui.ui.theme.TextPrimary, onClick: () -> Unit) {
    Text(
        text = label,
        color = tint,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(20.dp, 16.dp)
    )
}

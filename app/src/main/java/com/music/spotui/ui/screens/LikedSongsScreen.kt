package com.music.spotui.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Response
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.LikedSongsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun LikedSongsScreen(navController: NavController) {

    val likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
    val songsResp by likedSongsViewModel.songs.collectAsState()
    val context = LocalContext.current

    val allSongs = (songsResp as? Response.Success)?.data.orEmpty()

    // Filter: search, where the like came from, or an artist.
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<LikedFilter?>(null) }
    val sources by androidx.compose.runtime.produceState(LikedSources(), allSongs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            LikedSources(
                spotify = com.music.spotui.data.api.YouTubeLibrary.spotifyLiked(context).mapTo(HashSet()) { it.id },
                youtube = com.music.spotui.data.api.YouTubeLibrary.likedSongs(context).mapTo(HashSet()) { it.id },
            )
        }
    }
    val topArtists = remember(allSongs) {
        allSongs.groupingBy { it.singer.split(",").first().trim() }.eachCount()
            .filter { it.key.isNotBlank() && it.value >= 2 }
            .entries.sortedByDescending { it.value }.take(8).map { it.key }
    }
    val songs = remember(allSongs, query, filter, sources) {
        allSongs
            .filter { s ->
                when (val f = filter) {
                    null -> true
                    LikedFilter.Spotify -> s.id in sources.spotify
                    LikedFilter.YouTube -> s.id in sources.youtube
                    LikedFilter.Aalap -> s.id !in sources.spotify && s.id !in sources.youtube
                    is LikedFilter.Artist -> s.singer.split(",").any { it.trim().equals(f.name, ignoreCase = true) }
                }
            }
            .filter { s -> query.isBlank() || s.title.contains(query, true) || s.singer.contains(query, true) || s.album.contains(query, true) }
    }


    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = {
                // If the song was unliked in the menu, drop it from the list right
                // away (the Spotify-side removal is already in flight).
                if (!com.music.spotui.data.preferences.isSongLiked(context, sel.id.toString())) {
                    likedSongsViewModel.removeLocally(sel.id)
                }
                menuSong = null
            },
        )
    }

    // Spotify's Liked Songs uses a purple → dark gradient.
    val likedColor = Color(0xFF5038A0)

    // Pulling down only stretches and springs back: this page never refreshes on a pull.
    com.music.spotui.ui.components.AalapBounce(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(AppBackground.toArgb()))
        ) {
            if (songsResp is Response.Loading) {
                Loader()
                return@Surface
            }

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        modifier = Modifier.padding(16.dp, 0.dp),
                        navigationIcon = {
                            Icon(
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { navController.navigateUp() },
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "",
                                tint = com.music.spotui.ui.theme.TextPrimary
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = com.music.spotui.ui.theme.TextPrimary,
                        ),
                        title = { Text(text = "") }
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    // Only bottom padding: the coloured header runs up behind the transparent top
                    // bar and the status bar (top padding left a dark band above it).
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(AppBackground.toArgb()))
                        .consumeWindowInsets(innerPadding)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(likedColor, Color(AppBackground.toArgb())),
                                        startY = -100f,
                                    ),
                                ),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Spacer(modifier = Modifier.statusBarsPadding().height(56.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(230.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF8E6FE0), Color(0xFF3B2A82)),
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "",
                                        tint = com.music.spotui.ui.theme.TextPrimary,
                                        modifier = Modifier.size(90.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.padding(5.dp))
                            Text(
                                modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                                text = "Liked Songs",
                                color = com.music.spotui.ui.theme.TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                                text = if (songs.size == allSongs.size) "${allSongs.size} songs" else "${songs.size} of ${allSongs.size} songs",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(20.dp, 0.dp)
                            ) {
                                // Download all liked songs for offline playback.
                                var askDownloadAll by remember { mutableStateOf(false) }
                                var likedDownloaded by remember(songs) {
                                    mutableStateOf(songs.isNotEmpty() && com.music.spotui.di.Downloads.allDownloaded(songs, context))
                                }
                                if (songs.isNotEmpty()) {
                                    Icon(
                                        imageVector = if (likedDownloaded)
                                            Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                        tint = if (likedDownloaded) Color(AppPalette.toArgb()) else com.music.spotui.ui.theme.TextPrimary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                if (!likedDownloaded) askDownloadAll = true
                                            },
                                        contentDescription = "Download liked songs",
                                    )
                                    if (askDownloadAll) {
                                        com.music.spotui.ui.components.ConfirmDownloadAllDialog(
                                            songs.size, onDismiss = { askDownloadAll = false },
                                        ) {
                                            com.music.spotui.di.Downloads.downloadAll(songs, context)
                                            android.widget.Toast.makeText(
                                                context, "Downloading ${songs.size} tracks…", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    // Shuffle-play: start liked songs in random order.
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_player_shuffle),
                                        tint = com.music.spotui.ui.theme.TextPrimary,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                likedSongsViewModel.startShuffled(songs)?.let { first ->
                                                    SongPlayer.playSong(first.url, context)
                                                    likedSongsViewModel.updateSongState(
                                                        first.coverUri,
                                                        first.title,
                                                        first.singer,
                                                        true,
                                                        first.id,
                                                        0,
                                                        "Liked Songs",
                                                    )
                                                }
                                            },
                                        contentDescription = "Shuffle play",
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                // Always visible: pause when playing, resume when this
                                // list's track is paused, otherwise start from the top.
                                if (songs.isNotEmpty()) {
                                    val playing = likedSongsViewModel.currentSongPlayingState.value
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(com.music.spotui.ui.theme.Saffron)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                when {
                                                    playing -> likedSongsViewModel.setPlaying(false)
                                                    songs.any { it.id == likedSongsViewModel.currentSongId.value } ->
                                                        likedSongsViewModel.setPlaying(true)
                                                    else -> {
                                                        likedSongsViewModel.updateQueue(songs)
                                                        SongPlayer.playSong(songs[0].url, context)
                                                        likedSongsViewModel.updateSongState(
                                                            songs[0].coverUri,
                                                            songs[0].title,
                                                            songs[0].singer,
                                                            true,
                                                            songs[0].id,
                                                            0,
                                                            "Liked Songs"
                                                        )
                                                    }
                                                }
                                            }
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(25.dp),
                                            tint = com.music.spotui.ui.theme.OnSaffron,
                                            painter = painterResource(
                                                id = if (playing) R.drawable.ic_playing else R.drawable.play_svgrepo_com,
                                            ),
                                            contentDescription = if (playing) "Pause" else "Play"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "filters") {
                        LikedFilters(
                            query = query, onQuery = { query = it },
                            filter = filter, onFilter = { filter = it },
                            artists = topArtists,
                            hasAalapOnly = allSongs.any { it.id !in sources.spotify && it.id !in sources.youtube } && sources.spotify.isNotEmpty(),
                        )
                    }
                    if (songs.isEmpty() && allSongs.isNotEmpty()) item(key = "none") {
                        Text("No liked songs match.", color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp, 24.dp))
                    }

                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        com.music.spotui.ui.components.SwipeToQueue(song) {
                        val currentColor = if (song.id == likedSongsViewModel.currentSongId.value)
                            Color(AppPalette.toArgb()) else com.music.spotui.ui.theme.TextPrimary

                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp, 8.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = { menuSong = song },
                                    onClick = {
                                        likedSongsViewModel.updateQueue(songs)
                                        SongPlayer.playSong(song.url, context)
                                        likedSongsViewModel.updateSongState(
                                            song.coverUri,
                                            song.title,
                                            song.singer,
                                            true,
                                            song.id,
                                            index,
                                            "Liked Songs"
                                        )
                                    },
                                )
                        ) {
                            GlideImage(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                model = song.coverUri,
                                failure = placeholder(R.drawable.placeholder),
                                contentScale = ContentScale.Crop,
                                contentDescription = ""
                            )
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = currentColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = song.singer,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                        }
                    }

                    item { Spacer(modifier = Modifier.padding(80.dp)) }
                }
            }
        }
    }
}


private sealed interface LikedFilter {
    data object Spotify : LikedFilter
    data object YouTube : LikedFilter
    data object Aalap : LikedFilter
    data class Artist(val name: String) : LikedFilter
}

private data class LikedSources(val spotify: Set<Int> = emptySet(), val youtube: Set<Int> = emptySet())

/** Search box, then a row of chips: where the like came from, and your top artists. */
@Composable
private fun LikedFilters(
    query: String, onQuery: (String) -> Unit,
    filter: LikedFilter?, onFilter: (LikedFilter?) -> Unit,
    artists: List<String>,
    hasAalapOnly: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = com.music.spotui.ui.theme.TextPrimary, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(com.music.spotui.ui.theme.Saffron),
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            decorationBox = { field ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(com.music.spotui.ui.theme.SurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = com.music.spotui.ui.theme.TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Find in Liked Songs", color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp)
                        field()
                    }
                    if (query.isNotEmpty()) Icon(
                        Icons.Default.Close, contentDescription = "Clear", tint = com.music.spotui.ui.theme.TextMuted,
                        modifier = Modifier.size(18.dp).clickable { onQuery("") },
                    )
                }
            },
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            val sourceChips = buildList {
                add(LikedFilter.Spotify to "Spotify"); add(LikedFilter.YouTube to "YouTube Music")
                if (hasAalapOnly) add(LikedFilter.Aalap to "Liked in Aalap")
            }
            sourceChips.forEach { (f, label) ->
                item { LikedChip(label, selected = filter == f) { onFilter(if (filter == f) null else f) } }
            }
            artists.forEach { a ->
                val f = LikedFilter.Artist(a)
                item { LikedChip(a, selected = filter == f) { onFilter(if (filter == f) null else f) } }
            }
        }
    }
}

@Composable
private fun LikedChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) com.music.spotui.ui.theme.OnSaffron else com.music.spotui.ui.theme.TextPrimary,
        fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) com.music.spotui.ui.theme.Saffron else com.music.spotui.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

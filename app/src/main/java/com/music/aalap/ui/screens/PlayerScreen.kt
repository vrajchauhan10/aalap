package com.music.aalap.ui.screens

import androidx.compose.ui.draw.rotate
import com.music.aalap.ui.components.pressSquish
import androidx.compose.animation.togetherWith
import androidx.lifecycle.repeatOnLifecycle
import com.music.aalap.ui.components.swipeDownToDismiss
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.togetherWith
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.aalap.R
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.addLikedSongId
import com.music.aalap.data.preferences.alternativeStreamKey
import com.music.aalap.data.preferences.clearAlternativeStream
import com.music.aalap.data.preferences.getAlternativeStream
import com.music.aalap.data.preferences.getLikedSongIds
import com.music.aalap.data.preferences.getSongsByIds
import com.music.aalap.data.preferences.isSongLiked
import com.music.aalap.data.preferences.removeLikedSongId
import com.music.aalap.data.preferences.setLocalAlternativeStream
import com.music.aalap.data.preferences.setYouTubeAlternativeStream
import com.music.aalap.di.Palette
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.components.Snackbar
import com.music.aalap.ui.navigation.Routes
import com.music.aalap.ui.navigation.albumRoute
import com.music.aalap.ui.navigation.artistRoute
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.AppPalette
import com.music.aalap.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(navController: NavController) {
    val playerViewModel : PlayerViewModel = hiltViewModel()
    val songTitle = playerViewModel.currentSongTitle.value
    val songSinger = playerViewModel.currentSongSinger.value
    val songCoverUri = playerViewModel.currentSongCoverUri.value
    val songPlayingState = playerViewModel.currentSongPlayingState.value
    val songId = playerViewModel.currentSongId.value
    val context = LocalContext.current
    val isLiked = remember {
        mutableStateOf(isSongLiked(context, songId.toString()))
    }
    // Liked or unliked anywhere else (mini player, notification, menus): show it here too.
    val likedVersion by com.music.aalap.data.preferences.likedVersion.collectAsState()
    LaunchedEffect(likedVersion, songId) { isLiked.value = isSongLiked(context, songId.toString()) }
    var showMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    if (showMenu) {
        PlayerOptionsSheet(
            navController = navController,
            playerViewModel = playerViewModel,
            context = context,
            isLiked = isLiked,
            onDismiss = { showMenu = false }
        )
    }



    // Set as the screen appears (and per song), then moved by the timer below. They used to be
    // rewritten while drawing, which asks for another drawing: a redraw loop while playing.
    // One reading of the player (SongPlayer.progress) for the bar and both times, so they can't
    // disagree, and only for this song: the last song's position no longer shows on the new one.
    // Read only inside [ProgressBar]: read here, every tick rebuilt the whole player page
    // (cover pager, buttons, lyrics…) instead of just the bar and the times. Measured on the S24
    // with the player open: ~33% of one core, most of it redrawing.
    val progressState = remember(songId) { mutableStateOf(SongPlayer.progress()) }

    //playerViewModel.updateSongState(songCoverUri, songTitle, songSinger, songPlayingState)



    var dominentColor by remember {
        mutableStateOf(com.music.aalap.ui.theme.ImmersiveBackground)
    }
    // Once per cover (not on every redraw): reading the cover's colour is slow.
    androidx.compose.runtime.LaunchedEffect(songCoverUri) {
        Palette().extractSecondColorFromCoverUrl(context = context, songCoverUri){ color ->
            dominentColor = color
        }
    }

    val shuffle = playerViewModel.shuffleState.value
    val repeat = playerViewModel.repeatState.value

    // The queue is whatever list the user actually started playing (album tracks,
    // search results, liked songs) — stored when the song was tapped. Falling back
    // to the global top-tracks feed used to crash / be empty (it's rate-limited).
    val queueSongs = playerViewModel.queue.value

    // ── Now-playing swipe pager ──
    // Index of the playing track in the queue (fallback to 0 so the pager is valid
    // even before the queue/current id line up).
    val currentIndex = queueSongs.indexOfFirst { it.id == playerViewModel.currentSongId.value }
        .let { if (it >= 0) it else 0 }
    // Not saved and restored: a restored page (after switching tabs, or Android rebuilding
    // the app when you open it from the notification) pointed at an old song, and settling
    // on it played that song. The pager always starts at the song that's playing.
    val queueSize = androidx.compose.runtime.rememberUpdatedState(queueSongs.size)
    val artworkPagerState = remember { LivePagerState(currentIndex) { queueSize.value.coerceAtLeast(1) } }
    // Only a swipe of your finger changes the song; anything else that moves the pager
    // (catching up with the song that's playing, the queue growing) never does.
    val swipedByUser = remember { mutableStateOf(false) }
    LaunchedEffect(artworkPagerState) {
        artworkPagerState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) swipedByUser.value = true
        }
    }
    val latestQueue = androidx.compose.runtime.rememberUpdatedState(queueSongs)
    // External track changes (auto-advance, prev/next buttons, queue edits) → snap the
    // pager to the new track. Guard on settled state so we don't fight an in-progress swipe.
    LaunchedEffect(currentIndex, queueSongs.size) {
        if (currentIndex in 0 until queueSongs.size &&
            artworkPagerState.currentPage != currentIndex &&
            !artworkPagerState.isScrollInProgress
        ) {
            // Neighbouring track: slide the artwork across. Far jumps (queue taps) snap.
            if (kotlin.math.abs(artworkPagerState.currentPage - currentIndex) == 1) {
                artworkPagerState.animateScrollToPage(
                    currentIndex,
                    animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                )
            } else {
                artworkPagerState.scrollToPage(currentIndex)
            }
        }
    }
    // User settled the pager on a different page → play that track. Compare against the
    // live current id (not currentIndex captured above) to avoid a replay feedback loop.
    LaunchedEffect(artworkPagerState) {
        snapshotFlow { artworkPagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (!swipedByUser.value) return@collect
                swipedByUser.value = false
                val queue = latestQueue.value
                queue.getOrNull(page)?.let { target ->
                    if (target.id != playerViewModel.currentSongId.value) {
                        playerViewModel.playSongAt(queue, page, context)
                        isLiked.value = isSongLiked(context, target.id.toString())
                    }
                }
            }
    }

    // Warm the stream cache for the adjacent tracks so next/previous start instantly.
    LaunchedEffect(playerViewModel.currentSongId.value, queueSongs) {
        val idx = queueSongs.indexOfFirst { it.id == playerViewModel.currentSongId.value }
        if (idx >= 0) {
            queueSongs.getOrNull(idx + 1)?.let { SongPlayer.prefetch(it.url, context) }
            queueSongs.getOrNull(idx - 1)?.let { SongPlayer.prefetch(it.url, context) }
        }
    }

    // Load the current track's Spotify Canvas (full-screen looping video background).
    LaunchedEffect(playerViewModel.currentSongId.value, queueSongs) {
        val track = queueSongs.firstOrNull { it.id == playerViewModel.currentSongId.value }
        playerViewModel.loadCanvas(track)
    }




    // End of track (repeat / next / radio) is handled by PlaybackService, so it also
    // happens with the screen off.


    // Only while it's on screen (not with Aalap in the background).
    val playerLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    // Restarted per song too: the values above are new for each song, and a timer left running
    // from the last song kept moving its old values, so the next one sat at 0:00.
    LaunchedEffect(songPlayingState, playerLifecycle, songId) {
        playerLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            // Once while paused (the song may have been loaded or moved meanwhile), then once a
            // second while playing: the times change once a second, the bar barely between.
            // FLAC (and any slow start) learns its length after the screen first drew; it's
            // picked up here.
            do {
                progressState.value = SongPlayer.progress()
                if (songPlayingState) delay(1_000L)
            } while (songPlayingState)
        }
    }









    val canvasUrl = playerViewModel.canvasUrl.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Pull down from the top of the player to close it.
            .swipeDownToDismiss { navController.navigateUp() }
            .background(
                Brush.verticalGradient(
                    colors = listOf(dominentColor, com.music.aalap.ui.theme.ImmersiveBackground),
                    startY = 100f
                )
            )
    ) {
        if (canvasUrl != null) {
            // Spotify Canvas: the looping video fills the whole now-playing screen
            // edge-to-edge behind the controls (the "immersive" treatment), with a
            // scrim on top so the title, slider and buttons stay readable.
            CanvasVideo(
                url = canvasUrl,
                synced = playerViewModel.videoSynced.value,
                onFail = { playerViewModel.videoFailed() },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.50f),
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.80f),
                            )
                        )
                    )
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        item {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillParentMaxHeight()
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayerTopBar(
                navController,
                onMenuClick = { showMenu = true },
                contextName = playerViewModel.currentSongAlbum.value,
            )
            //Spacer(modifier = Modifier.padding(16.dp))
            // Swipe the artwork left/right to skip to the next/previous track. Using a
            // HorizontalPager makes the artwork follow the finger and snap, syncing the
            // change with the track (Spotify's now-playing gesture) instead of an abrupt
            // swipe-then-switch. When the queue is empty fall back to a static image.
            // When a Canvas is playing it fills the screen behind this column, so the
            // artwork is hidden (alpha 0) rather than removed — the pager stays in
            // the layout so the swipe-to-skip gesture keeps working over the video.
            // The artwork is the FLEXIBLE part of the screen (weight), capped at its
            // old 385dp size. On short/scaled displays the fixed-size version pushed
            // the slider and playback buttons off the bottom of the screen; now the
            // artwork shrinks instead and the controls always fit.
            // Double-tap the artwork to like: a heart pops where you tapped (and the
            // petals fall if it wasn't liked yet). Double-tapping a liked song only pops the heart.
            val bloom = com.music.aalap.ui.components.rememberHeartBloom()
            val bloomScope = rememberCoroutineScope()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(songId) {
                        detectTapGestures(onDoubleTap = { at ->
                            bloomScope.launch { bloom.show(at) }
                            if (!isLiked.value) {
                                addLikedSongId(context, songId.toString())
                                isLiked.value = isSongLiked(context, songId.toString())
                                com.music.aalap.ui.components.LikeCelebration.burst()
                                com.music.aalap.data.api.SpotifySync.setTrackSaved(
                                    context, queueSongs.firstOrNull { it.id == songId }?.spotifyTrackId.orEmpty(), true)
                            }
                        })
                    }
            ) {
                if (queueSongs.isEmpty()) {
                    GlideImage(
                        modifier = Modifier
                            .sizeIn(maxWidth = 385.dp, maxHeight = 385.dp)
                            .aspectRatio(1f)
                            .padding(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .alpha(if (canvasUrl != null) 0f else 1f),
                        model = com.music.aalap.data.api.Covers.hd(songCoverUri),
                        contentScale = ContentScale.Crop,
                        contentDescription = "")
                } else {
                    HorizontalPager(
                        state = artworkPagerState,
                        modifier = Modifier
                            .sizeIn(maxWidth = 385.dp, maxHeight = 385.dp)
                            .aspectRatio(1f),
                    ) { page ->
                        GlideImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .alpha(if (canvasUrl != null) 0f else 1f),
                            model = com.music.aalap.data.api.Covers.hd(queueSongs.getOrNull(page)?.coverUri ?: songCoverUri),
                            contentScale = ContentScale.Crop,
                            contentDescription = "")
                    }
                }
                com.music.aalap.ui.components.HeartBloomLayer(bloom, Modifier.matchParentSize())
            }
            //Spacer(modifier = Modifier.padding(30.dp))

            // Only as tall as title, bar and buttons need: the fixed 300 dp block left empty
            // space above the buttons and took it from the cover.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 0.dp)
            ){
                // Source and quality are Compose state in SongPlayer: shown as they change.
                PlayerInfo(
                    songTitle, songSinger, songId, context, isLiked,
                    source = SongPlayer.currentSource,
                    quality = SongPlayer.currentQuality,
                    onArtistClick = {
                        val track = queueSongs.firstOrNull { it.id == songId }
                        playerViewModel.goToArtist(track?.spotifyTrackId.orEmpty(), songSinger) { route ->
                            com.music.aalap.ui.navigation.PlayerReturn.open(navController, route)
                        }
                    },
                    spotifyTrackId = queueSongs.firstOrNull { it.id == songId }?.spotifyTrackId.orEmpty(),
                )

                ProgressBar(progressState, songPlayingState, playerViewModel)
                PlayerFull(songPlayingState, playerViewModel, context, isLiked, shuffle, repeat, queueSongs)
            }

            // The three things people open the ⋮ menu for most, one tap away.
            PlayerQuickActions(
                song = queueSongs.firstOrNull { it.id == songId },
                singer = songSinger,
                navController = navController,
                playerViewModel = playerViewModel,
                context = context,
                isLiked = isLiked,
            )
            // Spotify-style bottom row: current audio device (Connect) on the left,
            // share + queue on the right.
            PlayerConnectRow(
                navController = navController,
                context = context,
                currentTrack = queueSongs.firstOrNull { it.id == playerViewModel.currentSongId.value },
            )
            // The maker's mark, bottom-right.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = 0.dp, end = 26.dp), contentAlignment = Alignment.CenterEnd) { com.music.aalap.ui.components.Signature(size = 12f) }

            //PlayerEndInfo()
        }
        }
        item {
            InlineLyrics(
                title = songTitle,
                artist = songSinger,
                album = playerViewModel.currentSongAlbum.value,
                accentColor = dominentColor,
                onExpand = { showLyrics = true },
            )
        }
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 250, easing = EaseOut),
                initialOffsetY = { it },
            ),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 200, easing = EaseOut),
                targetOffsetY = { it },
            ),
        ) {
            LyricsScreen(
                title = songTitle,
                artist = songSinger,
                album = playerViewModel.currentSongAlbum.value,
                accentColor = dominentColor,
                onClose = { showLyrics = false }
            )
        }
    }
}









@Composable
fun PlayerTopBar(
    navController: NavController,
    onMenuClick: () -> Unit,
    contextName: String = "",
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Slides the player down into the mini player (the same motion as pulling it down).
        val closeSheet = com.music.aalap.ui.navigation.LocalCloseSheet.current
        Icon(modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (closeSheet != null) closeSheet() else navController.navigateUp()
            },
            painter = painterResource(id = R.drawable.ic_down),
            tint = Color.White,
            contentDescription = "Close player")

        // Spotify shows the source context here (album/playlist), not a generic label.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "PLAYING FROM",
                color = com.music.aalap.ui.theme.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = contextName.ifBlank { "Now Playing" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                tint = Color.White,
                modifier = Modifier
                    .size(23.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onMenuClick() },
                contentDescription = "More options")
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerInfo(
    songTitle: String,
    songSinger: String,
    songId: Int,
    context: Context,
    isLiked: MutableState<Boolean>,
    source: String = "",
    quality: String = "",
    onArtistClick: (() -> Unit)? = null,
    spotifyTrackId: String = "",
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 25.dp, vertical = 2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
//                        GlideImage(
//                            modifier = Modifier.size(60.dp),
//                            model = albumSongs[song].coverUri,
//                            contentScale = ContentScale.Crop,
//                            contentDescription = ""
//                        )
            // Title and artist glide in from below when the track changes.
            androidx.compose.animation.AnimatedContent(
                targetState = songId,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300, delayMillis = 60)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { it / 3 })
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)))
                },
                label = "trackInfo",
            ) { _ ->
            Column {
                Text(
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    text = songTitle,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = com.music.aalap.ui.components.shortArtists(songSinger, max = 3),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onArtistClick() } else Modifier,
                )
                // No source badge: users only ever see Aalap, not which service plays a song.
                // Instead, why this song is special for you: Trending, Top hit, Liked, Your taste.
                val likeVersion by com.music.aalap.data.preferences.likedVersion.collectAsState()
                val why by androidx.compose.runtime.produceState<com.music.aalap.data.recommendation.HomeMixes.TodaySong?>(null, songId, likeVersion) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            com.music.aalap.data.recommendation.HomeMixes.reasonFor(
                                context,
                                com.music.aalap.data.entity.SongsModel(songId, songTitle, "", songSinger, "", "", spotifyTrackId),
                            )
                        }.getOrNull()
                    }
                }
                // The player is always dark, so the bright tag colours are used here.
                why?.let { t -> com.music.aalap.ui.components.SongTags(t, horizontal = true, onDark = true, modifier = Modifier.padding(top = 6.dp)) }
            }
            } // AnimatedContent(trackInfo)
        }

        com.music.aalap.ui.components.LikeFillIcon(
            liked = isLiked.value,
            outline = painterResource(id = R.drawable.ic_add),
            filled = painterResource(id = R.drawable.added),
            fillColor = Color(AppPalette.toArgb()),
            outlineColor = Color.White,
            contentDescription = if (isLiked.value) "Saved" else "Add to Liked Songs",
            size = 26.dp,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap likes, tap again unlikes ("Add to playlist" is in the menu).
                    // No message either way: the button itself shows it (and petals fall on a like).
                    if (isLiked.value) removeLikedSongId(context, songId.toString())
                    else addLikedSongId(context, songId.toString())
                    isLiked.value = isSongLiked(context, songId.toString())
                    // Liked: marigold petals fall over the screen.
                    if (isLiked.value) com.music.aalap.ui.components.LikeCelebration.burst()
                    // Mirror the like to the real Spotify account.
                    com.music.aalap.data.api.SpotifySync.setTrackSaved(context, spotifyTrackId, isLiked.value)
                },
        )
    }
}

/**
 * The progress bar and the two times: the only part of the player that changes every second,
 * so the only part that is rebuilt by the timer.
 */
@Composable
private fun ProgressBar(
    progressState: MutableState<com.music.aalap.playback.PlaybackSync.Progress>,
    songPlayingState: Boolean,
    playerViewModel: PlayerViewModel,
) {
    val progress = progressState.value
    // Smooth scrubbing: while dragging, the thumb follows the finger
    // locally (no seek per delta — that fired a web seek on every pixel
    // and fought the polled position, making it jerky). We seek ONCE on
    // release.
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val liveFraction = progress.fraction
    CustomSlider(
        value = if (isDragging) dragValue else liveFraction,
        onValueChange = { newValue ->
            isDragging = true
            dragValue = newValue
        },
        onValueChangeFinished = {
            val target = (dragValue * progress.durationMs).toLong()
            SongPlayer.seekTo(target)
            // Shown at once, not after the next tick (it jumped back for a moment).
            progressState.value = progress.copy(positionMs = target)
            isDragging = false
            if (!songPlayingState) {
                SongPlayer.play()
                playerViewModel.updateSongState(
                    playerViewModel.currentSongCoverUri.value,
                    playerViewModel.currentSongTitle.value,
                    playerViewModel.currentSongSinger.value,
                    true,
                    playerViewModel.currentSongId.value,
                    playerViewModel.currentSongIndex.value,
                    playerViewModel.currentSongAlbum.value
                )
            }
        },
        valueRange = 0f..1f,
        steps = 0,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 0.dp),
        colors = SliderDefaults.colors(
            thumbColor = com.music.aalap.ui.theme.SaffronGold,
            activeTrackColor = com.music.aalap.ui.theme.SaffronGold,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(25.dp, 0.dp)
        ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            // While scrubbing, show the dragged time so the label tracks the finger.
            text = if (isDragging)
                playerViewModel.formatDuration((dragValue * progress.durationMs).toLong())
            else playerViewModel.formatDuration(progress.positionMs),
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = playerViewModel.formatDuration(progress.durationMs),
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    // Centred: the slider's touch area is taller than this box, and lined up at the top it
    // hung the bar about 20 dp lower than its space, leaving a gap under the song name.
    Box(modifier = modifier.height(10.dp), contentAlignment = Alignment.CenterStart) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            thumb = {
                SliderDefaults.Thumb( //androidx.compose.material3.SliderDefaults
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier.align(Alignment.Center),
                    colors = colors,
                    thumbSize = DpSize(9.dp, 9.dp)
                )
            }
        )
    }
}

@Composable
fun PlayerEndInfo() {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)){
        Icon(
            modifier = Modifier
                .size(22.dp),
            painter = painterResource(id = R.drawable.ic_output_phone),
            tint = Color.White,
            contentDescription = "Devices")
        Icon(
            modifier = Modifier
                .size(16.dp),
            painter = painterResource(id = R.drawable.ic_share),
            tint = Color.White,
            contentDescription = "Share")
    }
}

@Composable
fun PlayerFull(
    songPlayingState: Boolean,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
    shuffle: Boolean,
    repeat: Boolean,
    queueSongs: List<SongsModel>
) {




    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Icon(
            modifier = Modifier
                .size(25.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (shuffle) {
                        playerViewModel.updateShuffleState(false)
                    } else {
                        playerViewModel.updateShuffleState(true)
                    }

                }
            ,
            tint = if (shuffle){
                Color(AppPalette.toArgb())
            }
            else{
                Color.White
            },
            painter = painterResource(id = R.drawable.ic_player_shuffle),
            contentDescription = "Shuffle")
        Icon(
            modifier = Modifier
                .size(35.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // The queue itself is already in shuffled order when shuffle
                    // is on (reordered once at toggle) — never re-shuffle per tap.
                    playerViewModel.playPreviousSong(queueSongs, context)
                    isLiked.value =
                        isSongLiked(context, playerViewModel.currentSongId.value.toString())
                }
            ,
            tint = Color.White,
            painter = painterResource(id = R.drawable.ic_player_back),
            contentDescription = "Previous")
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                // requiredSize forces an exact 64×64 square even if the parent Column
                // constrains height — .size() alone let it get squished into an ellipse.
                .requiredSize(64.dp)
                .pressSquish(0.88f)
                .clip(CircleShape)
                .background(com.music.aalap.ui.theme.Saffron)
                .clickable {
                    if (songPlayingState) {
                        SongPlayer.pause()
                        playerViewModel.updateSongState(
                            playerViewModel.currentSongCoverUri.value,
                            playerViewModel.currentSongTitle.value,
                            playerViewModel.currentSongSinger.value,
                            false,
                            playerViewModel.currentSongId.value,
                            playerViewModel.currentSongIndex.value,
                            playerViewModel.currentSongAlbum.value
                        )
                    } else {
                        SongPlayer.play()
                        playerViewModel.updateSongState(
                            playerViewModel.currentSongCoverUri.value,
                            playerViewModel.currentSongTitle.value,
                            playerViewModel.currentSongSinger.value,
                            true,
                            playerViewModel.currentSongId.value,
                            playerViewModel.currentSongIndex.value,
                            playerViewModel.currentSongAlbum.value
                        )
                    }
                }
            ,
            contentAlignment = Alignment.Center
        ) {
            // Play ⇄ pause: the old icon spins away shrinking while the new one turns in.
            androidx.compose.animation.AnimatedContent(
                targetState = songPlayingState,
                transitionSpec = {
                    (androidx.compose.animation.scaleIn(com.music.aalap.ui.components.Motion.arrive, initialScale = 0.4f) +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)))
                        .togetherWith(androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(140), targetScale = 0.4f) +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                },
                label = "playPause",
            ) { playing ->
                val turn = remember { androidx.compose.animation.core.Animatable(if (playing) -90f else 90f) }
                LaunchedEffect(Unit) { turn.animateTo(0f, com.music.aalap.ui.components.Motion.arrive) }
                Icon(
                    modifier = Modifier.size(30.dp).rotate(turn.value),
                    tint = Color.White,
                    painter = if (playing) painterResource(id = R.drawable.ic_playing) else painterResource(id = R.drawable.play_svgrepo_com),
                    contentDescription = if (playing) "Pause" else "Play",
                )
            }
        }

        Icon(
            modifier = Modifier
                .size(35.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {

                    playerViewModel.playNextSongs(queueSongs, context)
                    isLiked.value =
                        isSongLiked(context, playerViewModel.currentSongId.value.toString())
                }
            ,
            tint = Color.White,
            painter = painterResource(id = R.drawable.ic_player_skip),
            contentDescription = "Next")
        RepeatButton(playerViewModel, context)
    }
}

/** A pager position that isn't saved and restored (see the artwork pager in the player). */
@OptIn(ExperimentalFoundationApi::class)
private class LivePagerState(page: Int, private val count: () -> Int) : androidx.compose.foundation.pager.PagerState(page, 0f) {
    override val pageCount: Int get() = count()
}

/**
 * The loop button. Tap: Off → loop the playlist → loop this song → Off. Press and hold
 * for every option, including "once more" (the song or the playlist one more time,
 * then the music carries on). A small badge shows ∞ or 1×.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepeatButton(playerViewModel: PlayerViewModel, context: Context) {
    val mode = playerViewModel.repeatMode.value
    var menu by remember { mutableStateOf(false) }
    fun set(m: com.music.aalap.di.RepeatMode) {
        playerViewModel.updateRepeatMode(m)
        android.widget.Toast.makeText(context, m.label, android.widget.Toast.LENGTH_SHORT).show()
    }
    val active = mode != com.music.aalap.di.RepeatMode.OFF
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = { menu = true },
                    onClick = {
                        set(when (mode) {
                            com.music.aalap.di.RepeatMode.OFF -> com.music.aalap.di.RepeatMode.LIST
                            com.music.aalap.di.RepeatMode.LIST, com.music.aalap.di.RepeatMode.LIST_ONCE -> com.music.aalap.di.RepeatMode.SONG
                            else -> com.music.aalap.di.RepeatMode.OFF
                        })
                    },
                )
                .semantics { contentDescription = "Loop: ${mode.label}. Press and hold for more" },
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                tint = if (active) Color(AppPalette.toArgb()) else Color.White,
                painter = painterResource(
                    id = if (mode == com.music.aalap.di.RepeatMode.SONG || mode == com.music.aalap.di.RepeatMode.SONG_ONCE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                ),
                contentDescription = null,
            )
            if (active) Text(
                if (mode == com.music.aalap.di.RepeatMode.SONG_ONCE || mode == com.music.aalap.di.RepeatMode.LIST_ONCE) "1×" else "∞",
                color = Color(AppPalette.toArgb()), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                com.music.aalap.di.RepeatMode.OFF to "Off",
                com.music.aalap.di.RepeatMode.SONG_ONCE to "This song once more",
                com.music.aalap.di.RepeatMode.SONG to "This song, on repeat",
                com.music.aalap.di.RepeatMode.LIST_ONCE to "This playlist once more",
                com.music.aalap.di.RepeatMode.LIST to "This playlist, on repeat",
            ).forEach { (m, label) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { menu = false; set(m) },
                )
            }
        }
    }
}


/**
 * Shares a story-sized "now listening" image card plus the Spotify link. The menu it came
 * from closes at once ([onDone]); the card is made in the background ([com.music.aalap.ui.share.ShareTask]).
 */
fun shareSongCard(context: Context, track: SongsModel?, onDone: () -> Unit = {}) {
    val title = track?.title.orEmpty()
    val artist = track?.singer.orEmpty()
    // One message for everyone: its links open on Android and iPhone alike.
    val message = com.music.aalap.ui.share.ShareMessage.forSong(title, artist, track?.spotifyTrackId.orEmpty())
    com.music.aalap.ui.share.ShareTask.run(context, "the song") { app ->
        val card = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val art = runCatching {
                com.bumptech.glide.Glide.with(app).asBitmap().load(track?.coverUri.orEmpty()).submit(900, 900).get()
            }.getOrNull()
            com.music.aalap.ui.share.ShareCards.songCard(app, art, title, artist)
        }
        com.music.aalap.ui.share.ShareCards.share(app, card, "aalap-song", message)
    }
    onDone()
}

@Composable
fun PlayerConnectRow(
    navController: NavController,
    context: Context,
    currentTrack: SongsModel?,
) {
    // Connected outputs and the one in use, kept up to date as devices come and go.
    var outputs by remember { mutableStateOf(com.music.aalap.audio.OutputDevices.connected(context)) }
    var pickedId by remember { mutableStateOf(SongPlayer.pickedOutputId(context)) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val am = context.getSystemService(android.media.AudioManager::class.java)
        val cb = object : android.media.AudioDeviceCallback() {
            fun refresh() { outputs = com.music.aalap.audio.OutputDevices.connected(context); pickedId = SongPlayer.pickedOutputId(context) }
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>?) = refresh()
        }
        am?.registerAudioDeviceCallback(cb, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(cb) }
    }
    val current = outputs.firstOrNull { it.first.id == pickedId }?.first ?: com.music.aalap.audio.OutputDevices.defaultOf(outputs)
    val routeName = current?.name ?: "This phone"
    var showOutputs by remember { mutableStateOf(false) }
    if (showOutputs) OutputPicker(
        outputs = outputs.map { it.first },
        currentId = current?.id,
        onPick = { id ->
            SongPlayer.setOutput(outputs.firstOrNull { it.first.id == id }?.second)
            pickedId = SongPlayer.pickedOutputId(context)
            showOutputs = false
        },
        onDismiss = { showOutputs = false },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 25.dp, vertical = 4.dp),
    ) {
        // Where it's playing (tap to choose): the phone, headphones or a Bluetooth device.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showOutputs = true }.padding(vertical = 4.dp, horizontal = 2.dp),
        ) {
            Icon(
                painter = painterResource(id = if (current == null || current.kind == com.music.aalap.audio.OutputDevices.Kind.PHONE) R.drawable.ic_output_phone else R.drawable.ic_output_headphones),
                tint = com.music.aalap.ui.theme.Saffron,
                modifier = Modifier.size(18.dp),
                contentDescription = "Device",
            )
            Text(
                text = routeName,
                color = com.music.aalap.ui.theme.Saffron,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .widthIn(max = 170.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Playback speed: tap to cycle 0.75× → 1× → 1.25× → 1.5× → 2×.
            var speed by remember { mutableStateOf(com.music.aalap.data.preferences.getPlaybackSpeed(context)) }
            var showSpeed by remember { mutableStateOf(false) }
            if (showSpeed) {
                com.music.aalap.ui.components.SpeedSheet(
                    speed = speed,
                    onChange = { speed = it; SongPlayer.setSpeed(context, it) },
                    onDismiss = { showSpeed = false },
                )
            }
            Text(
                com.music.aalap.ui.components.formatSpeed(speed),
                color = if (speed != 1f) com.music.aalap.ui.theme.OnSaffron else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (speed != 1f) com.music.aalap.ui.theme.Saffron else Color.White.copy(alpha = 0.16f))
                    .clickable { showSpeed = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Spacer(modifier = Modifier.width(18.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_equalizer),
                tint = if (com.music.aalap.data.preferences.isEqEnabled(context)) com.music.aalap.ui.theme.Saffron else Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { com.music.aalap.ui.navigation.PlayerReturn.open(navController, Routes.Sound.route) },
                contentDescription = "Sound and equalizer",
            )
            Spacer(modifier = Modifier.width(20.dp))
            val sharing by com.music.aalap.ui.share.ShareTask.busy.collectAsState()
            if (sharing) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share),
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { shareSongCard(context, currentTrack) },
                    contentDescription = "Share song",
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                tint = Color.White,
                modifier = Modifier
                    .size(23.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { navController.navigate(Routes.Queue.route) },
                contentDescription = "Queue",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun PlayerOptionsSheet(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleep by remember { mutableStateOf(false) }
    var showSavedIn by remember { mutableStateOf(false) }
    var showAlternativeStream by remember { mutableStateOf(false) }

    val title = playerViewModel.currentSongTitle.value
    val singer = playerViewModel.currentSongSinger.value
    val cover = playerViewModel.currentSongCoverUri.value
    val album = playerViewModel.currentSongAlbum.value
    val songId = playerViewModel.currentSongId.value
    // The full track model (spotify id, real album, stream url) — the state above
    // only carries display strings, and `album` is the *context* name (playlist…).
    val currentSong = playerViewModel.queue.value.firstOrNull { it.id == songId }
    var downloaded by remember(songId) { mutableStateOf(com.music.aalap.data.preferences.isDownloaded(context, songId.toString())) }
    var downloadingNow by remember(songId) { mutableStateOf(currentSong != null && com.music.aalap.di.Downloads.isDownloading(currentSong.url)) }
    var askQuality by remember { mutableStateOf(false) }
    val alternativeKey = currentSong?.let { alternativeStreamKey(it) }.orEmpty()
    var currentAlternative by remember(songId, alternativeKey) {
        mutableStateOf(alternativeKey.takeIf { it.isNotBlank() }?.let { getAlternativeStream(context, it) })
    }
    val localFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val song = currentSong ?: return@rememberLauncherForActivityResult
        val picked = uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        setLocalAlternativeStream(context, alternativeKey, picked, picked.lastPathSegment.orEmpty())
        SongPlayer.invalidateResolvedStream(song.url)
        currentAlternative = getAlternativeStream(context, alternativeKey)
        Toast.makeText(context, "Alternative stream set to local file", Toast.LENGTH_SHORT).show()
    }

    if (showSavedIn && currentSong != null) {
        com.music.aalap.ui.components.SavedInSheet(
            song = currentSong,
            context = context,
            onDismiss = { showSavedIn = false; onDismiss() },
            onLikedChanged = { isLiked.value = it },
        )
        return
    }

    val sheetScope = rememberCoroutineScope()
    // Back from Sleep timer (or Alternative stream) goes back to this menu, not out of it.
    val backToMenu: () -> Unit = {
        showSleep = false
        showAlternativeStream = false
        sheetScope.launch { sheetState.show() }
    }
    ModalBottomSheet(
        onDismissRequest = { if (showSleep || showAlternativeStream) backToMenu() else onDismiss() },
        sheetState = sheetState,
        containerColor = com.music.aalap.ui.theme.Surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Long menu on a phone: it scrolls, so the last rows (Sleep timer…) are reachable.
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        ) {
            if (showAlternativeStream) {
                AlternativeStreamEditor(
                    currentAlternative = currentAlternative,
                    enabled = currentSong != null,
                    onBack = { showAlternativeStream = false },
                    onUseYouTube = { text ->
                        val song = currentSong ?: return@AlternativeStreamEditor
                        val videoId = SongPlayer.videoIdFromYouTubeLink(text)
                        if (videoId == null) {
                            Toast.makeText(context, "Paste a YouTube video link or video ID", Toast.LENGTH_SHORT).show()
                        } else {
                            setYouTubeAlternativeStream(context, alternativeKey, videoId)
                            SongPlayer.invalidateResolvedStream(song.url)
                            currentAlternative = getAlternativeStream(context, alternativeKey)
                            Toast.makeText(context, "Alternative stream set to YouTube", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPickLocal = {
                        localFileLauncher.launch(arrayOf("audio/*"))
                    },
                    onClear = {
                        val song = currentSong ?: return@AlternativeStreamEditor
                        clearAlternativeStream(context, alternativeKey)
                        SongPlayer.invalidateResolvedStream(song.url)
                        currentAlternative = null
                        Toast.makeText(context, "Alternative stream cleared", Toast.LENGTH_SHORT).show()
                    },
                )
            } else if (!showSleep) {
                // ── Now-playing header ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    GlideImage(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        model = cover,
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(com.music.aalap.ui.components.shortArtists(singer), color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = com.music.aalap.ui.theme.SurfaceHigh)

                PlayerMenuRow(
                    icon = Icons.Default.Share,
                    label = "Share song"
                ) { shareSongCard(context, currentSong, onDismiss) }
                if (currentSong != null) {
                    // Tell the recommendations what you think of this song.
                    listOf(
                        Triple(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(com.music.aalap.R.drawable.ic_thumb_up), "More like this", com.music.aalap.data.recommendation.engine.Feedback.Kind.MORE),
                        Triple(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(com.music.aalap.R.drawable.ic_thumb_down), "Less like this", com.music.aalap.data.recommendation.engine.Feedback.Kind.LESS),
                        Triple(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(com.music.aalap.R.drawable.ic_block), "Don't play this song", com.music.aalap.data.recommendation.engine.Feedback.Kind.BLOCK_SONG),
                        Triple(androidx.compose.ui.graphics.vector.ImageVector.vectorResource(com.music.aalap.R.drawable.ic_block), "Don't play " + currentSong.singer.substringBefore(",").trim(), com.music.aalap.data.recommendation.engine.Feedback.Kind.BLOCK_ARTIST),
                    ).forEach { (icon, label, kind) ->
                        PlayerMenuRow(icon = icon, label = label) {
                            playerViewModel.feedback(currentSong, kind, context)
                            onDismiss()
                        }
                    }
                }
                // Live progress right here in the menu while it downloads (polled only while
                // this menu is open and a download is running).
                var downloadPct by remember { mutableStateOf(-1) }
                LaunchedEffect(downloadingNow) {
                    while (downloadingNow) {
                        downloadPct = com.music.aalap.di.Downloads.downloadProgress(currentSong?.url.orEmpty())
                        kotlinx.coroutines.delay(300)
                    }
                }
                PlayerMenuRow(
                    icon = if (downloaded) Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                    iconTint = if (downloaded) Color(AppPalette.toArgb()) else com.music.aalap.ui.theme.TextPrimary,
                    label = when {
                        downloaded -> "Downloaded — remove"
                        downloadingNow -> if (downloadPct in 1..99) "Downloading… $downloadPct%" else "Downloading… getting ready"
                        else -> "Download"
                    },
                    enabled = currentSong != null && !downloadingNow,
                ) {
                    val song = currentSong ?: return@PlayerMenuRow
                    if (downloaded) {
                        com.music.aalap.data.preferences.removeDownload(context, song.id.toString())
                        downloaded = false
                    } else {
                        askQuality = true
                    }
                }
                if (downloadingNow) com.music.aalap.ui.components.DownloadBar(downloadPct)
                if (askQuality && currentSong != null) com.music.aalap.ui.components.DownloadAsk(listOf(currentSong), onDone = { askQuality = false }) { q ->
                    downloadingNow = true
                    com.music.aalap.di.Downloads.downloadSong(currentSong, context, quality = q) { ok ->
                        downloadingNow = false
                        downloaded = ok
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    iconTint = if (currentAlternative != null) Color(AppPalette.toArgb()) else com.music.aalap.ui.theme.TextPrimary,
                    label = if (currentAlternative == null) "Alternative stream" else "Alternative stream set",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showAlternativeStream = true
                }
                PlayerMenuRow(
                    icon = if (isLiked.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (isLiked.value) Color(AppPalette.toArgb()) else com.music.aalap.ui.theme.TextPrimary,
                    label = if (isLiked.value) "Remove from Liked Songs" else "Add to Liked Songs"
                ) {
                    if (isLiked.value) {
                        removeLikedSongId(context, songId.toString())
                    } else {
                        addLikedSongId(context, songId.toString())
                        com.music.aalap.ui.components.LikeCelebration.burst()
                    }
                    isLiked.value = isSongLiked(context, songId.toString())
                    // Mirror the like to the real Spotify account.
                    com.music.aalap.data.api.SpotifySync.setTrackSaved(
                        context, currentSong?.spotifyTrackId.orEmpty(), isLiked.value)
                    onDismiss()
                }
                PlayerMenuRow(
                    icon = Icons.Default.Add,
                    label = "Add to playlist",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showSavedIn = true
                }
                PlayerMenuRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "View queue"
                ) {
                    onDismiss()
                    navController.navigate(Routes.Queue.route)
                }
                // Use the track's REAL album (currentSongAlbum is the playing
                // context — a playlist name would resolve to garbage).
                val realAlbum = currentSong?.album?.ifBlank { null } ?: album
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = "Go to album",
                    enabled = realAlbum.isNotBlank()
                ) {
                    onDismiss()
                    com.music.aalap.ui.navigation.PlayerReturn.open(navController, albumRoute(realAlbum, singer))
                }
                PlayerMenuRow(
                    icon = Icons.Default.Person,
                    label = "Go to artist",
                    enabled = singer.isNotBlank()
                ) {
                    onDismiss()
                    playerViewModel.goToArtist(currentSong?.spotifyTrackId.orEmpty(), singer) { route ->
                        com.music.aalap.ui.navigation.PlayerReturn.open(navController, route)
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.Notifications,
                    label = "Sleep timer",
                    trailingArrow = true
                ) { showSleep = true }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { backToMenu() }.padding(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = com.music.aalap.ui.theme.TextPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Sleep timer", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.HorizontalDivider(color = com.music.aalap.ui.theme.SurfaceHigh)
                val options = listOf(
                    "Off" to 0L,
                    "5 minutes" to 5L,
                    "15 minutes" to 15L,
                    "30 minutes" to 30L,
                    "45 minutes" to 45L,
                    "1 hour" to 60L
                )
                options.forEach { (label, minutes) ->
                    PlayerMenuRow(icon = Icons.Default.Notifications, label = label) {
                        SongPlayer.setSleepTimer(minutes * 60_000L)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun AlternativeStreamEditor(
    currentAlternative: com.music.aalap.data.preferences.AlternativeStream?,
    enabled: Boolean,
    onBack: () -> Unit,
    onUseYouTube: (String) -> Unit,
    onPickLocal: () -> Unit,
    onClear: () -> Unit,
) {
    var youtubeText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                tint = com.music.aalap.ui.theme.TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() },
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Alternative stream",
                color = com.music.aalap.ui.theme.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when {
                currentAlternative == null -> "No alternative stream set"
                currentAlternative.isYouTube -> "Current: YouTube video ${currentAlternative.value}"
                currentAlternative.isLocal -> "Current: local file ${currentAlternative.label.ifBlank { currentAlternative.value }}"
                else -> "Current alternative stream"
            },
            color = if (currentAlternative == null) com.music.aalap.ui.theme.TextMuted else Color(AppPalette.toArgb()),
            fontSize = 13.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = youtubeText,
            onValueChange = { youtubeText = it },
            enabled = enabled,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp),
            label = { Text("YouTube link or video ID", color = com.music.aalap.ui.theme.TextMuted) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = enabled && youtubeText.isNotBlank(), onClick = { onUseYouTube(youtubeText) }) {
                Text("Use YouTube", color = if (enabled && youtubeText.isNotBlank()) AppPalette else Color.Gray)
            }
        }
        PlayerMenuRow(
            icon = Icons.Default.Add,
            label = "Use local audio file",
            enabled = enabled,
        ) {
            onPickLocal()
        }
        PlayerMenuRow(
            icon = Icons.Default.CheckCircle,
            label = "Clear alternative stream",
            enabled = enabled && currentAlternative != null,
            iconTint = Color(0xFFE57373),
        ) {
            onClear()
        }
    }
}

@Composable
fun PlayerMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = com.music.aalap.ui.theme.TextPrimary,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            // A little tighter so more of the menu fits on one screen.
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Icon(
            imageVector = icon,
            tint = if (enabled) iconTint else Color.Gray,
            modifier = Modifier.size(22.dp),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            color = if (enabled) com.music.aalap.ui.theme.TextPrimary else Color.Gray,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailingArrow) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
        }
    }
}
/**
 * Plays a Spotify Canvas clip: a short, muted, looping video filling the
 * now-playing background. Uses a dedicated ExoPlayer (separate from the audio
 * engine) released when the composable leaves. Falls back to nothing if the URL fails.
 *
 * Loops are served from the media cache: without it every loop re-downloaded the
 * clip, and since ExoPlayer ignores the activity lifecycle it kept doing so in the
 * background (#87, gigabytes per hour). It also pauses while the app is not visible.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun CanvasVideo(url: String, synced: Boolean = false, onFail: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    val exo = remember(url) {
        val sources: androidx.media3.datasource.DataSource.Factory = if (synced) {
            // A YouTube music video: streamed like the audio (the headers of the YouTube app
            // that issued the link, in ranged chunks), not cached since it's big.
            val http = androidx.media3.datasource.DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            com.music.aalap.playback.ChunkedDataSource.Factory(
                androidx.media3.datasource.ResolvingDataSource.Factory(http) { spec ->
                    spec.withAdditionalHeaders(com.metrolist.innertube.models.YouTubeClient.forStreamUrl(spec.uri.toString()).mediaHeaders())
                },
                2L * 1024 * 1024,
            )
        } else androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(SongPlayer.mediaCache(context.applicationContext))
            .setUpstreamDataSourceFactory(androidx.media3.datasource.DefaultHttpDataSource.Factory())
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(sources))
            .build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                // A Canvas loops; a music video follows the song instead.
                repeatMode = if (synced) androidx.media3.common.Player.REPEAT_MODE_OFF else androidx.media3.common.Player.REPEAT_MODE_ALL
                volume = 0f
                playWhenReady = true
                // If the video can't play, the artwork comes back instead of a black screen.
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true; onFail() }
                })
                prepare()
            }
    }
    if (failed) return
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    // A music video stays in step with the song: same position (within a second), and
    // paused when the song is.
    if (synced) LaunchedEffect(exo) {
        while (true) {
            val songPos = SongPlayer.exoPlayer?.currentPosition ?: 0L
            if (exo.playbackState == androidx.media3.common.Player.STATE_READY && kotlin.math.abs(exo.currentPosition - songPos) > 1_000) exo.seekTo(songPos)
            exo.playWhenReady = SongPlayer.isPlaying() && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            kotlinx.coroutines.delay(700)
        }
    }
    androidx.compose.runtime.DisposableEffect(url, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            exo.playWhenReady = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            exo.release()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        // factory runs once; a new track's clip gets a new player, so rebind it here.
        update = { it.player = exo },
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exo
                // Strip ALL chrome: no controller, no buffering spinner, and no
                // artwork/placeholder icon (that "play icon" overlay) — just video.
                useController = false
                controllerAutoShow = false
                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                setArtworkDisplayMode(androidx.media3.ui.PlayerView.ARTWORK_DISPLAY_MODE_OFF)
                setUseArtwork(false)
                setDefaultArtwork(null)
                hideController()
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // Until a clip's first frame, show the screen behind, not a black shutter.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
            }
        },
    )
}

/** Choose where the music comes out, from the devices connected now. */
@Composable
private fun OutputPicker(
    outputs: List<com.music.aalap.audio.OutputDevices.Output>,
    currentId: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play on") },
        text = {
            Column {
                outputs.forEach { o ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(o.id) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = if (o.kind == com.music.aalap.audio.OutputDevices.Kind.PHONE) R.drawable.ic_output_phone else R.drawable.ic_output_headphones),
                            tint = if (o.id == currentId) com.music.aalap.ui.theme.Saffron else com.music.aalap.ui.theme.TextPrimary,
                            modifier = Modifier.size(20.dp), contentDescription = null,
                        )
                        Text(
                            o.name, modifier = Modifier.padding(start = 12.dp).weight(1f),
                            color = if (o.id == currentId) com.music.aalap.ui.theme.Saffron else com.music.aalap.ui.theme.TextPrimary,
                            fontWeight = if (o.id == currentId) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (o.id == currentId) Text("Playing", color = com.music.aalap.ui.theme.Saffron, fontSize = 12.sp)
                    }
                }
                Text(
                    "Not listed? Connect it in Bluetooth settings first.",
                    color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onDismiss()
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Bluetooth settings", color = com.music.aalap.ui.theme.Saffron) }
        },
        dismissButton = {
            // Android 14+: its own switcher, which also lists cast and nearby devices.
            if (android.os.Build.VERSION.SDK_INT >= 34) androidx.compose.material3.TextButton(onClick = {
                onDismiss()
                runCatching { android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher() }
            }) { Text("More devices", color = com.music.aalap.ui.theme.TextMuted) }
        },
    )
}


/**
 * Add to playlist, the artist and download, as buttons on the player instead of only inside
 * the ⋮ menu. They do exactly what the menu rows do. Removing a download stays in the menu,
 * so a stray tap here never deletes anything.
 */
@Composable
private fun PlayerQuickActions(
    song: com.music.aalap.data.entity.SongsModel?,
    singer: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
) {
    val id = song?.id ?: return
    var savedIn by remember { mutableStateOf(false) }
    var downloaded by remember(id) { mutableStateOf(com.music.aalap.data.preferences.isDownloaded(context, id.toString())) }
    var downloading by remember(id) { mutableStateOf(com.music.aalap.di.Downloads.isDownloading(song.url)) }
    if (savedIn) com.music.aalap.ui.components.SavedInSheet(
        song = song,
        context = context,
        onDismiss = { savedIn = false },
        onLikedChanged = { isLiked.value = it },
    )
    var askQuality by remember { mutableStateOf(false) }
    if (askQuality) com.music.aalap.ui.components.DownloadAsk(listOf(song), onDone = { askQuality = false }) { q ->
        downloading = true
        com.music.aalap.di.Downloads.downloadSong(song, context, quality = q) { ok -> downloading = false; downloaded = ok }
    }
    @Composable
    fun Pill(label: String, icon: androidx.compose.ui.graphics.painter.Painter, tint: Color, modifier: Modifier, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp, horizontal = 8.dp),
        ) {
            Icon(painter = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
    ) {
        Pill("Playlist", androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Add), Color.White, Modifier.weight(1f)) { savedIn = true }
        Pill("Artist", androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Person), Color.White, Modifier.weight(1f)) {
            playerViewModel.goToArtist(song.spotifyTrackId, singer) { route -> com.music.aalap.ui.navigation.PlayerReturn.open(navController, route) }
        }
        Pill(
            when { downloaded -> "Saved"; downloading -> "Saving…"; else -> "Download" },
            if (downloaded) androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.CheckCircle) else painterResource(R.drawable.ic_download),
            if (downloaded) Color(AppPalette.toArgb()) else Color.White,
            Modifier.weight(1f),
        ) {
            if (downloaded || downloading) return@Pill
            askQuality = true
        }
    }
}

package com.music.aalap.ui.components

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.preferences.addLikedSongId
import com.music.aalap.data.preferences.isSongLiked
import com.music.aalap.data.preferences.removeLikedSongId
import com.music.aalap.di.Palette
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.navigation.Routes
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.GridBackground
import com.music.aalap.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun Loader() {
    // Aalap's bouncing sound bars, centred in the space (was a fixed purple spinner).
    CenteredLoader(Modifier.background(com.music.aalap.ui.theme.AppBackground))
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MiniPlayer(navController: NavHostController) {
    val miniPlayerViewModel : PlayerViewModel = hiltViewModel()
    val songTitle = miniPlayerViewModel.currentSongTitle.value
    val songSinger = miniPlayerViewModel.currentSongSinger.value
    val songCoverUri = miniPlayerViewModel.currentSongCoverUri.value
    var songPlayingState = miniPlayerViewModel.currentSongPlayingState.value
    val songId = miniPlayerViewModel.currentSongId.value
    val songIndex = miniPlayerViewModel.currentSongIndex.value
    val songAlbum = miniPlayerViewModel.currentSongAlbum.value


    // Where the song is, set once as the bar appears and then moved by the timer below. (It was
    // written while drawing, which asks for another drawing: with the song always moving, the
    // mini player redrew itself every frame, endlessly, open or in the background.)
    var songProgress by remember(songId) {
        mutableFloatStateOf(SongPlayer.progress().fraction)
    }

    val currentRoute = navController.currentBackStackEntry?.destination?.route



    // The bar moves only while Aalap is on screen: in the background this redrew the mini
    // player three times a second for nobody (the biggest part of Aalap's background CPU).
    val miniLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    // Restarted per song too (songProgress is new for each song; see PlayerScreen).
    LaunchedEffect(songPlayingState, miniLifecycle, songId) {
        miniLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            // Once while paused, then each second while playing (same reading as the player screen).
            do {
                songProgress = SongPlayer.progress().fraction
                // (The full player used to open by itself when a song ended; the end of a song
                // is the playback service's job, and opening the player then could jump songs.)
                // Once a second: the bar moves under half a percent a second, and every step
                // redraws the screen.
                if (songPlayingState) delay(1_000L)
            } while (songPlayingState)
        }
    }

    val context = LocalContext.current

    var darkVibrantColor by remember {
        mutableStateOf(com.music.aalap.ui.theme.Surface)
    }
    // Once per cover (not on every redraw): reading the cover's colour is slow.
    androidx.compose.runtime.LaunchedEffect(songCoverUri) {
        Palette().extractFirstColorFromImageUrl(context = context, songCoverUri){ color ->
            darkVibrantColor = color
        }
    }

    var isLiked by remember {
        mutableStateOf(false)
    }
    val likeState = miniPlayerViewModel.likeState.value
    // Any like or unlike, wherever it happened (player, menus, notification), shows here too.
    val likedVersion by com.music.aalap.data.preferences.likedVersion.collectAsState()
    LaunchedEffect(likeState, songId, likedVersion) {
        isLiked = isSongLiked(context, songId.toString())
    }
    val currentTrack = miniPlayerViewModel.queue.value.firstOrNull { it.id == songId }


    // Swipe the track info sideways to skip: left = next, right = previous.
    val swipe = remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    // New track (from a swipe, the buttons, or the queue moving on): fade its info in.
    val enter = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(songId) {
        enter.snapTo(0f)
        enter.animateTo(1f, androidx.compose.animation.core.tween(320))
    }

    // Floating card tinted by the artwork, over the theme surface (lighter wash in light mode).
    val cardColor = androidx.compose.ui.graphics.lerp(
        com.music.aalap.ui.theme.Surface, darkVibrantColor, if (com.music.aalap.ui.theme.AalapPalette.isLight) 0.22f else 0.55f)
    val cardShape = RoundedCornerShape(20.dp)
    // Liking from here: marigold petals drop through the bar and a gold border draws itself, left to right.
    val likeBurst = rememberBarCelebration()
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .shadow(10.dp, cardShape)
            .clip(cardShape)
            .background(cardColor)
            .barCelebration(likeBurst, 20.dp)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp)
            .clipToBounds()
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                //.background(Color.Green)
                .padding(0.dp, 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navController.navigate(Routes.Player.route)
                }
                // Swipe up on the mini player to open the full player.
                .pointerInput(Unit) {
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pulled = 0f },
                        onVerticalDrag = { change, dy -> change.consume(); pulled += dy },
                        onDragEnd = {
                            if (pulled < -swipeThreshold * 0.6f && currentRoute != Routes.Player.route) {
                                navController.navigate(Routes.Player.route)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeScope.launch { swipe.snapTo(swipe.value + dragAmount) }
                        },
                        onDragCancel = { swipeScope.launch { swipe.animateTo(0f) } },
                        onDragEnd = {
                            swipeScope.launch {
                                val offset = swipe.value
                                if (kotlin.math.abs(offset) < swipeThreshold) {
                                    swipe.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f))
                                    return@launch
                                }
                                val queue = miniPlayerViewModel.queue.value
                                val dir = if (offset < 0) -1f else 1f
                                // Slide the old track out, switch, then bring the new one in from the other side.
                                swipe.animateTo(dir * size.width, androidx.compose.animation.core.tween(160))
                                if (queue.isNotEmpty()) {
                                    if (dir < 0) miniPlayerViewModel.playNextSongs(queue, context)
                                    else miniPlayerViewModel.playPreviousSong(queue, context)
                                }
                                swipe.snapTo(-dir * size.width * 0.5f)
                                swipe.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f))
                            }
                        },
                    )
                }
        ){

            Row(horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                // Takes what the buttons leave (a fixed 260 dp squeezed the play button into a
                // sliver on screens under ~396 dp wide, e.g. an OPPO at 360 dp).
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = swipe.value
                        alpha = enter.value * (1f - (kotlin.math.abs(swipe.value) / (size.width * 1.2f))).coerceIn(0f, 1f)
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(0.dp, 0.dp, 12.dp, 0.dp)
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    GlideImage(
                        modifier = Modifier.fillMaxSize(),
                        model = songCoverUri,
                        contentScale = ContentScale.Crop,
                        failure = placeholder(R.drawable.placeholder),
                        loading = placeholder(R.drawable.placeholder),
                        contentDescription = ""
                    )
                    // A small equalizer on the cover while the song plays.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = songPlayingState,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(250)),
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                            CoverEqualizer(songId)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .widthIn(Dp.Unspecified, 200.dp)
                ) {
                    Text(text = songTitle, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis )
                    Text(text = songSinger, color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(84.dp)
            ) {

                // Plus = save: it fills with saffron from left to right; tap again to unlike.
                LikeFillIcon(
                    liked = isLiked,
                    outline = painterResource(id = R.drawable.ic_add),
                    filled = painterResource(id = R.drawable.added),
                    fillColor = com.music.aalap.ui.theme.Saffron,
                    outlineColor = com.music.aalap.ui.theme.TextPrimary,
                    contentDescription = if (isLiked) "Saved" else "Add",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isLiked) {
                            // Tap again to unlike: the button empties right to left.
                            com.music.aalap.data.preferences.removeLikedSongId(context, songId.toString())
                            isLiked = false
                            miniPlayerViewModel.updateLikeState(!likeState)
                            com.music.aalap.data.api.SpotifySync.setTrackSaved(
                                context, currentTrack?.spotifyTrackId.orEmpty(), false)
                            return@clickable
                        }
                        addLikedSongId(context, songId.toString())
                        isLiked = true
                        swipeScope.launch { likeBurst.play() }
                        miniPlayerViewModel.updateLikeState(!likeState)
                        // Mirror the like to the real Spotify account.
                        com.music.aalap.data.api.SpotifySync.setTrackSaved(
                            context, currentTrack?.spotifyTrackId.orEmpty(), true)
                    },
                )


                Icon(
                    painter = if (songPlayingState)
                        painterResource(id = R.drawable.ic_playing)
                    else
                        painterResource(id = R.drawable.play_svgrepo_com)
                    ,
                    contentDescription = if (songPlayingState) "Pause" else "Play",
                    tint = com.music.aalap.ui.theme.OnSaffron,
                    // Round saffron play button: Aalap's signature control.
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.music.aalap.ui.theme.Saffron)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (songPlayingState) {
                                SongPlayer.pause()
                                miniPlayerViewModel.updateSongState(
                                    songCoverUri,
                                    songTitle,
                                    songSinger,
                                    false,
                                    songId,
                                    songIndex,
                                    songAlbum
                                )
                            } else {
                                SongPlayer.play()
                                miniPlayerViewModel.updateSongState(
                                    songCoverUri,
                                    songTitle,
                                    songSinger,
                                    true,
                                    songId,
                                    songIndex,
                                    songAlbum
                                )
                            }
                        }
                        // Inset the glyph after clickable so the whole 40dp circle is tappable.
                        .padding(11.dp)
                )
            }


        }


        CustomSlider(
            value = songProgress,
            onValueChange = { newValue ->
                SongPlayer.seekTo((newValue * SongPlayer.getDuration()).toLong())
            },
            valueRange = 0f..1f,
            steps = 0,
            modifier = Modifier
                .fillMaxWidth()
            ,
            colors = SliderDefaults.colors(
                thumbColor = com.music.aalap.ui.theme.Saffron,
                activeTrackColor = com.music.aalap.ui.theme.Saffron,
                inactiveTrackColor = com.music.aalap.ui.theme.TextMuted.copy(alpha = 0.3f)
            )
        )
    }



}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Box(
        //contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .fillMaxWidth()
            .height(0.dp)

    ) {

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            thumb = {
                SliderDefaults.Thumb( //androidx.compose.material3.SliderDefaults
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier.align(Alignment.Center),
                    colors = colors,
                    thumbSize = DpSize(4.dp, 4.dp)
                )
            }
        )
    }
}


@Composable
fun Snackbar(showMessage : String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ){
        Text(
            fontWeight = FontWeight.W500,
            fontSize = 14.sp,
            color = Color.Black,
            text = showMessage
        )
    }
}

/**
 * Asks before downloading a whole list: the download-all icon is small and shows
 * up only once the list has loaded, so a stray tap used to start dozens of downloads.
 */
@Composable
fun ConfirmDownloadAllDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Download $count tracks?") },
        text = { androidx.compose.material3.Text("They are saved for offline playback. You can cancel from Downloads.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm() }) {
                androidx.compose.material3.Text("Download")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") }
        },
    )
}


/**
 * Four little saffron bars that move with the music: only while sound is really coming out
 * ([SongPlayer.audible]: not while paused, loading, stalled or muted by a call), gliding to a
 * new height on every beat of the song's measured tempo ([BeatBars]) for a few seconds, then
 * holding still; only while Aalap is on screen. Paused or loading, they settle low. The heights are read only while drawing,
 * so each frame redraws these bars alone, not the mini player around them.
 */
@Composable
private fun CoverEqualizer(songId: Int) {
    val audible = SongPlayer.audible.value
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    var bpm by remember(songId) { mutableStateOf<Double?>(null) }
    LaunchedEffect(songId) {
        val song = SongPlayer.queuedSong(songId) ?: return@LaunchedEffect
        bpm = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.music.aalap.data.recommendation.engine.VibeStore.tempoOf(context.applicationContext, song)
        }
    }
    // The bars glide (eased) to a new height each beat, but the screen is redrawn only
    // BeatBars.FRAME_MS apart (25 a second): at the display's 60–120 a second every frame
    // redraws the whole window (see BeatBars.FRAME_MS for the measured cost).
    val now = remember { FloatArray(4) { BeatBars.REST[it] } }
    var frame by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(audible, bpm, songId) {
        suspend fun glide(target: List<Float>, ms: Long) {
            val from = now.copyOf()
            val steps = (ms / BeatBars.FRAME_MS).coerceAtLeast(1)
            for (k in 1..steps) {
                val e = androidx.compose.animation.core.FastOutSlowInEasing.transform(k.toFloat() / steps)
                for (i in 0 until 4) now[i] = from[i] + (target[i] - from[i]) * e
                frame++
                kotlinx.coroutines.delay(BeatBars.FRAME_MS)
            }
        }
        // Settle gently instead of freezing mid-movement.
        if (!audible) { glide(BeatBars.REST, 350); return@LaunchedEffect }
        // A few seconds of gliding each time music starts, resumes, changes song or Aalap comes
        // back on screen, then a still "playing" pose: gliding all the time cost a third of a core.
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            val step = BeatBars.stepMs(bpm)
            repeat(BeatBars.glideBeats(step)) { n -> glide(BeatBars.heights(n, seed = songId), step) }
            glide(BeatBars.HOLD, step)
            kotlinx.coroutines.awaitCancellation()
        }
    }
    // Read only while drawing: a new frame redraws these bars, not the mini player.
    EqualizerBars { i -> frame.let { now[i] } }
}

@Composable
private fun EqualizerBars(height: (Int) -> Float) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
        val w = 3.dp.toPx(); val gap = (size.width - w * 4) / 3
        for (i in 0 until 4) {
            val h = size.height * height(i)
            drawRoundRect(
                com.music.aalap.ui.theme.Saffron,
                topLeft = androidx.compose.ui.geometry.Offset(i * (w + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
            )
        }
    }
}

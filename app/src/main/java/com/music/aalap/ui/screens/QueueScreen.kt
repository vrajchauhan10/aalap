package com.music.aalap.ui.screens

import com.music.aalap.ui.components.swipeDownToDismiss
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.theme.AppPalette
import com.music.aalap.ui.viewmodel.PlayerViewModel

/**
 * The "play queue" — the track that's playing now plus everything coming up
 * (Spotify's Queue view). Tapping an upcoming track jumps straight to it;
 * dragging the handle reorders it; swiping a row removes it.
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(navController: NavController) {
    val closeSheet = com.music.aalap.ui.navigation.LocalCloseSheet.current
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val context = LocalContext.current
    val queue by playerViewModel.queue
    val currentId = playerViewModel.currentSongId.value
    val curIdx = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val upcoming = if (queue.isNotEmpty()) queue.drop(curIdx + 1) else emptyList()
    val current = queue.getOrNull(curIdx)

    // Drag-to-reorder state: which upcoming row is dragged and its live offset.
    var draggingId by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 70.dp.toPx() }

    val playingFrom = playerViewModel.currentSongAlbum.value
    val leftMs = upcoming.sumOf { it.durationMs.toLong() }
    val T = com.music.aalap.ui.theme.TextPrimary
    val M = com.music.aalap.ui.theme.TextMuted

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Pull the list down from its top to close the queue.
            .swipeDownToDismiss { navController.navigateUp() }
            .background(com.music.aalap.ui.theme.Surface)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBarsIgnoringVisibility)
    ) {
        // ── Header: close, title, what's left, shuffle / clear for what's coming ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Close",
                tint = T,
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable { closeSheet?.invoke() ?: navController.navigateUp() }
                    .padding(2.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Queue", color = T, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (upcoming.isEmpty()) "Nothing up next" else "${upcoming.size} up next" + (if (leftMs > 0) " · ${queueTime(leftMs)}" else ""),
                    color = M, fontSize = 12.sp,
                )
            }
            if (upcoming.size > 1) {
                QueueChip("Shuffle") {
                    playerViewModel.updateQueue(queue.take(curIdx + 1) + upcoming.shuffled())
                }
                Spacer(Modifier.width(8.dp))
                QueueChip("Clear") {
                    playerViewModel.updateQueue(queue.take(curIdx + 1))
                    android.widget.Toast.makeText(context, "Cleared. Aalap will keep the music going with songs like this one.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            current?.let {
                item(key = "now") { NowPlayingCard(it, playingFrom, playerViewModel.currentSongPlayingState.value) }
            }
            if (upcoming.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Nothing queued. When this song ends, Aalap plays songs like it. Use \"Add to queue\" on any song to line one up.",
                        color = M, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(20.dp, 24.dp),
                    )
                }
            } else {
                item(key = "nextup") {
                    Text(
                        "Next up",
                        color = T,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(20.dp, 18.dp, 16.dp, 6.dp)
                    )
                }
                itemsIndexed(upcoming, key = { _, s -> s.id }) { upIdx, song ->
                    val isDragging = draggingId == song.id
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                playerViewModel.removeFromQueue(song)
                                true
                            } else false
                        }
                    )
                    Box(Modifier.animateItemPlacement(androidx.compose.animation.core.spring(stiffness = 500f, dampingRatio = 0.8f))) {
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF7A1F1F))
                                    .padding(horizontal = 22.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Remove", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White)
                                }
                            }
                        },
                        content = {
                            QueueRow(
                                song = song,
                                number = upIdx + 1,
                                isDragging = isDragging,
                                dragOffsetY = if (isDragging) dragOffset else 0f,
                                onClick = {
                                    val abs = queue.indexOfFirst { it.id == song.id }
                                    if (abs >= 0) playerViewModel.playSongAt(queue, abs, context)
                                },
                                onDragStart = { draggingId = song.id; dragOffset = 0f },
                                onDragEnd = { draggingId = -1; dragOffset = 0f },
                                onDragDelta = { dy ->
                                    dragOffset += dy
                                    val abs = queue.indexOfFirst { it.id == song.id }
                                    if (abs >= 0) {
                                        if (dragOffset > rowHeightPx / 2 && abs < queue.size - 1) {
                                            playerViewModel.moveQueueItem(abs, abs + 1)
                                            dragOffset -= rowHeightPx
                                        } else if (dragOffset < -rowHeightPx / 2 && upIdx > 0) {
                                            playerViewModel.moveQueueItem(abs, abs - 1)
                                            dragOffset += rowHeightPx
                                        }
                                    }
                                },
                            )
                        }
                    )
                    }
                }
            }
        }
    }
}

/** "1 h 12 min left" / "24 min left". */
private fun queueTime(ms: Long): String {
    val min = (ms / 60_000).toInt()
    return if (min >= 60) "${min / 60} h ${min % 60} min left" else "${min.coerceAtLeast(1)} min left"
}

@Composable
private fun QueueChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = com.music.aalap.ui.theme.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(com.music.aalap.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** The song playing now, as a card: bigger cover, where it's playing from, live bars. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun NowPlayingCard(song: com.music.aalap.data.entity.SongsModel, from: String, playing: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp, 10.dp, 12.dp, 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(
                com.music.aalap.ui.theme.Saffron.copy(alpha = 0.22f), com.music.aalap.ui.theme.SurfaceHigh,
            )))
            .padding(12.dp),
    ) {
        GlideImage(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("NOW PLAYING", color = com.music.aalap.ui.theme.Saffron, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(song.title, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(com.music.aalap.ui.components.shortArtists(song.singer), color = com.music.aalap.ui.theme.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (from.isNotBlank()) Text("From $from", color = com.music.aalap.ui.theme.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayingBars(playing)
    }
}

/** Three bars that rise and fall while music plays and rest low when paused. */
@Composable
private fun PlayingBars(playing: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bars")
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(20.dp).padding(start = 8.dp)) {
        listOf(520, 680, 430).forEachIndexed { i, ms ->
            val h by t.animateFloat(
                0.3f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier.padding(horizontal = 1.5.dp).width(3.dp)
                    .fillMaxHeight(if (playing) h else 0.3f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(com.music.aalap.ui.theme.Saffron),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueRow(
    song: com.music.aalap.data.entity.SongsModel,
    number: Int,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
) {
    // The dragged row lifts: a touch bigger, outlined, above the rest.
    val lift by androidx.compose.animation.core.animateFloatAsState(if (isDragging) 1f else 0f, label = "lift")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift
                shadowElevation = 16f * lift
                shape = RoundedCornerShape(14.dp); clip = true
            }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDragging) com.music.aalap.ui.theme.SurfaceHigh else com.music.aalap.ui.theme.Surface)
            .border(1.dp, com.music.aalap.ui.theme.Saffron.copy(alpha = lift), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp, 8.dp)
    ) {
        Text("$number", color = com.music.aalap.ui.theme.TextMuted, fontSize = 13.sp, modifier = Modifier.width(24.dp))
        GlideImage(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = ""
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = com.music.aalap.ui.theme.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = com.music.aalap.ui.components.shortArtists(song.singer) +
                    (if (song.durationMs > 0) " · ${song.durationMs / 60000}:${"%02d".format(song.durationMs / 1000 % 60)}" else ""),
                color = com.music.aalap.ui.theme.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Drag to reorder",
            tint = com.music.aalap.ui.theme.TextMuted,
            modifier = Modifier
                .size(40.dp)
                .padding(6.dp)
                .pointerInput(song.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.y)
                        },
                    )
                }
        )
    }
}

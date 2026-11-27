package com.music.aalap.ui.screens

import androidx.lifecycle.repeatOnLifecycle
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.music.aalap.R
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.getDownloadedSongs
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.AppPalette
import com.music.aalap.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(navController: NavController) {

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val context = LocalContext.current
    val accent = AppPalette
    // Opening Downloads clears the "something new" dot (and again for any that finish here).
    val unseenNow by com.music.aalap.di.Downloads.unseen.collectAsState()
    androidx.compose.runtime.LaunchedEffect(unseenNow) { if (unseenNow > 0) com.music.aalap.di.Downloads.markSeen(context) }

    // Completed downloads (from prefs) + live in-progress ones (from SongPlayer). Poll
    // while the screen is open so a track appears here the moment its download starts,
    // shows a live percentage, and moves into the list when it finishes.
    var songs by remember { mutableStateOf(getDownloadedSongs(context)) }
    // Each download with when it was saved and its size, for sorting and the total shown on top.
    val items = remember(songs) {
        com.music.aalap.data.preferences.getDownloadedEntries(context).map { (s, path) ->
            val f = java.io.File(path)
            DownloadsView.Item(s, if (f.exists()) f.lastModified() else 0L, if (f.exists()) f.length() else 0L)
        }
    }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DownloadsView.Sort.RECENT) }
    var show by remember { mutableStateOf(DownloadsView.Show.ALL) }
    val liked = remember(songs, com.music.aalap.data.preferences.likedVersion.collectAsState().value) {
        com.music.aalap.data.preferences.getLikedSongIds(context)
    }
    val shown = remember(items, query, sort, show, liked) { DownloadsView.apply(items, query, sort, show, liked).map { it.song } }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Remove all downloads?") },
        text = { Text("${com.music.aalap.ui.components.counted(songs.size, "song")} will be deleted from this phone. You can download them again later.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                confirmClear = false
                val n = com.music.aalap.data.preferences.clearAllDownloads(context)
                songs = getDownloadedSongs(context)
                android.widget.Toast.makeText(context, "Removed $n download${if (n == 1) "" else "s"}", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Remove all", color = Color(0xFFE57373)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Keep") } },
    )
    var inProgress by remember {
        mutableStateOf(com.music.aalap.di.Downloads.downloadingSnapshot())
    }
    val downloadsLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(downloadsLifecycle) { downloadsLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        while (true) {
            val snap = com.music.aalap.di.Downloads.downloadingSnapshot()
            if (snap.size != inProgress.size) songs = getDownloadedSongs(context)
            inProgress = snap
            kotlinx.coroutines.delay(400)
        }
    } }

    var menuSong by remember { mutableStateOf<SongsModel?>(null) }
    menuSong?.let { sel ->
        com.music.aalap.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
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
                            tint = com.music.aalap.ui.theme.TextPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = com.music.aalap.ui.theme.TextPrimary,
                    ),
                    title = { Text(text = "") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
                    .consumeWindowInsets(innerPadding)
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(accent.copy(alpha = 0.5f), Color(AppBackground.toArgb())),
                                startY = -100f,
                            ),
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.padding(25.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_download),
                                contentDescription = "",
                                tint = accent,
                                modifier = Modifier.size(90.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(5.dp))
                    Text(
                        modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                        text = "Downloaded",
                        color = com.music.aalap.ui.theme.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                        text = com.music.aalap.ui.components.counted(songs.size, "song") +
                            (items.sumOf { it.bytes }.takeIf { it > 0 }?.let { " • " + com.music.aalap.ui.components.formatBytes(it) } ?: "") +
                            " • plays with no internet",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (songs.isNotEmpty()) {
                    // Play in the order shown, or shuffled (songs you haven't heard lately first).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(20.dp, 0.dp, 20.dp, 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(accent)
                                .clickable(enabled = shown.isNotEmpty()) { playerViewModel.playAll(shown, "Downloaded", context) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Icon(painterResource(R.drawable.play_svgrepo_com), null, tint = com.music.aalap.ui.theme.OnSaffron, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play", color = com.music.aalap.ui.theme.OnSaffron, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(com.music.aalap.ui.theme.Surface)
                                .clickable(enabled = shown.isNotEmpty()) {
                                    playerViewModel.playAll(com.music.aalap.data.preferences.smartShuffled(context, shown), "Downloaded", context)
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_player_shuffle), null, tint = com.music.aalap.ui.theme.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        var more by remember { mutableStateOf(false) }
                        Box {
                            Text("⋯", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 22.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { more = true }.padding(horizontal = 12.dp, vertical = 4.dp))
                            androidx.compose.material3.DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text("Save all to phone storage") }, onClick = {
                                    more = false
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val (n, dest) = com.music.aalap.data.preferences.exportDownloads(context)
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context,
                                                if (n > 0) "Saved $n song${if (n == 1) "" else "s"} to $dest" else dest,
                                                android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                })
                                androidx.compose.material3.DropdownMenuItem(text = { Text("Remove all downloads", color = Color(0xFFE57373)) }, onClick = {
                                    more = false; confirmClear = true
                                })
                            }
                        }
                    }
                    // Search your downloads.
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search downloads", color = com.music.aalap.ui.theme.TextMuted) },
                        trailingIcon = if (query.isNotEmpty()) ({
                            Text("✕", color = com.music.aalap.ui.theme.TextMuted, modifier = Modifier.clickable { query = "" }.padding(12.dp))
                        }) else null,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    // Filters and order.
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        items(DownloadsView.Show.entries.size) { i ->
                            val v = DownloadsView.Show.entries[i]
                            DlChip(v.label, show == v) { show = v }
                        }
                        item { Spacer(Modifier.width(6.dp)) }
                        items(DownloadsView.Sort.entries.size) { i ->
                            val v = DownloadsView.Sort.entries[i]
                            DlChip(v.label, sort == v && query.isBlank()) { sort = v }
                        }
                    }
                }

                // ── In-progress downloads (with live progress bar) ──
                if (inProgress.isNotEmpty()) {
                    androidx.compose.material3.TextButton(
                        onClick = { com.music.aalap.di.Downloads.cancelAll() },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text("Cancel all downloads (${inProgress.size})", color = com.music.aalap.ui.theme.TextPrimary) }
                }
                inProgress.forEach { (song, pct) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp),
                    ) {
                        GlideImage(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            model = song.coverUri,
                            failure = placeholder(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = "",
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(text = song.title, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (pct.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accent,
                                trackColor = com.music.aalap.ui.theme.SurfaceHigh,
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                if (songs.isEmpty() && inProgress.isEmpty()) {
                    Text(
                        text = "No downloads yet. Tap ⋯ on a track and choose Download.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    if (shown.isEmpty()) Text(
                        text = if (query.isNotBlank()) "No downloads match \"$query\"." else "No liked songs among your downloads yet.",
                        color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                    )
                    repeat(shown.size) { index ->
                        com.music.aalap.ui.components.SwipeToQueue(shown[index]) {
                        val song = shown[index]

                        com.music.aalap.ui.components.SongRow(
                            song = song,
                            playing = song.id == playerViewModel.currentSongId.value,
                            // A tap plays from here through the list as shown (all downloaded, so it works offline).
                            onClick = { playerViewModel.playAll(shown, "Downloaded", context, index) },
                            onMenu = { menuSong = song },
                        )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(com.music.aalap.ui.components.BottomClearance))
            }
        }
    }
}

@Composable
private fun DlChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) com.music.aalap.ui.theme.OnSaffron else com.music.aalap.ui.theme.TextPrimary,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (on) AppPalette else com.music.aalap.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

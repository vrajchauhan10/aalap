package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Discover
import com.music.spotui.data.api.Mood
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A mood mix: Spotify's mood playlists, your favourite artists first, refreshed daily. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MoodScreen(navController: NavController, moodName: String) {
    val context = LocalContext.current
    val mood = remember(moodName) { runCatching { Mood.valueOf(moodName) }.getOrDefault(Mood.HAPPY) }
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val songs by produceState<List<SongsModel>?>(initialValue = null, mood) {
        value = withContext(Dispatchers.IO) { Discover.moodMix(context, mood) }
    }
    val yourArtists = remember { Discover.topArtists(context).map { it.lowercase() }.toSet() }
    val top = Color(mood.colors.first)
    val bottom = Color(mood.colors.second)

    fun play(list: List<SongsModel>, index: Int) {
        if (list.isEmpty()) return
        playerViewModel.updateQueue(list)
        playerViewModel.playSongAt(list, index, context)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(top, bottom, AppBackground)))
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White,
                    modifier = Modifier.size(26.dp).clickable { navController.navigateUp() },
                )
                Spacer(Modifier.height(36.dp))
                Text("MOOD", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(mood.label, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Text(mood.blurb, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                Text("Picked from what listeners file under ${mood.label.lowercase()}, your artists first. New mix every day.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                val list = songs.orEmpty()
                if (list.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                        MoodButton("Play", filled = true) { play(list, 0) }
                        MoodButton("Shuffle") { play(list.shuffled(), 0) }
                    }
                }
            }
        }
        val list = songs
        when {
            list == null -> item { com.music.spotui.ui.components.SongListSkeleton() }
            list.isEmpty() -> item {
                Text("Couldn't load this mood right now. Pull down on Home to refresh.",
                    color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            }
            else -> items(list.size) { i ->
                val song = list[i]
                val yours = song.singer.lowercase().split(",").any { it.trim() in yourArtists }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { play(list, i) }
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                ) {
                    GlideImage(
                        model = song.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(song.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Same tags as everywhere: Trending, Top hit, Liked, Your taste.
                    com.music.spotui.ui.components.SongTasteTag(song, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MoodButton(label: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Color(0xFF1A0E05) else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

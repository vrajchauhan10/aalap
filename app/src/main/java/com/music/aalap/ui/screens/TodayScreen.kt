package com.music.aalap.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.music.aalap.R
import com.music.aalap.data.recommendation.HomeMixes
import com.music.aalap.ui.components.AalapLoader
import com.music.aalap.ui.components.ReasonTag
import com.music.aalap.ui.components.TodayPlaylistSection
import com.music.aalap.ui.components.reasonColor
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import com.music.aalap.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aaj ka Aalap: today's 30 with why each song is there, Play and Shuffle. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TodayScreen(navController: NavController) {
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    val songs by produceState(HomeMixes.todayCached()) {
        if (value.isEmpty()) value = withContext(Dispatchers.IO) { runCatching { HomeMixes.todayPlaylist(context) }.getOrDefault(emptyList()) }
    }
    val list = songs.map { it.song }
    fun play(i: Int) { if (list.isNotEmpty()) { player.updateQueue(list); player.playSongAt(list, i, context) } }
    val counts = songs.groupingBy { it.reason }.eachCount()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
    ) {
        item {
            Row(Modifier.padding(16.dp, 12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(26.dp).clickable { navController.navigateUp() })
            }
            TodayPlaylistSection(songs, action = "Play") { play(0) }
            // What today's list is made of.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                listOf("Trending", "Top hit", "Your taste", "Liked").forEach { r ->
                    Text(
                        "${counts[r] ?: 0} ${if (r == "Liked") "liked" else r.lowercase()}", color = reasonColor(r), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(reasonColor(r).copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 8.dp)) {
                Text("Play", color = OnSaffron, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron).clickable { play(0) }.padding(horizontal = 28.dp, vertical = 10.dp))
                Text("Shuffle", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(SurfaceHigh).clickable {
                        if (list.isNotEmpty()) { val sh = list.shuffled(); player.updateQueue(sh); player.playSongAt(sh, 0, context) }
                    }.padding(horizontal = 24.dp, vertical = 10.dp))
            }
            if (songs.isEmpty()) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { AalapLoader() }
        }
        itemsIndexed(songs, key = { _, t -> t.song.id }) { i, t ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { play(i) }.padding(horizontal = 20.dp, vertical = 7.dp),
            ) {
                Text("${i + 1}", color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(26.dp))
                GlideImage(
                    model = t.song.coverUri, contentDescription = t.song.title, contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.song.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.song.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                com.music.aalap.ui.components.SongTags(t)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

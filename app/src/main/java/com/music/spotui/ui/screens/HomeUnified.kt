package com.music.spotui.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.recommendation.HomeMixes
import com.music.spotui.ui.components.AalapLoader
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Section title in the Aalap style, with an optional line under it. */
@Composable
fun HomeHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(Saffron))
            Spacer(Modifier.width(10.dp))
            Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp))
    }
}

/**
 * Quick picks: songs in pages of four rows, swipe for more (YouTube Music's own
 * suggestions for you, mixed with the engine's, ordered for your taste).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun QuickPicksSection(picks: List<HomeMixes.TodaySong>) {
    if (picks.isEmpty()) return
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    HomeHeader("Quick picks")
    val pages = picks.chunked(4)
    val pager = rememberPagerState { pages.size }
    HorizontalPager(state = pager, contentPadding = PaddingValues(start = 20.dp, end = 48.dp), pageSpacing = 12.dp) { p ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pages[p].forEach { t ->
                val s = t.song
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { player.playWithRadio(s, context) },
                ) {
                    GlideImage(
                        model = s.coverUri, contentDescription = s.title, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Why it's here: Trending, Top hit, Liked, Your taste.
                    if (t.reason != "For you") com.music.spotui.ui.components.ReasonTag(t.reason, com.music.spotui.ui.components.reasonColor(t.reason))
                }
            }
        }
    }
}

/** Made for you: Daily Mixes, each a 2×2 cover collage. Tap plays the mix. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MadeForYouSection(mixes: List<HomeMixes.Mix>) {
    if (mixes.isEmpty()) return
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    HomeHeader("Made for you", "Daily Mixes of your top artists · new every day")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(mixes.size) { i ->
            val m = mixes[i]
            Column(Modifier.width(150.dp).clickable { player.updateQueue(m.songs); player.playSongAt(m.songs, 0, context) }) {
                Box(Modifier.size(150.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceHigh)) {
                    Collage(m.covers, 150)
                    Text(
                        m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(m.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(m.subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Collage(covers: List<String>, size: Int) {
    if (covers.size >= 4) {
        Column {
            covers.take(4).chunked(2).forEach { row ->
                Row { row.forEach { url ->
                    GlideImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size((size / 2).dp))
                } }
            }
        }
    } else {
        GlideImage(model = covers.firstOrNull(), contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.fillMaxSize())
    }
}

/** Radio for your top artists (from every service). Tap starts the artist's radio. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistRadiosSection(artists: List<ArtistsModel>) {
    if (artists.isEmpty()) return
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<String?>(null) }
    HomeHeader("Your artist radios", "Their best songs, then music like them")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(artists.size) { i ->
            val a = artists[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(112.dp).clickable {
                    if (loading != null) return@clickable
                    loading = a.name
                    scope.launch {
                        val songs = withContext(Dispatchers.IO) { runCatching { HomeMixes.artistRadio(context, a) }.getOrDefault(emptyList()) }
                        loading = null
                        if (songs.isNotEmpty()) { player.updateQueue(songs); player.playSongAt(songs, 0, context) }
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GlideImage(
                        model = a.coverUri, contentDescription = a.name, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(112.dp).clip(CircleShape).background(SurfaceHigh),
                    )
                    if (loading == a.name) Box(Modifier.size(112.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { AalapLoader(size = 36.dp) }
                    Text(
                        "RADIO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Saffron)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(a.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** New releases from the artists you follow and play most. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun NewReleasesSection(navController: NavController, releases: List<LibraryEntry>) {
    if (releases.isEmpty()) return
    HomeHeader("New from your artists")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(releases.size) { i ->
            val e = releases[i]
            Column(Modifier.width(140.dp).clickable { navController.navigate(albumRoute(e.name, e.artists)) }) {
                GlideImage(
                    model = e.coverUri, contentDescription = e.name, contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(e.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

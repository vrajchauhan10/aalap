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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.music.aalap.data.entity.PodcastModel
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.local.Podcasts
import com.music.aalap.ui.navigation.showRoute
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** Plays a podcast episode on its own (it continues where you stopped). */
fun playEpisode(player: com.music.aalap.ui.viewmodel.PlayerViewModel, ep: SongsModel, context: android.content.Context) {
    player.updateQueue(listOf(ep))
    com.music.aalap.di.SongPlayer.playSong(ep.url, context)
    player.updateSongState(ep.coverUri, ep.title, ep.singer, true, ep.id, 0, ep.album)
}

/**
 * The podcasts page (Search → Podcasts, or "See all" on Home): its own search for shows and
 * episodes, episodes you're partway through, and today's popular shows.
 */
@Composable
fun PodcastsScreen(navController: NavController) {
    val context = LocalContext.current
    val player: com.music.aalap.ui.viewmodel.PlayerViewModel = hiltViewModel()
    var query by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf<List<PodcastModel>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<SongsModel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var popular by remember { mutableStateOf(Podcasts.popularCached(context)) }
    val cont = remember { Podcasts.continueListening(context) }
    val mine = remember { Podcasts.followed(context) }
    var fresh by remember { mutableStateOf<List<SongsModel>>(emptyList()) }
    LaunchedEffect(Unit) { if (mine.isNotEmpty()) fresh = runCatching { Podcasts.newFromFollowed(context) }.getOrDefault(emptyList()) }
    LaunchedEffect(Unit) { popular = runCatching { Podcasts.popularToday(context, 10) }.getOrDefault(popular) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { shows = emptyList(); episodes = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300) // wait for a pause in typing
        val api = com.music.aalap.data.api.Api(context)
        shows = runCatching { api.searchShows(q, 10) }.getOrDefault(emptyList())
        episodes = runCatching { api.searchEpisodes(q, 10) }.getOrDefault(emptyList())
        searching = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = com.music.aalap.ui.components.BottomClearance),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp, 12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
                    modifier = Modifier.size(26.dp).clickable { navController.popBackStack() })
                Spacer(Modifier.width(14.dp))
                Text("Podcasts", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search podcasts and episodes", color = TextMuted) },
                trailingIcon = if (query.isNotEmpty()) ({ Text("✕", color = TextMuted, modifier = Modifier.clickable { query = "" }.padding(12.dp)) }) else null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (query.trim().length >= 2) {
            if (searching && shows.isEmpty() && episodes.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { com.music.aalap.ui.components.AalapLoader() } }
            if (!searching && shows.isEmpty() && episodes.isEmpty()) item {
                Text("No podcasts found for \"${query.trim()}\".", color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            }
            if (shows.isNotEmpty()) {
                item { SectionLabel("Podcasts") }
                items(shows.size) { i -> SearchShowRow(shows[i]) { navController.navigate(showRoute(shows[i].id, shows[i].name)) } }
            }
            if (episodes.isNotEmpty()) {
                item { SectionLabel("Episodes") }
                items(episodes.size) { i -> EpisodeRow(episodes[i], null) { playEpisode(player, episodes[i], context) } }
            }
        } else {
            // Your podcasts: the shows you follow, and a way to choose more.
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 16.dp)) {
                    Box(Modifier.weight(1f)) { SectionLabel("Your podcasts") }
                    Text(if (mine.isEmpty()) "Choose" else "Edit", color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { navController.navigate(com.music.aalap.ui.navigation.Routes.PodcastPicker.route) }.padding(8.dp))
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    item {
                        Column(Modifier.width(96.dp).clickable { navController.navigate(com.music.aalap.ui.navigation.Routes.PodcastPicker.route) }) {
                            Box(contentAlignment = Alignment.Center,
                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)).background(Saffron.copy(alpha = 0.14f))) {
                                Text("+", color = Saffron, fontSize = 32.sp)
                            }
                            Text("Add podcasts", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    items(mine.size) { i -> ShowTile(mine[i]) { navController.navigate(showRoute(mine[i].id, mine[i].name)) } }
                }
            }
            if (fresh.isNotEmpty()) {
                item { SectionLabel("New from your podcasts") }
                items(fresh.size) { i -> EpisodeRow(fresh[i], null) { playEpisode(player, fresh[i], context) } }
            }
            if (cont.isNotEmpty()) {
                item { SectionLabel("Continue listening") }
                items(cont.size) { i -> EpisodeRow(cont[i].episode, cont[i]) { playEpisode(player, cont[i].episode, context) } }
            }
            if (popular.isNotEmpty()) {
                item { SectionLabel("Popular today") }
                items(popular.size) { i ->
                    val sh = popular[i]
                    RankedShowRow(i + 1, sh) { navController.navigate(showRoute(sh.id, sh.name)) }
                }
            } else item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { com.music.aalap.ui.components.AalapLoader() } }
        }
    }
}

@Composable
private fun SectionLabel(t: String) =
    Text(t, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 6.dp))

/** An episode, with how far you got when you've started it. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun EpisodeRow(ep: SongsModel, progress: Podcasts.Progress?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp),
    ) {
        GlideImage(model = ep.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(ep.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Episode • ${ep.singer}", color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null && progress.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { progress.fraction }, color = Saffron, trackColor = SurfaceHigh,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
                Text("${(progress.durationMs - progress.positionMs) / 60_000} min left", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RankedShowRow(rank: Int, show: PodcastModel, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp),
    ) {
        Text("$rank", color = if (rank <= 3) Saffron else TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
        GlideImage(model = show.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(show.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Podcast" + if (show.publisher.isNotBlank()) " • ${show.publisher}" else "", color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Home: today's top 5 podcasts, with "See all" opening the podcasts page. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PodcastsHomeSection(navController: NavController) {
    val context = LocalContext.current
    var shows by remember { mutableStateOf(Podcasts.popularCached(context).take(5)) }
    LaunchedEffect(Unit) { runCatching { Podcasts.popularToday(context, 5) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { shows = it } }
    if (shows.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 20.dp)) {
            Box(Modifier.weight(1f)) { HomeHeader("Popular podcasts today") }
            Text("See all", color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { navController.navigate(com.music.aalap.ui.navigation.Routes.Podcasts.route) }.padding(8.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(shows.size) { i ->
                val sh = shows[i]
                Column(Modifier.width(140.dp).clickable { navController.navigate(showRoute(sh.id, sh.name)) }) {
                    Box {
                        GlideImage(model = sh.coverUri, contentDescription = sh.name, contentScale = ContentScale.Crop,
                            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)))
                        Text("#${i + 1}", color = com.music.aalap.ui.theme.OnSaffron, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Saffron).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(sh.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sh.publisher, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ShowTile(show: PodcastModel, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick)) {
        GlideImage(model = show.coverUri, contentDescription = show.name, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
        Text(show.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * History's podcast part (only when you've listened to podcasts this month): the last episode you
 * didn't finish, to continue where you stopped, and the shows you listen to most.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PodcastHistoryBlock(navController: NavController) {
    val context = LocalContext.current
    val player: com.music.aalap.ui.viewmodel.PlayerViewModel = hiltViewModel()
    val last = remember { Podcasts.continueListening(context, 1).firstOrNull() }
    val top = remember { Podcasts.topShows(context, 5) }
    if (last == null && top.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        Text("Podcasts", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 8.dp))
        last?.let { pr ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(SurfaceHigh).clickable { playEpisode(player, pr.episode, context) }.padding(10.dp),
            ) {
                GlideImage(model = pr.episode.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("CONTINUE LISTENING", color = Saffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(pr.episode.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { pr.fraction }, color = Saffron, trackColor = AppBackground,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)))
                    if (pr.durationMs > 0) Text("${(pr.durationMs - pr.positionMs) / 60_000} min left", color = TextMuted, fontSize = 11.sp)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Saffron)) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.play_svgrepo_com), "Continue", tint = com.music.aalap.ui.theme.OnSaffron, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (top.isNotEmpty()) {
            Text("Your top podcasts", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp))
            top.forEachIndexed { i, t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        // The show's page when we know it; otherwise continue its latest episode.
                        if (t.showId.isNotBlank()) navController.navigate(showRoute(t.showId, t.name)) else playEpisode(player, t.latest.episode, context)
                    }.padding(16.dp, 6.dp),
                ) {
                    Text("${i + 1}", color = if (i < 3) Saffron else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    GlideImage(model = t.cover, contentDescription = null, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(t.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.minutes} min · ${t.episodes} episode${if (t.episodes == 1) "" else "s"} this month", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

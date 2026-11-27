package com.music.aalap.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
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
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.entity.PodcastModel
import com.music.aalap.data.local.Podcasts
import com.music.aalap.ui.navigation.showRoute
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * "Your podcasts": follow the shows you like (like choosing your artists). Their new episodes
 * show on the Podcasts page; a tap on a cover opens the show.
 */
@Composable
fun PodcastPickerScreen(navController: NavController) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastModel>>(emptyList()) }
    var popular by remember { mutableStateOf(Podcasts.popularCached(context)) }
    var followed by remember { mutableStateOf(Podcasts.followed(context)) }
    // Shown in the "Following" row even after unfollowing, until you leave (so a mis-tap is easy to undo).
    val followingAtStart = remember { followed }
    LaunchedEffect(Unit) { popular = runCatching { Podcasts.popularToday(context, 20) }.getOrDefault(popular) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = runCatching { com.music.aalap.data.api.Api(context).searchShows(query.trim(), 18) }.getOrDefault(emptyList())
    }
    fun toggle(s: PodcastModel) {
        val on = followed.none { it.id == s.id }
        Podcasts.setFollowed(context, s, on)
        followed = Podcasts.followed(context)
    }

    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp, 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
                modifier = Modifier.size(26.dp).clickable { navController.navigateUp() })
            Spacer(Modifier.weight(1f))
            Text("${followed.size} following", color = Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Text("Your podcasts", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Text("Follow the shows you like. Their new episodes show up on the Podcasts page.",
            color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search any podcast", color = TextMuted) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fun header(t: String) = item(span = { GridItemSpan(3) }) {
                Text(t, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            if (query.trim().length >= 2) {
                header("Results")
                items(results, key = { "r:" + it.id }) { s -> ShowCell(s, followed.any { it.id == s.id }, { navController.navigate(showRoute(s.id, s.name)) }) { toggle(s) } }
            } else {
                val mine = (followingAtStart + followed).distinctBy { it.id }
                if (mine.isNotEmpty()) {
                    header("Following")
                    items(mine, key = { "f:" + it.id }) { s -> ShowCell(s, followed.any { it.id == s.id }, { navController.navigate(showRoute(s.id, s.name)) }) { toggle(s) } }
                }
                val more = popular.filter { p -> mine.none { it.id == p.id } }
                if (more.isNotEmpty()) {
                    header("Popular today")
                    items(more, key = { "p:" + it.id }) { s -> ShowCell(s, followed.any { it.id == s.id }, { navController.navigate(showRoute(s.id, s.name)) }) { toggle(s) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ShowCell(show: PodcastModel, following: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlideImage(model = show.coverUri, contentDescription = show.name, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen))
        Spacer(Modifier.height(6.dp))
        Text(show.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                .background(if (following) Saffron else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, Saffron, RoundedCornerShape(50))
                .clickable(onClick = onToggle).padding(vertical = 6.dp),
        ) {
            Text(if (following) "Following" else "Follow", color = if (following) com.music.aalap.ui.theme.OnSaffron else Saffron,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

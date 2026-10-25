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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.api.ArtistPicker
import com.music.aalap.data.entity.ArtistsModel
import com.music.aalap.data.recommendation.engine.SongMatcher
import com.music.aalap.ui.components.AalapLoader
import com.music.aalap.ui.navigation.artistRoute
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Your artists": follow anyone. Every artist has a clear Follow / Following button;
 * the photo opens the artist. Following someone slides in three similar artists right
 * after them (like Spotify), so you can keep going. Suggestions come from your own
 * listening (artists you play a lot, with your play count) and from fans-also-like.
 */
@OptIn(ExperimentalGlideComposeApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ArtistPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<ArtistPicker.Pick>() }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val fromListening = remember { mutableStateListOf<ArtistPicker.Pick>() }
    val similar = remember { mutableStateListOf<ArtistPicker.Pick>() }
    val following = remember { mutableStateListOf<ArtistsModel>() }
    var followedKeys by remember { mutableStateOf(emptySet<String>()) }
    // Artists that just slid in after a follow (briefly highlighted).
    var fresh by remember { mutableStateOf(emptySet<String>()) }
    // Never blank (a blank key twice in one list crashed the screen): the name itself then.
    fun key(name: String) = SongMatcher.artistKeys(name).firstOrNull()?.takeIf { it.isNotBlank() } ?: name.trim().lowercase()
    // A place in the grid: the Spotify id (two artists can share a name, e.g. two "Arijit"s,
    // and the same key twice crashed the screen), the name when there's no id.
    fun slot(a: ArtistsModel) = a.id.ifBlank { key(a.name) }
    val trending = remember { mutableStateListOf<ArtistPicker.Pick>() }
    // Unfollowed in "Following": fades and shrinks away, then leaves the list.
    var leaving by remember { mutableStateOf(emptySet<String>()) }

    // Trending now: loads alongside your own lists (it doesn't wait for them).
    LaunchedEffect(Unit) {
        val hot = withContext(Dispatchers.IO) {
            runCatching { ArtistPicker.trending(context, emptySet(), limit = 24) }
                .onFailure { android.util.Log.w("ArtistPicker", "trending failed", it) }.getOrDefault(emptyList())
        }
        trending.addAll(hot)
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val f = ArtistPicker.following(context)
            withContext(Dispatchers.Main) { following.addAll(f.distinctBy { slot(it) }); followedKeys = f.mapTo(HashSet()) { key(it.name) } }
            val mine = runCatching { ArtistPicker.fromYourListening(context) }
                .onFailure { android.util.Log.w("ArtistPicker", "top artists failed", it) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { fromListening.addAll(mine) }
            val sim = runCatching { ArtistPicker.similar(context, mine.mapTo(HashSet()) { key(it.artist.name) }) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { similar.addAll(sim); loading = false }
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { results.clear(); return@LaunchedEffect }
        delay(300)
        searching = true
        val found = withContext(Dispatchers.IO) { runCatching { ArtistPicker.search(context, query) }.getOrDefault(emptyList()) }
        // Each artist once: the same artist twice (two listings) crashed the grid.
        results.clear(); results.addAll(found.distinctBy { slot(it) }.map { ArtistPicker.Pick(it, "Artist") })
        searching = false
    }

    /** Follow/unfollow; on follow, three similar artists slide in right after it in [list]. */
    fun toggle(artist: ArtistsModel, list: androidx.compose.runtime.snapshots.SnapshotStateList<ArtistPicker.Pick>?) {
        val k = key(artist.name)
        val follow = k !in followedKeys
        followedKeys = if (follow) followedKeys + k else followedKeys - k
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { ArtistPicker.setFollowed(context, artist, follow) }
            if (follow) {
                if (following.none { key(it.name) == k }) following.add(0, resolved)
                if (list != null) {
                    val shown = (fromListening + similar + trending + results).mapTo(HashSet()) { key(it.artist.name) } + followedKeys
                    val more = withContext(Dispatchers.IO) { runCatching { ArtistPicker.relatedTo(context, resolved, shown) }.getOrDefault(emptyList()) }
                        .distinctBy { key(it.artist.name) }.filter { m -> list.none { key(it.artist.name) == key(m.artist.name) } }
                    val at = list.indexOfFirst { key(it.artist.name) == k }
                    if (at >= 0 && more.isNotEmpty()) {
                        list.addAll(at + 1, more)
                        fresh = more.mapTo(HashSet()) { key(it.artist.name) }
                        delay(2500); fresh = emptySet()
                    }
                }
            } else {
                leaving = leaving + k
                delay(320)
                following.removeAll { key(it.name) == k }
                leaving = leaving - k
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp, 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
                modifier = Modifier.size(26.dp).clickable { navController.navigateUp() })
            Spacer(Modifier.weight(1f))
            Text(
                "${followedKeys.size} following", color = Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text("Your artists", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Tap Follow on artists you love. Aalap Radio and your recommendations follow along.",
            color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
        )
        AalapSearchBar(query, onFocusChange = {}, placeholder = "Search any artist") { query = it }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (query.isNotBlank()) {
                if (searching && results.isEmpty()) item(span = { GridItemSpan(3) }) { CenterLoader() }
                items(results, key = { "s:" + slot(it.artist) }) { p ->
                    ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys, key(p.artist.name) in fresh,
                        Modifier.animateItemPlacement(), onOpen = { navController.navigate(artistRoute(p.artist.name, p.artist.id)) }) { toggle(p.artist, results) }
                }
            } else {
                if (fromListening.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Your top artists", "From what you play the most") }
                    items(fromListening, key = { "m:" + slot(it.artist) }) { p ->
                        ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys, key(p.artist.name) in fresh,
                            Modifier.animateItemPlacement(), onOpen = { navController.navigate(artistRoute(p.artist.name, p.artist.id)) }) { toggle(p.artist, fromListening) }
                    }
                }
                if (trending.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Trending now", "Most heard in India this week") }
                    items(trending, key = { "t:" + slot(it.artist) }) { p ->
                        ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys, key(p.artist.name) in fresh,
                            Modifier.animateItemPlacement(), onOpen = { navController.navigate(artistRoute(p.artist.name, p.artist.id)) }) { toggle(p.artist, trending) }
                    }
                }
                if (similar.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Fans also like", "Similar to the artists you love") }
                    items(similar, key = { "r:" + slot(it.artist) }) { p ->
                        ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys, key(p.artist.name) in fresh,
                            Modifier.animateItemPlacement(), onOpen = { navController.navigate(artistRoute(p.artist.name, p.artist.id)) }) { toggle(p.artist, similar) }
                    }
                }
                if (loading) item(span = { GridItemSpan(3) }) { CenterLoader() }
                if (following.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Following", "Everyone you follow on Spotify, YouTube Music and Aalap") }
                    items(following, key = { "f:" + slot(it) }) { a ->
                        val gone by androidx.compose.animation.core.animateFloatAsState(if (key(a.name) in leaving) 0f else 1f, androidx.compose.animation.core.tween(300), label = "leave")
                        ArtistCell(a, null, key(a.name) in followedKeys, false,
                            Modifier.animateItemPlacement().graphicsLayer { alpha = gone; scaleX = 0.7f + 0.3f * gone; scaleY = 0.7f + 0.3f * gone }, onOpen = { navController.navigate(artistRoute(a.name, a.id)) }) { toggle(a, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(top = 20.dp, start = 6.dp, bottom = 2.dp)) {
        Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CenterLoader() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { AalapLoader() }
}

/** An artist: photo (opens the artist), name, why it's here, and a clear Follow / Following button. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistCell(
    artist: ArtistsModel,
    reason: String?,
    followed: Boolean,
    justAdded: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(if (justAdded) Saffron.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(Modifier.clickable(onClick = onOpen)) {
            GlideImage(
                model = artist.coverUri, contentDescription = artist.name, contentScale = ContentScale.Crop,
                loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                modifier = Modifier.size(88.dp).clip(CircleShape).background(SurfaceHigh),
            )
            if (followed) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Saffron),
                ) { Text("✓", color = OnSaffron, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(artist.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(reason ?: "Artist", color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (followed) "Following" else "Follow",
            color = if (followed) OnSaffron else Saffron,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(if (followed) Saffron else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, Saffron, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

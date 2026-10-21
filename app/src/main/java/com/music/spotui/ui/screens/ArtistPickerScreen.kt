package com.music.spotui.ui.screens

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
import com.music.spotui.R
import com.music.spotui.data.api.ArtistPicker
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.recommendation.engine.SongMatcher
import com.music.spotui.ui.components.AalapLoader
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Your artists": follow anyone. Suggestions come from your own listening (artists
 * you play a lot but don't follow, with your play count) and from artists similar to
 * your favourites. Tap to follow or unfollow; it applies to Spotify, your Library and
 * recommendations.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistPickerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ArtistsModel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val fromListening = remember { mutableStateListOf<ArtistPicker.Pick>() }
    val similar = remember { mutableStateListOf<ArtistPicker.Pick>() }
    val following = remember { mutableStateListOf<ArtistsModel>() }
    // Keys of artists followed right now (drives the check marks).
    var followedKeys by remember { mutableStateOf(emptySet<String>()) }
    fun key(name: String) = SongMatcher.artistKeys(name).firstOrNull().orEmpty()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val f = ArtistPicker.following(context)
            withContext(Dispatchers.Main) { following.addAll(f); followedKeys = f.mapTo(HashSet()) { key(it.name) } }
            val mine = runCatching { ArtistPicker.fromYourListening(context) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { fromListening.addAll(mine) }
            val sim = runCatching { ArtistPicker.similar(context, mine.mapTo(HashSet()) { key(it.artist.name) }) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { similar.addAll(sim); loading = false }
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        searching = true
        results = withContext(Dispatchers.IO) { runCatching { ArtistPicker.search(context, query) }.getOrDefault(emptyList()) }
        searching = false
    }

    fun toggle(artist: ArtistsModel) {
        val follow = key(artist.name) !in followedKeys
        followedKeys = if (follow) followedKeys + key(artist.name) else followedKeys - key(artist.name)
        scope.launch {
            val resolved = withContext(Dispatchers.IO) { ArtistPicker.setFollowed(context, artist, follow) }
            if (follow) { if (following.none { key(it.name) == key(resolved.name) }) following.add(0, resolved) }
            else following.removeAll { key(it.name) == key(artist.name) }
        }
    }

    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp, 12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
                modifier = Modifier.size(26.dp).clickable { navController.navigateUp() })
        }
        Text("Your artists", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Follow the artists you love. Aalap Radio and your recommendations follow along.",
            color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
        )
        AalapSearchBar(query, onFocusChange = {}, placeholder = "Search any artist") { query = it }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (query.isNotBlank()) {
                if (searching && results.isEmpty()) item(span = { GridItemSpan(3) }) { CenterLoader() }
                items(results, key = { "s:" + it.id.ifBlank { it.name } }) { a ->
                    ArtistCell(a, null, key(a.name) in followedKeys) { toggle(a) }
                }
            } else {
                if (fromListening.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Suggested from your listening", "Artists you play a lot but don't follow yet") }
                    items(fromListening, key = { "m:" + it.artist.name }) { p ->
                        ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys) { toggle(p.artist) }
                    }
                }
                if (similar.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("You might also like", "Similar to your favourite artists") }
                    items(similar, key = { "r:" + it.artist.name }) { p ->
                        ArtistCell(p.artist, p.reason, key(p.artist.name) in followedKeys) { toggle(p.artist) }
                    }
                }
                if (loading) item(span = { GridItemSpan(3) }) { CenterLoader() }
                if (following.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) { Header("Following · ${following.size}", "Tap to unfollow") }
                    items(following, key = { "f:" + it.name }) { a ->
                        ArtistCell(a, null, key(a.name) in followedKeys) { toggle(a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, start = 4.dp)) {
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun CenterLoader() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { AalapLoader() }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistCell(artist: ArtistsModel, reason: String?, followed: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box {
            GlideImage(
                model = artist.coverUri, contentDescription = artist.name, contentScale = ContentScale.Crop,
                loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SurfaceHigh)
                    .border(if (followed) 3.dp else 0.dp, if (followed) Saffron else SurfaceHigh, CircleShape),
            )
            if (followed) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(Saffron),
                ) { Text("✓", color = OnSaffron, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(artist.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (reason != null) Text(reason, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

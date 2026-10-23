package com.music.aalap.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.api.Mood
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.AalapRadio
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronDeep
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import com.music.aalap.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CARD = 148.dp

/**
 * Aalap Radio on Home: one calm row of stations. "Your Mix" (your favourites and new
 * finds, shown with your own album covers) comes first, then mood stations ordered
 * for you and the time of day. Tap a station and it starts playing; the engine keeps
 * it going after it ends.
 */
@Composable
fun AalapRadioSection() {
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf<String?>(null) }

    val artists by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { AalapRadio.topArtists(context, 2) } }
    val covers by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { AalapRadio.mixCovers(context) } }
    val moods = remember { AalapRadio.moodOrder(context).take(6) }

    fun start(key: String, build: suspend () -> List<SongsModel>) {
        if (loading != null) return
        loading = key
        scope.launch {
            val songs = runCatching { withContext(Dispatchers.IO) { build() } }.getOrDefault(emptyList())
            loading = null
            if (songs.isEmpty()) {
                Toast.makeText(context, "Couldn't start this station. Try again in a moment.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            player.updateQueue(songs)
            player.playSongAt(songs, 0, context)
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp),
        ) {
            Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(Saffron))
            Spacer(Modifier.width(10.dp))
            Text("Mood stations", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Each mood ordered for your taste · tap to play",
            color = TextMuted, fontSize = 12.sp,
            modifier = Modifier.padding(start = 34.dp, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            // Your Mix lives in Made for you (Daily Mixes); this row is the mood stations.
            items(moods.size) { i ->
                val mood = moods[i]
                Station(
                    title = "${mood.label} Radio",
                    subtitle = mood.blurb,
                    loading = loading == mood.name,
                    onClick = { start(mood.name) { AalapRadio.moodRadio(context, mood) } },
                ) { MoodArt(mood) }
            }
        }
    }
}

@Composable
private fun Station(
    title: String,
    subtitle: String,
    loading: Boolean,
    onClick: () -> Unit,
    art: @Composable () -> Unit,
) {
    Column(Modifier.width(CARD).clickable(onClick = onClick)) {
        Box(Modifier.size(CARD).clip(RoundedCornerShape(18.dp)).background(SurfaceHigh)) {
            art()
            if (loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    AalapLoader(size = 40.dp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
    }
}

/** Your Mix artwork: a 2×2 grid of your favourites' covers (one cover, or colours, when there are fewer). */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun CoverCollage(covers: List<String>) {
    when {
        covers.size >= 4 -> {
            val half: Dp = CARD / 2
            Column {
                covers.take(4).chunked(2).forEach { row ->
                    Row {
                        row.forEach { url ->
                            GlideImage(
                                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                                loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                                modifier = Modifier.size(half),
                            )
                        }
                    }
                }
            }
        }
        covers.isNotEmpty() -> GlideImage(
            model = covers.first(), contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.fillMaxSize(),
        )
        else -> Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Saffron, SaffronDeep, Color(0xFF3A1F6B)))).padding(14.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text("Your Mix", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("RADIO", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
    }
}

/** A mood station's artwork: its colours and name, nothing else. */
@Composable
private fun MoodArt(mood: Mood) {
    // Each mood has its own hand-drawn craft: rangoli, paisley, bandhani, mirror work…
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        val craft = mood.craft()
        CraftCard(craft, Color(mood.colors.second), Modifier.fillMaxSize(), ink = craftInk(Color(mood.colors.first)))
        Column(Modifier.padding(14.dp)) {
            Text(mood.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${craft.label.uppercase()} · RADIO", color = craftInk(Color(mood.colors.first)), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1)
        }
    }
}

package com.music.spotui.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.recommendation.HomeMixes
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import com.music.spotui.ui.viewmodel.PlayerViewModel

private val Indigo = Color(0xFF2A1A55)
private val SaffronInk = Color(0xFFE0661B)
private val Marigold = Color(0xFFFFC56B)
private val Cream = Color(0xFFFFF3E0)

/** Each reason's colour on the song tags: deeper shades on the light theme so they read on cream. */
fun reasonColor(reason: String, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight): Color {
    val light = !onDark
    return when (reason) {
        "Your taste" -> if (light) Color(0xFFC2550F) else Color(0xFFFF8A3D)
        "Trending" -> if (light) Color(0xFFB8235A) else Color(0xFFE5397A)
        "Top hit" -> if (light) Color(0xFF3448B8) else Color(0xFF6E8BFF)
        else -> if (light) Color(0xFF8A5A00) else Marigold // Liked
    }
}

/**
 * Aaj ka Aalap: today's 30, on a hand-drawn poster (marigold mandala, paisley curls,
 * block-print border), then the songs with why each one is there.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun TodayPlaylistSection(songs: List<HomeMixes.TodaySong>, action: String = "Open", onOpen: () -> Unit) {
    val context = LocalContext.current
    val updated = HomeMixes.todayUpdatedAt(context).takeIf { it > 0 }
        ?.let { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)) }

    // The day, kept current: it ticks over at midnight while the app is open, and is
    // re-read whenever the app comes back, so the poster changes with the date.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val day by androidx.compose.runtime.produceState(java.time.LocalDate.now().toEpochDay(), lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                value = java.time.LocalDate.now().toEpochDay()
                val now = java.time.LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                kotlinx.coroutines.delay(java.time.Duration.between(now, midnight).toMillis() + 1_000)
            }
        }
    }
    // A design picked with the shuffle button stays for the rest of that day.
    val posterPrefs = remember { context.getSharedPreferences("home_mixes", android.content.Context.MODE_PRIVATE) }
    var shuffled by remember(day) {
        mutableStateOf(posterPrefs.getString("poster.shuffle", null)?.split(":")
            ?.takeIf { it.size == 2 && it[0] == day.toString() }?.get(1)?.toIntOrNull())
    }
    val design = shuffled?.takeIf { PosterDesignOverride.intValue < 0 }?.let { PosterDesigns.getOrNull(it) } ?: posterFor(day)

    Column(Modifier.padding(top = 14.dp)) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(min = 212.dp) // grows with large system font sizes instead of overlapping
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpen), // opens the full list of today's 30
        ) {
            // Today's handmade design (a new craft every day, from midnight).
            androidx.compose.animation.Crossfade(design, Modifier.matchParentSize(), animationSpec = androidx.compose.animation.core.tween(450), label = "poster") { d ->
                Canvas(Modifier.fillMaxSize()) { d.paint(this) }
            }
            // Shuffle: another design (never the same one twice in a row).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp)
                    .clip(CircleShape).background(Color(0xCC100B1C))
                    .clickable {
                        val current = PosterDesigns.indexOf(design)
                        val next = (PosterDesigns.indices - current).random()
                        shuffled = next
                        posterPrefs.edit().putString("poster.shuffle", "$day:$next").apply()
                    },
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_player_shuffle), contentDescription = "Shuffle design",
                    tint = Color.White, modifier = Modifier.size(16.dp),
                )
            }
            Text(
                "${design.craft} · ${design.origin}".uppercase(),
                color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, maxLines = 1,
                // Bottom right, over the art: well away from the words.
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xCC100B1C)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Column(Modifier.padding(start = 18.dp, top = 16.dp, end = 120.dp, bottom = 30.dp)) {
                Text("AALAP PLAYLIST FOR TODAY", color = Cream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, maxLines = 1)
                Text("Aaj ka Aalap", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${if (songs.isEmpty()) 30 else songs.size} songs · new every morning", color = Cream.copy(alpha = 0.9f), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Chip("Trending"); Chip("Your taste") }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { Chip("Top hits"); Chip("Your likes") }
                Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF100B1C)), contentAlignment = Alignment.Center) {
                    if (songs.isEmpty()) AalapLoader(size = 24.dp)
                    else Canvas(Modifier.size(16.dp)) {
                        drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height); close() }, Color(0xFFFF8A3D))
                    }
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(if (songs.isEmpty()) "Making today's playlist" else "$action today's ${songs.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (updated != null) Text("Updated $updated", color = Cream.copy(alpha = 0.85f), fontSize = 10.sp)
                }
            }
            }
        }

    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = Cream, fontSize = 10.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x59100B1C)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/**
 * Quick picks' light tags: the main artist, then each reason (Trending, Top hit, Your
 * taste) as small coloured text with a dot, all on one line under the title.
 */
@Composable
fun ReasonLine(t: HomeMixes.TodaySong, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight) {
    val reasons = buildList {
        if (t.reason != "Liked" && t.reason != "For you") add(t.reason)
        if (t.matches && t.reason != "Your taste") add("Your taste")
    }
    val muted = com.music.spotui.ui.theme.TextMuted
    val text = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = muted)) { append(t.song.singer.split(",").first().trim()) }
        reasons.forEach { r ->
            withStyle(androidx.compose.ui.text.SpanStyle(color = muted.copy(alpha = 0.6f))) { append("  ·  ") }
            withStyle(androidx.compose.ui.text.SpanStyle(color = reasonColor(r, onDark), fontWeight = FontWeight.Medium)) { append("● ") }
            withStyle(androidx.compose.ui.text.SpanStyle(color = reasonColor(r, onDark), fontWeight = FontWeight.Medium)) { append(r) }
        }
    }
    Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A small gold heart when the song is liked; it pops in the moment you like it. */
@Composable
fun LikedHeartIfLiked(songId: Int, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight) {
    val context = LocalContext.current
    val version by com.music.spotui.data.preferences.likedVersion.collectAsState()
    val liked = remember(version, songId) { com.music.spotui.data.preferences.isSongLiked(context, songId.toString()) }
    if (liked) LikedHeart(songId.toString(), onDark)
}

@Composable
private fun LikedHeart(songId: String, onDark: Boolean) {
    val gold = if (onDark) Color(0xFFFFC94D) else Color(0xFFB07D05)
    val justLiked = remember(songId) {
        com.music.spotui.data.preferences.lastLikedId == songId &&
            System.currentTimeMillis() - com.music.spotui.data.preferences.lastLikedAtMs < 2_000
    }
    val pop = remember(songId) { androidx.compose.animation.core.Animatable(if (justLiked) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(songId) {
        if (pop.value < 1f) {
            pop.animateTo(1.35f, androidx.compose.animation.core.tween(220))
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
        }
    }
    androidx.compose.material3.Icon(
        androidx.compose.material.icons.Icons.Filled.Favorite, contentDescription = "Liked", tint = gold,
        modifier = Modifier.size(16.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    )
}

/**
 * The Liked tag: bold gold text in a thick gold frame with a shine that glides across
 * it every couple of seconds. Liking a song pops it in (grows past full size and
 * settles); songs that were already liked just shine.
 */
@Composable
fun LikedTag(songId: String, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight) {
    val gold = if (onDark) Color(0xFFFFC94D) else Color(0xFFB07D05)
    val shine = if (onDark) Color(0xFFFFF7D9) else Color(0xFFFFE08A)
    val justLiked = remember(songId) {
        com.music.spotui.data.preferences.lastLikedId == songId &&
            System.currentTimeMillis() - com.music.spotui.data.preferences.lastLikedAtMs < 2_000
    }
    val pop = remember(songId) { androidx.compose.animation.core.Animatable(if (justLiked) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(songId) {
        if (pop.value < 1f) {
            pop.animateTo(1.3f, androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
        }
    }
    // The shine: a bright band crossing the frame, then a pause before the next pass.
    val sweep by androidx.compose.animation.core.rememberInfiniteTransition(label = "likedShine").animateFloat(
        initialValue = -0.6f, targetValue = 2.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "sweep",
    )
    Text(
        "Liked", color = gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                val band = size.width * 0.45f
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(gold, shine, gold),
                        start = Offset(x - band, 0f), end = Offset(x + band, size.height),
                    ),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    style = Stroke(stroke),
                )
            }
            .clip(RoundedCornerShape(50))
            .background(gold.copy(alpha = if (onDark) 0.14f else 0.1f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ReasonTag(text: String, color: Color, filled: Boolean = false, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight) {
    Text(
        text, color = if (filled) (if (!onDark) Color.White else Color(0xFF1A0E05)) else color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/**
 * A song's tags: why it's here (Trending, Top hit, Your taste, Liked), a filled
 * "Your taste" when a trending or top song also fits you, and "Liked" as soon as you
 * like it. Reads the like live, so the tag appears (or goes) the moment you tap like.
 */
@Composable
fun SongTags(t: HomeMixes.TodaySong, horizontal: Boolean = false, onDark: Boolean = !com.music.spotui.ui.theme.AalapPalette.isLight, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by com.music.spotui.data.preferences.likedVersion.collectAsState()
    val liked = remember(version, t.song.id) { com.music.spotui.data.preferences.isSongLiked(context, t.song.id.toString()) }
    val tags = buildList {
        if (t.reason != "Liked" || liked) add(t.reason to false)
        if (t.matches && t.reason != "Your taste" && t.reason != "Liked") add("Your taste" to true)
        if (liked && t.reason != "Liked") add("Liked" to false)
    }
    if (tags.isEmpty()) return
    val tag: @Composable (Pair<String, Boolean>) -> Unit = { (text, filled) ->
        if (text == "Liked") LikedTag(t.song.id.toString(), onDark)
        else ReasonTag(text, reasonColor(text, onDark), filled = filled, onDark = onDark)
    }
    if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) { tags.forEach { tag(it) } }
    else Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) { tags.forEach { tag(it) } }
}

/**
 * The tag for any song row (charts, moods, categories, search): Trending, Top hit,
 * Liked or Your taste, plus a filled "Your taste" when a trending/top song also fits
 * you. [personalOnly] shows just Liked / Your taste (on chart pages). Nothing when
 * none apply.
 */
@Composable
fun SongTasteTag(song: com.music.spotui.data.entity.SongsModel, personalOnly: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version by com.music.spotui.data.preferences.likedVersion.collectAsState()
    val t by androidx.compose.runtime.produceState<HomeMixes.TodaySong?>(null, song.id, personalOnly, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { HomeMixes.reasonFor(context, song, personalOnly) }.getOrNull()
        }
    }
    SongTags(t ?: return, modifier = modifier)
}

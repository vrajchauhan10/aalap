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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
private fun reasonColor(reason: String): Color {
    val light = com.music.spotui.ui.theme.AalapPalette.isLight
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
fun TodayPlaylistSection(songs: List<HomeMixes.TodaySong>) {
    val context = LocalContext.current
    val player: PlayerViewModel = hiltViewModel()
    var expanded by remember { mutableStateOf(false) }
    val list = songs.map { it.song }
    fun play(i: Int) { if (list.isNotEmpty()) { player.updateQueue(list); player.playSongAt(list, i, context) } }
    val updated = HomeMixes.todayUpdatedAt(context).takeIf { it > 0 }
        ?.let { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it)) }

    Column(Modifier.padding(top = 14.dp)) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(min = 212.dp) // grows with large system font sizes instead of overlapping
                .clip(RoundedCornerShape(24.dp))
                .clickable { play(0) },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width; val h = size.height
                drawRect(Indigo)
                // Saffron panel with a hand-cut slanted edge.
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(w * 0.64f, 0f); lineTo(w * 0.52f, h); lineTo(0f, h); close() }, SaffronInk)
                // Marigold mandala.
                drawMandala(Marigold, Offset(w * 0.79f, h * 0.46f), h * 0.37f, h * 0.008f, accent = Color(0xFFFF8A3D))
                // Paisley curls.
                val curl = Stroke(h * 0.008f)
                drawPath(Path().apply {
                    moveTo(w * 0.06f, h * 0.86f)
                    cubicTo(w * 0.01f, h * 0.8f, w * 0.03f, h * 0.7f, w * 0.08f, h * 0.7f)
                    cubicTo(w * 0.12f, h * 0.7f, w * 0.13f, h * 0.78f, w * 0.1f, h * 0.81f)
                }, Marigold.copy(alpha = 0.8f), style = curl)
                // Block-print border.
                val band = h * 0.92f
                drawLine(Marigold.copy(alpha = 0.7f), Offset(0f, band), Offset(w, band), strokeWidth = h * 0.007f)
                var x = h * 0.03f
                while (x < w) { drawCircle(Marigold.copy(alpha = 0.55f), h * 0.007f, Offset(x, band + h * 0.04f)); x += h * 0.05f }
            }
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
                    Text(if (songs.isEmpty()) "Making today's playlist" else "Play today's ${songs.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (updated != null) Text("Updated $updated", color = Cream.copy(alpha = 0.85f), fontSize = 10.sp)
                }
            }
            }
        }

        // The songs, with why each one is in today's list.
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).animateContentSize()) {
            val shown = if (expanded) songs else songs.take(5)
            shown.forEachIndexed { i, t ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { play(i) }.padding(vertical = 6.dp),
                ) {
                    GlideImage(
                        model = t.song.coverUri, contentDescription = t.song.title, contentScale = ContentScale.Crop,
                        loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.song.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.song.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Tag(t.reason, reasonColor(t.reason))
                        // A trending or top hit that's also right up your street.
                        if (t.matches && t.reason != "Your taste" && t.reason != "Liked") Tag("Your taste", reasonColor("Your taste"), filled = true)
                    }
                }
            }
            if (songs.size > 5) {
                Text(
                    if (expanded) "Show less" else "Show all ${songs.size} songs",
                    color = com.music.spotui.ui.theme.Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { expanded = !expanded }.padding(vertical = 8.dp, horizontal = 2.dp),
                )
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

@Composable
private fun Tag(text: String, color: Color, filled: Boolean = false) {
    Text(
        text, color = if (filled) (if (com.music.spotui.ui.theme.AalapPalette.isLight) Color.White else Color(0xFF1A0E05)) else color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.data.api.Mood
import com.music.spotui.ui.navigation.categoryRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Search page title. */
@Composable
fun AalapSearchHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Search", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Any song, artist, album or mood", color = TextMuted, fontSize = 13.sp)
        }
    }
}

/** The search field: a soft pill that lights up in saffron while typing, with a clear button. */
@Composable
fun AalapSearchBar(text: String, onFocusChange: (Boolean) -> Unit, onTextChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Box(Modifier.fillMaxWidth().background(AppBackground).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(Surface)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) Saffron else Hairline, RoundedCornerShape(50))
                .padding(horizontal = 18.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = if (focused) Saffron else TextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Songs, artists, albums or a mood", color = TextMuted, fontSize = 15.sp)
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(Saffron),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; onFocusChange(it.isFocused) },
                )
            }
            if (text.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(SurfaceHigh).clickable { onTextChange("") },
                ) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextPrimary, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** Result filters. */
enum class SearchFilter(val label: String) { ALL("All"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), PODCASTS("Podcasts") }

@Composable
fun SearchFilterChips(selected: SearchFilter, available: Set<SearchFilter>, onSelect: (SearchFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.background(AppBackground).padding(bottom = 6.dp),
    ) {
        val list = SearchFilter.entries.filter { it == SearchFilter.ALL || it in available }
        items(list.size) { i ->
            val f = list[i]
            val on = f == selected
            Text(
                f.label,
                color = if (on) com.music.spotui.ui.theme.OnSaffron else TextPrimary,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Saffron else Surface)
                    .border(1.dp, if (on) Saffron else Hairline, RoundedCornerShape(50))
                    .clickable { onSelect(f) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Explore categories: India first, each opening a page of playlists for it. */
private data class ExploreTile(val title: String, val query: String, val colors: Pair<Long, Long>)

private val EXPLORE = listOf(
    ExploreTile("Bollywood", "Bollywood hits", 0xFFFF8A3D to 0xFFB8235A),
    ExploreTile("Punjabi", "Punjabi hits", 0xFFFFC56B to 0xFFE0661B),
    ExploreTile("Gujarati", "Gujarati songs", 0xFFE5397A to 0xFF6C1238),
    ExploreTile("Haryanvi", "Haryanvi songs", 0xFF3BA55C to 0xFF1E5E33),
    ExploreTile("Tamil", "Tamil hits", 0xFF5B7CFA to 0xFF2A3A8C),
    ExploreTile("Telugu", "Telugu hits", 0xFF9B5DE5 to 0xFF4B2A8C),
    ExploreTile("90s Bollywood", "90s Bollywood", 0xFFE08A00 to 0xFF7A2E0E),
    ExploreTile("Ghazal and Sufi", "Sufi ghazal", 0xFF6E8B74 to 0xFF2F4A36),
    ExploreTile("Indie India", "Indian indie", 0xFF2BB3C0 to 0xFF15606B),
    ExploreTile("Garba", "Garba songs", 0xFFFF5E7E to 0xFF8C1F4A),
    ExploreTile("Lo-fi", "Hindi lofi", 0xFF3D6BE0 to 0xFF1A2A6C),
    ExploreTile("Desi Hip-Hop", "Desi hip hop", 0xFF777777 to 0xFF2A2140),
    ExploreTile("International", "Top hits global", 0xFF2BB3C0 to 0xFF3A1F6B),
    ExploreTile("K-Pop", "K-pop", 0xFFE5397A to 0xFF3D6BE0),
    ExploreTile("New releases", "New releases India", 0xFFFF8A3D to 0xFF3A1F6B),
    ExploreTile("Podcasts", "Podcast", 0xFF1E3264 to 0xFF100B1C),
)

/**
 * What Search shows before you type: quick searches drawn from your own taste,
 * moods, and an India-first Explore grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchExplore(navController: NavController, onQuickSearch: (String) -> Unit) {
    val context = LocalContext.current
    val quick by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            val mine = runCatching { com.music.spotui.data.recommendation.engine.Personalizer.summary(context).artists }.getOrDefault(emptyList())
            (mine.take(4) + listOf("Arijit Singh", "Punjabi hits", "Garba", "Lofi", "90s romance")).distinct().take(8)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (quick.isNotEmpty()) {
            ExploreHeader("Quick searches")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                quick.forEach { q ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Surface)
                            .border(1.dp, Hairline, RoundedCornerShape(50))
                            .clickable { onQuickSearch(q) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Search, null, tint = Saffron, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(q, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        ExploreHeader("Moods")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(Mood.entries.size) { i ->
                val mood = Mood.entries[i]
                Box(
                    modifier = Modifier
                        .size(width = 112.dp, height = 64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(mood.colors.first), Color(mood.colors.second))))
                        .clickable { navController.navigate("mood/${mood.name}") }
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart,
                ) { Text(mood.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }

        ExploreHeader("Explore")
        EXPLORE.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
            ) {
                row.forEach { tile ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(92.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(tile.colors.first), Color(tile.colors.second))))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                navController.navigate(categoryRoute(tile.query, tile.title))
                            },
                    ) {
                        Text(
                            tile.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExploreHeader(text: String) {
    Text(
        text, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}

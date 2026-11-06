package com.music.aalap.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.music.aalap.ui.components.SongOptionsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.AlbumsModel
import com.music.aalap.data.entity.ArtistOverviewModel
import com.music.aalap.data.entity.ArtistTrackUi
import com.music.aalap.data.entity.ArtistsModel
import com.music.aalap.data.preferences.addLikedSongId
import com.music.aalap.data.preferences.isSongLiked
import com.music.aalap.data.preferences.removeLikedSongId
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.components.Loader
import com.music.aalap.ui.navigation.Routes
import com.music.aalap.ui.navigation.albumRoute
import com.music.aalap.ui.navigation.artistRoute
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.AppPalette
import com.music.aalap.ui.viewmodel.ArtistViewModel

private val SpotifyGreen get() = com.music.aalap.ui.theme.Saffron

/** The artist's bio as plain text: links become their words, &amp; and friends become characters. */
internal fun cleanBio(raw: String): String =
    androidx.core.text.HtmlCompat.fromHtml(raw.replace("\n", "<br>"), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()

/** Pulls web addresses out of the bio: the words left, and each link with a short name (Instagram, X, Website…). */
internal fun splitLinks(bio: String): Pair<String, List<Pair<String, String>>> {
    val urls = Regex("""https?://\S+|www\.\S+""").findAll(bio).map { it.value.trimEnd('.', ',', ')') }.distinct().toList()
    fun name(u: String): String {
        val host = android.net.Uri.parse(if (u.startsWith("http")) u else "https://$u").host.orEmpty().removePrefix("www.").removePrefix("m.")
        return when {
            "instagram" in host -> "Instagram"
            "youtube" in host || "youtu.be" in host -> "YouTube"
            "twitter" in host || host == "x.com" -> "X"
            "facebook" in host -> "Facebook"
            "spotify" in host -> "Spotify"
            else -> host.substringBefore('/').ifBlank { "Website" }
        }
    }
    var text = bio
    urls.forEach { text = text.replace(it, "") }
    text = text.replace(Regex("[ \t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
    return text to urls.map { name(it) to (if (it.startsWith("http")) it else "https://$it") }.distinctBy { it.first }
}

/** Header size for an artist's name: big for short names, smaller as they get longer. */
internal fun artistNameSize(name: String): androidx.compose.ui.unit.TextUnit {
    // The longest piece that can't wrap (hyphens stay together) has to fit on one line;
    // at 42 sp about 12 letters fit across the S24.
    val longestWord = name.split(' ').maxOfOrNull { it.length } ?: 0
    val byLength = when {
        name.length <= 12 -> 42f
        name.length <= 18 -> 34f
        name.length <= 26 -> 28f
        else -> 24f
    }
    val byWord = when {
        longestWord <= 12 -> 42f
        longestWord <= 15 -> 34f
        longestWord <= 19 -> 28f
        else -> 22f
    }
    return minOf(byLength, byWord).sp
}

/** 347802860 → "347M", 1520000000 → "1.5B", 45200 → "45K": easy to read at a glance. */
internal fun shortCount(n: Long): String {
    fun one(v: Double) = if (v < 10) String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0") else v.toLong().toString()
    return when {
        n >= 1_000_000_000L -> one(n / 1e9) + "B"
        n >= 1_000_000L -> one(n / 1e6) + "M"
        n >= 1_000L -> one(n / 1e3) + "K"
        else -> n.toString()
    }
}

private fun grouped(n: Long): String =
    "%,d".format(n)

@Composable
fun ArtistScreen(navController: NavController, artistName: String, artistId: String = "") {
    val artistViewModel: ArtistViewModel = hiltViewModel()
    val overview by artistViewModel.overview.collectAsState()

    LaunchedEffect(artistName, artistId) { artistViewModel.loadArtistOverview(artistName, artistId) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        when (val state = overview) {
            is Response.Loading -> Loader()
            is Response.Error -> ArtistOverviewContent(
                navController, artistViewModel,
                ArtistOverviewModel(name = artistName), artistName,
            )
            is Response.Success -> ArtistOverviewContent(
                navController, artistViewModel, state.data, artistName,
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ArtistOverviewContent(
    navController: NavController,
    artistViewModel: ArtistViewModel,
    overview: ArtistOverviewModel,
    artistName: String,
) {
    val context = LocalContext.current
    val tracks = overview.topTracks
    val displayName = overview.name.ifBlank { artistName }

    var menuSong by remember { mutableStateOf<com.music.aalap.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
        )
    }

    // Followed = known locally OR followed on the real Spotify account.
    val followedIds by artistViewModel.followedArtistIds.collectAsState()
    var following by remember(overview.id, followedIds) {
        mutableStateOf(
            com.music.aalap.data.preferences.isArtistFollowed(context, overview.id) ||
                overview.id in followedIds
        )
    }

    fun playTrackAt(index: Int) {
        if (index !in tracks.indices) return
        val songs = tracks.map { it.song }
        // That song, then a mix that fits it, with more of this artist now and then.
        artistViewModel.playFromList(songs[index], songs, overview.name, context)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 130.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        // ── Header: big artist image with scrim + name ──
        item {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
            ) {
                GlideImage(
                    modifier = Modifier.fillMaxSize(),
                    model = overview.headerImage.ifBlank { overview.avatarImage },
                    contentScale = ContentScale.Crop,
                    failure = placeholder(R.drawable.placeholder),
                    loading = placeholder(R.drawable.placeholder),
                    contentDescription = "",
                )
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color(AppBackground.toArgb()),
                            ),
                        )
                    )
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "",
                    tint = com.music.aalap.ui.theme.TextPrimary,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp, 12.dp)
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { navController.navigateUp() },
                )
                // Long names ("Shankar-Ehsaan-Loy", "A. R. Rahman & Shreya Ghoshal") get a smaller
                // size so they fit in two lines instead of spilling off the photo.
                Text(
                    text = displayName,
                    color = com.music.aalap.ui.theme.TextPrimary,
                    fontSize = artistNameSize(displayName),
                    lineHeight = artistNameSize(displayName) * 1.08f,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp, 0.dp, 16.dp, 12.dp),
                )
            }
        }

        // ── Verified + monthly listeners ──
        item {
            if (overview.verified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp),
                ) {
                    Box(modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A90E2)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Verified Artist", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            overview.monthlyListeners?.let {
                Text(
                    text = "${grouped(it)} monthly listeners",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp, 2.dp, 16.dp, 0.dp),
                )
            }
        }

        // ── Action row: Follow + shuffle + play ──
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            following = !following
                            // Persist locally and mirror the follow to the real
                            // Spotify account.
                            if (following) com.music.aalap.data.preferences.addFollowedArtist(context, overview.id, displayName)
                            else com.music.aalap.data.preferences.removeFollowedArtist(context, overview.id)
                            com.music.aalap.data.api.SpotifySync.setArtistFollowed(context, overview.id, following)
                        }
                        .padding(16.dp, 7.dp),
                    ) {
                        Text(
                            text = if (following) "Following" else "Follow",
                            color = com.music.aalap.ui.theme.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // More: share the artist, or a radio of them and artists like them.
                    var more by remember { mutableStateOf(false) }
                    Box {
                        androidx.compose.material3.IconButton(onClick = { more = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dots),
                                contentDescription = "More",
                                tint = Color.Gray,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = more, onDismissRequest = { more = false },
                            modifier = Modifier.background(com.music.aalap.ui.theme.Surface),
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Artist radio", color = com.music.aalap.ui.theme.TextPrimary) },
                                enabled = tracks.isNotEmpty(),
                                onClick = { more = false; playTrackAt(0) },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Share artist", color = com.music.aalap.ui.theme.TextPrimary) },
                                onClick = {
                                    more = false
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, com.music.aalap.ui.share.ShareMessage.forArtist(displayName))
                                    context.startActivity(android.content.Intent.createChooser(send, "Share $displayName"))
                                },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_shuffle),
                        contentDescription = "",
                        tint = com.music.aalap.ui.theme.TextPrimary,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (tracks.isNotEmpty()) artistViewModel.playAll(tracks.map { it.song }.shuffled(), overview.name, context) },
                    )
                    Spacer(Modifier.width(20.dp))
                    Box(modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (tracks.isNotEmpty()) artistViewModel.playAll(tracks.map { it.song }, overview.name, context) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.play_svgrepo_com),
                            contentDescription = "",
                            tint = com.music.aalap.ui.theme.OnSaffron,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        // ── Popular tracks ──
        if (tracks.isNotEmpty()) {
            item {
                Text(
                    text = "Popular",
                    color = com.music.aalap.ui.theme.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 4.dp),
                )
            }
            // Up to 16 songs, 4 to a page like Quick picks: swipe sideways for the next four.
            item {
                val pages = tracks.take(16).withIndex().toList().chunked(4)
                val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(start = 16.dp, end = 48.dp),
                    pageSpacing = 12.dp,
                    verticalAlignment = Alignment.Top,
                ) { p ->
                    Column {
                        pages[p].forEach { (index, item) ->
                            PopularTrackRow(
                                item, index, artistViewModel,
                                onLongClick = { menuSong = item.song },
                                onMenu = { menuSong = item.song },
                                onPlay = { playTrackAt(index) },
                            )
                        }
                        // A short last page keeps the full height, so nothing below jumps up.
                        repeat(4 - pages[p].size) { Spacer(Modifier.height(62.dp)) }
                    }
                }
                if (pages.size > 1) Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) com.music.aalap.ui.theme.Saffron else Color.Gray.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
        }

        // ── Popular releases (discography) — vertical list, first few + "Show all" ──
        if (overview.popularReleases.isNotEmpty()) {
            item { SectionHeader("Popular releases") }
            itemsIndexed(overview.popularReleases.take(4)) { _, album ->
                ReleaseRow(album) {
                    navController.navigate(albumRoute(album.name, album.artists.ifBlank { displayName }))
                }
            }
            if (overview.popularReleases.size > 4) {
                item {
                    ShowAllButton {
                        navController.navigate("${Routes.ArtistReleases.route}/${android.net.Uri.encode(artistName)}")
                    }
                }
            }
        }

        // ── About ──
        if (!overview.biography.isNullOrBlank()) {
            item { SectionHeader("About") }
            item {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(com.music.aalap.ui.theme.TextPrimary.copy(alpha = 0.06f))
                ) {
                    Column {
                        if (overview.avatarImage.isNotBlank()) {
                            GlideImage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                model = overview.avatarImage,
                                contentScale = ContentScale.Crop,
                                failure = placeholder(R.drawable.placeholder),
                                contentDescription = "",
                            )
                        }
                        overview.monthlyListeners?.let {
                            Text(
                                text = "${grouped(it)} monthly listeners",
                                color = com.music.aalap.ui.theme.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp, 12.dp, 14.dp, 4.dp),
                            )
                        }
                        // Spotify's text carries links (<a href="spotify:artist:…">Name</a>) and
                        // codes like &amp;: shown as plain words, a few lines with Read more.
                        val bioAndLinks = remember(overview.biography) { splitLinks(cleanBio(overview.biography.orEmpty())) }
                        val bio = bioAndLinks.first
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            text = bio,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(14.dp, 4.dp, 14.dp, 4.dp),
                        )
                        // Links in the bio (Instagram, YouTube…) as small buttons instead of raw addresses.
                        if (bioAndLinks.second.isNotEmpty()) Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp, 8.dp, 14.dp, 0.dp)
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        ) {
                            bioAndLinks.second.forEach { (label, url) ->
                                Text(
                                    label, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.Gray, RoundedCornerShape(50))
                                        .clickable {
                                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                        if (bio.length > 220 || bio.count { it == '\n' } > 3) Text(
                            if (expanded) "Show less" else "Read more",
                            color = com.music.aalap.ui.theme.Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { expanded = !expanded }.padding(14.dp, 4.dp, 14.dp, 14.dp),
                        )
                    }
                }
            }
        }

        // ── Fans also like ──
        if (overview.relatedArtists.isNotEmpty()) {
            item { SectionHeader("Fans also like") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp),
                ) {
                    items(overview.relatedArtists.size) { i ->
                        RelatedArtistCard(overview.relatedArtists[i]) {
                            navController.navigate(artistRoute(overview.relatedArtists[i].name, overview.relatedArtists[i].id))
                        }
                    }
                }
            }
        }

        // ── Appears on ──
        if (overview.appearsOn.isNotEmpty()) {
            item { SectionHeader("Appears on") }
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 0.dp),
                ) {
                    items(overview.appearsOn.size) { i ->
                        ReleaseCard(overview.appearsOn[i]) {
                            navController.navigate(albumRoute(overview.appearsOn[i].name, overview.appearsOn[i].artists))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PopularTrackRow(
    item: ArtistTrackUi,
    index: Int,
    artistViewModel: ArtistViewModel,
    onLongClick: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val song = item.song
    var isLiked by remember { mutableStateOf(isSongLiked(context, song.id.toString())) }
    val likeState = artistViewModel.likeState.value
    LaunchedEffect(likeState, com.music.aalap.data.preferences.likedVersion.collectAsState().value) { isLiked = isSongLiked(context, song.id.toString()) }
    val titleColor =
        if (song.id == artistViewModel.currentSongId.value) Color(AppPalette.toArgb()) else com.music.aalap.ui.theme.TextPrimary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onPlay,
            ),
    ) {
        // Same look as Quick picks: cover, title, then rank and plays underneath.
        GlideImage(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp)),
            model = song.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                text = item.playcount?.let { "${shortCount(it)} plays" } ?: song.singer,
                color = Color.Gray, fontSize = 12.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = if (isLiked) painterResource(id = R.drawable.added) else painterResource(id = R.drawable.ic_add),
            contentDescription = "",
            tint = if (isLiked) com.music.aalap.ui.theme.Saffron else Color.Gray,
            modifier = Modifier
                .size(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isLiked) removeLikedSongId(context, song.id.toString())
                    else { addLikedSongId(context, song.id.toString(), song); com.music.aalap.ui.components.LikeCelebration.burst() }
                    isLiked = isSongLiked(context, song.id.toString())
                    // Mirror the like to the real Spotify account.
                    com.music.aalap.data.api.SpotifySync.setTrackSaved(context, song.spotifyTrackId, isLiked)
                    artistViewModel.updateLikeState(!artistViewModel.likeState.value)
                },
        )
        if (onMenu != null) androidx.compose.material3.IconButton(onClick = onMenu, modifier = Modifier.padding(start = 4.dp).size(36.dp)) {
            Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "More options", tint = Color.Gray)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = com.music.aalap.ui.theme.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp),
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ReleaseCard(album: AlbumsModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(6.dp)),
            model = album.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.height(6.dp))
        Text(text = album.name, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(
            text = album.time.ifBlank { album.artists }.let { if (album.time.isNotBlank()) "${album.time} • Album" else it },
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ReleaseRow(album: AlbumsModel, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(4.dp)),
            model = album.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = album.name, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val kind = album.type.replaceFirstChar { it.uppercase() }.ifBlank { "" }
            val year = releaseYear(album)
            Text(
                text = listOf(year, kind).filter { it.isNotBlank() }.joinToString(" • "),
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ShowAllButton(onClick: () -> Unit) {
    Box(modifier = Modifier
        .padding(16.dp, 10.dp, 16.dp, 4.dp)
        .clip(RoundedCornerShape(20.dp))
        .border(1.dp, Color.Gray, RoundedCornerShape(20.dp))
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick() }
        .padding(20.dp, 8.dp),
    ) {
        Text(text = "Show all", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Release-year helper: Spotify release dates come as "2024" or "2024-05-17". */
private fun releaseYear(album: AlbumsModel): String = album.time.take(4)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ArtistReleasesScreen(navController: NavController, artistName: String) {
    val artistViewModel: ArtistViewModel = hiltViewModel()
    val overview by artistViewModel.overview.collectAsState()

    LaunchedEffect(artistName) { artistViewModel.loadArtistOverview(artistName) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        val data = (overview as? Response.Success)?.data
        val releases = data?.popularReleases ?: emptyList()
        val featuredOn = data?.appearsOn ?: emptyList()
        // Spotify's Releases view: filter chips + a "Latest release" spotlight,
        // then releases grouped by kind, newest first.
        val albums = releases.filter { it.type == "album" || it.type == "compilation" }
        val singles = releases.filter { it.type == "single" || it.type == "ep" }
        val untyped = releases.filter { it.type.isBlank() }
        val latest = releases.maxByOrNull { it.time }
        var filter by remember { mutableStateOf("") } // "" | "albums" | "singles" | "featured"

        fun openAlbum(album: AlbumsModel) {
            navController.navigate(albumRoute(album.name, album.artists.ifBlank { artistName }))
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 130.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color(AppBackground.toArgb()))
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp, 12.dp, 16.dp, 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "",
                        tint = com.music.aalap.ui.theme.TextPrimary,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { navController.navigateUp() },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Releases",
                        color = com.music.aalap.ui.theme.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Row(modifier = Modifier.padding(12.dp, 0.dp, 12.dp, 8.dp)) {
                    ReleaseFilterChip("Albums", filter == "albums") {
                        filter = if (filter == "albums") "" else "albums"
                    }
                    Spacer(Modifier.width(8.dp))
                    ReleaseFilterChip("Singles and EPs", filter == "singles") {
                        filter = if (filter == "singles") "" else "singles"
                    }
                    if (featuredOn.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        ReleaseFilterChip("Featured on", filter == "featured") {
                            filter = if (filter == "featured") "" else "featured"
                        }
                    }
                }
            }
            if (overview is Response.Loading) {
                item { Loader() }
            }
            when (filter) {
                "albums" -> items(albums.size) { i -> ReleaseRow(albums[i]) { openAlbum(albums[i]) } }
                "singles" -> items(singles.size) { i -> ReleaseRow(singles[i]) { openAlbum(singles[i]) } }
                "featured" -> items(featuredOn.size) { i -> ReleaseRow(featuredOn[i]) { openAlbum(featuredOn[i]) } }
                else -> {
                    latest?.let {
                        item { ReleaseSectionHeader("Latest release") }
                        item { ReleaseRow(it) { openAlbum(it) } }
                    }
                    if (albums.isNotEmpty()) {
                        item { ReleaseSectionHeader("Albums") }
                        items(albums.size) { i -> ReleaseRow(albums[i]) { openAlbum(albums[i]) } }
                    }
                    if (singles.isNotEmpty()) {
                        item { ReleaseSectionHeader("Singles and EPs") }
                        items(singles.size) { i -> ReleaseRow(singles[i]) { openAlbum(singles[i]) } }
                    }
                    if (untyped.isNotEmpty()) {
                        if (albums.isEmpty() && singles.isEmpty()) {
                            item { ReleaseSectionHeader("Popular releases") }
                        }
                        items(untyped.size) { i -> ReleaseRow(untyped[i]) { openAlbum(untyped[i]) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseSectionHeader(title: String) {
    Text(
        text = title,
        color = com.music.aalap.ui.theme.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp),
    )
}

@Composable
private fun ReleaseFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) com.music.aalap.ui.theme.TextPrimary else com.music.aalap.ui.theme.SurfaceHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(14.dp, 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) com.music.aalap.ui.theme.OnSaffron else com.music.aalap.ui.theme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RelatedArtistCard(artist: ArtistsModel, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(130.dp)
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape),
            model = artist.coverUri,
            contentScale = ContentScale.Crop,
            failure = placeholder(R.drawable.placeholder),
            loading = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(Modifier.height(6.dp))
        Text(text = artist.name, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(text = "Artist", color = Color.Gray, fontSize = 11.sp)
    }
}

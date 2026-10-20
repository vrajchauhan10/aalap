package com.music.spotui.ui.screens

import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.theme.GridBackground
import com.music.spotui.ui.viewmodel.HomeViewModel
import java.time.LocalTime


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(navController: NavController){

    val homeViewModel : HomeViewModel = hiltViewModel()
    val home by homeViewModel.home.collectAsState()
    val albums by homeViewModel.albums.collectAsState()
    val artists by homeViewModel.artists.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
            .statusBarsPadding()
    ) {
        val feed = (home as? Response.Success)?.data
        // Each feed resolves independently — albums (new releases) often succeeds
        // while artists (personalized) gets rate-limited. Render whatever arrived
        // instead of casting blindly (which crashed when one feed was an Error).
        val albumsList = (albums as? Response.Success)?.data.orEmpty()
        val artistsList = (artists as? Response.Success)?.data.orEmpty()

        when {
            // Offline: no feed, just what's on the phone.
            com.music.spotui.data.preferences.OfflineState.isOffline -> {
                OfflineHome(navController)
            }

            // Preferred: the real personalized Spotify home feed.
            feed != null && feed.sections.isNotEmpty() -> {
                HomeFeedContent(navController, feed)
            }

            // Still resolving the real personalized home feed. Show the loader even
            // if the new-releases/artists fallbacks already arrived from cache —
            // otherwise the old "Sum up" layout flashes for a beat before the real
            // Spotify-style feed swaps in.
            home is Response.Loading -> {
                Loader()
            }

            // Fallback: home feed errored but new-releases / artists came through.
            albumsList.isNotEmpty() || artistsList.isNotEmpty() -> {
                SumUpHomeScreen(navController = navController, albums = albumsList, artists = artistsList)
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load music.\nCheck your connection and try again.",
                        color = com.music.spotui.ui.theme.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun onHomeItemClick(navController: NavController, item: HomeItem) {
    when (item) {
        is HomeItem.Album -> navController.navigate(albumRoute(item.name, item.artists.ifBlank { item.subtitle }))
        is HomeItem.Artist -> navController.navigate(artistRoute(item.name, item.id))
        // Load the real playlist content by its Spotify id (daily mixes, etc).
        is HomeItem.Playlist ->
            if (item.id.isNotBlank()) navController.navigate(playlistRoute(item.id, item.name))
            else navController.navigate(albumRoute(item.name))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeFeedContent(navController: NavController, feed: HomeFeedModel) {
    // Aalap home: greeting + shortcut chips, a featured banner, a "Jump back in" tile
    // row (the feed's untitled shortcuts section), then the titled carousels in feed order.
    val sections = feed.sections
    val gridSection = sections.firstOrNull()?.takeIf { it.title.isBlank() }
    val carousels = if (gridSection != null) sections.drop(1) else sections
    val featured = gridSection?.items?.firstOrNull() ?: carousels.firstOrNull()?.items?.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 190.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(com.music.spotui.ui.theme.AppBackground)
    ) {
        item { HomeHeaderRow(navController) }
        featured?.let { item { FeaturedBanner(it) { onHomeItemClick(navController, it) } } }
        gridSection?.let { section ->
            val rest = section.items.drop(1).take(10)
            if (rest.isNotEmpty()) item { JumpBackInRow(navController, rest) }
        }
        items(carousels.size) { i ->
            HomeFeedSection(navController, carousels[i])
        }
    }
}

/** Greeting with the listener's name, profile avatar (opens Profile), and shortcut chips. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeHeaderRow(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.data.api.ProfileCache.ensure(context)
    }
    val firstName = com.music.spotui.data.api.ProfileCache.name?.trim()?.substringBefore(' ')
    val greeting = when (java.time.LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late-night listening"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                firstName ?: "Aalap",
                color = com.music.spotui.ui.theme.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        val avatarUrl = com.music.spotui.data.api.ProfileCache.imageUrl
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(com.music.spotui.ui.theme.Saffron)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { navController.navigate(Routes.Settings.route) },
        ) {
            if (avatarUrl != null) {
                GlideImage(
                    model = avatarUrl,
                    contentScale = ContentScale.Crop,
                    contentDescription = "Profile",
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Text(firstName?.firstOrNull()?.uppercase() ?: "A", color = com.music.spotui.ui.theme.OnSaffron, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        HomeChip(androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.Favorite), "Liked") {
            navController.navigate(Routes.Liked.route)
        }
        HomeChip(androidx.compose.ui.res.painterResource(R.drawable.ic_download), "Downloads") {
            navController.navigate(Routes.Downloads.route)
        }
        HomeChip(androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Default.DateRange), "History") {
            navController.navigate(Routes.History.route)
        }
        HomeChip(androidx.compose.ui.res.painterResource(R.drawable.ic_library_big), "Local files") {
            navController.navigate(Routes.LocalFiles.route)
        }
    }
}

@Composable
private fun HomeChip(icon: androidx.compose.ui.graphics.painter.Painter, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(com.music.spotui.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = com.music.spotui.ui.theme.Saffron, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Large card for the top item of the feed. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FeaturedBanner(item: HomeItem, onClick: () -> Unit) {
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> "Artist"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(com.music.spotui.ui.theme.Saffron.copy(alpha = 0.85f), com.music.spotui.ui.theme.SaffronDeep.copy(alpha = 0.55f), com.music.spotui.ui.theme.Surface)
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("FOR YOU", color = com.music.spotui.ui.theme.OnSaffron.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(item.name, color = com.music.spotui.ui.theme.OnSaffron, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 24.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = com.music.spotui.ui.theme.OnSaffron.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
            }
        }
        GlideImage(
            model = item.imageUrl,
            contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = item.name,
            modifier = Modifier
                .size(122.dp)
                .clip(if (item is HomeItem.Artist) CircleShape else RoundedCornerShape(18.dp)),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun JumpBackInRow(navController: NavController, items: List<HomeItem>) {
    SectionHeader("Jump back in")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(items.size) { i ->
            val item = items[i]
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier = Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onHomeItemClick(navController, item) },
            ) {
                GlideImage(
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    model = item.imageUrl,
                    loading = placeholder(R.drawable.placeholder),
                    failure = placeholder(R.drawable.placeholder),
                    contentDescription = item.name,
                )
                // Scrim so the name stays readable on any artwork.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)
                            )
                        )
                )
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/** Section title with Aalap's saffron accent bar. */
@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(com.music.spotui.ui.theme.Saffron)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun HomeFeedSection(navController: NavController, section: HomeSection) {
    SectionHeader(section.title)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        items(section.items.size) { i ->
            HomeFeedCard(section.items[i]) { onHomeItemClick(navController, section.items[i]) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeFeedCard(item: HomeItem, onClick: () -> Unit) {
    val isArtist = item is HomeItem.Artist
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> "Artist"
    }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .width(140.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        GlideImage(
            modifier = Modifier
                .size(140.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
            model = item.imageUrl,
            loading = placeholder(R.drawable.placeholder),
            failure = placeholder(R.drawable.placeholder),
            contentDescription = "",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name,
            color = com.music.spotui.ui.theme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = subtitle,
            color = com.music.spotui.ui.theme.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SumUpHomeScreen(navController : NavController, albums: List<AlbumsModel>, artists: List<ArtistsModel>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(AppBackground.toArgb()))
    ){

        GreetingSection()
        //ChipSection(chip = listOf(" All ", "Music", "Podcasts"))

        if (albums.isNotEmpty()) {
            HomePlaylistGrid(navController, albums)
            HomeAlbums(album = albums, navController)
        }
        //HomeRecentlyPlayed(navController, albums = listOf("karan aujla", "diljit", "fudfu", "frref", "frrf"))
        if (artists.isNotEmpty()) {
            HomeArtists(artists = artists, navController)
        }
        if (albums.isNotEmpty()) {
            ImageCard(navController, albums)
        }
    }
}



@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GreetingSection(name : String = "User") {
    val currentHour = LocalTime.now().hour
    val greeting = when {
        currentHour < 12 -> "Good Morning"
        currentHour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = com.music.spotui.ui.theme.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
                )
            Text(
                text = "Have a Nice Day",
                style = MaterialTheme.typography.bodyMedium,
                color = com.music.spotui.ui.theme.TextPrimary,
                fontSize = 13.sp
                )
        }
//        Icon(imageVector = Icons.Outlined.Person, contentDescription = "Profile", tint = com.music.spotui.ui.theme.TextPrimary)
    }
}

//@Composable
//fun ChipSection(
//    chip : List<String>
//) {
//    var selectedChip by remember {
//        mutableStateOf(0)
//    }
//    LazyRow{
//        items(chip.size){
//            Box(contentAlignment = Alignment.Center,
//                modifier = Modifier
//                    .padding(15.dp, 0.dp, 0.dp, 0.dp)
//                    .clickable {
//                        selectedChip = it
//                    }
//                    .clip(RoundedCornerShape(50.dp))
//                    .background(
//                        if (selectedChip == it) Color.Green
//                        else Color.Gray
//                    )
//                    .padding(10.dp, 5.dp)
//
//            ){
//                Text(text = chip[it], color = com.music.spotui.ui.theme.TextPrimary)
//            }
//        }
//    }
//}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomePlaylistGrid(navController: NavController, albums: List<AlbumsModel>) {
    // Use up to 8 albums, but don't assume there are at least 8 (rate-limited /
    // small feeds can return fewer) — that previously caused IndexOutOfBounds.
    val gridAlbums = albums.take(8)

    val chunkedAlbums = gridAlbums.chunked(2)
    Log.d("giveme", chunkedAlbums.toString())
    Column(
        modifier = Modifier
            .padding(0.dp, 10.dp)
    ){
        repeat(chunkedAlbums.size){
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(15.dp, 5.dp, 7.dp, 0.dp)
                    .fillMaxWidth()
            )
            {
                repeat(chunkedAlbums[it].size){ album ->
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(GridBackground.toArgb()))
                            .width(180.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val albumModel = chunkedAlbums[it][album]
                                Log.d("check", albumModel.name)
                                navController.navigate(albumRoute(albumModel.name, albumModel.artists))
                            }
                    ) {
                        GlideImage(modifier = Modifier
                            .size(55.dp),
                            contentScale = ContentScale.Crop,
                            model = chunkedAlbums[it][album].coverUri,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            contentDescription = "Profile")
                        Text(modifier = Modifier.padding(5.dp),
                            text = chunkedAlbums[it][album].name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.music.spotui.ui.theme.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                    }
                }

            }
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeAlbums(
    album : List<AlbumsModel>,
    navController: NavController
) {
    val reversedAlbum = album.reversed().dropLast(1)
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Albums",
        color = com.music.spotui.ui.theme.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        LazyRow(modifier = Modifier.padding(6.dp)){
            items(reversedAlbum.size){ album ->
                Box(modifier = Modifier
                    .padding(10.dp)
                    .width(150.dp)
                    .height(195.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navController.navigate(albumRoute(reversedAlbum[album].name, reversedAlbum[album].artists))
                    }
            ){
                Column(
                    horizontalAlignment = Alignment.Start,
                    ) {

                    GlideImage(modifier = Modifier
                        .size(150.dp),
                        contentScale = ContentScale.Crop,
                        model = reversedAlbum[album].coverUri,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentDescription = "Albums")
                    Text(
                        fontSize = 13.sp,
                        text = reversedAlbum[album].name,
                        textAlign = TextAlign.Center,
                        color = com.music.spotui.ui.theme.TextPrimary,
                        fontWeight = FontWeight.Bold)
                    Text(
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        text = reversedAlbum[album].artists,
                        color = Color.LightGray)
                }

            }
        }
    }
}

@Composable
fun HomeRecentlyPlayed(
    navController: NavController,
    albums : List<String>
) {
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Recently Played",
        color = com.music.spotui.ui.theme.TextPrimary,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold)
    LazyRow(modifier = Modifier.padding(6.dp)){
        items(albums.size){
            Box(modifier = Modifier
                .padding(10.dp)
                .width(130.dp)
                .height(140.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navController.navigate(Routes.Player.route)
                }
            ){
                Column(horizontalAlignment = Alignment.Start) {
                    Image(modifier = Modifier
                        .size(120.dp)
                        .background(Color.Green),
                        contentScale = ContentScale.Crop,
                        painter = painterResource(id = R.drawable.album),
                        contentDescription = "Albums")
                    Text(modifier = Modifier.padding(2.dp),
                        text = "Album name",
                        color = com.music.spotui.ui.theme.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp)
                }

            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeArtists(
    artists : List<ArtistsModel>,
    navController: NavController
) {
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Best of Artists",
        color = com.music.spotui.ui.theme.TextPrimary,
        fontSize = 23.sp,
        fontWeight = FontWeight.Bold)
    LazyRow(modifier = Modifier.padding(6.dp)){
        items(artists.size){artist ->
            Box(modifier = Modifier
                .padding(10.dp)
                .width(150.dp)
                .height(200.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    Log.d("check", artists[artist].name)
                    navController.navigate(artistRoute(artists[artist].name, artists[artist].id))
                }
            ){
                Column(horizontalAlignment = Alignment.Start) {



                    GlideImage(modifier = Modifier
                        .size(150.dp),
                        contentScale = ContentScale.Crop,
                        model = artists[artist].coverUri,
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentDescription = "Albums")
                    Text(modifier = Modifier.padding(2.dp),
                        text = "This is ${artists[artist].name}",
                        color = Color.LightGray,
                        fontSize = 11.sp)
                }

            }
        }
    }
}


@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageCard(
    navController: NavController,
    allAlbums: List<AlbumsModel>,
    modifier: Modifier = Modifier
) {

    val albums = allAlbums.takeLast(3)
    Text(modifier = Modifier
        .padding(20.dp, 10.dp, 0.dp, 0.dp),
        text = "Discover",
        color = com.music.spotui.ui.theme.TextPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold)
    Column(
        modifier = Modifier.padding(0.dp, 10.dp, 0.dp, 50.dp)
    ) {
        repeat(albums.size) { album ->
            Card(
                shape = RoundedCornerShape(15.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 5.dp
                ),
                modifier = Modifier
                    .padding(15.dp)
                    .fillMaxWidth()
                    .height(380.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                       navController.navigate(albumRoute(albums[album].name, albums[album].artists))
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    GlideImage(
                        modifier = Modifier.fillMaxSize(),
                        model = albums[album].coverUri,
                        contentDescription = "artists",
                        loading = placeholder(R.drawable.placeholder),
                        failure = placeholder(R.drawable.placeholder),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(AppBackground.toArgb())
                                    ),
                                    startY = 150f
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(0.dp, 0.dp, 0.dp, 30.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "Album : ${albums[album].name}",
                            style = TextStyle(color = com.music.spotui.ui.theme.TextPrimary, fontSize = 20.sp),
                            textAlign = TextAlign.Center
                        )

                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

/** Offline banner, shown on Home and Library while offline. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val count = remember { com.music.spotui.data.preferences.getDownloadedSongs(context).size }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(com.music.spotui.ui.theme.SaffronDeep.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("●", color = com.music.spotui.ui.theme.Saffron, fontSize = 10.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Offline · showing $count downloaded ${if (count == 1) "song" else "songs"}",
            color = com.music.spotui.ui.theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )
    }
}

/** Home while offline: your downloads, ready to play. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun OfflineHome(navController: NavController) {
    val context = LocalContext.current
    val playerViewModel: com.music.spotui.ui.viewmodel.PlayerViewModel = hiltViewModel()
    val songs = remember { com.music.spotui.data.preferences.getDownloadedSongs(context) }
    val offline = com.music.spotui.data.preferences.OfflineState

    fun playFrom(list: List<com.music.spotui.data.entity.SongsModel>, index: Int) {
        if (list.isEmpty()) return
        playerViewModel.updateQueue(list)
        playerViewModel.playSongAt(list, index, context)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 190.dp),
        modifier = Modifier.fillMaxSize().background(com.music.spotui.ui.theme.AppBackground),
    ) {
        item {
            Text(
                "You're offline",
                color = com.music.spotui.ui.theme.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
            )
            Text(
                if (offline.manual) "Offline mode is on. Only downloaded music plays." else "No internet. Only downloaded music plays.",
                color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OfflineBanner(Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                if (songs.isNotEmpty()) {
                    OfflineAction("Play", filled = true) { playFrom(songs, 0) }
                    OfflineAction("Shuffle") { playFrom(songs.shuffled(), 0) }
                }
                if (offline.manual) OfflineAction("Go online") { offline.setManual(context, false) }
            }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    "Nothing downloaded yet. Go online and tap the download button on any song, album or playlist.",
                    color = com.music.spotui.ui.theme.TextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        items(songs.size) { i ->
            val song = songs[i]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { playFrom(songs, i) }
                    .padding(horizontal = 20.dp, vertical = 7.dp),
            ) {
                GlideImage(
                    model = song.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                    loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(song.title, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(song.singer, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OfflineAction(label: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) com.music.spotui.ui.theme.OnSaffron else com.music.spotui.ui.theme.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) com.music.spotui.ui.theme.Saffron else com.music.spotui.ui.theme.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

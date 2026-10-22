package com.music.spotui.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import com.music.spotui.ui.components.swipeDownToDismiss
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.ui.screens.SpotifyLoginScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.music.spotui.ui.screens.AlbumScreen
import com.music.spotui.ui.screens.ArtistReleasesScreen
import com.music.spotui.ui.screens.ArtistScreen
import com.music.spotui.ui.screens.CategoryScreen
import com.music.spotui.ui.screens.YouTubeLoginScreen
import com.music.spotui.ui.screens.LocalFilesScreen
import com.music.spotui.ui.screens.SpotiflacVerifyScreen
import com.music.spotui.ui.screens.DownloadsScreen
import com.music.spotui.ui.screens.HistoryScreen
import com.music.spotui.ui.screens.HomeScreen
import com.music.spotui.ui.screens.LibraryScreen
import com.music.spotui.ui.screens.LikedSongsScreen
import com.music.spotui.ui.screens.PlayerScreen
import com.music.spotui.ui.screens.PlaylistScreen
import com.music.spotui.ui.screens.ShowScreen
import com.music.spotui.ui.screens.QueueScreen
import com.music.spotui.ui.screens.SearchScreen
import com.music.spotui.ui.screens.SettingsScreen
import com.music.spotui.ui.viewmodel.PlayerViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MyNavHost(
    navHostController: NavHostController,
    bottomBarState: MutableState<Boolean>,
    bottomBarPlayerState: MutableState<Boolean>
) {

    val playerViewModel : PlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.currentSongTitle

    Log.d("player", playerState.toString())

//    val context = LocalContext.current
//    var player : ExoPlayer? = null
//    player = ExoPlayer.Builder(context).build()

    val context = LocalContext.current
    // First launch (no Spotify session) lands on the login screen.
    // New users: Aalap welcome → connect your music → Home. YouTube Music is
    // recommended, not required (JioSaavn/Gaana play without any login).
    val startDestination = when {
        !com.music.spotui.data.preferences.isAalapOnboarded(context) -> Routes.Login.route
        SpotifySession.spDc(context).isBlank() -> Routes.MusicSource.route
        else -> Routes.Home.route
    }

    // Restore the last session: put the track back into the mini player (paused)
    // and arm the player to resume from the saved position on the first play tap.
    LaunchedEffect(Unit) {
        if (playerViewModel.currentSongTitle.value.isBlank()) {
            com.music.spotui.data.preferences.loadLastPlayback(context)?.let { (song, positionMs) ->
                playerViewModel.updateQueue(listOf(song))
                playerViewModel.updateSongState(
                    song.coverUri, song.title, song.singer, false, song.id, 0, song.album)
                com.music.spotui.di.SongPlayer.setRestorePoint(song.url, positionMs)
            }
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = startDestination,
        // Smooth, directional motion everywhere: a page opens by gliding in from the right
        // (the one beneath drifts left and dims), and closes the reverse way. The bottom tabs
        // cross-fade. The screen under the player/queue stays put so the sheet visibly
        // slides over it and back down into the mini player.
        enterTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) TabEnter else PushEnter
        },
        exitTransition = {
            when {
                targetState.destination.route in SheetRoutes -> ExitTransition.None
                isTabSwitch(initialState.destination.route, targetState.destination.route) -> TabExit
                else -> PushExit
            }
        },
        popEnterTransition = {
            when {
                initialState.destination.route in SheetRoutes -> EnterTransition.None
                isTabSwitch(initialState.destination.route, targetState.destination.route) -> TabEnter
                else -> PopEnter
            }
        },
        popExitTransition = {
            if (isTabSwitch(initialState.destination.route, targetState.destination.route)) TabExit else PopExit
        },
    ){
        composable(Routes.Login.route){
            LaunchedEffect(Unit) {
                bottomBarState.value = false
                bottomBarPlayerState.value = false
            }
            com.music.spotui.ui.screens.WelcomeScreen(navHostController)
        }
        composable(Routes.SpotifyConnect.route){
            LaunchedEffect(Unit) {
                bottomBarState.value = false
                bottomBarPlayerState.value = false
            }
            SpotifyLoginScreen(navHostController)
        }
        composable(Routes.Home.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
                HomeScreen(navHostController)
        }
        composable(Routes.Search.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            SearchScreen(navHostController)
        }
        composable(Routes.Library.route) {
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            LibraryScreen(navHostController)
        }
        // The player and queue are layers over the page you came from, which stays alive
        // underneath, so closing them is just a slide (see SheetLayer).
        dialog(Routes.Player.route, dialogProperties = SheetWindow) {
            SheetLayer(onClosed = { navHostController.popBackStack() }) { PlayerScreen(navHostController) }
        }

        dialog(Routes.Queue.route, dialogProperties = SheetWindow) {
            SheetLayer(onClosed = { navHostController.popBackStack() }) { QueueScreen(navHostController) }
        }

        composable(Routes.Liked.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { LikedSongsScreen(navHostController) }
        }

        composable(Routes.Downloads.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { DownloadsScreen(navHostController) }
        }

        composable(Routes.Settings.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { SettingsScreen(navHostController) }
        }

        composable("mood/{mood}") { entry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { com.music.spotui.ui.screens.MoodScreen(navHostController, entry.arguments?.getString("mood").orEmpty()) }
        }

        composable(Routes.Sound.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { com.music.spotui.ui.screens.SoundScreen(navHostController) }
        }

        composable(Routes.Today.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { com.music.spotui.ui.screens.TodayScreen(navHostController) }
        }

        composable(Routes.ArtistPicker.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { com.music.spotui.ui.screens.ArtistPickerScreen(navHostController) }
        }

        composable(Routes.Wrapped.route){
            LaunchedEffect(Unit) {
                bottomBarState.value = false
                bottomBarPlayerState.value = false
            }
            PullDownToClose(navHostController) { com.music.spotui.ui.screens.WrappedScreen(navHostController) }
        }

        composable(Routes.History.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { HistoryScreen(navHostController) }
        }

        composable(Routes.MusicSource.route) {
            LaunchedEffect(Unit) {
                bottomBarState.value = false
                bottomBarPlayerState.value = false
            }
            com.music.spotui.ui.screens.ConnectServicesScreen(navHostController)
        }

        composable(
            "${Routes.YouTubeLogin.route}?next={next}",
            arguments = listOf(navArgument("next") { defaultValue = "" }),
        ) { entry ->
            LaunchedEffect(Unit) {
                bottomBarState.value = false
                bottomBarPlayerState.value = false
            }
            YouTubeLoginScreen(navHostController, entry.arguments?.getString("next").orEmpty())
        }

        composable(Routes.LocalFiles.route){
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            PullDownToClose(navHostController) { LocalFilesScreen(navHostController) }
        }

        composable(
            "${Routes.SpotiflacVerify.route}?next={next}",
            arguments = listOf(navArgument("next") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = false
                bottomBarPlayerState.value = playerState != ""
            }
            SpotiflacVerifyScreen(
                navHostController,
                next = navBackStackEntry.arguments?.getString("next").orEmpty(),
            )
        }

        composable(
            "${Routes.Category.route}/{genre}?title={title}",
            arguments = listOf(navArgument("title") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            val genre = navBackStackEntry.arguments?.getString("genre").orEmpty()
            val title = navBackStackEntry.arguments?.getString("title").orEmpty()
            PullDownToClose(navHostController) { CategoryScreen(navHostController, genre = genre, title = title.ifBlank { genre }) }
        }

        composable(
            "${Routes.Album.route}/{uString}?artist={artist}",
            arguments = listOf(navArgument("artist") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }

            /* Extracting the id from the route */
            val uId = navBackStackEntry.arguments?.getString("uString")
            val artist = navBackStackEntry.arguments?.getString("artist").orEmpty()
            /* We check if it's not null */
            uId?.let { id->
                PullDownToClose(navHostController) { AlbumScreen(navController = navHostController, albumName = id, artist = artist) }
            }
        }

        composable(
            "${Routes.Playlist.route}/{pId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            val pId = navBackStackEntry.arguments?.getString("pId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            PullDownToClose(navHostController) { pId?.let { PlaylistScreen(navHostController, playlistId = it, playlistName = name) } }
        }

        composable(
            "${Routes.Show.route}/{sId}?name={name}",
            arguments = listOf(navArgument("name") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            val sId = navBackStackEntry.arguments?.getString("sId")
            val name = navBackStackEntry.arguments?.getString("name").orEmpty()
            PullDownToClose(navHostController) { sId?.let { ShowScreen(navHostController, showId = it, showName = name) } }
        }

        composable("${Routes.ArtistReleases.route}/{aString}") { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }
            val aId = navBackStackEntry.arguments?.getString("aString")
            PullDownToClose(navHostController) { aId?.let { ArtistReleasesScreen(navHostController, it) } }
        }

        composable(
            "${Routes.Artist.route}/{aString}?id={artistId}",
            arguments = listOf(navArgument("artistId") { defaultValue = "" }),
        ) { navBackStackEntry ->
            LaunchedEffect(playerState) {
                bottomBarState.value = true
                bottomBarPlayerState.value = playerState != ""
            }

            /* Extracting the id from the route */
            val aId = navBackStackEntry.arguments?.getString("aString")
            val artistId = navBackStackEntry.arguments?.getString("artistId").orEmpty()
            /* We check if it's not null */
            aId?.let { aid->
                PullDownToClose(navHostController) { ArtistScreen(navHostController, aid, artistId) }
            }
        }
    }
}

/** Full-screen sheets that rise out of / sink back into the mini player. */
private val SheetRoutes = setOf(Routes.Player.route, Routes.Queue.route)

private val TabRoutes = setOf(Routes.Home.route, Routes.Search.route, Routes.Library.route)
private fun isTabSwitch(from: String?, to: String?) = from in TabRoutes && to in TabRoutes

private val MotionEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f) // Material "emphasized"

/** Opening a page: glide in from the right. */
private val PushEnter: EnterTransition =
    androidx.compose.animation.slideInHorizontally(tween(340, easing = MotionEasing)) { it / 3 } + fadeIn(tween(240))
/** The page beneath drifts a little left and fades. */
private val PushExit: ExitTransition =
    androidx.compose.animation.slideOutHorizontally(tween(340, easing = MotionEasing)) { -it / 10 } + fadeOut(tween(200))
/** Going back: the page beneath glides back from the left… */
private val PopEnter: EnterTransition =
    androidx.compose.animation.slideInHorizontally(tween(320, easing = MotionEasing)) { -it / 10 } + fadeIn(tween(240))
/** …while the closing page slides off to the right. */
private val PopExit: ExitTransition =
    androidx.compose.animation.slideOutHorizontally(tween(300, easing = MotionEasing)) { it / 3 } + fadeOut(tween(200))
/** Bottom tabs: a soft cross-fade with a hint of scale. */
private val TabEnter: EnterTransition =
    fadeIn(tween(260, delayMillis = 60)) + androidx.compose.animation.scaleIn(tween(320, easing = MotionEasing), initialScale = 0.98f)
private val TabExit: ExitTransition = fadeOut(tween(160))

private val SheetEasing = androidx.compose.animation.core.FastOutSlowInEasing
private val SheetOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)

/** Opening: rise from the bottom while growing out of the mini-player box. */
private val sheetEnter: EnterTransition =
    androidx.compose.animation.slideInVertically(tween(380, easing = SheetEasing)) { it / 2 } +
        androidx.compose.animation.scaleIn(tween(380, easing = SheetEasing), initialScale = 0.86f, transformOrigin = SheetOrigin) +
        fadeIn(tween(220))

/** Closing: sink down and shrink back into the mini-player box. */
private val sheetExit: ExitTransition =
    androidx.compose.animation.slideOutVertically(tween(320, easing = SheetEasing)) { it / 2 } +
        androidx.compose.animation.scaleOut(tween(320, easing = SheetEasing), targetScale = 0.86f, transformOrigin = SheetOrigin) +
        fadeOut(tween(260))

private val SheetWindow = androidx.compose.ui.window.DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
    dismissOnBackPress = false, // SheetLayer slides it away instead
    dismissOnClickOutside = false,
)

/** Detail pages close with the same pull-down gesture as the player. */
@Composable
private fun PullDownToClose(navController: NavHostController, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .swipeDownToDismiss { navController.navigateUp() }
    ) { content() }
}

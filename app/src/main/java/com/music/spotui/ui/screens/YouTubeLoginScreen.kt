package com.music.spotui.ui.screens

import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.music.spotui.data.preferences.MusicSource
import com.music.spotui.data.preferences.setPrimaryMusicSource
import com.music.spotui.data.preferences.setYoutubeLogin
import com.music.spotui.ui.navigation.Routes
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private const val YOUTUBE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/** Meld-style Google login: captures and validates cookie, visitor id and data-sync id together. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(navController: NavController, next: String = "") {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Connecting to YouTube Music") }
    var hasError by remember { mutableStateOf(false) }
    val validating = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(750)
            if (validating.get()) continue
            if (webView?.url?.startsWith("https://music.youtube.com") != true) continue
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val hasSapisid = cookie.split(';').any { it.substringBefore('=').trim() == "SAPISID" }
            if (!hasSapisid) continue
            if (!validating.compareAndSet(false, true)) continue

            status = "Connecting YouTube Music…"
            hasError = false
            YouTube.cookie = cookie
            YouTube.dataSyncId = null
            val visitorData = YouTube.visitorData().getOrNull()
            YouTube.visitorData = visitorData
            setYoutubeLogin(context, cookie, visitorData.orEmpty())
            YouTube.useLoginForBrowse = true
            setPrimaryMusicSource(context, MusicSource.YOUTUBE_MUSIC)
            status = "YouTube Music connected"
            // Bring in the YouTube Music library (likes, artists, recent plays) in the background.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.music.spotui.data.api.YouTubeLibrary.syncIfDue(context.applicationContext, force = true)
            }
            delay(350)
            when (next) {
                "spotiflac" -> {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.MusicSource.route) { inclusive = true }
                    }
                }
                "home" -> {
                    navController.navigate(Routes.Home.route) {
                        popUpTo(Routes.MusicSource.route) { inclusive = true }
                    }
                }
                else -> navController.popBackStack()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(com.music.spotui.ui.theme.AppBackground)) {
        AndroidView(
            // Above the keyboard and the gesture bar, so Next stays reachable.
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 42.dp).navigationBarsPadding().imePadding(),
            factory = { ctx ->
                val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
                WebView(ctx).apply {
                    webView = this
                    cookies.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    setUpForSignIn()
                    loadUrl(YOUTUBE_LOGIN_URL)
                }
            },
        )
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().height(42.dp).background(com.music.spotui.ui.theme.AppBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(status, color = if (hasError) Color(0xFFFF5252) else Color(0xFFFF0033),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
}

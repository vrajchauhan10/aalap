package com.music.aalap

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.notification.PlaybackService
import com.music.aalap.ui.theme.AalapTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var currentSongState: com.music.aalap.di.CurrentSongState

    /** The phone's speech recogniser, for the voice button; what you say goes to VoicePlay. */
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val words = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == RESULT_OK && !words.isNullOrBlank()) {
            com.music.aalap.di.VoicePlay.handle(this, currentSongState, com.music.aalap.di.VoicePlay.Request(words), fromApp = true)
        }
    }

    private fun listen() {
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Play a song, an artist or a mood")
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        runCatching { speech.launch(i) }.onFailure {
            android.widget.Toast.makeText(this, "This phone has no speech recognition", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** A voice assistant or an aalap://play link asked for music. */
    private fun handlePlayIntent(intent: android.content.Intent?) {
        if (intent?.action == com.music.aalap.di.VoiceAsk.ACTION) { listen(); return }
        val req = com.music.aalap.di.VoicePlay.fromIntent(intent)
            ?: intent?.data?.takeIf { it.scheme == "aalap" && it.host == "play" }?.let {
                com.music.aalap.di.VoicePlay.Request(it.getQueryParameter("q").orEmpty())
            }
            ?: return
        com.music.aalap.di.VoicePlay.handle(this, currentSongState, req, fromApp = true)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePlayIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Back in the app: read the connection again (see OfflineState.recheck).
        com.music.aalap.data.preferences.OfflineState.recheck()
    }


    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?){
        // Opening animation: keep the splash until the animated mark has played once,
        // then the splash fades into the app.
        val splash = androidx.core.splashscreen.SplashScreen.run { this@MainActivity.installSplashScreen() }
        val openedAt = android.os.SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { android.os.SystemClock.uptimeMillis() - openedAt < 850 }
        splash.setOnExitAnimationListener { view ->
            // Opening from the notification or a widget, Android shows the splash without an
            // icon (iconView is null then and reading it crashed the app); only animate it if present.
            val icon = runCatching { view.iconView }.getOrNull()
            // A quiet hand-off: the mark eases back a touch as the splash fades into Home.
            icon?.animate()?.scaleX(0.92f)?.scaleY(0.92f)?.alpha(0f)?.setDuration(260)
                ?.setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())?.start()
            view.view.animate().alpha(0f).setStartDelay(60).setDuration(280)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction { view.remove() }.start()
        }

        super.onCreate(savedInstanceState)
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Ask for notification permission (Android 13+) so the media notification shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Connect a controller to bootstrap the MediaSessionService: this brings up
        // the system media notification and keeps playback alive in the background.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()


        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {

            AalapTheme {
                // A surface container using the 'background' color from the theme
                    App()
            }
        }

        // Experimental Spotify web-player engine: attach its hidden WebView AFTER
        // setContent so the Compose content view doesn't replace/orphan it (an
        // orphaned WebView gets a 0×0 viewport and Spotify won't render/navigate).
        if (com.music.aalap.data.api.SpotifySession.spDc(this).isNotBlank()) {
            com.music.aalap.di.SpotifyWebPlayer.attach(this)
        }
        // Opened by a voice request (not when Android rebuilds the screen: it was handled then).
        if (savedInstanceState == null) handlePlayIntent(intent)
        // The mic on Home.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { com.music.aalap.di.VoiceAsk.requests.collect { listen() } }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        // Keep the player across recreation (theme/dark-mode switch) and while music
        // plays in the background; the PlaybackService owns playback from here.
        if (isFinishing && !isChangingConfigurations && !SongPlayer.isPlaying()) {
            SongPlayer.release()
        }
    }
}



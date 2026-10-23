package com.music.spotui

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
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.ui.theme.AalapTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        // Back in the app: read the connection again (see OfflineState.recheck).
        com.music.spotui.data.preferences.OfflineState.recheck()
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
            val icon = view.iconView
            // A quiet hand-off: the mark eases back a touch as the splash fades into Home.
            icon.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f).setDuration(260)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
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
        if (com.music.spotui.data.api.SpotifySession.spDc(this).isNotBlank()) {
            com.music.spotui.di.SpotifyWebPlayer.attach(this)
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



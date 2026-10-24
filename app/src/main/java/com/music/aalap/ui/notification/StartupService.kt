package com.music.aalap.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Holds Aalap in the foreground for the few seconds it takes to find a song after you
 * press play on the widget, notification or headset while the app is in the background.
 * Phones block background apps from the internet (battery saver, background limits),
 * and Media3 only becomes a foreground service once audio is loaded, so without this
 * the song couldn't be found. Stops as soon as the music plays, or after 20 seconds.
 */
class StartupService : Service() {
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Starting playback", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.music.aalap.R.drawable.media3_notification_small_icon)
            .setContentTitle("Aalap")
            .setContentText("Starting your music…")
            .setSilent(true)
            .setOngoing(true)
            .build()
        runCatching { ServiceCompat.startForeground(this, ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        foreground = true
        // The music already started before this service got going: it's done.
        if (stopRequested) { stopRequested = false; foreground = false; running = false; stopSelf(); return START_NOT_STICKY }
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ stopSelf() }, 20_000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        foreground = false
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "aalap_starting"
        private const val ID = 2001
        @Volatile var running = false
            private set

        fun start(c: Context) {
            stopRequested = false
            running = runCatching { ContextCompat.startForegroundService(c, Intent(c, StartupService::class.java)) }.isSuccess
        }

        @Volatile private var foreground = false
        @Volatile private var stopRequested = false

        fun stop(c: Context) {
            if (!running) return
            // Stopping before the service has called startForeground() makes Android kill
            // the app ("did not then call startForeground"), which happened when music
            // started very fast (a resume). Then it stops itself as soon as it's up.
            if (!foreground) { stopRequested = true; return }
            running = false
            foreground = false
            c.stopService(Intent(c, StartupService::class.java))
        }
    }
}

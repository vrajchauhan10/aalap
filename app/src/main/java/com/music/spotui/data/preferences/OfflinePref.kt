package com.music.spotui.data.preferences

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

private const val PREF = "offline_prefs"
private const val KEY_MANUAL = "offline_mode"
private const val KEY_AUTO_OFFLINE = "auto_offline"
private const val KEY_AUTO_DL_LIKED = "auto_download_liked"
private const val KEY_LAST_AUTO_DL = "last_auto_download"

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

/**
 * Offline mode, observable from Compose. Offline means: the switch is on, or
 * "go offline automatically" is on and the phone has no internet. While offline
 * only downloaded / local songs play and Home shows your downloads.
 */
object OfflineState {
    var manual by mutableStateOf(false)
        private set
    var autoWhenNoInternet by mutableStateOf(false)
        private set
    var autoDownloadLiked by mutableStateOf(false)
        private set
    var networkAvailable by mutableStateOf(true)
        private set
    /** Wi-Fi / unmetered: auto-downloads only run on these. */
    var unmetered by mutableStateOf(false)
        private set

    val isOffline: Boolean get() = manual || (autoWhenNoInternet && !networkAvailable)

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        manual = prefs(app).getBoolean(KEY_MANUAL, false)
        // On unless you turn it off: with no connection, Aalap switches to what's on the phone
        // instead of trying (and failing) to stream.
        autoWhenNoInternet = prefs(app).getBoolean(KEY_AUTO_OFFLINE, true)
        autoDownloadLiked = prefs(app).getBoolean(KEY_AUTO_DL_LIKED, false)

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivity = cm
        appContext = app
        fun refresh() = recheck()
        refresh()
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
                    // The phone blocks / unblocks a background app's internet (battery saver,
                    // background limits): that's not the connection going away.
                    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = refresh()
                },
            )
        }
    }

    private var connectivity: ConnectivityManager? = null
    private var appContext: Context? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Reads the connection again. While Android switches networks it can briefly report
     * none, and then send no further update; so "no internet" is checked twice more
     * (after 2 s and 8 s) before it's believed, and every time the app comes back.
     */
    fun recheck(force: Boolean = false) {
        val cm = connectivity ?: return
        val app = appContext ?: return
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        // force: a play press just lifted the background block; only ever turns "online" on.
        if (force && !online) return
        unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        if (online) {
            main.removeCallbacksAndMessages(RECHECK)
            networkAvailable = true
        } else if (networkAvailable) {
            main.removeCallbacksAndMessages(RECHECK)
            main.postAtTime({ confirmOffline(cm) }, RECHECK, android.os.SystemClock.uptimeMillis() + 2_000)
            main.postAtTime({ confirmOffline(cm) }, RECHECK, android.os.SystemClock.uptimeMillis() + 8_000)
        }
        AutoDownloader.maybeRun(app)
    }

    private val RECHECK = Any()

    private fun confirmOffline(cm: ConnectivityManager) {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        networkAvailable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun setManual(context: Context, on: Boolean) {
        manual = on
        prefs(context).edit().putBoolean(KEY_MANUAL, on).apply()
        AutoDownloader.maybeRun(context)
    }

    fun setAutoWhenNoInternet(context: Context, on: Boolean) {
        autoWhenNoInternet = on
        prefs(context).edit().putBoolean(KEY_AUTO_OFFLINE, on).apply()
    }

    fun setAutoDownloadLiked(context: Context, on: Boolean) {
        autoDownloadLiked = on
        prefs(context).edit().putBoolean(KEY_AUTO_DL_LIKED, on).apply()
        if (on) AutoDownloader.maybeRun(context, force = true)
    }
}

/** Keeps Liked Songs downloaded while on Wi-Fi, when the user turned that on. */
object AutoDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val MIN_INTERVAL_MS = 30 * 60 * 1000L

    /** Liked songs that still need downloading (from the last check). */
    @Volatile var pendingCount = 0
        private set

    fun maybeRun(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!OfflineState.autoDownloadLiked || OfflineState.manual || !OfflineState.unmetered) return
        val last = prefs(app).getLong(KEY_LAST_AUTO_DL, 0L)
        if (!force && System.currentTimeMillis() - last < MIN_INTERVAL_MS) return
        prefs(app).edit().putLong(KEY_LAST_AUTO_DL, System.currentTimeMillis()).apply()
        scope.launch {
            val liked = runCatching {
                com.music.spotui.data.api.Api(app).getLikedSongs().toList()
                    .filterIsInstance<com.music.spotui.data.api.Response.Success<List<com.music.spotui.data.entity.SongsModel>>>()
                    .lastOrNull()?.data
            }.getOrNull().orEmpty()
            val missing = liked.filter {
                !isDownloaded(app, it.id.toString()) && !com.music.spotui.di.Downloads.isDownloading(it.url)
            }
            pendingCount = missing.size
            if (missing.isNotEmpty()) com.music.spotui.di.Downloads.downloadAll(missing, app)
        }
    }
}

/** Downloaded songs + total size on disk, for the Offline card. */
fun downloadStorage(context: Context): Pair<Int, Long> {
    val entries = getDownloadedEntries(context)
    val bytes = entries.sumOf { (_, path) -> runCatching { java.io.File(path).length() }.getOrDefault(0L) }
    return entries.size to bytes
}

package com.music.aalap.data.preferences

import android.content.Context
import android.net.ConnectivityManager
import com.metrolist.music.constants.AudioQuality

/**
 * User-facing audio quality tiers (Spotify-style). Each maps to the YouTube format
 * selector ([AudioQuality]) and whether to attempt a lossless FLAC source first.
 */
enum class StreamQuality(
    val label: String,
    val detail: String,
    val audioQuality: AudioQuality,
    val lossless: Boolean,
) {
    LOW("Low", "Data saver — smallest size", AudioQuality.LOW, false),
    NORMAL("Normal", "Balanced for the network", AudioQuality.AUTO, false),
    HIGH("High", "Best compressed quality", AudioQuality.HIGH, false),
    LOSSLESS("Lossless", "FLAC when available, else High", AudioQuality.HIGH, true),
}

private const val PREF = "settings_prefs"
private const val KEY_WIFI_Q = "stream_quality_wifi"
private const val KEY_CELL_Q = "stream_quality_cellular"
private const val KEY_DL_Q = "download_quality"
private const val KEY_PRELOAD = "preload_enabled"
private const val KEY_CROSSFADE_MS = "crossfade_duration_ms"
private const val KEY_CROSSFADE_DJ = "crossfade_dj_mode"
private const val KEY_WEB_PLAYBACK = "web_playback_enabled"
private const val KEY_VIDEO_FALLBACK = "video_fallback_enabled"
private const val KEY_LIBRARY_GRID = "library_grid_view"
private const val KEY_LYRIC_TRANSLATE_LANG = "lyric_translate_lang"

/** Off (0s) … 12s. 0 disables crossfade. */
const val CROSSFADE_MIN_MS = 0
const val CROSSFADE_MAX_MS = 12000
const val CROSSFADE_DEFAULT_MS = 6000

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

private fun readQ(c: Context, key: String, def: StreamQuality): StreamQuality =
    runCatching { StreamQuality.valueOf(prefs(c).getString(key, def.name)!!) }.getOrDefault(def)

private fun writeQ(c: Context, key: String, q: StreamQuality) =
    prefs(c).edit().putString(key, q.name).apply()

fun getWifiQuality(c: Context): StreamQuality = readQ(c, KEY_WIFI_Q, StreamQuality.HIGH)
fun setWifiQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_WIFI_Q, q)

fun getCellularQuality(c: Context): StreamQuality = readQ(c, KEY_CELL_Q, StreamQuality.NORMAL)
fun setCellularQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_CELL_Q, q)

fun getDownloadQuality(c: Context): StreamQuality = readQ(c, KEY_DL_Q, StreamQuality.LOSSLESS)
fun setDownloadQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_DL_Q, q)

/** Library layout: false = rows (default), true = Spotify-style 3-column grid. */
fun isLibraryGridView(c: Context): Boolean = prefs(c).getBoolean(KEY_LIBRARY_GRID, false)
fun setLibraryGridView(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIBRARY_GRID, v).apply()

fun isPreloadEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_PRELOAD, true)
fun setPreloadEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PRELOAD, v).apply()

/**
 * YouTube account cookie (captured from an in-app WebView login). Passed to the
 * InnerTube client so age-restricted / login-required videos resolve. Empty when
 * not signed in — the app then uses anonymous YouTube access.
 */
private const val KEY_YT_COOKIE = "youtube_cookie"
private const val KEY_YT_VISITOR_DATA = "youtube_visitor_data"
private const val KEY_YT_DATA_SYNC_ID = "youtube_data_sync_id"
private const val KEY_YT_LOGIN_VERSION = "youtube_login_version"
private const val YT_LOGIN_VERSION = 2

/**
 * The YouTube login lives in its own file, left out of backups and device transfers
 * (see backup_rules.xml), so a login cookie never leaves the phone. Logins saved by older
 * versions in the general settings are moved here once.
 */
private fun ytPrefs(c: Context): android.content.SharedPreferences {
    val yt = c.getSharedPreferences("youtube_session", Context.MODE_PRIVATE)
    val old = prefs(c)
    if (old.contains(KEY_YT_COOKIE) || old.contains(KEY_YT_LOGIN_VERSION)) {
        yt.edit()
            .putString(KEY_YT_COOKIE, old.getString(KEY_YT_COOKIE, "").orEmpty())
            .putString(KEY_YT_VISITOR_DATA, old.getString(KEY_YT_VISITOR_DATA, "").orEmpty())
            .putString(KEY_YT_DATA_SYNC_ID, old.getString(KEY_YT_DATA_SYNC_ID, "").orEmpty())
            .putInt(KEY_YT_LOGIN_VERSION, old.getInt(KEY_YT_LOGIN_VERSION, 0))
            .commit()
        old.edit().remove(KEY_YT_COOKIE).remove(KEY_YT_VISITOR_DATA).remove(KEY_YT_DATA_SYNC_ID).remove(KEY_YT_LOGIN_VERSION).commit()
    }
    return yt
}

fun getYoutubeCookie(c: Context): String = ytPrefs(c).getString(KEY_YT_COOKIE, "").orEmpty()
fun setYoutubeCookie(c: Context, v: String) = ytPrefs(c).edit().putString(KEY_YT_COOKIE, v).apply()
fun getYoutubeVisitorData(c: Context): String = ytPrefs(c).getString(KEY_YT_VISITOR_DATA, "").orEmpty()
fun getYoutubeDataSyncId(c: Context): String = ytPrefs(c).getString(KEY_YT_DATA_SYNC_ID, "").orEmpty()

/** Handles both primary-channel and delegated-channel DATASYNC_ID shapes used by YouTube. */
fun normalizeYoutubeDataSyncId(value: String): String = when {
    "||" !in value -> value
    value.endsWith("||") -> value.substringBefore("||")
    else -> value.substringAfter("||")
}

fun setYoutubeLogin(c: Context, cookie: String, visitorData: String = "", dataSyncId: String = "") {
    ytPrefs(c).edit()
        .putString(KEY_YT_COOKIE, cookie)
        .putString(KEY_YT_VISITOR_DATA, visitorData)
        .putString(KEY_YT_DATA_SYNC_ID, normalizeYoutubeDataSyncId(dataSyncId))
        .putInt(KEY_YT_LOGIN_VERSION, YT_LOGIN_VERSION)
        .apply()
}

fun clearYoutubeLogin(c: Context) {
    ytPrefs(c).edit()
        .remove(KEY_YT_COOKIE)
        .remove(KEY_YT_VISITOR_DATA)
        .remove(KEY_YT_DATA_SYNC_ID)
        .remove(KEY_YT_LOGIN_VERSION)
        .apply()
}

/** SAPISID is required to sign InnerTube requests; a generic Google cookie is not a login. */
fun isYoutubeLoggedIn(c: Context): Boolean =
    ytPrefs(c).getInt(KEY_YT_LOGIN_VERSION, 0) >= YT_LOGIN_VERSION &&
        getYoutubeCookie(c).split(';').any { it.substringBefore('=').trim() == "SAPISID" }

/**
 * Play audio through Spotify's own web player in a hidden WebView (real Spotify
 * streaming, no decryption/bypass). DEFAULT OFF (and hidden from Settings) — the
 * YouTube/FLAC engine is the primary source; this path is real-time only with no
 * download/crossfade support.
 */
fun isWebPlaybackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_WEB_PLAYBACK, false)
fun setWebPlaybackEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_WEB_PLAYBACK, v).apply()

fun isVideoFallbackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_VIDEO_FALLBACK, true)
fun setVideoFallbackEnabled(c: Context, v: Boolean) =
    prefs(c).edit().putBoolean(KEY_VIDEO_FALLBACK, v).apply()

/** Lyric translation target (e.g. en, ja). Empty = device language. */
fun getLyricTranslateLang(c: Context): String =
    prefs(c).getString(KEY_LYRIC_TRANSLATE_LANG, "").orEmpty()

fun setLyricTranslateLang(c: Context, code: String) =
    prefs(c).edit().putString(KEY_LYRIC_TRANSLATE_LANG, code.trim()).apply()

/**
 * Crossfade overlap length in ms (0 = off). When > 0, the end of each track is blended
 * into the start of the next over this window.
 */
fun getCrossfadeMs(c: Context): Int = prefs(c).getInt(KEY_CROSSFADE_MS, 0)
fun setCrossfadeMs(c: Context, ms: Int) =
    prefs(c).edit().putInt(KEY_CROSSFADE_MS, ms.coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)).apply()

fun isCrossfadeEnabled(c: Context): Boolean = getCrossfadeMs(c) > 0

/** DJ-style mixing: low-pass the outgoing track and high-pass the incoming one during the blend. */
fun isCrossfadeDjMode(c: Context): Boolean = prefs(c).getBoolean(KEY_CROSSFADE_DJ, false)
fun setCrossfadeDjMode(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CROSSFADE_DJ, v).apply()

/**
 * The streaming quality to use for the *current* active network: the cellular setting
 * on a metered connection (mobile data / metered hotspot), the Wi-Fi setting otherwise.
 */
fun currentStreamingQuality(c: Context): StreamQuality {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val isMetered = cm?.isActiveNetworkMetered ?: false
    return if (isMetered) getCellularQuality(c) else getWifiQuality(c)
}

/** App appearance: follow the phone's dark-mode setting, or force one palette. */
enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

private const val KEY_THEME_MODE = "theme_mode"

fun getThemeMode(c: Context): ThemeMode =
    runCatching { ThemeMode.valueOf(prefs(c).getString(KEY_THEME_MODE, null) ?: "") }
        .getOrDefault(ThemeMode.SYSTEM)

fun setThemeMode(c: Context, mode: ThemeMode) =
    prefs(c).edit().putString(KEY_THEME_MODE, mode.name).apply()

private const val KEY_FAST_LOADING = "fast_loading"

/**
 * Fast loading: start a song after ~0.5 s of audio, keep buffering up to the whole
 * track, and prepare the next two queue songs ahead. Uses more data on heavy skipping.
 */
fun isFastLoading(c: Context): Boolean = prefs(c).getBoolean(KEY_FAST_LOADING, true)
fun setFastLoading(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_FAST_LOADING, v).apply()

private const val KEY_NOTIF_BUTTONS = "notification_extra_buttons"

/** Show Like and Shuffle next to the transport buttons in the media notification. */
fun isNotificationExtraButtons(c: Context): Boolean = prefs(c).getBoolean(KEY_NOTIF_BUTTONS, true)
fun setNotificationExtraButtons(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_NOTIF_BUTTONS, v).apply()

private const val KEY_JIOSAAVN = "jiosaavn_enabled"

/** JioSaavn as an audio source (raced with YouTube; used when it has the exact song). */
fun isJioSaavnEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_JIOSAAVN, true)
fun setJioSaavnEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_JIOSAAVN, v).apply()

private const val KEY_GAANA = "gaana_enabled"

/** Gaana as the last audio fallback (after YouTube Music and JioSaavn). */
fun isGaanaEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_GAANA, true)
fun setGaanaEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_GAANA, v).apply()

/** Looping song videos behind the player. */
enum class SongVideos(val label: String, val subtitle: String) {
    ALWAYS("Always", "Show song videos on Wi-Fi and mobile data"),
    WIFI("On Wi-Fi only", "Save mobile data"),
    OFF("Off", "Always show the album art"),
}

private const val KEY_SONG_VIDEOS = "song_videos"

/** Off unless you turn them on (from 6.3; earlier versions had them on, so that's reset once). */
fun getSongVideos(c: Context): SongVideos {
    val p = prefs(c)
    if (!p.getBoolean("song_videos_default_off", false)) {
        p.edit().putBoolean("song_videos_default_off", true).putString(KEY_SONG_VIDEOS, SongVideos.OFF.name).apply()
    }
    return runCatching { SongVideos.valueOf(p.getString(KEY_SONG_VIDEOS, null) ?: "OFF") }.getOrDefault(SongVideos.OFF)
}

fun setSongVideos(c: Context, v: SongVideos) = prefs(c).edit().putString(KEY_SONG_VIDEOS, v.name).apply()

/** Whether a song video may play right now (setting + current network). */
fun songVideosAllowed(c: Context): Boolean = when (getSongVideos(c)) {
    SongVideos.ALWAYS -> true
    SongVideos.OFF -> false
    SongVideos.WIFI -> {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }
}

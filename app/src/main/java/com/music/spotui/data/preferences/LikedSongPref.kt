package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel

/** Bumped on every like or unlike, so song tags on screen can refresh. */
val likedVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

/** The song liked most recently and when, so its Liked tag can pop in just once. */
@Volatile var lastLikedId: String? = null
@Volatile var lastLikedAtMs: Long = 0L

fun addLikedSongId(context: Context, songId: String) {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString(songId, songId)
    editor.apply()
    lastLikedId = songId
    lastLikedAtMs = System.currentTimeMillis()
    likedVersion.value++
}

fun removeLikedSongId(context: Context, songId: String) {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.remove(songId)
    editor.apply()
    likedVersion.value++
}

fun isSongLiked(context: Context, songId: String): Boolean {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    return sharedPreferences.contains(songId)
}

fun getLikedSongIds(context: Context): Set<Int> {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    return sharedPreferences.all.keys.mapNotNull { it.toIntOrNull() }.toSet()
}

fun getSongsByIds(songIds: Set<Int>, songs: List<SongsModel>): List<SongsModel> {
    return songs.filter { song -> song.id in songIds }
}
/**
 * Mirrors a Spotify Liked Songs fetch in one write. A [complete] fetch also drops ids
 * an earlier fetch brought in that Spotify no longer has (unliked elsewhere). Likes
 * made only in this app were never in that earlier set, so they stay.
 */
fun syncSpotifyLikedSongIds(context: Context, ids: Set<String>, complete: Boolean) {
    val seed = context.getSharedPreferences("LikedSongsFromSpotify", Context.MODE_PRIVATE)
    val previous = seed.getStringSet("ids", null).orEmpty()
    context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE).edit().apply {
        if (complete) (previous - ids).forEach { remove(it) }
        ids.forEach { putString(it, it) }
    }.apply()
    if (complete) seed.edit().putStringSet("ids", ids).apply()
}

/**
 * Mirrors the YouTube Music liked songs into the app's likes (same idea as the
 * Spotify mirror): songs unliked on YouTube since the last sync are dropped, unless
 * they're also liked on Spotify or were liked here.
 */
fun syncYouTubeLikedSongIds(context: Context, ids: Set<String>) {
    val seed = context.getSharedPreferences("LikedSongsFromYouTube", Context.MODE_PRIVATE)
    val previous = seed.getStringSet("ids", null).orEmpty()
    val fromSpotify = context.getSharedPreferences("LikedSongsFromSpotify", Context.MODE_PRIVATE).getStringSet("ids", null).orEmpty()
    context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE).edit().apply {
        (previous - ids - fromSpotify).forEach { remove(it) }
        ids.forEach { putString(it, it) }
    }.apply()
    seed.edit().putStringSet("ids", ids).apply()
}

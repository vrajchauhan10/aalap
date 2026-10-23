package com.music.aalap.data.preferences

import android.content.Context
import com.music.aalap.data.entity.SongsModel

/** Bumped on every like or unlike, so song tags on screen can refresh. */
val likedVersion = kotlinx.coroutines.flow.MutableStateFlow(0)

/** The song liked most recently and when, so its Liked tag can pop in just once. */
@Volatile var lastLikedId: String? = null
@Volatile var lastLikedAtMs: Long = 0L

/**
 * Likes a song. [song] (or, without it, the queue's copy) is remembered with the like,
 * so a song liked here shows in Liked Songs straight away, even one Spotify and
 * YouTube Music don't have. Song id 0 is "nothing playing" and is never liked.
 */
fun addLikedSongId(context: Context, songId: String, song: SongsModel? = null) {
    val id = songId.toIntOrNull() ?: return
    if (id == 0) return
    (song ?: com.music.aalap.di.SongPlayer.queuedSong(id))?.let { AalapLikes.add(context, it) }
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString(songId, songId)
    editor.apply()
    lastLikedId = songId
    lastLikedAtMs = System.currentTimeMillis()
    likedVersion.value++
    // A like is a strong taste signal: rebuild the taste with it straight away.
    com.music.aalap.data.recommendation.engine.Personalizer.invalidate()
}

fun removeLikedSongId(context: Context, songId: String) {
    val sharedPreferences = context.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.remove(songId)
    editor.apply()
    songId.toIntOrNull()?.let { AalapLikes.remove(context, it) }
    likedVersion.value++
    com.music.aalap.data.recommendation.engine.Personalizer.invalidate()
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

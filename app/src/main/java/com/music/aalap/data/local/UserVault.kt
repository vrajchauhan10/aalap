package com.music.aalap.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Each Aalap user's own library on this phone. Logging out saves it under the account's
 * email and clears it, so the next person starts fresh; logging back in with the same
 * email puts it all back: likes (including songs liked only in Aalap), Aalap playlists,
 * listening history, recently played, followed artists, removed playlists, the cached
 * Spotify / YouTube Music library, your recommendation settings, Blends and mixes. Listening stats and taste are already kept
 * per email in the recommendation database. Downloads stay on the phone for everyone.
 *
 * Stored in the app's private files (`files/users/<hash of email>.json`), never online.
 */
object UserVault {
    /** The settings files that hold one person's library. */
    internal val FILES = listOf(
        "LikedSongs", "LikedSongsFromSpotify", "LikedSongsFromYouTube", "aalap_likes",
        "aalap_playlists", "ListeningHistory", "RecentItems", "FollowedArtists", "LikedAlbums",
        "hidden_playlists", "favourite_albums", "spotify_taste", "youtube_library",
        // Your settings for recommendations (discovery, languages, follow my pace), your
        // Blends and today's mixes made for you: they're yours, so they go and come back
        // with you too.
        "reco_prefs", "blend_sync", "blend", "home_mixes", "LibraryCache",
    )

    private fun file(c: Context, email: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(c.filesDir, "users").apply { mkdirs() }, "$hash.json")
    }

    fun has(c: Context, email: String) = email.isNotBlank() && file(c, email).exists()

    /** Saves [email]'s library from the app's settings files. */
    fun save(c: Context, email: String) {
        if (email.isBlank()) return
        val out = JSONObject()
        for (name in FILES) {
            val values = JSONObject()
            c.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                values.put(k, when (v) {
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> return@forEach
                })
            }
            out.put(name, values)
        }
        val f = file(c, email)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(out.toString())
        tmp.renameTo(f)
    }

    /** Empties the library (after saving it), so the next person starts fresh. */
    fun clear(c: Context) {
        for (name in FILES) c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        refreshScreens()
    }

    /** Puts [email]'s saved library back. Returns false when there's nothing saved for it. */
    fun restore(c: Context, email: String): Boolean {
        val f = file(c, email)
        if (!f.exists()) return false
        val saved = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return false
        for (name in FILES) {
            val values = saved.optJSONObject(name) ?: continue
            val e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { k ->
                val o = values.getJSONObject(k)
                when (o.getString("t")) {
                    "s" -> e.putString(k, o.getString("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "set" -> e.putStringSet(k, o.getJSONArray("v").let { a -> (0 until a.length()).mapTo(HashSet()) { a.getString(it) } })
                }
            }
            e.commit()
        }
        refreshScreens()
        return true
    }

    /** In-memory copies of the library are dropped, so every screen reads the new one. */
    private fun refreshScreens() {
        com.music.aalap.data.api.YouTubeLibrary.invalidateUnified()
        com.music.aalap.data.recommendation.engine.Personalizer.invalidate()
        com.music.aalap.data.api.Api.HomeCache.clear()
        AalapPlaylists.reload()
        HiddenPlaylists.reload()
        com.music.aalap.data.preferences.likedVersion.value++
    }
}

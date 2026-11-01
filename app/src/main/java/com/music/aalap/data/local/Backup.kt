package com.music.aalap.data.local

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Backing up your library to a file you choose (Drive, Downloads, another phone…), and
 * putting it back. It holds what only lives on this phone: likes (including ones made
 * only in Aalap), Aalap playlists, listening history, followed artists, recommendation
 * settings, Blends, sound settings, and what Aalap learned about your taste (plays,
 * skips, song tempos). It never holds sign-ins (Spotify / YouTube cookies) or downloads.
 */
object Backup {
    private const val FORMAT = 1
    private val FILES = UserVault.FILES + "sound_prefs"

    class Summary(val madeAt: Long, val plays: Int, val appVersion: String)

    /** Writes a backup to [uri] (from the system's "save as" screen). Runs off the main thread. */
    fun write(c: Context, uri: Uri) {
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
        val json = JSONObject()
            .put("aalap_backup", FORMAT)
            .put("made_at", System.currentTimeMillis())
            .put("app_version", version)
            .put("prefs", UserVault.dump(c, FILES))
            .put("db", com.music.aalap.data.recommendation.engine.RecoDb.get(c).exportRows())
        c.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(json.toString()) }
    }

    /** Reads a backup file; null when it isn't one (any other file is left alone). */
    fun read(c: Context, uri: Uri): JSONObject? = runCatching {
        val json = c.contentResolver.openInputStream(uri)!!.bufferedReader().use { JSONObject(it.readText()) }
        json.takeIf { it.optInt("aalap_backup") == FORMAT && it.has("prefs") }
    }.getOrNull()

    fun summary(json: JSONObject) = Summary(
        madeAt = json.optLong("made_at"),
        plays = json.optJSONObject("db")?.optJSONArray("play_history")?.length() ?: 0,
        appVersion = json.optString("app_version"),
    )

    /**
     * Puts a backup back: the library files are replaced by the backup's, and the
     * backup's listening and taste are added to what's here (nothing learned is lost).
     */
    fun restore(c: Context, json: JSONObject) {
        UserVault.load(c, json.getJSONObject("prefs"), FILES)
        json.optJSONObject("db")?.let { rows ->
            val db = com.music.aalap.data.recommendation.engine.RecoDb.get(c)
            db.importRows(rows)
            // Restored under another email (a new phone, a different sign-in): the
            // backup's listening becomes this account's, so its taste isn't left behind.
            val from = rows.optJSONArray("user_taste")?.optJSONObject(0)?.optString("user_id").orEmpty()
            if (from.isNotBlank()) db.adoptUser(from, com.music.aalap.data.recommendation.engine.RecoDb.userId(c))
        }
        UserVault.refreshScreens()
        com.music.aalap.audio.AudioEffects.apply(c)
    }
}

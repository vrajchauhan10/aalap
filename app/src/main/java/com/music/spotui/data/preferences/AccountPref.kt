package com.music.spotui.data.preferences

import android.content.Context

/**
 * The Aalap account: the name and email the user chose in Aalap. Connected services
 * (Spotify, YouTube Music) keep their own account names; Aalap shows its own name in
 * the UI and lists the connected names alongside it.
 */
private const val PREF = "aalap_account"
private const val KEY_NAME = "name"
private const val KEY_EMAIL = "email"
private const val KEY_ONBOARDED = "onboarded"

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

fun getAalapName(c: Context): String = prefs(c).getString(KEY_NAME, null).orEmpty()
fun getAalapEmail(c: Context): String = prefs(c).getString(KEY_EMAIL, null).orEmpty()

fun setAalapAccount(c: Context, name: String, email: String) {
    // Listening logged on this phone before (or without) an email stays with this person.
    val before = com.music.spotui.data.recommendation.engine.RecoDb.userId(c)
    // Signing back in with an email used on this phone before brings that person's
    // library back (likes, Aalap playlists, history…); see UserVault.
    com.music.spotui.data.local.UserVault.restore(c, email)
    prefs(c).edit().putString(KEY_NAME, name.trim()).putString(KEY_EMAIL, email.trim()).putBoolean(KEY_ONBOARDED, true).commit()
    val after = com.music.spotui.data.recommendation.engine.RecoDb.userId(c)
    Thread {
        runCatching { com.music.spotui.data.recommendation.engine.RecoDb.get(c).adoptUser(before, after) }
        com.music.spotui.data.recommendation.engine.Personalizer.invalidate()
    }.start()
}

fun setAalapName(c: Context, name: String) = prefs(c).edit().putString(KEY_NAME, name.trim()).apply()

/** Finished the welcome step (installs from before 2.0 that are already connected count too). */
fun isAalapOnboarded(c: Context): Boolean =
    prefs(c).getBoolean(KEY_ONBOARDED, false) || com.music.spotui.data.api.SpotifySession.spDc(c).isNotBlank()

/** The name to show: the Aalap name if set, else [fallback] (e.g. the connected account's name). */
fun aalapDisplayName(c: Context, fallback: String): String = getAalapName(c).ifBlank { fallback }

/**
 * Logging out of Aalap saves this person's library under their email and clears it
 * (UserVault), then forgets the account and the welcome step. Their listening stats
 * stay in the recommendation database under their email. Anything played before the
 * next sign-in goes to whoever signs in next.
 */
fun clearAalapAccount(c: Context) {
    val email = getAalapEmail(c)
    if (email.isNotBlank()) {
        com.music.spotui.data.local.UserVault.save(c, email)
        com.music.spotui.data.local.UserVault.clear(c)
    }
    prefs(c).edit().clear().commit()
}

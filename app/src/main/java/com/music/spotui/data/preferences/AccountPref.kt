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
 * Logging out of Aalap forgets the welcome step too. The email is kept as the last
 * account, so listening logged after logout (and before signing back in) isn't lost.
 */
fun clearAalapAccount(c: Context) {
    val email = getAalapEmail(c)
    prefs(c).edit().clear().putString(KEY_EMAIL, email).apply()
}

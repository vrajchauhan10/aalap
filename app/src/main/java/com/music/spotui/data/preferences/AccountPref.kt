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

fun setAalapAccount(c: Context, name: String, email: String) =
    prefs(c).edit().putString(KEY_NAME, name.trim()).putString(KEY_EMAIL, email.trim()).putBoolean(KEY_ONBOARDED, true).apply()

fun setAalapName(c: Context, name: String) = prefs(c).edit().putString(KEY_NAME, name.trim()).apply()

/** Finished the welcome step (installs from before 2.0 that are already connected count too). */
fun isAalapOnboarded(c: Context): Boolean =
    prefs(c).getBoolean(KEY_ONBOARDED, false) || com.music.spotui.data.api.SpotifySession.spDc(c).isNotBlank()

/** The name to show: the Aalap name if set, else [fallback] (e.g. the connected account's name). */
fun aalapDisplayName(c: Context, fallback: String): String = getAalapName(c).ifBlank { fallback }

/** Logging out of Aalap forgets the welcome step too. */
fun clearAalapAccount(c: Context) = prefs(c).edit().clear().apply()

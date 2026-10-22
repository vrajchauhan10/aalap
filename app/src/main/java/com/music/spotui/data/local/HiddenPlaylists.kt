package com.music.spotui.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Spotify playlists removed from the Aalap library. They stay on Spotify; library
 * syncs skip them until they're shown again from the account menu in Library.
 */
object HiddenPlaylists {
    private const val PREF = "hidden_playlists"
    private const val KEY = "ids"
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun ids(c: Context): Set<String> = prefs(c).getStringSet(KEY, emptySet()).orEmpty()

    fun isHidden(c: Context, id: String) = id in ids(c)

    fun hide(c: Context, id: String) {
        prefs(c).edit().putStringSet(KEY, ids(c) + id).apply()
        _version.value++
    }

    fun showAll(c: Context) {
        prefs(c).edit().remove(KEY).apply()
        _version.value++
    }
}

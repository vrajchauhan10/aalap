package com.music.aalap.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which Home sections are hidden. Everything shows until you hide it, so an update changes
 * nothing for anyone who never opens this. Kept per phone (it's about the screen, not taste).
 */
object HomeSections {
    /** Key → name shown in the list, in Home's order. */
    val ALL = listOf(
        "recent" to "Recently played",
        "today" to "Aaj ka Aalap",
        "quick" to "Quick picks",
        "radio" to "Aalap Radio",
        "charts" to "Charts",
        "mixes" to "Made for you",
        "jump" to "Jump back in",
        "daily" to "Daily songs",
        "artistRadios" to "Artist radios",
        "releases" to "New releases",
        "more" to "More to explore",
    )

    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("home_sections", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        hidden = prefs(c).getStringSet("hidden", emptySet())!!.toSet()
    }

    fun shows(key: String) = key !in hidden

    fun set(c: Context, key: String, show: Boolean) {
        hidden = if (show) hidden - key else hidden + key
        prefs(c).edit().putStringSet("hidden", hidden).apply()
    }
}

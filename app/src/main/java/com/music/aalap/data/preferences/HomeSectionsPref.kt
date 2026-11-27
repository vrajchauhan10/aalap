package com.music.aalap.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Home's sections: their order and which are hidden. Aaj ka Aalap ([PINNED]) always stays
 * first; the rest can be moved and hidden from "Edit Home".
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
        "podcasts" to "Popular podcasts",
        "charts" to "Charts",
        "mixes" to "Made for you",
        "jump" to "Jump back in",
        "daily" to "Daily songs",
        "artistRadios" to "Artist radios",
        "releases" to "New releases",
        "more" to "More to explore",
    )

    /** Always at the top of Home, and can't be moved or hidden. */
    const val PINNED = "today"

    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set

    /** The movable sections, top to bottom (everything but [PINNED]). */
    var order by mutableStateOf(defaultOrder())
        private set
    private var loaded = false

    fun defaultOrder() = ALL.map { it.first }.filter { it != PINNED }

    fun label(key: String) = ALL.firstOrNull { it.first == key }?.second ?: key

    /** A saved order, with sections added in later versions appended and unknown ones dropped. */
    fun normalise(saved: List<String>): List<String> {
        val known = defaultOrder()
        val kept = saved.filter { it in known }.distinct()
        return kept + known.filter { it !in kept }
    }

    /** Moves [key] one place up (-1) or down (+1). */
    fun move(c: Context, key: String, by: Int) {
        val i = order.indexOf(key); val j = i + by
        if (i < 0 || j !in order.indices) return
        order = order.toMutableList().also { it.add(j, it.removeAt(i)) }
        prefs(c).edit().putString("order", order.joinToString(",")).apply()
    }

    fun resetOrder(c: Context) {
        order = defaultOrder()
        prefs(c).edit().remove("order").apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("home_sections", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        hidden = prefs(c).getStringSet("hidden", emptySet())!!.toSet() - PINNED
        prefs(c).getString("order", null)?.let { order = normalise(it.split(",")) }
    }

    fun shows(key: String) = key == PINNED || key !in hidden

    fun set(c: Context, key: String, show: Boolean) {
        hidden = if (show) hidden - key else hidden + key
        prefs(c).edit().putStringSet("hidden", hidden).apply()
    }
}

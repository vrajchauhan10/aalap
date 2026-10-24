package com.music.aalap.data.preferences

import android.content.Context
import org.json.JSONObject

/**
 * Your settings for recommendations (Settings → Recommendations).
 *
 *  - The discovery dial: how much you hear songs you know vs new ones.
 *  - The language mix: how much of each language; empty = follow what you're playing.
 */
object RecoPrefs {
    private const val PREF = "reco_prefs"

    /** The dial's stops: share of known songs, share of surprises. */
    enum class Discovery(val label: String, val blurb: String, val familiar: Double?, val surprise: Double) {
        FAMILIAR("Familiar", "Mostly songs you know", 0.75, 0.0),
        MOSTLY_FAMILIAR("Mostly familiar", "Songs you know, some new", 0.55, 0.05),
        BALANCED("Balanced", "Learns from what you finish", null, 0.1),
        EXPLORE("Explore", "Mostly new songs", 0.2, 0.15),
        SURPRISE("Surprise me", "New songs and artists you don't usually play", 0.08, 0.3),
    }

    /** Languages offered in the mix (code to label). */
    val LANGUAGES = listOf(
        "hindi" to "Hindi", "punjabi" to "Punjabi", "english" to "English", "gujarati" to "Gujarati",
        "tamil" to "Tamil", "telugu" to "Telugu", "marathi" to "Marathi", "bengali" to "Bengali",
    )

    /** How much of a language: 0 none, 1 some, 2 lots. */
    val LEVELS = listOf("Off", "Some", "Lots")

    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun discovery(c: Context): Discovery =
        runCatching { Discovery.valueOf(p(c).getString("discovery", null) ?: "BALANCED") }.getOrDefault(Discovery.BALANCED)

    fun setDiscovery(c: Context, d: Discovery) = p(c).edit().putString("discovery", d.name).apply()

    /** Levels per language (0..2); empty = automatic. */
    fun languageLevels(c: Context): Map<String, Int> = runCatching {
        val o = JSONObject(p(c).getString("languages", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    fun setLanguageLevels(c: Context, levels: Map<String, Int>) =
        p(c).edit().putString("languages", JSONObject(levels as Map<*, *>).toString()).apply()

    /** The mix as shares adding up to 1 (empty = automatic, or nothing chosen). */
    fun languageMix(c: Context): Map<String, Double> {
        val levels = languageLevels(c)
        val total = levels.values.sum()
        if (levels.isEmpty() || total == 0) return emptyMap()
        return levels.mapValues { it.value.toDouble() / total }
    }
}

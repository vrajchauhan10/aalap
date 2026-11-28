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

    /** How much of a language: 0 off (never plays), 1 low, 2 medium, 3 high. */
    val LEVELS = listOf("Off", "Low", "Medium", "High")

    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun discovery(c: Context): Discovery =
        runCatching { Discovery.valueOf(p(c).getString("discovery", null) ?: "BALANCED") }.getOrDefault(Discovery.BALANCED)

    fun setDiscovery(c: Context, d: Discovery) = p(c).edit().putString("discovery", d.name).apply()

    /** Mixes lean towards your pace lately (tempo and energy); on unless switched off. */
    fun followPace(c: Context): Boolean = p(c).getBoolean("follow_pace", true)
    fun setFollowPace(c: Context, on: Boolean) = p(c).edit().putBoolean("follow_pace", on).apply()

    /**
     * Levels per language (0 Off .. 3 High). Asked before you can enter the app (see
     * [languagesChosen]); empty only until then. Older versions saved 0..2 (Off/Some/Lots):
     * those come back as Off/Medium/High, pre-filled for you to confirm.
     */
    fun languageLevels(c: Context): Map<String, Int> = runCatching {
        p(c).getString("languages_v2", null)?.let { raw ->
            val o = JSONObject(raw)
            return@runCatching o.keys().asSequence().associateWith { o.getInt(it).coerceIn(0, 3) }
        }
        val o = JSONObject(p(c).getString("languages", "{}") ?: "{}")
        o.keys().asSequence().associateWith { when (o.getInt(it)) { 0 -> 0; 1 -> 2; else -> 3 } }
    }.getOrDefault(emptyMap())

    fun setLanguageLevels(c: Context, levels: Map<String, Int>) =
        p(c).edit().putString("languages_v2", JSONObject(levels.mapValues { it.value.coerceIn(0, 3) } as Map<*, *>).toString()).apply()

    /** Whether you've chosen your languages (needed once, after this update or a fresh install). */
    fun languagesChosen(c: Context): Boolean =
        p(c).getBoolean("languages_chosen", false) && languageLevels(c).values.any { it > 0 }

    fun setLanguagesChosen(c: Context) = p(c).edit().putBoolean("languages_chosen", true).apply()

    /**
     * Whether a song in [lang] may play. Your languages are a hard rule everywhere (next songs,
     * radio, mixes): an Off language never plays, and a song whose language isn't known doesn't
     * either (it used to slip through, which is how English songs reached a Hindi gym radio).
     * Before you've chosen, everything is allowed.
     */
    fun languageAllowed(c: Context, lang: String): Boolean = languageAllowed(languageMix(c), lang)

    fun languageAllowed(mix: Map<String, Double>, lang: String): Boolean {
        if (mix.isEmpty()) return true
        val l = lang.lowercase().trim()
        if (l.isBlank()) return false
        return mix.entries.any { (k, v) -> v > 0 && com.music.aalap.data.recommendation.engine.LanguageGuess.same(k, l) }
    }

    /**
     * "Mix languages": off by default. Off, you can still turn on several languages, but each
     * queue stays in the language of the song you started (a Hindi song, only Hindi next; an
     * English one, only English), and stations play your main language. On, queues and
     * stations blend your languages by their Low / Medium / High levels.
     */
    fun mixLanguages(c: Context): Boolean = p(c).getBoolean("mix_languages", false)
    fun setMixLanguages(c: Context, on: Boolean) = p(c).edit().putBoolean("mix_languages", on).apply()

    /** Whether queues blend languages: Mix is on and more than one language is turned on. */
    fun mixing(mixOn: Boolean, mix: Map<String, Double>): Boolean = mixOn && mix.count { it.value > 0 } > 1

    /** The language set highest (ties: alphabetical, so it doesn't change between runs). */
    fun mainLanguage(mix: Map<String, Double>): String =
        mix.entries.filter { it.value > 0 }.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key.orEmpty()

    /**
     * The one language a queue stays in ("" = blend, when [mixing]): the song you started's,
     * else the session's; when neither is one of your languages, your main language. Before
     * you've chosen languages, the song's (or session's) language, as before.
     */
    fun queueLanguage(mix: Map<String, Double>, mixOn: Boolean, songLang: String, sessionLang: String): String {
        if (mixing(mixOn, mix)) return ""
        if (mix.isEmpty()) return songLang.ifBlank { sessionLang }
        return listOf(songLang, sessionLang).firstOrNull { it.isNotBlank() && languageAllowed(mix, it) } ?: mainLanguage(mix)
    }

    /** The mix as shares adding up to 1 (empty = automatic, or nothing chosen). */
    fun languageMix(c: Context): Map<String, Double> {
        val levels = languageLevels(c)
        val total = levels.values.sum()
        if (levels.isEmpty() || total == 0) return emptyMap()
        return levels.mapValues { it.value.toDouble() / total }
    }
}

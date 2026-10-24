package com.music.aalap.data.recommendation.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The feel of a song. Every song gets a mix of these (a song can be romantic and
 * chill at once); [energy] is how high-tempo each one usually is.
 */
enum class Feel(val energy: Double, val label: String) {
    ENERGETIC(0.95, "Energetic"),
    PARTY(0.9, "Party"),
    HAPPY(0.7, "Happy"),
    ROMANTIC(0.45, "Romantic"),
    DEVOTIONAL(0.35, "Devotional"),
    CHILL(0.3, "Chill"),
    SAD(0.2, "Sad"),
    FOCUS(0.2, "Focus"),
}

/**
 * A song's feel as weights per [Feel] (summing to 1), how sure we are ([confidence],
 * 0..1), and its [styles] (lofi, rap, remix, sufi…), which work like genres.
 * Pure (no Android), so the queue rules can be tested on their own.
 */
class Vibe(
    val weights: Map<Feel, Double>,
    val confidence: Double,
    val styles: Set<String> = emptySet(),
    /** Measured from the sound itself while it played (0..1), when known. */
    val audioEnergy: Double? = null,
    /** Beats per minute, measured from the sound, when known. */
    val bpm: Double? = null,
) {

    val known: Boolean get() = confidence >= 0.15 && weights.isNotEmpty()

    /** Whether the energy is more than a guess (from the sound, or a known feel). */
    val energyKnown: Boolean get() = audioEnergy != null || known

    /** 0..1: how high-energy the song is: measured from the sound when known, else from its feel. 0.5 when unknown. */
    val energy: Double get() {
        val fromFeel = if (weights.isEmpty()) null else weights.entries.sumOf { it.key.energy * it.value }
        return when {
            audioEnergy != null && fromFeel != null -> 0.4 * fromFeel + 0.6 * audioEnergy
            audioEnergy != null -> audioEnergy
            fromFeel != null -> fromFeel
            else -> 0.5
        }
    }

    fun withAudio(energy: Double?, bpm: Double?): Vibe = Vibe(weights, confidence, styles, energy ?: audioEnergy, bpm ?: this.bpm)

    val dominant: Feel? get() = weights.maxByOrNull { it.value }?.key

    /** Cosine similarity of the two feel mixes (0..1). */
    fun similarity(other: Vibe): Double {
        if (weights.isEmpty() || other.weights.isEmpty()) return 0.0
        var dot = 0.0; var a = 0.0; var b = 0.0
        for (f in Feel.entries) {
            val x = weights[f] ?: 0.0; val y = other.weights[f] ?: 0.0
            dot += x * y; a += x * x; b += y * y
        }
        return if (a == 0.0 || b == 0.0) 0.0 else dot / sqrt(a * b)
    }

    override fun toString(): String =
        weights.entries.sortedByDescending { it.value }.take(2).joinToString("+") { "${it.key.label} %.0f%%".format(it.value * 100) } +
            " (E %.2f, conf %.2f%s%s)".format(energy, confidence, if (bpm != null) ", %.0f bpm".format(bpm) else "", if (styles.isEmpty()) "" else ", " + styles.joinToString("/"))

    companion object {
        val UNKNOWN = Vibe(emptyMap(), 0.0)

        /** Normalises raw evidence ([mass] = how much there was) into a vibe. */
        fun of(raw: Map<Feel, Double>, styles: Set<String> = emptySet()): Vibe {
            val pos = raw.filterValues { it > 0 }
            val mass = pos.values.sum()
            if (mass <= 0) return Vibe(emptyMap(), 0.0, styles)
            // Sharpened (power 1.5) so the strongest feels stand out over a spread of weak hints.
            val sharp = pos.mapValues { Math.pow(it.value / mass, 1.5) }
            val total = sharp.values.sum()
            return Vibe(sharp.mapValues { it.value / total }, 1.0 - exp(-mass / 1.6), styles)
        }

        /** Adds [b] (times [w]) to the raw evidence [a]. */
        fun addInto(a: MutableMap<Feel, Double>, b: Map<Feel, Double>, w: Double) {
            for ((f, v) in b) a[f] = (a[f] ?: 0.0) + v * w
        }

        /** 0..1 closeness in energy (1 = same tempo). */
        fun energyMatch(a: Double, b: Double): Double = 1.0 - abs(a - b)

        // ── words in titles and albums that say what a song feels like ──
        // Hindi, Punjabi, Urdu, Gujarati and English, as they're usually written in Latin script.
        // [strong] words decide a feel on their own; [weak] ones only nudge (they're common in any song).

        private class Rule(val feel: Feel, val strong: List<String>, val weak: List<String> = emptyList())

        private val RULES = listOf(
            Rule(Feel.DEVOTIONAL, listOf("bhajan", "aarti", "aarati", "mantra", "chalisa", "stuti", "stotram", "stotra", "shloka", "kirtan",
                "bhakti", "devotional", "gurbani", "shabad", "ardas", "namah", "namo", "hanuman", "mahadev", "bholenath", "mahakal",
                "ganpati", "ganesh", "durga", "ambe", "waheguru", "sai baba", "jai shri", "jai shree", "shri ram", "shree ram",
                "radhe", "krishna", "govind", "vitthal", "balaji", "khatu shyam", "om", "naat", "hamd", "satsang", "aradhana", "worship", "gospel", "hymn",
                "kun faya", "kun faaya", "khwaja", "sufi", "qawwali"),
                listOf("shiv", "shiva", "ram", "mata", "maa", "prabhu", "bhagwan", "devi", "allah", "maula", "khwaja", "dua")),
            Rule(Feel.PARTY, listOf("party", "remix", "dj", "club", "dance", "disco", "bhangra", "nachna", "naach", "thumka",
                "daaru", "daru", "sharaab", "sharab", "tequila", "vodka", "whiskey", "peg", "mashup", "dance floor", "jhoome",
                "garba", "dandiya", "dhol", "item song", "saturday", "party anthem"),
                listOf("nach", "nachle", "nache", "bottle", "shots", "beat", "move", "baby")),
            Rule(Feel.ENERGETIC, listOf("workout", "gym", "beast", "warrior", "attack", "drill", "phonk", "rage", "hustle", "banger",
                "khatarnak", "dhamaka", "josh", "zinda", "power", "unstoppable", "fight", "battle", "thunder", "rap", "trap",
                "sped up", "speed up", "anthem", "jatt", "gabru", "sher", "badmash", "bad boy", "boss", "gangsta", "goat", "hard"),
                listOf("fire", "run", "rise", "king", "machine", "bandook", "gun", "game", "rock", "bass")),
            Rule(Feel.HAPPY, listOf("happy", "khushi", "masti", "smile", "sunshine", "celebration", "shaadi", "wedding", "mubarak",
                "badhai", "mehendi", "sangeet", "baraat", "holi", "good vibes", "road trip", "cheerful", "joy", "naacho", "fun"),
                listOf("sunny", "summer", "life", "dosti", "yaar", "yaariyan", "friends", "zindagi", "gaana", "hello")),
            Rule(Feel.ROMANTIC, listOf("love", "pyaar", "pyar", "ishq", "mohabbat", "mohobbat", "romantic", "romance", "valentine",
                "humsafar", "sanam", "sajna", "saajna", "saiyaan", "saiyan", "piya", "jaanu", "jaana", "mehboob", "dilbar",
                "kiss", "darling", "crush", "raataan", "pehla pyaar", "with you", "forever", "falling"),
                listOf("dil", "jaan", "tera", "tere", "teri", "tu", "tujhe", "tumse", "tum", "heart", "baby", "chand", "aankhon", "sath", "saath")),
            Rule(Feel.SAD, listOf("sad", "dard", "bewafa", "judaai", "judai", "tanha", "tanhai", "alone", "broken", "toota", "tuta",
                "tute", "rona", "roya", "aansu", "aasu", "alvida", "dhokha", "gham", "akela", "akeli", "heartbreak", "breakup",
                "miss you", "tadap", "bichad", "bichhad", "virah", "tears", "cry", "pain", "hurt", "lonely", "mar jayenge", "maafi"),
                listOf("yaad", "yaadein", "phir", "kabhi", "lost", "goodbye", "door", "kasoor", "mere bina")),
            Rule(Feel.CHILL, listOf("lofi", "lo-fi", "lo fi", "chill", "slowed", "reverb", "unplugged", "acoustic", "relax", "calm",
                "mellow", "lounge", "late night", "sleep", "baarish", "barish", "monsoon", "coffee", "soothing", "peaceful", "soft"),
                listOf("night", "rain", "easy", "breeze", "slow", "stripped", "jam", "cover", "sukoon")),
            Rule(Feel.FOCUS, listOf("instrumental", "piano", "flute", "sitar", "violin", "santoor", "theme", "bgm", "background music",
                "ost", "score", "ambient", "meditation", "study", "focus", "white noise", "binaural", "lullaby", "karaoke", "tabla"),
                listOf("music", "tune", "dhun", "raag", "raga")),
        )

        /** Styles that work like genres; the session keeps to a style it's clearly in. */
        private val STYLES = listOf(
            "lofi" to listOf("lofi", "lo-fi", "lo fi", "slowed", "reverb"),
            "remix" to listOf("remix", "mashup", "dj"),
            "acoustic" to listOf("unplugged", "acoustic", "stripped", "cover"),
            "rap" to listOf("rap", "drill", "trap", "hip hop", "hip-hop", "cypher", "freestyle"),
            "sufi" to listOf("sufi", "qawwali", "kun faya", "kun faaya", "maula", "khwaja", "ali ali", "arziyan"),
            "ghazal" to listOf("ghazal", "nazm"),
            "classical" to listOf("raag", "raga", "classical", "carnatic", "hindustani"),
            "instrumental" to listOf("instrumental", "bgm", "theme", "piano", "flute", "karaoke"),
            "devotional" to listOf("bhajan", "aarti", "chalisa", "mantra", "kirtan", "gurbani", "shabad", "naat", "worship"),
            "folk" to listOf("folk", "lok geet", "garba", "dandiya", "lavani", "bihu", "boliyan"),
            "retro" to listOf("retro", "old is gold", "90s", "80s", "70s", "evergreen", "classic hits"),
        )

        /** [word] as a whole word (or phrase) in [text]. */
        private fun hit(text: String, word: String): Boolean {
            var i = text.indexOf(word)
            while (i >= 0) {
                val before = i == 0 || !text[i - 1].isLetterOrDigit()
                val end = i + word.length
                val after = end >= text.length || !text[end].isLetterOrDigit()
                if (before && after) return true
                i = text.indexOf(word, i + 1)
            }
            return false
        }

        /** What a song's title and album say about its feel (raw evidence, no normalising). */
        fun lexicon(title: String, album: String = ""): Map<Feel, Double> {
            val text = " " + (title + " " + album).lowercase().replace(Regex("[()\\[\\]{}|_.,!?:;\"'-]"), " ").replace(Regex("\\s+"), " ") + " "
            val out = HashMap<Feel, Double>()
            for (r in RULES) {
                val s = r.strong.count { hit(text, it) }
                val w = r.weak.count { hit(text, it) }
                val v = minOf(2.0, s * 1.0) + minOf(0.6, w * 0.25)
                if (v > 0) out[r.feel] = v
            }
            return out
        }

        fun styles(title: String, album: String = "", artists: String = ""): Set<String> {
            val text = " " + (title + " " + album + " " + artists).lowercase().replace(Regex("[()\\[\\]{}|_.,!?:;\"'-]"), " ").replace(Regex("\\s+"), " ") + " "
            return STYLES.filter { (_, words) -> words.any { hit(text, it) } }.mapTo(HashSet()) { it.first }
        }

        /** The feel a mood station ("Party", "Heartbroken"…) stands for. */
        fun forMoodName(name: String): Map<Feel, Double> = when (name.uppercase()) {
            "ENERGETIC" -> mapOf(Feel.ENERGETIC to 1.0)
            "HAPPY" -> mapOf(Feel.HAPPY to 1.0)
            "SAD" -> mapOf(Feel.SAD to 1.0)
            "HEARTBROKEN" -> mapOf(Feel.SAD to 0.8, Feel.ROMANTIC to 0.2)
            "ROMANTIC" -> mapOf(Feel.ROMANTIC to 1.0)
            "CHILL" -> mapOf(Feel.CHILL to 1.0)
            "PARTY" -> mapOf(Feel.PARTY to 1.0)
            "DEVOTIONAL" -> mapOf(Feel.DEVOTIONAL to 1.0)
            "FOCUS" -> mapOf(Feel.FOCUS to 1.0)
            "ROAD_TRIP" -> mapOf(Feel.HAPPY to 0.5, Feel.ENERGETIC to 0.5)
            else -> emptyMap()
        }
    }
}

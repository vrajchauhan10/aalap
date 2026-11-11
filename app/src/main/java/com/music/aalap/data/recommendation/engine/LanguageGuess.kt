package com.music.aalap.data.recommendation.engine

/** A song's likely language from its title (and album) words, for songs no service labels. Pure. */
object LanguageGuess {
    // Words that give a title's language away (Latin-script titles, as they're usually written).
    private val PUNJABI = setOf("jatt", "kudi", "munda", "mundeya", "sohneya", "sohniye", "tenu", "mainu", "tainu", "ve", "yaar", "pind", "gabru", "jatti", "naal", "kitthe", "lagda", "nachna", "dil da", "teri meri", "chann", "akhiyan", "patiala", "mitran", "kaala", "laung", "lehenga", "rabba", "ishq da")
    private val GUJARATI = setOf("garba", "chogada", "mane", "tame", "ramzat", "dhol baje", "rang moro", "taari", "maari", "valam", "kesariyo", "mogal", "khodiyar", "dandiya")
    private val HINDI = setOf("dil", "tum", "hai", "mera", "tera", "teri", "meri", "ishq", "pyaar", "pyar", "kya", "main", "hum", "ki", "ka", "ke", "se", "mein", "tujhe", "mujhe", "hoon", "kabhi", "zindagi", "sanam", "jaan", "aaj", "raat", "ye", "yeh", "woh", "kuch", "nahi", "tu", "hi", "ho", "bhi", "na", "aur", "saath")
    private val ENGLISH = setOf("the", "you", "me", "love", "my", "i", "and", "of", "in", "to", "it", "your", "we", "don't", "dont", "baby", "night", "heart", "girl", "feel", "like", "be", "all", "on", "with", "what", "this", "just", "can't", "know", "want", "way", "time", "life", "light", "lights")

    /**
     * Artists who sing (almost) only in Punjabi. Their titles are often plain Hindi words
     * ("Tenu", "Dil", "Ishq"), so the words alone called them Hindi or nothing, and Punjabi
     * songs slipped into Hindi listening. Lower-case, as artist names are compared.
     */
    private val PUNJABI_ARTISTS = setOf(
        "diljit dosanjh", "sidhu moose wala", "ap dhillon", "karan aujla", "shubh", "jass manak", "ammy virk",
        "harrdy sandhu", "gurnam bhullar", "babbu maan", "parmish verma", "garry sandhu", "jassie gill",
        "satinder sartaaj", "gippy grewal", "mankirt aulakh", "arjan dhillon", "sunanda sharma", "nimrat khaira",
        "afsana khan", "r nait", "amrit maan", "prem dhillon", "tegi pannu", "cheema y", "gur sidhu", "navaan sandhu",
        "sultaan", "bohemia", "hustinder", "kaka", "sukh-e muzical doctorz", "jaz dhami", "sharry mann", "ninja",
        "gurlez akhtar", "simar kaur", "jordan sandhu", "guri", "veet baljit", "dilpreet dhillon", "kulwinder billa",
        "rajvir jawanda", "sippy gill", "jasmine sandlas", "b praak", "yo yo honey singh", "imran khan", "zack knight",
        "jasmine sandlas", "mickey singh", "raftaar", "badshah", "guru randhawa", "jassi gill", "ranjit bawa",
        "kanwar grewal", "mehtab virk", "nirvair pannu", "gulab sidhu", "jasmeen akhtar", "the landers",
        "harf cheema", "baaz", "bilal saeed", "nseeb", "gurinder gill", "shinda kahlon", "yungsta",
    )
    // Sing in both: Punjabi only when the title says so too.
    private val MIXED = setOf("b praak", "yo yo honey singh", "badshah", "guru randhawa", "raftaar", "imran khan", "zack knight", "jasmine sandlas")

    /** The language an artist sings in, when it's clear from who they are ("" otherwise). */
    fun ofArtist(artists: String): String {
        val first = artists.split(",", "&", " x ", " X ").firstOrNull()?.trim()?.lowercase().orEmpty()
        return if (first in PUNJABI_ARTISTS && first !in MIXED) "punjabi" else ""
    }

    /** A title's likely language from its words ("" when it doesn't say). */
    fun of(title: String, album: String = ""): String {
        val words = (title + " " + album).lowercase().replace(Regex("""\(.*?\)|\[.*?\]"""), " ").split(Regex("[^\\p{L}']+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        // Non-Latin scripts say it directly.
        val text = words.joinToString(" ")
        if (text.any { it in '\u0A00'..'\u0A7F' }) return "punjabi"
        if (text.any { it in '\u0A80'..'\u0AFF' }) return "gujarati"
        if (text.any { it in '\u0900'..'\u097F' }) return "hindi"
        if (text.any { it in '\u0C00'..'\u0C7F' }) return "telugu"
        if (text.any { it in '\u0B80'..'\u0BFF' }) return "tamil"
        val p = words.count { it in PUNJABI }; val g = words.count { it in GUJARATI }
        val h = words.count { it in HINDI }; val e = words.count { it in ENGLISH }
        return when {
            p > 0 && p >= g -> "punjabi"
            g > 0 -> "gujarati"
            h > e -> "hindi"
            e > 0 && e >= words.size / 2 -> "english"
            else -> ""
        }
    }
}

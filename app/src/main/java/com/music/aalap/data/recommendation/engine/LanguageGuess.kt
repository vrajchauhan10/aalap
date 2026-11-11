package com.music.aalap.data.recommendation.engine

/** A song's likely language from its title (and album) words, for songs no service labels. Pure. */
object LanguageGuess {
    // Words that give a title's language away (Latin-script titles, as they're usually written).
    private val PUNJABI = setOf("jatt", "kudi", "munda", "mundeya", "sohneya", "sohniye", "tenu", "mainu", "tainu", "ve", "yaar", "pind", "gabru", "jatti", "naal", "kitthe", "lagda", "nachna", "dil da", "teri meri", "chann", "akhiyan", "patiala", "mitran", "kaala", "laung", "lehenga", "rabba", "ishq da")
    private val GUJARATI = setOf("garba", "chogada", "mane", "tame", "ramzat", "dhol baje", "rang moro", "taari", "maari", "valam", "kesariyo", "mogal", "khodiyar", "dandiya")
    private val HINDI = setOf("dil", "tum", "hai", "mera", "tera", "teri", "meri", "ishq", "pyaar", "pyar", "kya", "main", "hum", "ki", "ka", "ke", "se", "mein", "tujhe", "mujhe", "hoon", "kabhi", "zindagi", "sanam", "jaan", "aaj", "raat", "ye", "yeh", "woh", "kuch", "nahi", "tu", "hi", "ho", "bhi", "na", "aur", "saath")
    // Telugu and Tamil words as their titles are written in Latin letters ("Choosa Choosa",
    // "Kanulanu Thaake", "Neeve"). Only words that don't also mean something in Hindi.
    private val TELUGU = setOf("choosa", "choosi", "choodu", "chudu", "kanulanu", "kanulu", "kallu", "rendu", "mullu", "neeve", "nene", "nenu", "nuvvu", "nuvve", "ninnu", "ninne", "ninnila", "inkem", "kaavaale", "kavali", "endhuko", "enduko", "emaindi", "premalo", "manasu", "manase", "pilla", "chinnadana", "gundello", "gunde", "andala", "aakasam", "choopu", "naalo", "neelo", "nuvvante", "kaavali", "vachindi", "undi", "ledu", "telusa", "cheppu", "padipoya", "bava", "ammadi", "ramuloo", "ramulamma", "samajavaragamana", "butta", "bomma")
    private val TAMIL = setOf("kadhal", "kaadhal", "anbe", "uyire", "nenjame", "nenje", "kannamma", "kanmani", "penne", "ponnu", "vaada", "thalaivaa", "thalaiva", "unnai", "ennai", "illai", "pogum", "paadal", "kanne", "mazhai", "vizhi", "paattu", "thaane", "aaluma", "doluma", "kuthu", "machi", "otha", "kolaveri", "venmathi", "munbe", "vaathi", "pudhu", "nenjukkul", "azhagu", "kannazhaga", "thangamey", "pulla", "naanum", "neeyum", "enakku", "unakku")
    private val ENGLISH = setOf("the", "you", "me", "love", "my", "i", "and", "of", "in", "to", "it", "your", "we", "don't", "dont", "baby", "night", "heart", "girl", "feel", "like", "be", "all", "on", "with", "what", "this", "just", "can't", "know", "want", "way", "time", "life", "light", "lights")

    /**
     * Artists who sing (almost) only in Punjabi. Their titles are often plain Hindi words
     * ("Tenu", "Dil", "Ishq"), so the words alone called them Hindi or nothing, and Punjabi
     * songs slipped into Hindi listening. Lower-case, as artist names are compared.
     */
    private val PUNJABI_ARTISTS = setOf(
        "diljit dosanjh", "sidhu moose wala", "pav dharia", "ap dhillon", "karan aujla", "shubh", "jass manak", "ammy virk",
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

    /**
     * Singers and composers who work (almost) only in South Indian films. Hindi film songs carry
     * plain Hindi words, but Tamil/Telugu titles in Latin letters ("Ala Bolelo", "Pesamale")
     * don't, so without this they had no language and slipped into Hindi radio.
     */
    private val SOUTH_ARTISTS = mapOf(
        "tamil" to setOf(
            "arunraja kamaraj", "yuvan shankar raja", "harris jayaraj", "ilaiyaraaja", "hiphop tamizha",
            "santhosh narayanan", "dhanush", "thalapathy vijay", "vijay", "sai abhyankkar", "g. v. prakash kumar", "g. v. prakash", "g.v. prakash", "rabbit mac",
            "g.v. prakash kumar", "gv prakash", "d. imman", "d imman", "vijay antony", "sean roldan", "pradeep kumar",
            "leon james", "sivakarthikeyan", "anthony daasan", "gana bala", "velmurugan", "andrea jeremiah",
            "yogi b", "paal dabba", "asal kolaar", "ofro", "vivek - mervin", "ghibran", "c. sathya", "sid sriram",
            "kapil kapilan", "sathyaprakash", "haricharan", "vijay yesudas",
            "shweta mohan", "vandana srinivasan",
        ),
        "telugu" to setOf(
            "s. thaman", "thaman s", "anup rubens", "anantha sriram", "padmalatha", "sniggy", "ss thaman", "mickey j. meyer", "mangli", "rahul sipligunj", "ram miriyala",
            "anurag kulkarni", "kaala bhairava", "sri krishna", "hesham abdul wahab", "bheems ceciroleo",
            "geetha madhuri", "sunitha", "ramya behara", "dhanunjay", "sahithi chaganti", "mano",
        ),
        "kannada" to setOf("ajaneesh loknath", "arjun janya", "v. harikrishna", "sanjith hegde", "chandan shetty", "raghu dixit"),
        "malayalam" to setOf("sushin shyam", "vineeth sreenivasan", "gopi sundar", "shaan rahman", "jakes bejoy", "dabzee", "hesham"),
    )
    // Work in Hindi films too: only the other names on the song (or its title) decide.
    private val SOUTH_MIXED = setOf(
        "a.r. rahman", "a. r. rahman", "ar rahman", "anirudh ravichander", "devi sri prasad", "m. m. keeravani",
        "mm keeravani", "shreya ghoshal", "armaan malik", "javed ali", "shankar mahadevan", "sonu nigam",
        "kk", "benny dayal", "s. p. balasubrahmanyam", "k. s. chithra", "chinmayi", "jonita gandhi",
        "vijay prakash", "k. j. yesudas", "hariharan", "karthik", "shilpa rao", "sunidhi chauhan", "neeti mohan",
    )

    private fun names(artists: String) =
        artists.split(",", "&", " x ", " X ", " feat. ", " ft. ").map { it.trim().lowercase() }.filter { it.isNotBlank() }

    /** The language an artist sings in, when it's clear from who they are ("" otherwise). */
    fun ofArtist(artists: String): String {
        val all = names(artists)
        val first = all.firstOrNull().orEmpty()
        if (first in PUNJABI_ARTISTS && first !in MIXED) return "punjabi"
        // South: any of the names who only works there (composers who also do Hindi don't count).
        for (name in all) {
            if (name in SOUTH_MIXED) continue
            SOUTH_ARTISTS.entries.firstOrNull { name in it.value }?.let { return it.key }
        }
        return ""
    }

    private val LABELS = listOf("hindi", "tamil", "telugu", "kannada", "malayalam", "punjabi", "marathi", "bengali", "bhojpuri", "gujarati", "haryanvi", "urdu", "english")
    private val LABEL = Regex("""(?:\(|\[|-|\s)(${LABELS.joinToString("|")})(?:\s+(?:version|ver\.?|song|songs|cover))?\s*(?:\)|\]|"|$)""", RegexOption.IGNORE_CASE)

    /**
     * A language the title or album states outright: "Srivalli (Hindi)", "Naatu Naatu - Tamil
     * Version", "Pushpa (Telugu)". Dubbed versions say it this way, and it beats the singers.
     */
    fun labelled(title: String, album: String = ""): String =
        LABEL.find(title)?.groupValues?.get(1)?.lowercase()
            ?: LABEL.find(album)?.groupValues?.get(1)?.lowercase()
            ?: ""

    /** The language a title's letters are in (Tamil, Telugu… script), "" for Latin letters. */
    fun script(text: String): String = when {
        text.any { it in '\u0B80'..'\u0BFF' } -> "tamil"
        text.any { it in '\u0C00'..'\u0C7F' } -> "telugu"
        text.any { it in '\u0C80'..'\u0CFF' } -> "kannada"
        text.any { it in '\u0D00'..'\u0D7F' } -> "malayalam"
        text.any { it in '\u0A00'..'\u0A7F' } -> "punjabi"
        text.any { it in '\u0A80'..'\u0AFF' } -> "gujarati"
        text.any { it in '\u0980'..'\u09FF' } -> "bengali"
        text.any { it in '\u0900'..'\u097F' } -> "hindi"
        else -> ""
    }

    /** Hindi and Urdu film songs are one language to a listener. */
    fun same(a: String, b: String): Boolean {
        val x = a.lowercase().let { if (it == "urdu") "hindi" else it }
        val y = b.lowercase().let { if (it == "urdu") "hindi" else it }
        return x == y
    }

    /** A title's likely language from its words ("" when it doesn't say). */
    fun of(title: String, album: String = ""): String {
        val words = (title + " " + album).lowercase().replace(Regex("""\(.*?\)|\[.*?\]"""), " ").split(Regex("[^\\p{L}']+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        // Non-Latin scripts say it directly.
        script(words.joinToString(" ")).takeIf { it.isNotBlank() }?.let { return it }
        val te = words.count { it in TELUGU }; val ta = words.count { it in TAMIL }
        if (te > 0 && te >= ta) return "telugu"
        if (ta > 0) return "tamil"
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

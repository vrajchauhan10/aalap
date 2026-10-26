package com.music.aalap.data.recommendation.engine

/**
 * Which take of a song a title names: "Paro (Unplugged)", "KALYANI - Remix", "Bulleya
 * (Reprise)" are different recordings from "Paro", "KALYANI", "Bulleya". Every source that
 * finds a song to play (YouTube, JioSaavn, Gaana) must find the same take: the unplugged one
 * when you asked for it, never a remix you didn't. Whole words, so "live" isn't in "deliver".
 */
object SongVersion {
    private val KINDS = linkedMapOf(
        "remix" to """re\s*mix|rmx""",
        "unplugged" to """unplugged|acoustic""",
        "reprise" to """reprise|reprised""",
        "lofi" to """lo\s*-?\s*fi""",
        "slowed" to """slowed|reverb""",
        "sped up" to """sped\s*up|speed\s*up|nightcore""",
        "live" to """live""",
        "cover" to """cover|covered by|originally by""",
        "karaoke" to """karaoke|instrumental|minus one""",
        "mashup" to """mashup|mash\s*up""",
        "8d" to """8d""",
        "female" to """female version""",
        "male" to """male version""",
    ).mapValues { (_, p) -> Regex("""(^|[^\p{L}\p{N}])($p)([^\p{L}\p{N}]|$)""", RegexOption.IGNORE_CASE) }

    /** The kinds of take [title] names (empty = the song itself). */
    fun of(title: String): Set<String> = KINDS.filterValues { it.containsMatchIn(title) }.keys

    /** Whether a found title is the same take as the one asked for. */
    fun same(asked: String, found: String): Boolean = of(asked) == of(found)
}

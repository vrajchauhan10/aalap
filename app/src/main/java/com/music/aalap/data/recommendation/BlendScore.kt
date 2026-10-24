package com.music.aalap.data.recommendation

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * A Blend's score: how well your tastes match (from your two taste codes), raised by
 * listening to the Blend. Every Blend song either of you hears (30 s or more) in the
 * last 7 days adds a point; a song you have both heard adds two more. The points close
 * the gap to 99 step by step (the first songs count most), and fade out after a week,
 * so the score stays up while you both keep listening. Both phones work it out from
 * the same shared data, so it's the same on both. Pure, tested.
 */
object BlendScore {
    const val WINDOW_DAYS = 7
    private const val SCALE = 40.0

    /** One day of listening: song keys each of you heard. */
    data class Day(val mine: Set<String> = emptySet(), val theirs: Set<String> = emptySet())

    data class Points(val mine: Int, val theirs: Int, val together: Int) {
        val total: Int get() = mine + theirs + 2 * together
    }

    /** Points from the last [WINDOW_DAYS] days up to [today] (each song once per person). */
    fun points(days: Map<Long, Day>, today: Long): Points {
        val recent = days.filterKeys { it in (today - WINDOW_DAYS + 1)..today }.values
        val mine = recent.flatMapTo(HashSet()) { it.mine }
        val theirs = recent.flatMapTo(HashSet()) { it.theirs }
        return Points(mine.size, theirs.size, (mine intersect theirs).size)
    }

    fun score(match: Int, points: Int): Int =
        (match + (99 - match) * (1 - exp(-points / SCALE))).roundToInt().coerceIn(1, 99)

    fun score(match: Int, days: Map<Long, Day>, today: Long): Int = score(match, points(days, today).total)

    /** Points needed for the score to reach [target] (null if it can't). */
    fun pointsFor(match: Int, target: Int): Int? {
        if (target <= match) return 0
        if (target >= 99) return null
        // match + (99 - match)(1 - e^(-p/S)) >= target - 0.5
        val need = (target - 0.5 - match) / (99 - match)
        if (need >= 1) return null
        return kotlin.math.ceil(-SCALE * kotlin.math.ln(1 - need)).toInt().coerceAtLeast(1)
    }

    /** The next band's lower edge above [score] (Common ground at 30, Great match at 50…), or null at the top. */
    fun nextBand(score: Int): Int? = listOf(30, 50, 70, 85).firstOrNull { it > score }

    /** A song's key in a Blend: its Spotify id, or its title and first artist. */
    fun key(title: String, artists: String, spotifyId: String): String =
        spotifyId.ifBlank {
            fun n(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?\]"""), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            n(title) + "|" + n(artists.substringBefore(","))
        }
}

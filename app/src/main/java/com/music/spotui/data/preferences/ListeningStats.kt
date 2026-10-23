package com.music.spotui.data.preferences

/**
 * One way of counting listening, shared by History (Replay) and Aalap Wrapped so their
 * numbers always match.
 *
 * A play's time is what the listening clock measured (only while music was actually
 * playing). Plays from before the clock existed fall back to an estimate: until the next
 * song started, at most the song's length. A play under 30 s (or under half a short
 * song) is a skip: it adds its seconds but doesn't count as a play in rankings.
 */
object ListeningStats {
    private const val TYPICAL_SONG_MS = 210_000L

    class Play(val entry: HistoryEntry, val listenedMs: Long) {
        private val length = if (entry.durationMs > 0) entry.durationMs.toLong() else TYPICAL_SONG_MS
        val skipped: Boolean get() = listenedMs < minOf(30_000L, length / 2)
    }

    /** [history] newest first, as stored. */
    fun plays(history: List<HistoryEntry>): List<Play> = history.mapIndexed { i, e ->
        val length = if (e.durationMs > 0) e.durationMs.toLong() else TYPICAL_SONG_MS
        val gap = if (i == 0) Long.MAX_VALUE else history[i - 1].ts - e.ts
        Play(e, if (e.listenedMs >= 0) e.listenedMs else minOf(length, gap.coerceAtLeast(0)))
    }
}

package com.music.aalap.data.recommendation.engine

import android.content.Context
import kotlin.math.abs

/**
 * Your pace lately: the tempo and energy of what you've been playing, in any language.
 * Mixes lean towards it: after a run of fast Hindi songs, "Play English songs" starts with
 * fast English ones. Pure (no Android) apart from [RecentPace].
 */
object Pace {
    /** [energy] 0..1, [bpm] when enough songs were measured, from [songs] recent songs. */
    data class Target(val energy: Double, val bpm: Double?, val songs: Int) {
        val label: String get() = when {
            energy >= 0.62 -> "fast and lively"
            energy <= 0.38 -> "slow and easy"
            else -> "easy-going"
        }
    }

    private const val MIN_SONGS = 5

    /** From recent songs' vibes, newest first (newer ones count more); null if too few are known. */
    fun target(recent: List<Vibe>): Target? {
        val known = recent.filter { it.energyKnown }
        if (known.size < MIN_SONGS) return null
        var sum = 0.0; var weight = 0.0
        known.forEachIndexed { i, v -> val w = 1.0 / (1 + i * 0.08); sum += v.energy * w; weight += w }
        val bpms = known.mapNotNull { it.bpm }.filter { it in 50.0..220.0 }.sorted()
        val bpm = if (bpms.size >= 3) bpms[bpms.size / 2] else null
        return Target(sum / weight, bpm, known.size)
    }

    /** 0..1: how close a song is to your pace (energy first, tempo when both are measured). */
    fun closeness(t: Target, v: Vibe): Double {
        val e = (1.0 - abs(v.energy - t.energy) * 2.0).coerceIn(0.0, 1.0)
        val b = if (t.bpm != null && v.bpm != null) (1.0 - minOf(abs(v.bpm - t.bpm), abs(v.bpm * 2 - t.bpm), abs(v.bpm / 2 - t.bpm)) / 40.0).coerceIn(0.0, 1.0) else null
        // A song whose energy is only a guess counts as halfway: it neither leads nor trails.
        val c = if (b != null) 0.65 * e + 0.35 * b else e
        return if (v.energyKnown) c else 0.5
    }

    /**
     * [items] ordered by closeness to [t], with a little shuffle ([jitter]) so the same
     * request doesn't give the same order every time.
     */
    fun <T> rank(items: List<T>, t: Target?, vibeOf: (T) -> Vibe, random: kotlin.random.Random, jitter: Double = 0.25): List<T> {
        if (t == null) return items.shuffled(random)
        return items.map { it to closeness(t, vibeOf(it)) + random.nextDouble() * jitter }.sortedByDescending { it.second }.map { it.first }
    }
}

/** [Pace.Target] from your listening history (songs you heard at least 30 s). */
object RecentPace {
    private const val DAYS_3 = 3L * 24 * 3_600_000

    /** Your pace, or null when you've switched "Follow my pace" off. */
    fun target(context: Context): Pace.Target? =
        if (com.music.aalap.data.preferences.RecoPrefs.followPace(context)) measured(context) else null

    /** Your pace whether or not mixes follow it (for showing it). */
    fun measured(context: Context): Pace.Target? = runCatching {
        val all = com.music.aalap.data.preferences.getListeningHistory(context)
            .filter { it.listenedMs < 0 || it.listenedMs >= 30_000 }
            .sortedByDescending { it.ts }
        val now = System.currentTimeMillis()
        val lately = all.filter { now - it.ts < DAYS_3 }.let { if (it.size >= 8) it else all }.distinctBy { it.title.lowercase() + "|" + it.singer.lowercase() }.take(25)
        if (lately.isEmpty()) return null
        val models = lately.map { com.music.aalap.data.entity.SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs) }
        val songs = RecoDb.get(context).resolveAll(models.map(SongBridge::toCandidate))
        val vibes = VibeStore.vibes(context, songs)
        Pace.target(songs.map { vibes[it.id] ?: Vibe.UNKNOWN })
    }.getOrNull()
}

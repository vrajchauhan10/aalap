package com.music.aalap.data.recommendation

/**
 * Gym Freaks: which songs of the gym playlists suit a workout. A song's energy (its feel, and
 * the sound when Aalap has measured it) and tempo decide; a song Aalap knows nothing about yet
 * isn't judged, it just comes after the ones known to fit. Songs known to be calm or slow are
 * left out, unless that would leave too few to play. Pure (GymFitTest).
 */
object GymFit {
    data class Track<T>(val item: T, val energy: Double?, val bpm: Double?)

    /** Below this energy (0..1) a song is calm: Sad 0.2, Chill 0.3, Romantic 0.45. */
    const val MIN_ENERGY = 0.55
    /** Slower than this, a song needs clearly high energy to stay (a heavy half-time beat). */
    const val MIN_BPM = 95.0
    const val STRONG_ENERGY = 0.75
    /** Fewer songs than this after leaving out the calm ones: they're kept, at the end. */
    const val MIN_STATION = 15

    /** true: fits, false: calm or slow, null: not known yet. */
    fun fits(energy: Double?, bpm: Double?): Boolean? = when {
        energy == null && bpm == null -> null
        energy != null && energy < MIN_ENERGY -> false
        bpm != null && bpm < MIN_BPM && (energy == null || energy < STRONG_ENERGY) -> false
        else -> true
    }

    /** Known fits (most energetic first), then unknown songs in their order, then (if short) the rest. */
    fun <T> order(tracks: List<Track<T>>): List<T> {
        val byFit = tracks.groupBy { fits(it.energy, it.bpm) }
        val good = byFit[true].orEmpty().sortedByDescending { it.energy ?: STRONG_ENERGY }.map { it.item }
        val unknown = byFit[null].orEmpty().map { it.item }
        val calm = byFit[false].orEmpty().sortedByDescending { it.energy ?: 0.0 }.map { it.item }
        val kept = good + unknown
        return if (kept.size >= MIN_STATION) kept else kept + calm
    }
}

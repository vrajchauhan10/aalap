package com.music.aalap.data.recommendation.engine

import android.content.Context
import com.music.aalap.data.entity.SongsModel

/**
 * Your direct say over recommendations, from a song's menu:
 *
 *  - More like this: the song, its artist and its feel come up more; the queue turns toward it now.
 *  - Less like this: its artist and feel come up less; if it's playing, it's skipped.
 *  - Don't play this song / Don't play this artist: never recommended again.
 *
 * Kept in the feedback table, so it lasts (Settings can clear it).
 */
object Feedback {
    enum class Kind { MORE, LESS, BLOCK_SONG, BLOCK_ARTIST }

    /** What the feedback adds up to, ready for scoring. */
    data class Snapshot(
        val blockedSongs: Set<String> = emptySet(),
        val blockedArtists: Set<String> = emptySet(),
        val moreSongs: Set<String> = emptySet(),
        val lessSongs: Set<String> = emptySet(),
        val moreArtists: Map<String, Int> = emptyMap(),
        val lessArtists: Map<String, Int> = emptyMap(),
        /** Feels you asked for more / less of (0..1 each). */
        val moreFeel: Map<Feel, Double> = emptyMap(),
        val lessFeel: Map<Feel, Double> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = blockedSongs.isEmpty() && blockedArtists.isEmpty() && moreSongs.isEmpty() && lessSongs.isEmpty()

        /** A blocked song, or one with a blocked artist anywhere in its credits (a duet, a feature). */
        fun blocked(song: Song): Boolean =
            song.id in blockedSongs || (blockedArtists.isNotEmpty() && SongMatcher.artistKeys(song.artists).any { it in blockedArtists })

        /** Multiplier for a candidate (1 = no feedback touches it). */
        fun factor(song: Song, vibe: Vibe): Double {
            if (isEmpty) return 1.0
            var f = 1.0
            if (song.id in moreSongs) f *= 1.3
            if (song.id in lessSongs) f *= 0.2
            val artist = SongMatcher.artistKeys(song.artists).firstOrNull()
            moreArtists[artist]?.let { f *= 1.0 + 0.15 * minOf(it, 3) }
            lessArtists[artist]?.let { f *= Math.pow(0.55, minOf(it, 2).toDouble()) }
            if (vibe.known) {
                val less = vibe.weights.entries.sumOf { (feel, w) -> w * (lessFeel[feel] ?: 0.0) }
                val more = vibe.weights.entries.sumOf { (feel, w) -> w * (moreFeel[feel] ?: 0.0) }
                f *= (1.0 - 0.4 * less) * (1.0 + 0.2 * more)
            }
            return f
        }
    }

    @Volatile private var cached: Pair<Long, Snapshot>? = null

    fun invalidate() { cached = null }

    /** Pure: builds the snapshot from feedback rows and the songs' details (tested). */
    fun build(rows: List<Pair<String, String>>, songs: Map<String, Song>, vibes: Map<String, Vibe>): Snapshot {
        val blockedSongs = HashSet<String>(); val blockedArtists = HashSet<String>()
        val more = HashSet<String>(); val less = HashSet<String>()
        val moreA = HashMap<String, Int>(); val lessA = HashMap<String, Int>()
        val moreF = HashMap<Feel, Double>(); val lessF = HashMap<Feel, Double>()
        for ((kind, target) in rows) {
            when (kind) {
                Kind.BLOCK_SONG.name -> blockedSongs += target
                Kind.BLOCK_ARTIST.name -> blockedArtists += target
                Kind.MORE.name, Kind.LESS.name -> {
                    val isMore = kind == Kind.MORE.name
                    (if (isMore) more else less) += target
                    songs[target]?.let { s ->
                        SongMatcher.artistKeys(s.artists).firstOrNull()?.let { a -> val m = if (isMore) moreA else lessA; m[a] = (m[a] ?: 0) + 1 }
                        vibes[target]?.takeIf { it.known }?.let { v -> Vibe.addInto(if (isMore) moreF else lessF, v.weights, 1.0) }
                    }
                }
            }
        }
        fun norm(m: Map<Feel, Double>): Map<Feel, Double> { val top = m.values.maxOrNull() ?: return emptyMap(); return m.mapValues { (it.value / top).coerceIn(0.0, 1.0) } }
        return Snapshot(blockedSongs, blockedArtists, more, less, moreA, lessA, norm(moreF), norm(lessF))
    }

    fun snapshot(context: Context, userId: String = RecoDb.userId(context)): Snapshot {
        cached?.takeIf { System.currentTimeMillis() - it.first < 20_000 }?.let { return it.second }
        val snap = runCatching {
            val db = RecoDb.get(context)
            val rows = db.feedback(userId)
            val ids = rows.filter { it.first == Kind.MORE.name || it.first == Kind.LESS.name }.map { it.second }
            val songs = db.songs(ids)
            build(rows, songs, VibeStore.vibes(context, songs.values))
        }.getOrDefault(Snapshot())
        cached = System.currentTimeMillis() to snap
        return snap
    }

    /** Records [kind] for [song]; returns the canonical song. */
    fun record(context: Context, song: SongsModel, kind: Kind): Song {
        val db = RecoDb.get(context)
        val userId = RecoDb.userId(context)
        val s = db.resolve(SongBridge.toCandidate(song))
        val target = if (kind == Kind.BLOCK_ARTIST) SongMatcher.artistKeys(s.artists).firstOrNull().orEmpty() else s.id
        if (target.isNotBlank()) {
            // More and less of the same song cancel each other.
            if (kind == Kind.MORE) db.removeFeedback(userId, Kind.LESS.name, target)
            if (kind == Kind.LESS) db.removeFeedback(userId, Kind.MORE.name, target)
            db.putFeedback(userId, kind.name, target)
        }
        invalidate()
        return s
    }

    // ── what your feedback adds up to (Profile → Recommendations) ───────────

    /**
     * One side of your feedback (more or less) as what it's about rather than a list of songs:
     * the artists ("Arijit Singh" ×3), the kinds of song (Romantic ×4) and their pace.
     */
    data class Summary(
        val songs: Int,
        /** Artist key → (name to show, how many songs). */
        val artists: List<Triple<String, String, Int>>,
        val feels: List<Pair<Feel, Int>>,
        val bpm: Double?,
        val energy: Double?,
    ) {
        val pace: String? get() = energy?.let { e ->
            (if (e >= 0.62) "Fast, lively" else if (e <= 0.38) "Slow, easy" else "Easy-going") + (bpm?.let { " · about ${it.toInt()} bpm" } ?: "")
        }
    }

    /** Pure: [rows] of one kind's song ids with their songs and vibes, summed up (tested). */
    fun summarise(ids: List<String>, songs: Map<String, Song>, vibes: Map<String, Vibe>): Summary {
        val artists = LinkedHashMap<String, Pair<String, Int>>()
        val feels = HashMap<Feel, Int>()
        val energies = ArrayList<Double>(); val bpms = ArrayList<Double>()
        ids.mapNotNull { songs[it] }.forEach { s ->
            val name = s.artists.split(",").first().trim()
            SongMatcher.artistKeys(s.artists).firstOrNull()?.let { k -> artists[k] = name to ((artists[k]?.second ?: 0) + 1) }
            vibes[s.id]?.let { v ->
                if (v.known) v.dominant?.let { feels[it] = (feels[it] ?: 0) + 1 }
                if (v.energyKnown) energies += v.energy
                v.bpm?.takeIf { it in 50.0..220.0 }?.let { bpms += it }
            }
        }
        return Summary(
            ids.size,
            artists.entries.sortedByDescending { it.value.second }.map { Triple(it.key, it.value.first, it.value.second) },
            feels.entries.sortedByDescending { it.value }.map { it.key to it.value },
            bpms.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] },
            energies.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    fun summary(context: Context, kind: Kind): Summary {
        val db = RecoDb.get(context)
        val ids = db.feedback(RecoDb.userId(context)).filter { it.first == kind.name }.map { it.second }
        val songs = db.songs(ids)
        return summarise(ids, songs, VibeStore.vibes(context, songs.values))
    }

    /** Blocked artists (names) and how many songs are blocked. */
    fun blocked(context: Context): Pair<List<String>, Int> {
        val rows = RecoDb.get(context).feedback(RecoDb.userId(context))
        return rows.filter { it.first == Kind.BLOCK_ARTIST.name }.map { it.second } to rows.count { it.first == Kind.BLOCK_SONG.name }
    }

    /**
     * Forgets part of one side: the songs by [artistKey], or of [feel], or all of [kind] when
     * neither is given. For blocks, [artistKey] unblocks that artist; neither unblocks all.
     */
    fun clear(context: Context, kind: Kind, artistKey: String? = null, feel: Feel? = null) {
        val db = RecoDb.get(context)
        val userId = RecoDb.userId(context)
        val ids = db.feedback(userId).filter { it.first == kind.name }.map { it.second }
        val drop = when {
            kind == Kind.BLOCK_ARTIST && artistKey != null -> listOf(artistKey)
            artistKey != null -> db.songs(ids).values.filter { SongMatcher.artistKeys(it.artists).firstOrNull() == artistKey }.map { it.id }
            feel != null -> { val songs = db.songs(ids); val v = VibeStore.vibes(context, songs.values); songs.values.filter { v[it.id]?.dominant == feel }.map { it.id } }
            else -> ids
        }
        drop.forEach { db.removeFeedback(userId, kind.name, it) }
        invalidate()
    }

    fun clearAll(context: Context) {
        RecoDb.get(context).writableDatabase.delete("feedback", "user_id = ?", arrayOf(RecoDb.userId(context)))
        invalidate()
    }
}

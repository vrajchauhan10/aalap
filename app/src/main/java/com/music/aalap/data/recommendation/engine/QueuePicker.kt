package com.music.aalap.data.recommendation.engine

import java.util.Random
import kotlin.math.sqrt

/**
 * One song of the listening session right now. [weight] says how much it shows where
 * the session is: the song you chose counts most, songs you finished count, songs
 * queued for you count a little, and a song you skipped counts against its feel.
 */
data class SessionSong(
    val id: String,
    val artists: String,
    val vibe: Vibe,
    val language: String,
    val weight: Double,
)

/**
 * Where the listening session is: its feel, energy, languages and styles. Built from
 * the song you chose and what you finished or skipped since, so an energetic start
 * keeps the queue energetic, and skipping sad songs steers away from sad ones.
 * [prior] (what you usually play at this time of day) only matters while the session
 * itself says little.
 */
class Session(val songs: List<SessionSong>, prior: Map<Feel, Double> = emptyMap()) {

    val vibe: Vibe
    val languages: Map<String, Double>
    val styles: Map<String, Double>
    val skippedArtists: Set<String>
    /** How sure the session is of its feel (0..1). */
    val strength: Double

    init {
        val raw = HashMap<Feel, Double>()
        val neg = HashMap<Feel, Double>()
        val lang = HashMap<String, Double>()
        val style = HashMap<String, Double>()
        var known = 0.0
        for (s in songs) {
            val c = s.vibe.confidence
            if (s.weight > 0) {
                Vibe.addInto(raw, s.vibe.weights, s.weight * c)
                if (s.language.isNotBlank()) lang[s.language] = (lang[s.language] ?: 0.0) + s.weight
                if (s.vibe.known) {
                    known += s.weight
                    s.vibe.styles.forEach { style[it] = (style[it] ?: 0.0) + s.weight }
                }
            } else {
                Vibe.addInto(neg, s.vibe.weights, -s.weight * c)
            }
        }
        val mass = raw.values.sum()
        // Skipped feels are taken off, at most down to a third of what's there.
        for ((f, v) in neg) raw[f] = maxOf((raw[f] ?: 0.0) * 0.33, (raw[f] ?: 0.0) - 0.7 * v)
        if (prior.isNotEmpty()) Vibe.addInto(raw, prior, 0.25 / (1.0 + mass))
        vibe = Vibe.of(raw)
        strength = vibe.confidence
        val lt = lang.values.sum()
        languages = if (lt > 0) lang.mapValues { it.value / lt } else emptyMap()
        styles = if (known > 0) style.mapValues { it.value / known } else emptyMap()
        skippedArtists = songs.filter { it.weight < 0 }.flatMap { SongMatcher.artistKeys(it.artists) }.toSet()
    }

    /** 0..1: how well a song with [v] fits the session's feel (0.5 when either is unknown). */
    fun vibeScore(v: Vibe): Double {
        if (!v.known || !vibe.known) return 0.5
        // Feel first (sharpened, so "a bit alike" isn't enough), then tempo.
        val sim = v.similarity(vibe)
        var s = 0.78 * Math.pow(sim, 1.6) + 0.22 * Vibe.energyMatch(v.energy, vibe.energy)
        if (v.dominant == vibe.dominant) s += 0.06
        // The session's style (lofi, rap, sufi…) when it's clearly in one.
        val strongStyle = styles.filterValues { it >= 0.5 }.keys
        if (strongStyle.isNotEmpty()) s += if (v.styles.any { it in strongStyle }) 0.12 else -0.06
        val sure = sqrt(minOf(v.confidence, vibe.confidence))
        return (0.5 + (s - 0.5) * sure).coerceIn(0.0, 1.0)
    }

    /** 0..1: does the song's language fit (0.5 when unknown). */
    fun languageScore(language: String): Double {
        if (language.isBlank() || languages.isEmpty()) return 0.5
        return 0.25 + 0.75 * (languages[language] ?: 0.0)
    }

    companion object {
        val EMPTY = Session(emptyList())
    }
}

/** Everything known about one candidate for the next song. */
data class Features(
    val song: Song,
    /** What the world plays after the current song (fused, 0..1). */
    val global: Double,
    /** What the world plays after the whole session (the song you chose and the last few), 0..1. */
    val session: Double,
    val vibe: Vibe,
    val language: String,
    /** Your long-term taste (artists you finish, feels you like, languages, time of day), 0..1. */
    val taste: Double,
    /** How often you played it right after the current song, 0..1. */
    val habit: Double,
    /** On today's charts. */
    val trend: Boolean = false,
    /** You've played it before (finished it at least once). */
    val familiar: Boolean = false,
    val liked: Boolean = false,
    /** From the playlist you started from. */
    val fromList: Boolean = false,
    val sources: Set<Source> = emptySet(),
    /** A song you used to play a lot but haven't for a while. */
    val rediscover: Boolean = false,
    /** Fits the session but is outside your usual artists: a surprise. */
    val surprise: Boolean = false,
    /** Your "more / less like this" feedback, as a multiplier (1 = none). */
    val feedback: Double = 1.0,
)

/** How the songs picked so far are made up, so the queue keeps a good mix. */
data class PickState(
    /** Main artists of the session's songs and picks, most recent first. */
    val recentArtists: List<String> = emptyList(),
    val picks: Int = 0,
    val familiar: Int = 0,
    val liked: Int = 0,
    val fromList: Int = 0,
    val rediscover: Int = 0,
    val surprise: Int = 0,
    /** Picks per language. */
    val languages: Map<String, Int> = emptyMap(),
    /** The song before (for a smooth flow of energy and tempo). */
    val lastVibe: Vibe? = null,
) {
    fun after(f: Features): PickState = copy(
        recentArtists = listOfNotNull(SongMatcher.artistKeys(f.song.artists).firstOrNull()) + recentArtists,
        picks = picks + 1,
        familiar = familiar + if (f.familiar) 1 else 0,
        liked = liked + if (f.liked) 1 else 0,
        fromList = fromList + if (f.fromList) 1 else 0,
        rediscover = rediscover + if (f.rediscover) 1 else 0,
        surprise = surprise + if (f.surprise) 1 else 0,
        languages = if (f.language.isBlank()) languages else languages + (f.language to (languages[f.language] ?: 0) + 1),
        lastVibe = f.vibe,
    )
}

/**
 * The mix to aim for. [familiar]: share of songs you know (learned from how often you
 * finish new songs vs ones you know). [maxLiked]: liked songs are welcome but never
 * take over. [fromList]: share from the playlist you started from (0 if none).
 */
data class Targets(
    val familiar: Double = 0.35,
    val maxLiked: Double = 0.35,
    val fromList: Double = 0.0,
    /** Share of old favourites to bring back. */
    val rediscover: Double = 0.1,
    /** Share of surprises (from the discovery dial). */
    val surprise: Double = 0.1,
    /** Your chosen language mix (shares, adding up to 1); empty = follow the session. */
    val languageMix: Map<String, Double> = emptyMap(),
)

/** The score's parts, from what the world plays next to your own behaviour. */
data class Weights(val global: Double, val session: Double, val fit: Double, val taste: Double, val habit: Double, val trend: Double) {
    companion object {
        val ESTABLISHED = Weights(global = 0.28, session = 0.14, fit = 0.26, taste = 0.16, habit = 0.10, trend = 0.06)
        /** A new listener: no taste or habits yet, so the world's choices and the session's feel decide. */
        val NEW = Weights(global = 0.40, session = 0.18, fit = 0.34, taste = 0.0, habit = 0.0, trend = 0.08)
    }
}

/**
 * Scores and picks the next song. The old engine scored mostly by "what plays next"
 * plus artist taste (where likes counted a lot). This one also asks: does it fit the
 * session's feel, energy, language and style; is it right after the whole session, not
 * just the last song; is it trending; and does the queue keep a good mix of known and
 * new songs without being taken over by likes or one artist.
 */
object QueuePicker {
    const val TOP_N = 6

    fun score(f: Features, session: Session, isNew: Boolean, state: PickState, targets: Targets): Scored {
        val w = if (isNew) Weights.NEW else Weights.ESTABLISHED
        val feel = session.vibeScore(f.vibe)
        val mixTop = targets.languageMix.values.maxOrNull() ?: 0.0
        val lang = if (mixTop > 0 && f.language.isNotBlank()) 0.25 + 0.75 * ((targets.languageMix[f.language] ?: 0.0) / mixTop)
            else session.languageScore(f.language)
        val fit = 0.7 * feel + 0.3 * lang
        var s = w.global * f.global + w.session * f.session + w.fit * fit + w.taste * f.taste +
            w.habit * f.habit + w.trend * (if (f.trend) 1.0 else 0.0)
        val tags = LinkedHashSet<String>()

        // A song clearly off the session's feel needs a lot to get in.
        if (session.vibe.known && f.vibe.known) when {
            feel < 0.45 -> { s *= 0.45; tags += "off-feel" }
            feel < 0.55 -> { s *= 0.8; tags += "loose-fit" }
        }

        val artist = SongMatcher.artistKeys(f.song.artists).firstOrNull().orEmpty()
        if (artist.isNotEmpty()) {
            if (artist in session.skippedArtists) { s *= 0.25; tags += "skipped-artist" }
            val lately = state.recentArtists.take(6).count { it == artist }
            if (lately >= 2) { s *= 0.35; tags += "artist-tired" } else if (lately == 1) { s *= 0.7; tags += "artist-again" }
        }

        if (state.picks > 0 || targets.familiar > 0) {
            val famShare = if (state.picks == 0) 0.0 else state.familiar.toDouble() / state.picks
            // The further the dial is from the middle, the harder it pulls.
            if (f.familiar && famShare < targets.familiar) { s += 0.06 + 0.3 * maxOf(0.0, targets.familiar - 0.35); tags += "known" }
            if (!f.familiar && famShare >= targets.familiar) { s += 0.04 + 0.4 * maxOf(0.0, 0.35 - targets.familiar); tags += "new" }
            if (f.familiar && famShare >= targets.familiar + 0.1 && targets.familiar < 0.3) { s *= 0.7; tags += "known-capped" }
        }
        if (f.liked && state.picks > 0) {
            // Would picking it put liked songs over their share? Then other songs go first.
            val after = (state.liked + 1).toDouble() / (state.picks + 1)
            if (after > targets.maxLiked + 0.05) { s *= 0.45; tags += "likes-capped" }
        }
        if (f.fromList && targets.fromList > 0) {
            val listShare = if (state.picks == 0) 0.0 else state.fromList.toDouble() / state.picks
            when {
                listShare < targets.fromList -> { s += 0.1; tags += "from-list" }
                listShare > targets.fromList + 0.15 -> { s *= 0.7; tags += "list-capped" }
            }
        }
        if (f.trend) tags += "trending"

        // Smooth flow: no sudden jumps in energy or tempo from the song before.
        state.lastVibe?.let { prev ->
            if (prev.energyKnown && f.vibe.energyKnown) {
                val gap = kotlin.math.abs(prev.energy - f.vibe.energy)
                if (gap > 0.18) { s *= 1.0 - minOf(0.45, (gap - 0.18) * 1.6); tags += "energy-jump" }
            }
            val a = prev.bpm; val b = f.vibe.bpm
            if (a != null && b != null && a > 0 && b > 0) {
                // Half or double time flows fine (a 70 bpm song after a 140 bpm one).
                val r = maxOf(a, b) / minOf(a, b)
                val off = minOf(r, kotlin.math.abs(r - 2.0) + 1.0)
                if (off > 1.2) { s *= 0.85; tags += "tempo-jump" } else if (off < 1.06) { s += 0.03; tags += "tempo-match" }
            }
        }

        // Your language mix: keep each language near its share, and none you turned off.
        if (mixTop > 0 && f.language.isNotBlank()) {
            val want = targets.languageMix[f.language] ?: 0.0
            if (want <= 0.0) { s *= 0.35; tags += "language-off" }
            else {
                val have = if (state.picks == 0) 0.0 else (state.languages[f.language] ?: 0).toDouble() / state.picks
                // The further a language is behind its share, the harder it pulls.
                if (have < want) { s += 0.08 + 0.4 * (want - have); tags += "language-mix" }
                else if (have > want + 0.1) { s *= 0.8; tags += "language-full" }
            }
        }

        // Old favourites and surprises, each up to its share.
        val share = { n: Int -> if (state.picks == 0) 0.0 else n.toDouble() / state.picks }
        if (f.rediscover) {
            if (share(state.rediscover) < targets.rediscover) { s += 0.1; tags += "rediscover" }
            else { s *= 0.5; tags += "rediscover-capped" }
        }
        if (f.surprise && share(state.surprise) < targets.surprise) { s += 0.12 + 0.6 * maxOf(0.0, targets.surprise - 0.1); tags += "surprise" }

        // "More / less like this".
        if (f.feedback != 1.0) { s *= f.feedback; tags += if (f.feedback > 1.0) "more-like" else "less-like" }

        return Scored(
            song = f.song, global = f.global, taste = f.taste, habit = f.habit, final = s.coerceAtLeast(0.0),
            sources = f.sources, session = f.session, fit = fit, trend = if (f.trend) 1.0 else 0.0,
            vibe = f.vibe, tags = tags,
        )
    }

    /**
     * The next song: never the same artist twice in a row, nothing in [exclude], one of
     * the top [TOP_N] sampled by score² (strong songs are much likelier, but the queue
     * isn't the same every time).
     */
    fun pick(
        candidates: List<Features>,
        session: Session,
        isNew: Boolean,
        state: PickState,
        targets: Targets,
        previousArtists: String,
        exclude: Set<String>,
        rnd: Random,
    ): Scored? {
        val scored = candidates.asSequence()
            .filter { it.song.id !in exclude }
            .distinctBy { it.song.id }
            .map { score(it, session, isNew, state, targets) }
            .toList()
        if (scored.isEmpty()) return null
        val fresh = scored.filter { previousArtists.isBlank() || !SongMatcher.artistsOverlap(it.song.artists, previousArtists) }
        val pool = fresh.ifEmpty { scored }.sortedByDescending { it.final }.take(TOP_N)
        return Diversifier.sampleWeighted(pool, rnd) { it.final * it.final }
    }
}

package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Diversifier
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.Personalizer
import com.music.aalap.data.recommendation.engine.QueueEngine
import com.music.aalap.data.recommendation.engine.RecoDb
import com.music.aalap.data.recommendation.engine.SongBridge
import com.music.aalap.data.recommendation.engine.SongMatcher
import com.music.aalap.data.recommendation.engine.VibeStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Blend: a playlist from two people's tastes, with no server or account. You share a
 * taste code (top artists, feels, languages and 25 favourite songs, never your
 * history); a friend pastes it into Aalap (or shares the message to Aalap) and gets a
 * Blend: your songs that fit their taste, their songs that fit yours, and new songs
 * you'd both like, with a match score. A saved Blend refreshes itself every day on
 * each phone; a newer code from the same friend replaces their old one.
 */
object Blend {
    const val PREFIX = "AALAP-BLEND:"

    data class TasteCard(
        val name: String,
        val artists: List<String>,
        val feels: Map<String, Double>,
        val languages: List<String>,
        /** title, artists, Spotify id */
        val songs: List<Triple<String, String, String>>,
        /** Stays the same for one person's phone, so a newer code updates their Blend. */
        val id: String = "",
    )

    // ── the code (pure, tested) ─────────────────────────────────────────────

    fun encode(card: TasteCard): String {
        val o = JSONObject().put("v", 1).put("n", card.name)
            .put("a", JSONArray(card.artists)).put("f", JSONObject(card.feels as Map<*, *>)).put("l", JSONArray(card.languages))
            .put("s", JSONArray(card.songs.map { JSONArray(listOf(it.first, it.second, it.third)) })).put("u", card.id)
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(o.toString().toByteArray()) } }.toByteArray()
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The taste card in [text] (a pasted message can have other words around the code). */
    fun decode(text: String): TasteCard? = runCatching {
        val code = Regex("""${Regex.escape(PREFIX)}([A-Za-z0-9_-]+)""").find(text)?.groupValues?.get(1) ?: return null
        val bytes = java.util.Base64.getUrlDecoder().decode(code)
        if (bytes.size > 64 * 1024) return null
        // A real code unpacks to a few KB; reading stops at 256 KB so a crafted one can't
        // balloon in memory.
        val unpacked = GZIPInputStream(ByteArrayInputStream(bytes)).use { z ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var n: Int
            while (z.read(buf).also { n = it } > 0) { out.write(buf, 0, n); if (out.size() > 256 * 1024) return null }
            out.toByteArray()
        }
        val o = JSONObject(unpacked.decodeToString())
        val a = o.getJSONArray("a"); val l = o.getJSONArray("l"); val s = o.getJSONArray("s"); val f = o.getJSONObject("f")
        TasteCard(
            name = o.optString("n").ifBlank { "Friend" },
            artists = (0 until a.length()).map { a.getString(it) },
            feels = f.keys().asSequence().associateWith { f.getDouble(it) },
            languages = (0 until l.length()).map { l.getString(it) },
            songs = (0 until s.length()).map { s.getJSONArray(it).let { t -> Triple(t.getString(0), t.getString(1), t.optString(2)) } },
            id = o.optString("u"),
        )
    }.getOrNull()

    // ── the match score (pure, tested) ──────────────────────────────────────

    data class Match(val score: Int, val sharedArtists: List<String>, val sharedLanguages: List<String>, val sharedSongs: Int)

    private fun key(artist: String) = artist.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    /**
     * How well two tastes match, 1..99: artists you both love (most), the feel of your
     * music, your languages, and songs you both have. Top artists count more than the
     * rest of the list.
     */
    fun match(a: TasteCard, b: TasteCard): Match {
        val ak = a.artists.map(::key); val bk = b.artists.map(::key)
        val shared = a.artists.filterIndexed { i, _ -> ak[i] in bk }
        var artistPts = 0.0
        ak.forEachIndexed { i, k -> val j = bk.indexOf(k); if (j >= 0) artistPts += 1.0 / (1 + 0.12 * i) + 1.0 / (1 + 0.12 * j) }
        val artists = (artistPts / 2 / 4.0).coerceAtMost(1.0)            // ~4 strong shared artists = full marks
        val feels = cosine(a.feels, b.feels)
        val al = a.languages.map { it.lowercase() }.toSet(); val bl = b.languages.map { it.lowercase() }.toSet()
        val langs = if (al.isEmpty() || bl.isEmpty()) 0.5 else (al intersect bl).size.toDouble() / (al union bl).size
        val sk = { t: Triple<String, String, String> -> t.third.ifBlank { key(t.first) + "|" + key(t.second.substringBefore(",")) } }
        val sharedSongs = (a.songs.map(sk).toSet() intersect b.songs.map(sk).toSet()).size
        val songs = (sharedSongs / 4.0).coerceAtMost(1.0)
        val raw = 0.42 * artists + 0.28 * feels + 0.18 * langs + 0.12 * songs
        val score = (100 * Math.pow(raw, 0.8)).toInt().coerceIn(1, 99)
        return Match(score, shared, a.languages.filter { it.lowercase() in bl }, sharedSongs)
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.4
        val dot = a.entries.sumOf { (k, v) -> v * (b[k] ?: 0.0) }
        val na = Math.sqrt(a.values.sumOf { it * it }); val nb = Math.sqrt(b.values.sumOf { it * it })
        return if (na == 0.0 || nb == 0.0) 0.4 else (dot / (na * nb)).coerceIn(0.0, 1.0)
    }

    /** The score's name and what it means. */
    fun band(score: Int): Pair<String, String> = when {
        score < 30 -> "Opposites attract" to "Different worlds: perfect for finding new music in each other's"
        score < 50 -> "Common ground" to "A few artists and moods in common, plenty to discover"
        score < 70 -> "Great match" to "You'd happily share the aux"
        score < 85 -> "Music twins" to "Your playlists could swap and nobody would notice"
        else -> "Soulmates" to "Same heart, same playlist"
    }

    // ── your card ───────────────────────────────────────────────────────────

    fun myCard(context: Context): TasteCard {
        val profile = Personalizer.profile(context)
        val summary = Personalizer.summary(context, topN = 15)
        val db = RecoDb.get(context)
        val top = profile.songAffinity.entries.sortedByDescending { it.value }.take(25).map { it.key }
        val songs = db.songs(top).let { m -> top.mapNotNull { m[it] } }
        val name = com.music.aalap.data.preferences.getAalapName(context).trim().substringBefore(" ").ifBlank { "Friend" }
        return TasteCard(
            name = name,
            artists = summary.artists,
            feels = profile.feelPref.mapKeys { it.key.name }.filterValues { it > 0.5 },
            languages = summary.languages,
            songs = songs.map { Triple(it.title, it.artists, it.info.spotifyId) },
            id = myId(context),
        )
    }


    /** The message to share: a line of explanation, then the code. */
    fun shareText(context: Context, card: TasteCard = myCard(context)): String =
        "Let's Blend on Aalap! \uD83C\uDFA7 Copy this whole message and open Aalap (it picks the code up), " +
            "or tap Share → Aalap.\n\n" + encode(card)

    // ── the Blend ───────────────────────────────────────────────────────────

    /** [by]: who each song is from, by position: "me", "them" or "new" (for you both). */
    data class Made(val friend: TasteCard, val match: Match, val songs: List<SongsModel>, val by: List<String> = emptyList())

    /** Builds the Blend with [friend] (not saved: see [save]). */
    suspend fun make(context: Context, friend: TasteCard, size: Int = 30): Made = coroutineScope {
        val db = RecoDb.get(context)
        val profile = Personalizer.profile(context)
        val friendArtists = friend.artists.mapNotNull { SongMatcher.artistKeys(it).firstOrNull() }.toSet()
        val friendFeels = friend.feels.mapNotNull { (k, v) -> runCatching { Feel.valueOf(k) to v }.getOrNull() }.toMap()
        val myArtists = profile.usualArtists
        // A new mix each day: the day seeds which of the good fits come in.
        val rnd = java.util.Random(java.time.LocalDate.now().toEpochDay() * 31 + friend.id.hashCode())

        // Your songs that fit their taste.
        val mine = db.songs(profile.songAffinity.entries.sortedByDescending { it.value }.take(60).map { it.key }).values
        val mineVibes = VibeStore.vibes(context, mine)
        val myPicks = mine.sortedByDescending { s ->
            val artist = if (SongMatcher.artistKeys(s.artists).firstOrNull() in friendArtists) 1.0 else 0.0
            val feel = mineVibes[s.id]?.takeIf { it.known }?.weights?.entries?.sumOf { (f, w) -> w * (friendFeels[f] ?: 0.3) } ?: 0.3
            artist + feel + (profile.songAffinity[s.id] ?: 0.0) * 0.3 + rnd.nextDouble() * 0.4
        }.take(size / 3)

        // Their songs that fit yours.
        val theirs = db.resolveAll(friend.songs.map { (t, a, id) -> Candidate(title = t, artists = a, spotifyId = id) })
        val theirPicks = theirs.sortedByDescending { s ->
            profile.taste(s.info, s.id, VibeStore.vibe(context, s)) + (if (SongMatcher.artistKeys(s.artists).firstOrNull() in myArtists) 0.5 else 0.0) + rnd.nextDouble() * 0.3
        }.take(size / 3)

        // New to both: what plays after songs by artists you share (or each side's top songs).
        val shared = (myPicks + theirPicks).filter { SongMatcher.artistKeys(it.artists).firstOrNull() in friendArtists && SongMatcher.artistKeys(it.artists).firstOrNull() in myArtists }
        val seeds = (shared.shuffled(rnd) + myPicks.shuffled(rnd).take(2) + theirPicks.shuffled(rnd).take(2)).distinctBy { it.id }.take(4)
        val known = (mine.map { it.id } + theirs.map { it.id }).toSet() + profile.heard
        val userId = RecoDb.userId(context)
        val fresh = seeds.map { seed -> async { runCatching { QueueEngine.scoredAfter(context, userId, seed, timeoutMs = 6_000) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
            .filter { it.song.id !in known }
            .sortedByDescending { it.final + (if (SongMatcher.artistKeys(it.song.artists).firstOrNull().let { a -> a in friendArtists || a in myArtists }) 0.2 else 0.0) }
            .map { it.song }.distinctBy { it.id }.take(size - myPicks.size - theirPicks.size)

        // You, them, something new, in turn; never the same artist twice in a row.
        val mixed = ArrayList<com.music.aalap.data.recommendation.engine.Song>()
        for (i in 0 until maxOf(myPicks.size, theirPicks.size, fresh.size)) {
            myPicks.getOrNull(i)?.let { mixed += it }
            theirPicks.getOrNull(i)?.let { mixed += it }
            fresh.getOrNull(i)?.let { mixed += it }
        }
        val spread = Diversifier.spreadArtists(mixed.distinctBy { it.id }) { it.artists }
        val mineIds = myPicks.mapTo(HashSet()) { it.id }; val theirIds = theirPicks.mapTo(HashSet()) { it.id }
        Made(friend, match(myCard(context), friend), spread.map(SongBridge::toSongsModel),
            spread.map { if (it.id in mineIds) "me" else if (it.id in theirIds) "them" else "new" })
    }

    /** This phone's Blend id: the Blend sync sign-in when it's set up, else a random id made once. */
    fun myId(context: Context): String {
        BlendSync.uid?.let { return it }
        val p = context.getSharedPreferences("blend", Context.MODE_PRIVATE)
        return p.getString("me", null) ?: java.util.UUID.randomUUID().toString().take(12).also { p.edit().putString("me", it).apply() }
    }
}

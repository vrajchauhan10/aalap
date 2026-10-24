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
        val o = JSONObject(GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().decodeToString())
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

    /** This phone's Blend id (random, made once). */
    fun myId(context: Context): String {
        val p = context.getSharedPreferences("blend", Context.MODE_PRIVATE)
        return p.getString("me", null) ?: java.util.UUID.randomUUID().toString().take(12).also { p.edit().putString("me", it).apply() }
    }

    /** The message to share: a line of explanation, then the code. */
    fun shareText(context: Context, card: TasteCard = myCard(context)): String =
        "Let's Blend on Aalap! \uD83C\uDFA7 Copy this whole message and open Aalap (it picks the code up), " +
            "or tap Share → Aalap.\n\n" + encode(card)

    // ── the Blend ───────────────────────────────────────────────────────────

    data class Made(val friend: TasteCard, val match: Match, val songs: List<SongsModel>)

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
        val songs: List<SongsModel> = Diversifier.spreadArtists(mixed.distinctBy { it.id }) { it.artists }.map(SongBridge::toSongsModel)
        Made(friend, match(myCard(context), friend), songs)
    }

    // ── saved Blends ────────────────────────────────────────────────────────

    data class Saved(val friend: TasteCard, val playlistId: String, val score: Int, val day: Long)

    private fun store(c: Context) = c.getSharedPreferences("blend", Context.MODE_PRIVATE)

    fun saved(context: Context): List<Saved> = runCatching {
        val a = JSONArray(store(context).getString("saved", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            decode(o.getString("code"))?.let { Saved(it, o.getString("pl"), o.optInt("score"), o.optLong("day")) }
        }.filter { com.music.aalap.data.local.AalapPlaylists.get(context, it.playlistId) != null } // removed from the library = gone
    }.getOrDefault(emptyList())

    @Synchronized
    private fun write(context: Context, list: List<Saved>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("code", encode(it.friend)).put("pl", it.playlistId).put("score", it.score).put("day", it.day)) }
        store(context).edit().putString("saved", a.toString()).apply()
        _changed.value++
    }

    private val _changed = kotlinx.coroutines.flow.MutableStateFlow(0)
    val changed: kotlinx.coroutines.flow.StateFlow<Int> = _changed

    fun playlistName(friend: TasteCard) = "Blend · You + ${friend.name}"

    /** The saved Blend with this friend, if any (same phone id, or same name for old codes). */
    fun savedWith(context: Context, friend: TasteCard): Saved? =
        saved(context).firstOrNull { (friend.id.isNotBlank() && it.friend.id == friend.id) || (friend.id.isBlank() && it.friend.name == friend.name) }

    /** Saves [made] to your library (updating your Blend with the same friend); returns the playlist id. */
    fun save(context: Context, made: Made): String {
        val old = savedWith(context, made.friend)
        val id = old?.playlistId ?: com.music.aalap.data.local.AalapPlaylists.create(context, playlistName(made.friend))
        com.music.aalap.data.local.AalapPlaylists.replaceSongs(context, id, made.songs, playlistName(made.friend))
        write(context, saved(context).filterNot { it.playlistId == id } + Saved(made.friend, id, made.match.score, java.time.LocalDate.now().toEpochDay()))
        return id
    }

    fun remove(context: Context, saved: Saved) {
        write(context, saved(context).filterNot { it.playlistId == saved.playlistId })
        com.music.aalap.data.local.AalapPlaylists.delete(context, saved.playlistId)
    }

    /** Once a day (when Aalap opens): each saved Blend gets a fresh mix from both tastes as they are now. */
    suspend fun refreshDaily(context: Context) {
        val today = java.time.LocalDate.now().toEpochDay()
        for (s in saved(context).filter { it.day < today }) {
            runCatching {
                val made = make(context, s.friend)
                if (made.songs.size >= 10) save(context, made)
            }
        }
    }
}

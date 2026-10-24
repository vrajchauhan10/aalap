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
 * history); a friend pastes it into Aalap and gets a Blend: your songs that fit their
 * taste, their songs that fit yours, and new songs by artists you both like.
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
    )

    // ── the code (pure, tested) ─────────────────────────────────────────────

    fun encode(card: TasteCard): String {
        val o = JSONObject().put("v", 1).put("n", card.name)
            .put("a", JSONArray(card.artists)).put("f", JSONObject(card.feels as Map<*, *>)).put("l", JSONArray(card.languages))
            .put("s", JSONArray(card.songs.map { JSONArray(listOf(it.first, it.second, it.third)) }))
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
        )
    }.getOrNull()

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
        )
    }

    /** The message to share: a line of explanation, then the code. */
    fun shareText(context: Context): String =
        "My Aalap taste: open Aalap → Settings → Blend → Make a Blend, and paste this whole message.\n\n" + encode(myCard(context))

    // ── the Blend ───────────────────────────────────────────────────────────

    /** Builds the Blend with [friend] and saves it as an Aalap playlist; returns its id and size. */
    suspend fun make(context: Context, friend: TasteCard, size: Int = 30): Pair<String, Int> = coroutineScope {
        val db = RecoDb.get(context)
        val profile = Personalizer.profile(context)
        val friendArtists = friend.artists.mapNotNull { SongMatcher.artistKeys(it).firstOrNull() }.toSet()
        val friendFeels = friend.feels.mapNotNull { (k, v) -> runCatching { Feel.valueOf(k) to v }.getOrNull() }.toMap()
        val myArtists = profile.usualArtists

        // Your songs that fit their taste.
        val mine = db.songs(profile.songAffinity.entries.sortedByDescending { it.value }.take(60).map { it.key }).values
        val mineVibes = VibeStore.vibes(context, mine)
        val myPicks = mine.sortedByDescending { s ->
            val artist = if (SongMatcher.artistKeys(s.artists).firstOrNull() in friendArtists) 1.0 else 0.0
            val feel = mineVibes[s.id]?.takeIf { it.known }?.weights?.entries?.sumOf { (f, w) -> w * (friendFeels[f] ?: 0.3) } ?: 0.3
            artist + feel + (profile.songAffinity[s.id] ?: 0.0) * 0.3
        }.take(size / 3)

        // Their songs that fit yours.
        val theirs = db.resolveAll(friend.songs.map { (t, a, id) -> Candidate(title = t, artists = a, spotifyId = id) })
        val theirPicks = theirs.sortedByDescending { s ->
            profile.taste(s.info, s.id, VibeStore.vibe(context, s)) + (if (SongMatcher.artistKeys(s.artists).firstOrNull() in myArtists) 0.5 else 0.0)
        }.take(size / 3)

        // New to both: what plays after songs by artists you share (or each side's top songs).
        val shared = (myPicks + theirPicks).filter { SongMatcher.artistKeys(it.artists).firstOrNull() in friendArtists && SongMatcher.artistKeys(it.artists).firstOrNull() in myArtists }
        val seeds = (shared + myPicks.take(2) + theirPicks.take(2)).distinctBy { it.id }.take(4)
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
        val id = com.music.aalap.data.local.AalapPlaylists.create(context, "Blend · You + ${friend.name}")
        songs.forEach { com.music.aalap.data.local.AalapPlaylists.add(context, id, it) }
        id to songs.size
    }
}

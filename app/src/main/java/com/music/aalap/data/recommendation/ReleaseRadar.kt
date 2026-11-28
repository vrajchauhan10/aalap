package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.entity.SongsModel
import java.time.LocalDate
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject

/**
 * Release Radar: new songs from the artists you actually play, as one playable mix. Built from
 * the same "New from your artists" releases Home already loads, once a week (a new list every
 * Friday, when most music comes out), and kept on the phone so opening Home costs nothing.
 */
object ReleaseRadar {
    data class Release(val name: String, val date: String, val songs: List<SongsModel>)

    /** The Friday that starts this radar's week (today if it's Friday). */
    fun weekOf(today: LocalDate): LocalDate {
        val back = (today.dayOfWeek.value - java.time.DayOfWeek.FRIDAY.value + 7) % 7
        return today.minusDays(back.toLong())
    }

    private fun dateOf(s: String): LocalDate? = runCatching {
        when (s.length) {
            10 -> LocalDate.parse(s)
            7 -> LocalDate.parse("$s-01")
            4 -> LocalDate.parse("$s-01-01")
            else -> null
        }
    }.getOrNull()

    /**
     * Picks the songs: releases from the last 8 weeks (widened to a year when that gives too few),
     * newest first. Singles and EPs give up to 2 songs, albums their title song or first song,
     * at most 3 songs per artist so one big album can't fill it. Pure, so it is unit-tested.
     */
    fun pick(releases: List<Release>, today: LocalDate, limit: Int = 30): List<SongsModel> {
        fun within(days: Long) = releases.mapNotNull { r -> dateOf(r.date)?.let { d -> r to d } }
            .filter { (_, d) -> !d.isAfter(today) && !d.isBefore(today.minusDays(days)) }
        val recent = within(56).let { if (it.sumOf { (r, _) -> r.songs.size.coerceAtMost(2) } >= 8) it else within(365) }
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<SongsModel>()
        for ((r, _) in recent.sortedByDescending { it.second }) {
            val songs = r.songs
            val chosen = if (songs.size <= 6) songs.take(2)
            else listOfNotNull(songs.firstOrNull { it.title.equals(r.name, true) || it.title.startsWith(r.name, true) } ?: songs.firstOrNull())
            for (s in chosen) {
                val artist = s.singer.substringBefore(",").trim().lowercase()
                if ((perArtist[artist] ?: 0) >= 3 || out.any { it.id == s.id || it.title.equals(s.title, true) && it.singer == s.singer }) continue
                perArtist[artist] = (perArtist[artist] ?: 0) + 1
                out += s
                if (out.size >= limit) return out
            }
        }
        return out
    }

    private fun prefs(c: Context) = c.getSharedPreferences("release_radar", Context.MODE_PRIVATE)

    /** This week's radar if it was already built, else nothing (no network). */
    fun cached(c: Context): List<SongsModel> {
        val p = prefs(c)
        if (p.getString("week", null) != weekOf(LocalDate.now()).toString()) return emptyList()
        return read(p.getString("songs", "[]").orEmpty())
    }

    /** This week's radar, built from [releases] (id → name) the first time it's asked for each week. */
    suspend fun forWeek(c: Context, releases: List<Pair<String, String>>): List<SongsModel> {
        cached(c).takeIf { it.isNotEmpty() }?.let { return it }
        if (releases.isEmpty()) return emptyList()
        val api = com.music.aalap.data.api.Api(c)
        val loaded = kotlinx.coroutines.coroutineScope {
            releases.take(16).map { (id, name) ->
                async { runCatching { api.releaseWithSongs(id) }.getOrNull()?.let { (date, songs) -> Release(name, date, songs) } }
            }.map { it.await() }
        }.filterNotNull()
        val list = com.music.aalap.data.recommendation.engine.LanguageFilter.keep(c, pick(loaded, LocalDate.now()))
        if (list.isNotEmpty()) prefs(c).edit().putString("week", weekOf(LocalDate.now()).toString()).putString("songs", write(list)).apply()
        return list
    }

    private fun write(list: List<SongsModel>) = JSONArray().also { a ->
        list.forEach { m ->
            a.put(JSONObject().put("id", m.id).put("title", m.title).put("album", m.album).put("singer", m.singer)
                .put("cover", m.coverUri).put("url", m.url).put("sid", m.spotifyTrackId).put("explicit", m.explicit).put("dur", m.durationMs))
        }
    }.toString()

    private fun read(s: String): List<SongsModel> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                SongsModel(o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"))
            }
        }
    }.getOrDefault(emptyList())
}

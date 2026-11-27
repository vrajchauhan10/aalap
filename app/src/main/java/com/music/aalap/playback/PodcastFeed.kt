package com.music.aalap.playback

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Podcast audio from the show's own public feed (RSS), the way every podcast app gets episodes:
 * the feed's address comes from Apple's public podcast directory, the episode is matched by title,
 * and its audio file plays directly. Shows that only exist on Spotify have no feed; YouTube is
 * tried for those. Each show's episode list is kept on the phone for a day.
 */
object PodcastFeed {
    private const val TAG = "PodcastFeed"
    private const val DAY = 24 * 3_600_000L

    data class Item(val title: String, val audio: String)

    private val memory = com.music.aalap.di.CappedMap<String, List<Item>>(20)

    private fun get(url: String, timeoutMs: Int = 15_000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "Aalap podcast player")
        c.instanceFollowRedirects = true
        try { if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.onFailure { Log.w(TAG, "get failed: $it") }.getOrNull()

    /** The feed address for [show] (by [publisher] when several share a name), from Apple's directory. */
    private fun feedUrl(c: Context, show: String, publisher: String): String? {
        val prefs = c.getSharedPreferences("podcast_feeds", Context.MODE_PRIVATE)
        prefs.getString("feed:$show", null)?.let { return it.ifBlank { null } }
        val body = get("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=8&term=" + java.net.URLEncoder.encode(show, "UTF-8")) ?: return null
        val results = runCatching { org.json.JSONObject(body).getJSONArray("results") }.getOrNull() ?: return null
        val list = (0 until results.length()).map { results.getJSONObject(it) }
            .map { Triple(it.optString("collectionName"), it.optString("artistName"), it.optString("feedUrl")) }
        val best = bestShow(show, publisher, list)
        prefs.edit().putString("feed:$show", best.orEmpty()).apply() // blank = known to have no feed
        return best
    }

    /** Of directory results (name, publisher, feed), the one for [show]. Pure, unit-tested. */
    fun bestShow(show: String, publisher: String, results: List<Triple<String, String, String>>): String? {
        fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val s = norm(show); val p = norm(publisher)
        return results.filter { it.third.isNotBlank() }
            .map { r -> r to (if (norm(r.first) == s) 3 else if (norm(r.first).contains(s) || s.contains(norm(r.first))) 1 else 0) +
                (if (p.isNotBlank() && norm(r.second).contains(p)) 1 else 0) }
            .filter { it.second >= 2 || (it.second >= 1 && norm(it.first.first) == s) }
            .maxByOrNull { it.second }?.first?.third
    }

    private fun items(c: Context, feed: String): List<Item> {
        memory[feed]?.let { return it }
        val f = File(File(c.cacheDir, "podcast_feeds").apply { mkdirs() }, Integer.toHexString(feed.hashCode()) + ".txt")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < DAY) {
            val list = f.readLines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { Item(it[0], it[1]) } }
            if (list.isNotEmpty()) { memory[feed] = list; return list }
        }
        val xml = get(feed, 30_000) ?: return emptyList()
        val list = parse(xml)
        if (list.isNotEmpty()) {
            f.writeText(list.joinToString("\n") { "${it.title.replace('\t', ' ').replace('\n', ' ')}\t${it.audio}" })
            memory[feed] = list
        }
        return list
    }

    /** Episode titles and audio links from an RSS feed. Pure, unit-tested. */
    fun parse(xml: String): List<Item> =
        Regex("<item[\\s>](.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val it = m.groupValues[1]
            val title = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>", RegexOption.DOT_MATCHES_ALL).find(it)?.groupValues?.get(1)
                ?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&apos;", "'")?.replace("&quot;", "\"")?.trim()
            val audio = Regex("<enclosure[^>]*url=\"([^\"]+)\"").find(it)?.groupValues?.get(1)?.replace("&amp;", "&")
            if (title.isNullOrBlank() || audio.isNullOrBlank()) null else Item(title, audio)
        }.toList()

    /** The audio file for the episode [title] of [show], or null when the show has no feed or it isn't there. */
    fun audioUrl(c: Context, title: String, show: String, publisher: String = ""): String? {
        if (title.isBlank() || show.isBlank()) return null
        val feed = feedUrl(c, show, publisher) ?: return null
        val list = items(c, feed)
        val exact = list.firstOrNull { it.title.equals(title, true) }
        val hit = exact ?: PodcastMatch.best(title, list.map { it.audio to it.title }, min = 0.8)?.let { a -> list.first { it.audio == a } }
        Log.d(TAG, "'$title' in $show: ${list.size} episodes, ${if (hit != null) "found" else "not found"}")
        return hit?.audio
    }
}

package com.music.aalap.data.api

/** Pages kept for a few hours in memory, bounded, so revisiting is instant (see [Api.getArtistOverview]). */
object PageCache {
    class Timed<V : Any>(private val ttlMs: Long, private val max: Int) {
        private val map = object : java.util.LinkedHashMap<String, Pair<Long, V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, V>>) = size > max
        }
        @Synchronized fun get(key: String): V? = map[key]?.takeIf { System.currentTimeMillis() - it.first < ttlMs }?.second
        @Synchronized fun put(key: String, value: V) { map[key] = System.currentTimeMillis() to value }
    }
    private const val HOURS_3 = 3 * 60 * 60 * 1000L
    val artists = Timed<com.music.aalap.data.entity.ArtistOverviewModel>(HOURS_3, 60)
    val albums = Timed<List<com.music.aalap.data.entity.SongsModel>>(HOURS_3, 60)
}

package com.music.aalap.di

/**
 * A thread-safe map that stops growing: past [cap] entries it drops a tenth of the oldest
 * ones. Used for the per-song lookups (stream links, ids, durations…) that used to keep every
 * song ever queued for as long as the app ran. 2000 is far more than any queue, so the songs
 * you're playing are never the ones dropped.
 */
class CappedMap<K : Any, V : Any>(private val cap: Int = 2000) : java.util.concurrent.ConcurrentHashMap<K, V>() {
    private val order = java.util.concurrent.ConcurrentLinkedQueue<K>()

    private fun added(key: K) {
        order.add(key)
        if (size > cap) repeat(cap / 10) { order.poll()?.let { super.remove(it) } }
        // Keys replaced or removed leave stale entries in the order list; keep it bounded too.
        while (order.size > cap * 2) order.poll()
    }

    override fun put(key: K, value: V): V? = super.put(key, value).also { if (it == null) added(key) }
    override fun putIfAbsent(key: K, value: V): V? = super.putIfAbsent(key, value).also { if (it == null) added(key) }
    override fun putAll(from: Map<out K, V>) = from.forEach { (k, v) -> put(k, v) }
}

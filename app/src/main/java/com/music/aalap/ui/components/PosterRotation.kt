package com.music.aalap.ui.components

import android.content.Context

/**
 * Which Aaj ka Aalap poster shows: a new one every time you open Aalap (and with the
 * shuffle button), going through all the designs in a random order before any comes
 * back, so a poster returns only after every other one has been seen. A new round
 * never starts with the posters that ended the last one.
 */
object PosterRotation {
    private const val PREFS = "poster_rotation"
    /** Away this long counts as opening Aalap again. */
    private const val AWAY_MS = 5 * 60_000L

    @Volatile private var openedThisRun = false
    @Volatile private var leftAt = 0L

    /** Index of the poster to show now. */
    val current = androidx.compose.runtime.mutableIntStateOf(-1)

    /**
     * Pure (tested): a new round of [size] posters in random order, where a poster from
     * the end of the last round ([previous], oldest first) can't come back until at least
     * 5/8 of a round has passed since it showed.
     */
    fun newRound(size: Int, previous: List<Int>, rnd: java.util.Random = java.util.Random()): List<Int> {
        val gap = size * 5 / 8
        // Opens since each poster showed last (posters not in the last round: long ago).
        val since = HashMap<Int, Int>()
        previous.forEachIndexed { i, p -> since[p] = previous.size - i }
        val waiting = (0 until size).toMutableList().also { java.util.Collections.shuffle(it, rnd) }
        val out = ArrayList<Int>(size)
        while (waiting.isNotEmpty()) {
            val pos = out.size
            val ready = waiting.filter { (since[it] ?: size) + pos >= gap }
            val pick = if (ready.isNotEmpty()) ready[rnd.nextInt(ready.size)] else waiting.maxBy { since[it] ?: size }
            waiting.remove(pick); out += pick
        }
        return out
    }

    private fun advance(context: Context): Int {
        val size = PosterDesigns.size
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var order = p.getString("order", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == size && it.toSet().size == size }
        var pos = p.getInt("pos", -1) + 1
        if (order == null || pos >= size) {
            order = newRound(size, order.orEmpty())
            pos = 0
        }
        val index = order[pos]
        p.edit().putString("order", order.joinToString(",")).putInt("pos", pos).apply()
        current.intValue = index
        return index
    }

    /** Aalap came to the screen: a new poster when it's a fresh open (not a quick switch back). */
    fun onAppStart(context: Context) {
        val fresh = !openedThisRun || (leftAt > 0 && System.currentTimeMillis() - leftAt >= AWAY_MS)
        openedThisRun = true
        if (fresh || current.intValue < 0) advance(context.applicationContext)
    }

    fun onAppStop() { leftAt = System.currentTimeMillis() }

    /** The shuffle button: the next poster in the round. */
    fun shuffle(context: Context) { advance(context.applicationContext) }
}

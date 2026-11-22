package com.music.aalap.data.recommendation

import android.content.Context
import org.json.JSONObject

/**
 * How well Aalap knows you: what happens to the songs the engine picks for you, kept per day
 * on this phone (28 days). A pick counts as finished when it wasn't skipped, as an early skip
 * when you left it in the first 30 seconds, and as loved when you liked it. This is what tells
 * whether a change to the recommendations made them better or worse.
 */
object PickScore {
    private const val PREF = "pick_score"
    private const val DAY = 24 * 60 * 60 * 1000L

    data class Week(val picks: Int, val finished: Int, val early: Int, val liked: Int) {
        val finishRate get() = if (picks == 0) 0.0 else finished.toDouble() / picks
        val earlyRate get() = if (picks == 0) 0.0 else early.toDouble() / picks
    }

    /** One engine pick ended (when the next song started). */
    fun record(context: Context, skipped: Boolean, listenedMs: Long, liked: Boolean) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val o = runCatching { JSONObject(p.getString("days", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val day = (System.currentTimeMillis() / DAY).toString()
        val a = o.optJSONArray(day) ?: org.json.JSONArray(listOf(0, 0, 0, 0))
        fun inc(i: Int, by: Boolean) { if (by) a.put(i, a.optInt(i) + 1) }
        inc(0, true); inc(1, !skipped); inc(2, skipped && listenedMs < 30_000L); inc(3, liked)
        o.put(day, a)
        // Only the last 28 days.
        val cut = System.currentTimeMillis() / DAY - 28
        o.keys().asSequence().toList().forEach { k -> if ((k.toLongOrNull() ?: 0) < cut) o.remove(k) }
        p.edit().putString("days", o.toString()).apply()
    }

    /** This week ([weeksAgo] = 0) or an earlier one. */
    fun week(context: Context, weeksAgo: Int = 0): Week {
        val o = runCatching { JSONObject(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("days", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val today = System.currentTimeMillis() / DAY
        val from = today - 7L * (weeksAgo + 1) + 1; val to = today - 7L * weeksAgo
        var w = Week(0, 0, 0, 0)
        o.keys().forEach { k ->
            val d = k.toLongOrNull() ?: return@forEach
            if (d in from..to) {
                val a = o.getJSONArray(k)
                w = Week(w.picks + a.optInt(0), w.finished + a.optInt(1), w.early + a.optInt(2), w.liked + a.optInt(3))
            }
        }
        return w
    }
}

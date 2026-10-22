package com.music.spotui.data.recommendation.engine

import android.util.Log

/**
 * A readable, step-by-step account of how the next songs are chosen (debug builds
 * only): `adb logcat -s AalapReco` shows the seed, what each source returned, the
 * merged list, every candidate's scores and why the pick won.
 */
object RecoTrace {
    private const val TAG = "AalapReco"
    val on: Boolean get() = com.music.spotui.BuildConfig.DEBUG

    fun log(msg: String) { if (on) msg.lines().forEach { Log.i(TAG, it) } }

    fun songs(list: List<Song>, max: Int = 6) =
        list.take(max).joinToString("\n") { "      · ${it.title} — ${it.artists.substringBefore(",")}" }
}

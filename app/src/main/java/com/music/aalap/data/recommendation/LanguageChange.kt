package com.music.aalap.data.recommendation

import android.content.Context

/** After you change your languages, everything built for the old ones is built again. */
object LanguageChange {
    fun applied(c: Context) {
        runCatching { com.music.aalap.data.api.Discover.clearToday(c) }
        runCatching { HomeMixes.forgetCaches(c) }
        runCatching { c.getSharedPreferences("release_radar", Context.MODE_PRIVATE).edit().clear().apply() }
        runCatching { c.getSharedPreferences("podcasts", Context.MODE_PRIVATE).edit().remove("popular.day").apply() }
        // Learn the new languages' moods, eras and genres (on Wi-Fi).
        runCatching { com.music.aalap.data.recommendation.engine.Trainer.maybeRun(c) }
    }
}

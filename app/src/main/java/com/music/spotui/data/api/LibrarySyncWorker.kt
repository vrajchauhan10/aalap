package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps the unified library fresh: every hour (when online) it re-reads your Spotify
 * liked songs and followed artists and your YouTube Music likes, subscriptions and
 * recent plays. Runs in the background even when Aalap is closed.
 */
class LibrarySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        var ok = false
        if (com.music.spotui.data.api.SpotifySession.spDc(c).isNotBlank()) {
            runCatching {
                val api = Api(c)
                api.getLikedSongs().collect { } // saves the full list when the last page arrives
                api.getFollowedArtists().takeIf { it.isNotEmpty() }
                    ?.let { com.music.spotui.data.preferences.cacheFollowedArtists(c, it) }
                ok = true
            }.onFailure { Log.w(TAG, "Spotify sync failed: $it") }
        }
        if (com.music.spotui.data.preferences.isYoutubeLoggedIn(c)) {
            ok = YouTubeLibrary.syncIfDue(c, force = true) || ok
        }
        com.music.spotui.data.recommendation.engine.Personalizer.invalidate()
        Log.d(TAG, "hourly library sync done (ok=$ok)")
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "LibrarySync"
        private const val WORK = "aalap-library-sync"

        /** Schedules the hourly sync (keeps an existing schedule). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

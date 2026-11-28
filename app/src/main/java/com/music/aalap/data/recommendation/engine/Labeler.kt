package com.music.aalap.data.recommendation.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Works out a song's era, genres and feel when no playlist told us: from the songs played next to
 * it everywhere (Spotify, YouTube Music, JioSaavn). A song surrounded by 90s romantic songs is a
 * 90s romantic song. Saved as labels, so a good song that was held back (unknown can't join a
 * queue) is confirmed and can be picked next time.
 *
 * Background, a few songs at a time, each song at most once a week.
 */
object Labeler {
    private const val TAG = "Labeler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tried = com.music.aalap.di.CappedMap<String, Long>(3000)
    private val running = java.util.concurrent.atomic.AtomicInteger(0)

    fun learn(context: Context, songs: List<Song>) {
        val now = System.currentTimeMillis()
        val todo = songs.filter { s -> (tried[s.id] ?: 0L) < now - 7 * 86_400_000L }
        if (todo.isEmpty() || running.get() >= 2) return
        todo.forEach { tried[it.id] = now }
        running.incrementAndGet()
        scope.launch {
            try {
                val db = RecoDb.get(context)
                for (s in todo) runCatching { label(context, db, s) }.onFailure { Log.w(TAG, "${s.title}: $it") }
            } finally { running.decrementAndGet() }
        }
    }

    private suspend fun label(context: Context, db: RecoDb, song: Song) {
        val fused = Fusion.fuse(QueueEngine.ranked(context, song, QueueEngine.BACKGROUND_TIMEOUT_MS)).take(15)
        val near = db.songs(fused.map { it.id }).values.toList()
        if (near.size < 4) return
        val labels = near.map { VibeStore.labels(context, it) }
        val vibes = VibeStore.vibes(context, near)
        val era = QueuePicker.stationEra(labels.mapNotNull { it.era })
        val genres = QueuePicker.stationGenres(labels.map { it.genres })
        val feel = QueuePicker.stationFeel(near.mapNotNull { n -> vibes[n.id]?.let { it to 1.0 } })
        val ids = listOf(song.id)
        era?.let { db.putTags(ids, Trainer.ERAS[it], "near", 0.7) }
        genres.forEach { db.putTags(ids, "G_" + it.uppercase(), "near", 0.6) }
        feel?.dominant?.let { db.putTags(ids, it.name, "near", 0.6) }
        VibeStore.forget(song.id)
        Log.d(TAG, "'${song.title}': era=${era?.let { Trainer.ERAS[it] }} genres=$genres feel=${feel?.dominant}")
    }
}

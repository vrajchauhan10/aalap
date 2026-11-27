package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.QueueEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * The one recommendation entry point for every surface (autoplay, queue top-ups,
 * search radio, Fresh picks, mood mixes, Aalap Radio). The work happens in
 * [QueueEngine]; see docs/recommendation/README.md for the pipeline.
 */
object Recommender {

    /** The next songs after [seed], chained one by one (each pick seeds the next). */
    fun next(context: Context, seed: SongsModel, excludeAppIds: Set<Int> = emptySet(), length: Int = QueueEngine.DEFAULT_LENGTH): Flow<SongsModel> =
        // After a podcast episode comes more podcast, not songs.
        if (seed.url.startsWith("episode:")) kotlinx.coroutines.flow.flow {
            com.music.aalap.data.local.Podcasts.nextAfter(context, seed, excludeAppIds).forEach { emit(it) }
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
        else QueueEngine.queueSongs(context, seed, length, excludeAppIds)

    suspend fun freshPicks(context: Context, salt: Long, limit: Int = 25): List<SongsModel> =
        QueueEngine.freshPicks(context, salt, limit)

    fun rerank(context: Context, candidates: List<SongsModel>, familiar: Float): List<SongsModel> =
        QueueEngine.rerank(context, candidates, familiar)
}

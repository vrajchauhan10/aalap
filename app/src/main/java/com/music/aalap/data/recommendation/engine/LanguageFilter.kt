package com.music.aalap.data.recommendation.engine

import android.content.Context
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.RecoPrefs

/**
 * Your languages as a hard rule on any list of songs (stations, mixes, picks): only songs known
 * to be in a language you turned on stay. Nothing falls back to other languages when few are
 * left. IO thread (it looks songs up in the recommendation database).
 */
object LanguageFilter {
    fun keep(context: Context, songs: List<SongsModel>): List<SongsModel> = keepBy(context, songs) { it }

    fun <T> keepBy(context: Context, items: List<T>, song: (T) -> SongsModel): List<T> {
        val mix = RecoPrefs.languageMix(context)
        if (mix.isEmpty() || items.isEmpty()) return items
        return runCatching {
            val canon = RecoDb.get(context).resolveAll(items.map { SongBridge.toCandidate(song(it)) })
            items.zip(canon).filter { (_, c) -> RecoPrefs.languageAllowed(mix, VibeStore.language(context, c)) }.map { it.first }
        }.getOrDefault(items)
    }
}

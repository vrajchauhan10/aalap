package com.music.aalap.ui.share

import android.content.Context
import android.graphics.Bitmap
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shares playlists, albums, artists and the Blend invite as picture cards (like songs and
 * lyrics), with the same message as before alongside, so a friend still gets the link or the
 * Aalap code. The card is made in the background ([ShareTask]).
 */
object SharePosters {
    private fun load(app: Context, url: String, size: Int): Bitmap? =
        if (url.isBlank()) null else runCatching {
            com.bumptech.glide.Glide.with(app).asBitmap().load(url).submit(size, size).get()
        }.getOrNull()

    private fun lines(songs: List<SongsModel>) = songs.take(5).map { it.title to it.singer.substringBefore(",").trim() }

    fun list(context: Context, kind: String, title: String, coverUri: String, songs: List<SongsModel>, message: String) {
        ShareTask.run(context, "the ${kind.lowercase()}") { app ->
            val card = withContext(Dispatchers.IO) {
                // Four different covers make a grid; otherwise the list's own cover.
                val covers = songs.map { it.coverUri }.filter { it.isNotBlank() }.distinct().take(4)
                    .takeIf { it.size >= 4 }?.mapNotNull { load(app, it, 400) }?.takeIf { it.size >= 4 }
                    ?: listOfNotNull(load(app, coverUri.ifBlank { songs.firstOrNull()?.coverUri.orEmpty() }, 800))
                val sub = com.music.aalap.ui.components.counted(songs.size, "song")
                ShareCards.listCard(app, kind, covers, title, sub, lines(songs))
            }
            ShareCards.share(app, card, "aalap-${kind.lowercase()}", message)
        }
    }

    fun artist(context: Context, name: String, photoUri: String, songs: List<SongsModel>, message: String) {
        ShareTask.run(context, "the artist") { app ->
            val card = withContext(Dispatchers.IO) { ShareCards.artistCard(app, load(app, photoUri, 700), name, lines(songs)) }
            ShareCards.share(app, card, "aalap-artist", message)
        }
    }

    fun taste(context: Context, name: String, artists: List<String>, languages: List<String>, message: String) {
        ShareTask.run(context, "your taste") { app ->
            val card = withContext(Dispatchers.IO) { ShareCards.tasteCard(app, name, artists, languages) }
            ShareCards.share(app, card, "aalap-blend-invite", message)
        }
    }
}

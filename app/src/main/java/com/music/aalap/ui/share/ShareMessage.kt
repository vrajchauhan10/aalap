package com.music.aalap.ui.share

import java.net.URLEncoder

/**
 * The message shared with a song: links anyone can open, on Android or iPhone (Spotify and
 * YouTube Music), written in the message itself (WhatsApp and most chats drop links sent any
 * other way).
 */
object ShareMessage {
    /** An artist: their name and a link anyone can open, with no Spotify address in it. */
    fun forArtist(name: String): String {
        val q = java.net.URLEncoder.encode(name, "UTF-8")
        return "Listen to $name on Aalap\n\nhttps://music.youtube.com/search?q=$q"
    }

    fun forSong(title: String, artist: String, spotifyId: String): String {
        val q = URLEncoder.encode("$title $artist".trim(), "UTF-8")
        val spotify = if (spotifyId.isNotBlank()) "https://open.spotify.com/track/$spotifyId" else "https://open.spotify.com/search/$q"
        return "Listening to $title by $artist on Aalap\n\nSpotify: $spotify\nYouTube Music: https://music.youtube.com/search?q=$q"
    }
}

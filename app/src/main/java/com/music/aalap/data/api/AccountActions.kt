package com.music.aalap.data.api

import android.content.Context

/** The one log-out, used by every Log out button. */
object AccountActions {
    /**
     * Signs out of Spotify in Aalap: the saved login and the live token go, the cached
     * feeds are dropped and the welcome step comes back. Your plays, likes made in Aalap,
     * downloads and Aalap playlists stay on the phone; Spotify's own lists are refreshed
     * from whichever account signs in next.
     */
    fun logOut(context: Context) {
        SpotifySession.setSpDc(context, "")
        SpotifyTokenProvider.clear()
        Api.HomeCache.clear()
        com.music.aalap.data.preferences.clearAalapAccount(context)
    }
}

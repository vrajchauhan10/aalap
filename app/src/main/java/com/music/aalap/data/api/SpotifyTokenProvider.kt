package com.music.aalap.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures the [Spotify] singleton holds a valid web access token before any
 * metadata call. Mirrors how Meld authenticates: a logged-in `sp_dc` cookie is
 * exchanged for a short-lived bearer token via [SpotifyAuth.fetchAccessToken].
 *
 * The `sp_dc` cookie is read from [SpotifySession]; paste yours there (or via
 * the in-app settings hook) to enable real Spotify data. Tokens are cached
 * until ~1 minute before expiry and refreshed on demand.
 */
object SpotifyTokenProvider {
    /** Forgets the current token (logging out), so nothing keeps using the old account. */
    fun clear() {
        com.metrolist.spotify.Spotify.accessToken = null
        expiresAtMs = 0L
        anonymous = false
    }

    /** True while the token held is the signed-out one (no Spotify account connected). */
    @Volatile private var anonymous = false

    /**
     * A token for Spotify's public catalog: charts, search, artists, albums and playlists.
     * Your own account's when Spotify is connected; otherwise the signed-out web player's,
     * so Top 50, artist pages and search work for everyone, not only Spotify users.
     * Never use this for your likes, library or follows: those need [ensureToken].
     */
    suspend fun ensureCatalogToken(context: Context): Boolean {
        if (SpotifySession.spDc(context).isNotBlank()) return ensureToken(context)
        return mutex.withLock {
            val now = System.currentTimeMillis()
            if (anonymous && Spotify.accessToken != null && now < expiresAtMs - 60_000L) return@withLock true
            if (now - lastFailureAtMs < FAILURE_BACKOFF_MS) return@withLock false
            SpotifyAuth.fetchAnonymousToken().fold(
                onSuccess = { token ->
                    Spotify.accessToken = token.accessToken
                    expiresAtMs = token.accessTokenExpirationTimestampMs
                    anonymous = true
                    lastFailureAtMs = 0L
                    true
                },
                onFailure = { Log.e(TAG, "Signed-out Spotify token failed", it); lastFailureAtMs = now; false },
            )
        }
    }

    private const val TAG = "SpotifyTokenProvider"
    private val mutex = Mutex()
    private var expiresAtMs: Long = 0L
    // Each attempt costs 3 requests (one to the rate-limited GitHub API), so a failure
    // is remembered briefly instead of being retried by every call on every screen.
    private const val FAILURE_BACKOFF_MS = 30_000L
    private var lastFailureAtMs: Long = 0L

    /** [force] skips the cached token and the failure backoff (fresh login). */
    suspend fun ensureToken(context: Context, force: Boolean = false): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && !anonymous && Spotify.accessToken != null && now < expiresAtMs - 60_000L) {
            return@withLock true
        }
        if (!force && now - lastFailureAtMs < FAILURE_BACKOFF_MS) return@withLock false
        val spDc = SpotifySession.spDc(context)
        if (spDc.isBlank()) {
            Log.w(TAG, "No sp_dc cookie set — Spotify data unavailable")
            return@withLock false
        }
        SpotifyAuth.fetchAccessToken(spDc).fold(
            onSuccess = { token ->
                Spotify.accessToken = token.accessToken
                expiresAtMs = token.accessTokenExpirationTimestampMs
                anonymous = false
                lastFailureAtMs = 0L
                Log.d(TAG, "Spotify token refreshed")
                true
            },
            onFailure = {
                Log.e(TAG, "Failed to fetch Spotify token", it)
                lastFailureAtMs = now
                false
            },
        )
    }
}

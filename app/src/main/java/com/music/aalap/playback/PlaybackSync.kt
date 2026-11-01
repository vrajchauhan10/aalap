package com.music.aalap.playback

/**
 * What the screen shows about the song that's playing, worked out from the player itself.
 *
 * The player is the one truth: the song it holds (its media item's id, the play query) and
 * where that song is. The screen shows a position only for the song it shows; while another
 * song is still in the player (fading out, or the next one being found), it shows the start.
 * Pure, so it's tested without a phone (PlaybackSyncTest).
 */
object PlaybackSync {

    /** Elapsed and length of the shown song, in ms (length 0 while nobody knows it yet). */
    data class Progress(val positionMs: Long, val durationMs: Long) {
        /** How far along, 0..1 (0 while the length is unknown). */
        val fraction: Float
            get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    /**
     * @param shownQuery the play query of the song on screen.
     * @param playerQuery the play query of the song in the player (null: nothing loaded).
     * @param playerPositionMs the player's position (may be negative or unset while loading).
     * @param playerDurationMs the player's length (negative / unset until the stream says).
     * @param listedDurationMs the length the catalogue gives for the shown song (0 if unknown):
     *   only used until the stream reports its own, which can differ (another edit).
     * @param resumeAtMs where the shown song will resume when nothing is loaded yet (a restored
     *   session), 0 if it starts from the top.
     */
    fun progress(
        shownQuery: String,
        playerQuery: String?,
        playerPositionMs: Long,
        playerDurationMs: Long,
        listedDurationMs: Long,
        resumeAtMs: Long = 0L,
    ): Progress {
        val listed = listedDurationMs.coerceAtLeast(0L)
        // Nothing loaded: the restored song shows where it will carry on from.
        if (playerQuery == null) {
            val at = resumeAtMs.coerceAtLeast(0L).let { if (listed > 0) it.coerceAtMost(listed) else it }
            return Progress(at, listed)
        }
        // Another song is in the player: this one hasn't started.
        if (playerQuery != shownQuery) return Progress(0L, listed)
        val duration = if (playerDurationMs > 0) playerDurationMs else listed
        val position = playerPositionMs.coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }
        return Progress(position, duration)
    }

    /**
     * Whether a position read from the player may be saved as the saved song's resume point:
     * only when the player holds that same song (a skip used to save the old song's position
     * against the new one, so a later restore started it 30-odd seconds in).
     */
    fun positionBelongsTo(savedQuery: String?, playerQuery: String?): Boolean =
        !savedQuery.isNullOrBlank() && savedQuery == playerQuery

    /**
     * Whether Home offers "Continue" for a restored song: you were at least 15 s in and
     * not in its last 10 s (a song that was basically over isn't worth resuming).
     */
    fun worthContinuing(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= 15_000L && (durationMs <= 0L || positionMs < durationMs - 10_000L)
}

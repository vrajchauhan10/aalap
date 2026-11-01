package com.music.aalap.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen's position, length and resume point follow the song actually in the player. */
class PlaybackSyncTest {
    private val unset = Long.MIN_VALUE + 1 // ExoPlayer's C.TIME_UNSET

    @Test fun sameSongShowsThePlayersPositionAndLength() {
        val p = PlaybackSync.progress("kesariya arijit", "kesariya arijit", 65_000, 268_000, 270_000)
        assertEquals(65_000L, p.positionMs)
        assertEquals(268_000L, p.durationMs) // the stream's own length wins over the catalogue's
        assertEquals(65_000f / 268_000f, p.fraction, 1e-6f)
    }

    @Test fun previousSongStillInThePlayerShowsTheStart() {
        // Skipped at 0:35 of song A; B is on screen while A fades out / B is looked up.
        val p = PlaybackSync.progress("song b", "song a", 35_000, 200_000, 180_000)
        assertEquals(0L, p.positionMs)
        assertEquals(180_000L, p.durationMs)
        assertEquals(0f, p.fraction)
    }

    @Test fun unknownStreamLengthFallsBackToTheCatalogue() {
        val p = PlaybackSync.progress("q", "q", 4_000, unset, 150_000)
        assertEquals(4_000L, p.positionMs)
        assertEquals(150_000L, p.durationMs)
    }

    @Test fun nothingKnownShowsZeroNotNonsense() {
        val p = PlaybackSync.progress("q", "q", unset, unset, 0)
        assertEquals(0L, p.positionMs)
        assertEquals(0L, p.durationMs)
        assertEquals(0f, p.fraction)
    }

    @Test fun positionNeverPastTheEnd() {
        val p = PlaybackSync.progress("q", "q", 181_000, 180_000, 0)
        assertEquals(180_000L, p.positionMs)
        assertEquals(1f, p.fraction)
    }

    @Test fun shortAndLongSongs() {
        assertEquals(0.5f, PlaybackSync.progress("q", "q", 15_000, 30_000, 0).fraction, 1e-6f)
        val hour = 3_600_000L
        assertEquals(0.25f, PlaybackSync.progress("q", "q", hour / 4, hour, 0).fraction, 1e-6f)
    }

    @Test fun nothingLoadedShowsTheResumePoint() {
        val p = PlaybackSync.progress("q", null, 0, 0, 240_000, resumeAtMs = 46_000)
        assertEquals(46_000L, p.positionMs)
        assertEquals(240_000L, p.durationMs)
    }

    @Test fun resumePointIsClampedToTheLength() {
        val p = PlaybackSync.progress("q", null, 0, 0, 30_000, resumeAtMs = 46_000)
        assertEquals(30_000L, p.positionMs)
    }

    @Test fun nothingLoadedAndNoResumeStartsAtZero() {
        assertEquals(0L, PlaybackSync.progress("q", null, 0, 0, 200_000).positionMs)
    }

    @Test fun anItemWithoutAnIdIsNotTheShownSong() {
        assertEquals(0L, PlaybackSync.progress("q", "", 50_000, 100_000, 100_000).positionMs)
    }

    @Test fun positionIsSavedOnlyForTheSongItWasReadFrom() {
        assertTrue(PlaybackSync.positionBelongsTo("song b", "song b"))
        assertFalse(PlaybackSync.positionBelongsTo("song b", "song a")) // read during a skip
        assertFalse(PlaybackSync.positionBelongsTo("song b", null))
        assertFalse(PlaybackSync.positionBelongsTo(null, "song b"))
        assertFalse(PlaybackSync.positionBelongsTo("", ""))
    }

    @Test fun continueOnlyWhenPartWayThrough() {
        assertFalse(PlaybackSync.worthContinuing(5_000, 240_000)) // barely started
        assertTrue(PlaybackSync.worthContinuing(81_000, 240_000))
        assertFalse(PlaybackSync.worthContinuing(235_000, 240_000)) // practically over
        assertTrue(PlaybackSync.worthContinuing(81_000, 0)) // length unknown
    }
}

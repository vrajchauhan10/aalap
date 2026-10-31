package com.music.aalap.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quick picks: loading, success, empty and failed refreshes, with and without picks on screen. */
class PicksStatusTest {
    private val songs = Result.success(listOf("a", "b"))
    private val none = Result.success(emptyList<String>())
    private val failed = Result.failure<List<String>>(RuntimeException("rate limited"))

    @Test fun newPicksAreShown() {
        assertEquals(PicksStatus.READY, PicksStatus.after(0, songs))
        assertTrue(PicksStatus.replaces(songs))
    }

    @Test fun nothingFoundWithNothingShownSaysSo() {
        assertEquals(PicksStatus.EMPTY, PicksStatus.after(0, none))
        assertFalse(PicksStatus.replaces(none))
    }

    @Test fun failureWithNothingShownOffersARetry() {
        assertEquals(PicksStatus.FAILED, PicksStatus.after(0, failed))
        assertFalse(PicksStatus.replaces(failed))
    }

    @Test fun aFailedOrEmptyRefreshKeepsThePicksOnScreen() {
        assertEquals(PicksStatus.READY, PicksStatus.after(12, failed))
        assertEquals(PicksStatus.READY, PicksStatus.after(12, none))
        assertFalse(PicksStatus.replaces(failed))
        assertFalse(PicksStatus.replaces(none))
    }

    @Test fun aGoodRefreshReplacesThePicksOnScreen() {
        assertEquals(PicksStatus.READY, PicksStatus.after(12, songs))
        assertTrue(PicksStatus.replaces(songs))
    }
}

package com.music.aalap.data.recommendation

import com.music.aalap.data.recommendation.engine.SkipMemory
import com.music.aalap.data.recommendation.engine.SkipMemory.Heard
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which skipped songs are left out of queues and radio, and for how long. */
class SkipMemoryTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test fun anySkipInTheLastTwoWeeksIsLeftOut() {
        assertEquals(setOf("a"), SkipMemory.tiredOf(listOf(Heard("a", now - 3 * day, 25_000, true)), now))
    }

    @Test fun oneOldSkipIsForgiven() {
        assertEquals(emptySet<String>(), SkipMemory.tiredOf(listOf(Heard("a", now - 20 * day, 4_000, true)), now))
    }

    @Test fun twoEarlySkipsKeepItOutForNinetyDays() {
        val plays = listOf(Heard("a", now - 60 * day, 4_000, true), Heard("a", now - 30 * day, 6_000, true))
        assertEquals(setOf("a"), SkipMemory.tiredOf(plays, now))
    }

    @Test fun twoLateSkipsAreNotEarly() {
        val plays = listOf(Heard("a", now - 60 * day, 25_000, true), Heard("a", now - 30 * day, 25_000, true))
        assertEquals(emptySet<String>(), SkipMemory.tiredOf(plays, now))
    }

    @Test fun earlySkipsOlderThanNinetyDaysDontCount() {
        val plays = listOf(Heard("a", now - 120 * day, 4_000, true), Heard("a", now - 100 * day, 4_000, true))
        assertEquals(emptySet<String>(), SkipMemory.tiredOf(plays, now))
    }

    @Test fun finishingItLaterForgivesEarlierSkips() {
        val plays = listOf(
            Heard("a", now - 60 * day, 4_000, true), Heard("a", now - 30 * day, 4_000, true),
            Heard("a", now - 5 * day, 200_000, false),
        )
        assertEquals(emptySet<String>(), SkipMemory.tiredOf(plays, now))
    }

    @Test fun aSkipAfterFinishingStillCounts() {
        val plays = listOf(Heard("a", now - 20 * day, 200_000, false), Heard("a", now - 2 * day, 3_000, true))
        assertEquals(setOf("a"), SkipMemory.tiredOf(plays, now))
    }

    @Test fun zeroListenedIsNotAnEarlySkip() {
        // 0 ms means "not measured" (old entries), not "skipped instantly".
        val plays = listOf(Heard("a", now - 60 * day, 0, true), Heard("a", now - 30 * day, 0, true))
        assertEquals(emptySet<String>(), SkipMemory.tiredOf(plays, now))
    }
}

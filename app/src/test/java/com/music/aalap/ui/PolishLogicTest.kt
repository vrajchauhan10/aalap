package com.music.aalap.ui

import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.ReleaseRadar
import com.music.aalap.ui.screens.SelfCheck
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishLogicTest {
    private fun song(id: Int, title: String, artist: String) = SongsModel(id, title, "", artist, "", "q:$id")

    @Test fun radarWeekStartsOnFriday() {
        assertEquals(LocalDate.of(2026, 11, 20), ReleaseRadar.weekOf(LocalDate.of(2026, 11, 23))) // Monday
        assertEquals(LocalDate.of(2026, 11, 20), ReleaseRadar.weekOf(LocalDate.of(2026, 11, 20))) // Friday itself
        assertEquals(LocalDate.of(2026, 11, 13), ReleaseRadar.weekOf(LocalDate.of(2026, 11, 19))) // Thursday
    }

    @Test fun radarTakesNewestFirstAndCapsEachArtist() {
        val today = LocalDate.of(2026, 11, 23)
        val releases = listOf(
            ReleaseRadar.Release("Old", "2026-09-01", listOf(song(1, "Old", "A"))),
            ReleaseRadar.Release("Single B", "2026-11-20", listOf(song(2, "Single B", "B"), song(3, "Single B (Lofi)", "B"))),
            ReleaseRadar.Release("Big Album", "2026-11-10", List(12) { song(10 + it, if (it == 4) "Big Album" else "Track $it", "C") }),
        ) + List(4) { i -> ReleaseRadar.Release("S$i", "2026-11-0${i + 1}", listOf(song(40 + i, "S$i", "A"), song(50 + i, "S$i b", "A"))) }
        val out = ReleaseRadar.pick(releases, today)
        assertEquals(2, out.first().id) // newest release first
        assertEquals("Big Album", out.first { it.singer == "C" }.title) // album gives its title song
        assertEquals(1, out.count { it.singer == "C" })
        assertTrue(out.count { it.singer == "A" } <= 3)
        assertTrue(out.none { it.id == 1 }) // older than 8 weeks, and there were enough new ones
    }

    @Test fun radarReachesBackAYearWhenLittleIsNew() {
        val out = ReleaseRadar.pick(listOf(ReleaseRadar.Release("Old", "2026-03-01", listOf(song(1, "Old", "A")))), LocalDate.of(2026, 11, 23))
        assertEquals(listOf(1), out.map { it.id })
    }

    @Test fun coverTintStaysReadable() {
        val white = com.music.aalap.di.Palette.readableTint(0xFFFFFF)
        val r = (white shr 16) and 0xFF
        assertTrue("white cover tint should be darkened, was $r", r < 160)
        val dark = 0x202040
        assertEquals(dark or (0xFF shl 24), com.music.aalap.di.Palette.readableTint(dark))
    }

    @Test fun selfCheckSummary() {
        fun r(t: String, s: SelfCheck.Status) = SelfCheck.Result(t, s, "")
        assertEquals("Everything is working", SelfCheck.summary(listOf(r("Internet", SelfCheck.Status.OK))))
        assertEquals("Working, with 1 thing to look at", SelfCheck.summary(listOf(r("Notifications", SelfCheck.Status.WARN))))
        assertEquals("2 things need fixing", SelfCheck.summary(listOf(r("Internet", SelfCheck.Status.FAIL), r("Storage", SelfCheck.Status.FAIL))))
        assertEquals(SelfCheck.Status.FAIL, SelfCheck.storage(100_000_000L).status)
        assertEquals(SelfCheck.Status.OK, SelfCheck.storage(20_000_000_000L).status)
    }

    @Test fun cappedSetForgetsOldest() {
        val s = com.music.aalap.di.cappedSet<Int>(100)
        repeat(500) { s += it }
        assertTrue(s.size <= 100)
        assertTrue(499 in s)
    }
}

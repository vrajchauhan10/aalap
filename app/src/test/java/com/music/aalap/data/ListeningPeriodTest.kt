package com.music.aalap.data

import com.music.aalap.data.preferences.ListeningPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Wrapped's weeks run Monday to Sunday; months are calendar months. */
class ListeningPeriodTest {
    @Test fun weekRunsMondayToSunday() {
        val w = ListeningPeriod.week(LocalDate.of(2026, 11, 1)) // a Sunday
        assertEquals(LocalDate.of(2026, 10, 26), w.from)
        assertTrue(LocalDate.of(2026, 11, 1) in w)
        assertFalse(LocalDate.of(2026, 11, 2) in w)
        assertFalse(LocalDate.of(2026, 10, 25) in w)
        assertTrue(w.isWeek)
    }

    @Test fun previousWeekAndMonth() {
        assertEquals(LocalDate.of(2026, 10, 19), ListeningPeriod.week(LocalDate.of(2026, 10, 28)).previous().from)
        val m = ListeningPeriod.month(YearMonth.of(2026, 1)).previous()
        assertEquals(LocalDate.of(2025, 12, 1), m.from)
        assertEquals(LocalDate.of(2026, 1, 1), m.until)
    }
}

package com.music.aalap.data.preferences

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A stretch of days Wrapped can sum up: a week (Monday to Sunday) or a calendar month.
 * Pure, so it's tested.
 */
data class ListeningPeriod(val from: LocalDate, val until: LocalDate, val label: String, val isWeek: Boolean) {
    operator fun contains(day: LocalDate) = !day.isBefore(from) && day.isBefore(until)

    /** The same length of time just before this one (for "more than the week before"). */
    fun previous(): ListeningPeriod = if (isWeek) week(from.minusDays(7)) else month(YearMonth.from(from).minusMonths(1))

    companion object {
        fun week(anyDay: LocalDate): ListeningPeriod {
            val monday = anyDay.with(DayOfWeek.MONDAY)
            val label = "week of ${monday.dayOfMonth} ${monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
            return ListeningPeriod(monday, monday.plusDays(7), label, isWeek = true)
        }

        fun month(m: YearMonth) = ListeningPeriod(
            m.atDay(1), m.plusMonths(1).atDay(1),
            "${m.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${m.year}", isWeek = false,
        )
    }
}

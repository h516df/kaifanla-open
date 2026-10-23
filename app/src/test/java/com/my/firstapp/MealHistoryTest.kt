package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MealHistoryTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun time(year: Int, month: Int, day: Int, hour: Int = 12) = Calendar.getInstance(utc).apply {
        clear(); set(year, month - 1, day, hour, 0)
    }.timeInMillis
    private fun record(time: Long) = Record(time, "🍜", "Kitchen", "Noodles", 20.0, 15.0)

    @Test fun sameMonthAndDayIn2025And2026StaySeparate() {
        val days = mealDays(listOf(record(time(2025, 10, 19)), record(time(2026, 10, 19))), utc)
        assertEquals(listOf(2026, 2025), days.map { it.date.year })
        assertEquals(listOf(1, 1), days.map { it.records.size })
    }
    @Test fun repeatedWeekdayAcrossYearsDoesNotMergeDisplayLabels() {
        // 2020-10-19 and 2026-10-19 are both Mondays: the old M月d日 EEEE key collided.
        val days = mealDays(listOf(record(time(2020, 10, 19)), record(time(2026, 10, 19))), utc)
        assertEquals(2, days.size)
    }
    @Test fun monthTotalsExcludeOtherYearsAndMonths() {
        val summary = mealMonth(listOf(record(time(2025, 10, 19)), record(time(2026, 10, 19)), record(time(2026, 9, 30))), time(2026, 10, 20), utc)
        assertEquals(1, summary.count)
        assertEquals(15.0, summary.paid, 0.001)
        assertEquals(5.0, summary.saved, 0.001)
    }
    @Test fun recordsAreOrderedNewestFirstWithinEachDay() {
        val older = record(time(2026, 10, 19, 8))
        val newer = record(time(2026, 10, 19, 20))
        assertEquals(listOf(newer, older), mealDays(listOf(older, newer), utc).single().records)
    }
    @Test fun groupingRespectsLocalMidnight() {
        val days = mealDays(listOf(record(time(2026, 10, 19, 20))), TimeZone.getTimeZone("Asia/Shanghai"))
        assertEquals(20, days.single().date.day)
    }
    @Test fun changingZoneReprojectsBothDayGroupsAndMonthlyTotals() {
        val records = listOf(record(time(2026, 9, 30, 23) + 30 * 60 * 1000), record(time(2026, 10, 1, 0) + 30 * 60 * 1000))
        val now = time(2026, 10, 1, 1)
        val shanghai = TimeZone.getTimeZone("Asia/Shanghai")
        assertEquals(2, mealDays(records, utc).size)
        assertEquals(1, mealDays(records, shanghai).size)
        assertEquals(1, mealMonth(records, now, utc).count)
        assertEquals(2, mealMonth(records, now, shanghai).count)
    }
    @Test fun emptyHistoryHasZeroTotals() {
        assertTrue(mealDays(emptyList(), utc).isEmpty())
        assertEquals(0, mealMonth(emptyList(), time(2026, 10, 19), utc).count)
    }
}

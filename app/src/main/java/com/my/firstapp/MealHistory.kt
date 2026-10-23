package com.my.firstapp

import java.util.Calendar
import java.util.TimeZone

data class MealDate(val year: Int, val month: Int, val day: Int)
data class MealDay(val date: MealDate, val records: List<Record>)
data class MealMonth(val year: Int, val month: Int, val count: Int, val paid: Double, val saved: Double)

private fun mealDate(time: Long, zone: TimeZone): MealDate = Calendar.getInstance(zone).run {
    timeInMillis = time
    MealDate(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
}

/** Calendar identity includes the year, unlike a translated display label. */
fun mealDays(history: List<Record>, zone: TimeZone = TimeZone.getDefault()): List<MealDay> =
    history.sortedByDescending { it.time }
        .groupBy { mealDate(it.time, zone) }
        .map { (date, records) -> MealDay(date, records) }

fun mealMonth(history: List<Record>, now: Long, zone: TimeZone = TimeZone.getDefault()): MealMonth {
    val current = mealDate(now, zone)
    val records = history.filter {
        val date = mealDate(it.time, zone)
        date.year == current.year && date.month == current.month
    }
    return MealMonth(current.year, current.month, records.size, records.sumOf { it.paid }, records.sumOf { it.saved })
}

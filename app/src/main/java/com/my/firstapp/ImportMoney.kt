package com.my.firstapp

/** Shared input boundary; unknown is null, never a fabricated zero. */
internal fun validImportMoney(value: Double?): Boolean = value == null || (value.isFinite() && value >= 0)

/** Returns null for explicitly unknown, -1 for invalid input; never uses import time as observation. */
internal fun parseObservedDate(text: String): Long? {
    if(text.isBlank())return null
    if(!text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))return -1L
    val parser=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA).apply{isLenient=false}
    val position=java.text.ParsePosition(0)
    val date=parser.parse(text,position) ?: return -1L
    return date.time.takeIf{position.index==text.length && it>0} ?: -1L
}

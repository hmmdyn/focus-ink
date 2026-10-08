package com.dain.focusink.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Dates {
    fun today(now: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateOf(now, zone)

    fun dateOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

    fun timeOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()

    fun plusDays(date: String, days: Long): String = LocalDate.parse(date).plusDays(days).toString()

    fun weekStart(date: String): String =
        LocalDate.parse(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    fun dayOfWeek(date: String): Int = LocalDate.parse(date).dayOfWeek.value

    /** "21:30" → LocalTime. 잘못된 값이면 fallback. */
    fun parseTime(hhmm: String, fallback: LocalTime = LocalTime.of(21, 30)): LocalTime =
        runCatching { LocalTime.parse(hhmm.trim()) }.getOrDefault(fallback)

    fun hhmm(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeOf(epochMs, zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    private val KOREAN_DOW = listOf("월", "화", "수", "목", "금", "토", "일")

    fun dowShort(date: String): String = KOREAN_DOW[dayOfWeek(date) - 1]

    /** "10월 8일 (목)" */
    fun pretty(date: String): String {
        val d = LocalDate.parse(date)
        return "${d.monthValue}월 ${d.dayOfMonth}일 (${dowShort(date)})"
    }

    fun shortMd(date: String): String {
        val d = LocalDate.parse(date)
        return String.format(Locale.ROOT, "%d/%d", d.monthValue, d.dayOfMonth)
    }

    /** 오늘을 포함한 최근 n일, 오래된 것부터 */
    fun lastDays(today: String, n: Int): List<String> = (n - 1 downTo 0).map { plusDays(today, -it.toLong()) }
}

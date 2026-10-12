package com.todocloud.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Same rules as the server's app/recurrence.py, for tasks kept on the device. */
internal val RECURRENCE_RULES = setOf("daily", "weekdays", "weekly", "monthly")

internal fun parseInstantText(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

private fun step(local: LocalDateTime, rule: String, anchorDay: Int): LocalDateTime = when (rule) {
    "daily" -> local.plusDays(1)
    "weekdays" -> {
        var next = local.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) next = next.plusDays(1)
        next
    }
    "weekly" -> local.plusWeeks(1)
    "monthly" -> {
        val month = YearMonth.from(local).plusMonths(1)
        // The 31st becomes the last day of shorter months and returns to the 31st after.
        local.withYear(month.year).withMonth(month.monthValue).withDayOfMonth(minOf(anchorDay, month.lengthOfMonth()))
    }
    else -> throw IllegalArgumentException("unknown recurrence rule: $rule")
}

/**
 * The first occurrence after both [dueAt] and [now], stepped in [zone] so 08:00
 * stays 08:00 local time. A task finished days late gets one future copy.
 */
internal fun nextOccurrence(dueAt: Instant, rule: String, zone: ZoneId, now: Instant): Instant {
    val start = dueAt.atZone(zone).toLocalDateTime()
    val anchorDay = start.dayOfMonth
    var wall = start
    repeat(5000) {
        wall = step(wall, rule, anchorDay)
        val candidate = wall.atZone(zone).toInstant()
        if (candidate.isAfter(now)) return candidate
    }
    return wall.atZone(zone).toInstant()
}

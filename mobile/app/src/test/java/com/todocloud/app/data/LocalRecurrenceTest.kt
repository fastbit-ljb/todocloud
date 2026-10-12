package com.todocloud.app.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

/** Mirrors server/tests/test_recurrence.py so local mode repeats exactly like the server. */
class LocalRecurrenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun sh(y: Int, m: Int, d: Int, h: Int, min: Int): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    @Test
    fun dailyKeepsLocalTime() {
        val due = sh(2026, 10, 12, 8, 0)
        assertEquals(sh(2026, 10, 13, 8, 0), nextOccurrence(due, "daily", zone, due))
    }

    @Test
    fun weekly() {
        val due = sh(2026, 10, 14, 19, 30)
        assertEquals(sh(2026, 10, 21, 19, 30), nextOccurrence(due, "weekly", zone, due))
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val friday = sh(2026, 10, 16, 9, 0)
        assertEquals(sh(2026, 10, 19, 9, 0), nextOccurrence(friday, "weekdays", zone, friday))
    }

    @Test
    fun monthlyClampsShortMonths() {
        val jan31 = sh(2027, 1, 31, 10, 0)
        assertEquals(sh(2027, 2, 28, 10, 0), nextOccurrence(jan31, "monthly", zone, jan31))
        val dec = sh(2026, 12, 15, 10, 0)
        assertEquals(sh(2027, 1, 15, 10, 0), nextOccurrence(dec, "monthly", zone, dec))
    }

    @Test
    fun overdueTaskJumpsToFirstFutureOccurrence() {
        val due = sh(2026, 10, 1, 8, 0)
        assertEquals(sh(2026, 10, 13, 8, 0), nextOccurrence(due, "daily", zone, sh(2026, 10, 12, 12, 0)))
    }

    @Test
    fun unknownRuleFails() {
        val due = sh(2026, 10, 12, 8, 0)
        assertFailsWith<IllegalArgumentException> { nextOccurrence(due, "hourly", zone, due) }
    }

    @Test
    fun parsesServerAndLocalTimestamps() {
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), parseInstantText("2026-10-12T08:00:00+08:00"))
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), parseInstantText("2026-10-12T00:00:00Z"))
    }
}

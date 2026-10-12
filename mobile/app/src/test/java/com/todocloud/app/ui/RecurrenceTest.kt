package com.todocloud.app.ui

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class RecurrenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun ruleIsKeptWhenThereIsADueTime() {
        val draft = buildTaskDraft(
            "周会", "", null, LocalDateTime.of(2026, 10, 14, 15, 0), null, null, emptyList(),
            zone = zone, recurrence = "weekly",
        )
        assertEquals("weekly", draft?.recurrence)
    }

    @Test
    fun ruleIsDroppedWithoutADueTime() {
        val draft = buildTaskDraft("背单词", "", null, null, null, null, emptyList(), zone = zone, recurrence = "daily")
        assertNull(draft?.recurrence)
    }

    @Test
    fun labelsMatchTheServerRules() {
        assertEquals(listOf("daily", "weekdays", "weekly", "monthly"), RECURRENCE_OPTIONS.mapNotNull { it.rule })
        assertEquals("工作日", recurrenceLabel("weekdays"))
        assertNull(recurrenceLabel(null))
        assertNull(recurrenceLabel("hourly"))
    }
}

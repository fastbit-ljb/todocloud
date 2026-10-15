package com.todocloud.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun permissionTestReminderFiresFiveSecondsAfterScheduling() {
        assertEquals(15_000L, permissionTestTriggerAtMillis(dueMillis = 60_000L, nowMillis = 10_000L))
    }

    @Test
    fun permissionTestReminderDoesNotScheduleAnExpiredTask() {
        assertNull(permissionTestTriggerAtMillis(dueMillis = 10_000L, nowMillis = 10_000L))
        assertNull(permissionTestTriggerAtMillis(dueMillis = 9_999L, nowMillis = 10_000L))
    }
}

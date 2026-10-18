package com.todocloud.app.notification

import com.todocloud.app.notification.ReminderScheduler.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun usesTheSelectedReminderOffsetInBothModes() {
        ReminderMode.entries.forEach { mode ->
            assertEquals(300_000L, reminderTriggerAtMillis(900_000L, 10, 60_000L, mode))
            assertEquals(900_000L, reminderTriggerAtMillis(900_000L, 0, 60_000L, mode))
        }
    }

    @Test
    fun reloadingBeforeDeliveryDoesNotMoveTheReminderForward() {
        ReminderMode.entries.forEach { mode ->
            assertEquals(900_000L, reminderTriggerAtMillis(900_000L, 0, 10_000L, mode))
            assertEquals(900_000L, reminderTriggerAtMillis(900_000L, 0, 20_000L, mode))
        }
    }

    @Test
    fun expiredTasksAreNeverScheduled() {
        ReminderMode.entries.forEach { mode ->
            assertNull(reminderTriggerAtMillis(10_000L, 0, 10_000L, mode))
            assertNull(reminderTriggerAtMillis(9_999L, 0, 10_000L, mode))
        }
    }

    @Test
    fun pastNotificationReminderIsNotReplayed() {
        assertNull(reminderTriggerAtMillis(120_000L, 1, 60_000L, ReminderMode.NOTIFICATION))
        assertNull(reminderTriggerAtMillis(120_000L, 1, 70_000L, ReminderMode.NOTIFICATION))
    }

    @Test
    fun upcomingTaskWithPastAlarmOffsetRingsOnceSoon() {
        assertEquals(71_000L, reminderTriggerAtMillis(120_000L, 1, 70_000L, ReminderMode.ALARM))
    }
}

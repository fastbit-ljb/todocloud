package com.todocloud.app.notification

import com.todocloud.app.notification.ReminderScheduler.ReminderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun snoozeIsExactlyFiveMinutesFromTheButtonPress() {
        assertEquals(1_300_000L, snoozeTriggerAtMillis(1_000_000L))
    }

    @Test
    fun rebootKeepsFutureSnoozeTimeAndDeliversExpiredSnoozeSoon() {
        assertEquals(1_300_000L, restoredSnoozeTriggerAtMillis(1_300_000L, 1_000_000L))
        assertEquals(1_001_000L, restoredSnoozeTriggerAtMillis(900_000L, 1_000_000L))
    }

    @Test
    fun unchangedAlarmTaskKeepsItsActiveSnooze() {
        assertTrue(
            shouldKeepSnoozedAlarm(
                completed = false,
                hasReminder = true,
                alarmMode = true,
                taskDueAt = "2026-10-19T12:00:00Z",
                taskOffsetMinutes = 10,
                snoozedDueAt = "2026-10-19T12:00:00Z",
                snoozedOffsetMinutes = 10,
            ),
        )
    }

    @Test
    fun completedChangedOrNotificationTaskDropsItsSnooze() {
        assertFalse(
            shouldKeepSnoozedAlarm(
                completed = true,
                hasReminder = true,
                alarmMode = true,
                taskDueAt = "same",
                taskOffsetMinutes = 10,
                snoozedDueAt = "same",
                snoozedOffsetMinutes = 10,
            ),
        )
        assertFalse(
            shouldKeepSnoozedAlarm(
                completed = false,
                hasReminder = true,
                alarmMode = true,
                taskDueAt = "changed",
                taskOffsetMinutes = 10,
                snoozedDueAt = "old",
                snoozedOffsetMinutes = 10,
            ),
        )
        assertFalse(
            shouldKeepSnoozedAlarm(
                completed = false,
                hasReminder = true,
                alarmMode = false,
                taskDueAt = "same",
                taskOffsetMinutes = 10,
                snoozedDueAt = "same",
                snoozedOffsetMinutes = 10,
            ),
        )
    }

    @Test
    fun customSoundFallsBackWithoutTryingDuplicateDefaults() {
        assertEquals(
            listOf("content://custom", "content://alarm", "content://notification"),
            alarmSoundCandidateStrings(
                storedUri = "content://custom",
                defaultAlarmUri = "content://alarm",
                defaultNotificationUri = "content://notification",
            ),
        )
        assertEquals(
            listOf("content://alarm"),
            alarmSoundCandidateStrings(
                storedUri = " ",
                defaultAlarmUri = "content://alarm",
                defaultNotificationUri = "content://alarm",
            ),
        )
    }
}

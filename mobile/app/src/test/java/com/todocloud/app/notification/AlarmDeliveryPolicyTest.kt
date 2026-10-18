package com.todocloud.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDeliveryPolicyTest {
    @Test
    fun reminderAfterSwipingMainTaskAwayRestoresTheExistingAlarmPage() {
        assertEquals(
            AlarmDeliveryAction.SHOW_EXISTING_ALARM,
            alarmDeliveryAction(activeTaskId = -1, incomingTaskId = -1, alarmWindowFocused = false),
        )
    }

    @Test
    fun repeatedReminderDoesNotRestartAnAlarmPageAlreadyInTheForeground() {
        assertEquals(
            AlarmDeliveryAction.KEEP_CURRENT_ALARM,
            alarmDeliveryAction(activeTaskId = -1, incomingTaskId = -1, alarmWindowFocused = true),
        )
    }

    @Test
    fun firstReminderAfterStoppingStartsRingingAgain() {
        assertEquals(
            AlarmDeliveryAction.START_RINGING,
            alarmDeliveryAction(activeTaskId = null, incomingTaskId = -1, alarmWindowFocused = false),
        )
    }

    @Test
    fun differentTaskStartsItsOwnAlarmEvenWhenAnAlarmIsVisible() {
        assertEquals(
            AlarmDeliveryAction.START_RINGING,
            alarmDeliveryAction(activeTaskId = 42, incomingTaskId = 43, alarmWindowFocused = true),
        )
    }
}

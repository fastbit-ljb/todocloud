package com.todocloud.app.notification

internal enum class AlarmDeliveryAction {
    START_RINGING,
    SHOW_EXISTING_ALARM,
    KEEP_CURRENT_ALARM,
}

internal fun alarmDeliveryAction(
    activeTaskId: Int?,
    incomingTaskId: Int,
    alarmWindowFocused: Boolean,
): AlarmDeliveryAction = when {
    activeTaskId != incomingTaskId -> AlarmDeliveryAction.START_RINGING
    !alarmWindowFocused -> AlarmDeliveryAction.SHOW_EXISTING_ALARM
    else -> AlarmDeliveryAction.KEEP_CURRENT_ALARM
}

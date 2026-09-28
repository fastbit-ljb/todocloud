package com.todocloud.app.notification

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional OEM-background compatibility service.
 *
 * It does not read window content or perform actions. The system owns this
 * service lifecycle; reconnecting also repairs any reminders saved locally.
 */
class ReminderAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        ReminderScheduler.restoreScheduledTasks(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}

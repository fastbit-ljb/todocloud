package com.todocloud.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderScheduler.restoreScheduledTasks(context)
        }
        // Repeating alarms are cleared by a reboot or an app update.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            com.todocloud.app.update.UpdateNotifier.scheduleDailyCheck(context, replace = true)
        }
    }
}

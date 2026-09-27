package com.todocloud.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "任务提醒"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val steps = intent.getStringExtra(EXTRA_STEPS)
        val dueAt = intent.getStringExtra(EXTRA_DUE_AT)
        val reminderOffset = intent.getIntExtra(EXTRA_REMINDER_OFFSET, 0)
        val alarmMode = intent.getBooleanExtra(EXTRA_ALARM_MODE, false)
        if (alarmMode) {
            ReminderScheduler.markAlarmDismissed(context, taskId, dueAt, reminderOffset)
            ReminderScheduler.forgetStoredTask(context, taskId)
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_STEPS, steps)
                putExtra(EXTRA_DUE_AT, dueAt)
                putExtra(EXTRA_REMINDER_OFFSET, reminderOffset)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        } else {
            showNotification(context, taskId, title)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_DUE_AT = "due_at"
        const val EXTRA_REMINDER_OFFSET = "reminder_offset_minutes"
        const val EXTRA_ALARM_MODE = "alarm_mode"

        fun showNotification(context: Context, taskId: Int, title: String) {
            ReminderScheduler.createNotificationChannel(context)
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            val openAppIntent = Intent(context, com.todocloud.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val contentIntent = android.app.PendingIntent.getActivity(
                context,
                taskId,
                openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = android.app.Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("TodoCloud 任务提醒")
                .setContentText(title)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .build()
            manager.notify(taskId, notification)
        }
    }
}

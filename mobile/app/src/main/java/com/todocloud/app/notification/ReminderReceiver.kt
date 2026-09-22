package com.todocloud.app.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(
            context = context,
            taskId = intent.getIntExtra(EXTRA_TASK_ID, 0),
            title = intent.getStringExtra(EXTRA_TITLE) ?: "任务提醒",
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"

        fun showNotification(context: Context, taskId: Int, title: String) {
            ReminderScheduler.createNotificationChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("TodoCloud 任务提醒")
            .setContentText(title)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
            manager.notify(taskId, notification)
        }
    }
}

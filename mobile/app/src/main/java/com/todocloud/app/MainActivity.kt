package com.todocloud.app

import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import com.todocloud.app.ui.TodoCloudApp
import com.todocloud.app.ui.theme.TodoCloudTheme
import com.todocloud.app.notification.ReminderScheduler

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.createNotificationChannel(this)
        // Restore alarms/notifications before the network-backed task list is
        // loaded. System AlarmManager entries survive a task swipe, but this
        // also repairs schedules after a reboot, time change, or process stop.
        ReminderScheduler.restoreScheduledTasks(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            TodoCloudTheme {
                TodoCloudApp()
            }
        }
    }

    companion object {
        fun intent(context: Context, taskId: Int): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("task_id", taskId)
            }
    }
}

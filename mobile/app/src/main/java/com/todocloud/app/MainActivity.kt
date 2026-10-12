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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.todocloud.app.ui.SharedContent
import com.todocloud.app.ui.TodoCloudApp
import com.todocloud.app.ui.sharedContentFrom
import com.todocloud.app.ui.theme.TodoCloudTheme
import com.todocloud.app.notification.ReminderScheduler

class MainActivity : ComponentActivity() {
    private val requestedTab = mutableIntStateOf(0)
    private val requestedCalendarDate = mutableStateOf<String?>(null)
    private val sharedContent = mutableStateOf<SharedContent?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ always creates a system starting window. Remove its
            // exit transition immediately so launching TodoCloud does not look
            // like a custom splash animation.
            splashScreen.setOnExitAnimationListener { splashView ->
                splashView.remove()
            }
        }
        applyNavigationRequest(intent)
        // A recreated activity (rotation, dark-mode switch) keeps its original
        // intent; only read a share on the first creation.
        if (savedInstanceState == null) sharedContent.value = sharedContentFrom(intent)
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
                TodoCloudApp(
                    requestedTab = requestedTab.intValue,
                    requestedCalendarDate = requestedCalendarDate.value,
                    sharedContent = sharedContent.value,
                    onSharedContentHandled = { sharedContent.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationRequest(intent)
        sharedContentFrom(intent)?.let { sharedContent.value = it }
    }

    private fun applyNavigationRequest(intent: Intent?) {
        requestedTab.intValue = intent?.getIntExtra(EXTRA_INITIAL_TAB, 0) ?: 0
        requestedCalendarDate.value = intent?.getStringExtra(EXTRA_CALENDAR_DATE)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val EXTRA_CALENDAR_DATE = "calendar_date"

        fun intent(context: Context, taskId: Int): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("task_id", taskId)
            }
    }
}

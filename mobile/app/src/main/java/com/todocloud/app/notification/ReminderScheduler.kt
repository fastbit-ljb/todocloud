package com.todocloud.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.todocloud.app.MainActivity
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Temporary permission-audit interval. Eligible task reminders start ten seconds
 * after scheduling and repeat while the task remains due in the future. The
 * editor's reminder offset is ignored. Keep this out of production releases.
 */
internal const val REMINDER_PERMISSION_TEST_DELAY_MILLIS = 10_000L

internal fun permissionTestTriggerAtMillis(dueMillis: Long, nowMillis: Long): Long? =
    if (dueMillis > nowMillis) nowMillis + REMINDER_PERMISSION_TEST_DELAY_MILLIS else null

object ReminderScheduler {
    const val CHANNEL_ID = "task_reminders"
    // Use a fresh channel for this audit build. Android keeps an existing
    // channel's lock-screen policy across app updates.
    const val ALARM_SERVICE_CHANNEL_ID = "alarm_service_fullscreen_test"
    private const val CHANNEL_NAME = "任务提醒"
    private const val ALARM_SERVICE_CHANNEL_NAME = "闹钟运行中"
    private const val PREFERENCES_NAME = "reminder_settings"
    private const val REMINDER_MODE_KEY = "reminder_mode"
    private const val STORED_ALARM_IDS_KEY = "stored_alarm_ids"
    private const val STORED_ALARM_PREFIX = "stored_alarm_"
    private const val PERMISSION_TEST_DURATION_MILLIS = 10 * 60 * 1000L
    const val PERMISSION_TEST_TASK_ID = -1

    enum class ReminderMode {
        NOTIFICATION,
        ALARM,
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "TodoCloud 任务到期提醒"
            },
        )
    }

    fun createAlarmServiceChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ALARM_SERVICE_CHANNEL_ID,
                ALARM_SERVICE_CHANNEL_NAME,
                // Full-screen intents require a high-importance channel. The
                // channel itself is silent because AlarmService plays the alarm.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "闹钟响铃期间的系统运行保障，不发送任务通知"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun loadReminderMode(context: Context): ReminderMode {
        val value = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(REMINDER_MODE_KEY, ReminderMode.NOTIFICATION.name)
        return runCatching { ReminderMode.valueOf(value.orEmpty()) }
            .getOrDefault(ReminderMode.NOTIFICATION)
    }

    fun saveReminderMode(context: Context, mode: ReminderMode) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(REMINDER_MODE_KEY, mode.name)
            .apply()
        ensurePermissionTestRunning(context)
    }

    /** Run a local-only reminder audit for ten minutes, without creating a cloud task. */
    fun ensurePermissionTestRunning(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = runCatching {
            JSONObject(prefs.getString(STORED_ALARM_PREFIX + PERMISSION_TEST_TASK_ID, null).orEmpty())
        }.getOrNull()
        val now = System.currentTimeMillis()
        val existingDueAt = stored?.optString("dueAt")
        val existingDueMillis = existingDueAt?.let {
            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        }
        val dueAt = if (existingDueMillis != null && existingDueMillis > now) {
            existingDueAt
        } else {
            OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(now + PERMISSION_TEST_DURATION_MILLIS),
                ZoneOffset.UTC,
            ).toString()
        }
        schedule(
            context,
            TaskItem(
                id = PERMISSION_TEST_TASK_ID,
                title = "权限测试提醒（每 10 秒）",
                description = "本机测试，不会创建云端任务；10 分钟后自动停止。",
                dueAt = dueAt,
                reminderOffsetMinutes = 0,
                completed = false,
                completedAt = null,
            ),
        )
    }

    fun schedule(context: Context, task: TaskItem) {
        cancelAlarmOnly(context, task.id)
        context.getSystemService(NotificationManager::class.java).cancel(task.id)
        val dueAt = task.dueAt ?: return forgetStoredTask(context, task.id)
        if (task.reminderOffsetMinutes == null) return forgetStoredTask(context, task.id)
        if (task.completed) return forgetStoredTask(context, task.id)

        val dueMillis = runCatching { OffsetDateTime.parse(dueAt).toInstant().toEpochMilli() }.getOrNull()
            ?: return forgetStoredTask(context, task.id)
        val now = System.currentTimeMillis()
        val triggerAt = permissionTestTriggerAtMillis(dueMillis, now)
            ?: return forgetStoredTask(context, task.id)
        val mode = loadReminderMode(context)
        val pendingIntent = pendingIntent(context, task, mode)
        // Keep the payload locally so the receiver can schedule the next test
        // reminder and reboot/time-change restoration can rebuild it.
        rememberTask(context, task, mode)
        scheduleAlarm(context, task.id, mode, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, taskId: Int) {
        cancelAlarmOnly(context, taskId)
        context.getSystemService(NotificationManager::class.java).cancel(taskId)
        forgetStoredTask(context, taskId)
    }

    /** Returns false when a canceled or expired reminder must not be shown. */
    fun scheduleNextPermissionTestReminder(context: Context, deliveredIntent: Intent): Boolean {
        val taskId = deliveredIntent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0)
        if (taskId == 0) return false
        val raw = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(STORED_ALARM_PREFIX + taskId, null) ?: return false
        val stored = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val dueAt = stored.optString("dueAt")
        val mode = if (stored.optString("mode") == ReminderMode.ALARM.name) {
            ReminderMode.ALARM
        } else {
            ReminderMode.NOTIFICATION
        }
        if (dueAt != deliveredIntent.getStringExtra(ReminderReceiver.EXTRA_DUE_AT) ||
            stored.optInt("offset", Int.MIN_VALUE) !=
            deliveredIntent.getIntExtra(ReminderReceiver.EXTRA_REMINDER_OFFSET, Int.MIN_VALUE) ||
            (mode == ReminderMode.ALARM) !=
            deliveredIntent.getBooleanExtra(ReminderReceiver.EXTRA_ALARM_MODE, false)
        ) return false
        val dueMillis = runCatching { OffsetDateTime.parse(dueAt).toInstant().toEpochMilli() }.getOrNull()
            ?: run {
                forgetStoredTask(context, taskId)
                return false
            }
        val triggerAt = permissionTestTriggerAtMillis(dueMillis, System.currentTimeMillis())
            ?: run {
                forgetStoredTask(context, taskId)
                return false
            }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleAlarm(context, taskId, mode, triggerAt, pendingIntent)
        return true
    }

    fun restoreScheduledTasks(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_ALARM_IDS_KEY, emptySet()).orEmpty().toList()
        ids.forEach { rawId ->
            val taskId = rawId.toIntOrNull() ?: return@forEach
            val json = runCatching { JSONObject(prefs.getString(STORED_ALARM_PREFIX + taskId, null).orEmpty()) }
                .getOrNull() ?: return@forEach
            val task = TaskItem(
                id = taskId,
                title = json.optString("title", "任务提醒"),
                description = json.optString("description").takeIf { it.isNotBlank() },
                dueAt = json.optString("dueAt").takeIf { it.isNotBlank() },
                reminderOffsetMinutes = json.optInt("offset", 0),
                completed = false,
                completedAt = null,
                steps = json.optJSONArray("steps")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val title = item.optString("title").trim()
                            if (title.isNotEmpty()) {
                                add(TaskStep(title, item.optBoolean("completed")))
                            }
                        }
                    }
                }.orEmpty(),
            )
            schedule(context, task)
        }
    }

    @Suppress("unused")
    fun restoreAlarmTasks(context: Context) = restoreScheduledTasks(context)

    fun forgetStoredTask(context: Context, taskId: Int) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_ALARM_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        if (ids.remove(taskId.toString())) {
            prefs.edit()
                .putStringSet(STORED_ALARM_IDS_KEY, ids)
                .remove(STORED_ALARM_PREFIX + taskId)
                .apply()
        }
    }

    private fun cancelAlarmOnly(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }

    private fun rememberTask(context: Context, task: TaskItem, mode: ReminderMode) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_ALARM_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids.add(task.id.toString())
        prefs.edit()
            .putStringSet(STORED_ALARM_IDS_KEY, ids)
            .putString(
                STORED_ALARM_PREFIX + task.id,
                JSONObject()
                    .put("title", task.title)
                    .put("description", task.description)
                    .put("dueAt", task.dueAt)
                    .put("offset", task.reminderOffsetMinutes)
                    .put("mode", mode.name)
                    .put("steps", JSONArray().apply {
                        task.steps.forEach { step ->
                            put(JSONObject().put("title", step.title).put("completed", step.completed))
                        }
                    })
                    .toString(),
            )
            .apply()
    }

    private fun scheduleAlarm(
        context: Context,
        taskId: Int,
        mode: ReminderMode,
        triggerAt: Long,
        pendingIntent: PendingIntent,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        try {
            if (mode == ReminderMode.ALARM) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    taskId,
                    MainActivity.intent(context, taskId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    pendingIntent,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun pendingIntent(
        context: Context,
        task: TaskItem,
        mode: ReminderMode,
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, task.title)
            putExtra(ReminderReceiver.EXTRA_DESCRIPTION, task.description)
            putExtra(ReminderReceiver.EXTRA_STEPS, JSONArray().apply {
                task.steps.forEach { step ->
                    put(JSONObject().put("title", step.title).put("completed", step.completed))
                }
            }.toString())
            putExtra(ReminderReceiver.EXTRA_DUE_AT, task.dueAt)
            putExtra(ReminderReceiver.EXTRA_REMINDER_OFFSET, task.reminderOffsetMinutes)
            putExtra(ReminderReceiver.EXTRA_ALARM_MODE, mode == ReminderMode.ALARM)
        }
        return PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

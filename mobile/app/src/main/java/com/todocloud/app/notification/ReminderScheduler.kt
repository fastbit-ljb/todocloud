package com.todocloud.app.notification

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Process
import com.todocloud.app.MainActivity
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** Computes a one-shot reminder from the task's due time and selected offset. */
internal fun reminderTriggerAtMillis(
    dueMillis: Long,
    offsetMinutes: Int,
    nowMillis: Long,
    mode: ReminderScheduler.ReminderMode,
): Long? {
    if (dueMillis <= nowMillis) return null
    val requestedTrigger = dueMillis - offsetMinutes * 60_000L
    if (mode == ReminderScheduler.ReminderMode.NOTIFICATION && requestedTrigger <= nowMillis) return null
    return if (requestedTrigger <= nowMillis) nowMillis + 1_000L else requestedTrigger
}

internal const val SNOOZE_DELAY_MILLIS = 5 * 60_000L

internal fun snoozeTriggerAtMillis(nowMillis: Long): Long = nowMillis + SNOOZE_DELAY_MILLIS

internal fun restoredSnoozeTriggerAtMillis(storedTriggerAtMillis: Long, nowMillis: Long): Long =
    maxOf(storedTriggerAtMillis, nowMillis + 1_000L)

internal fun shouldKeepSnoozedAlarm(
    completed: Boolean,
    hasReminder: Boolean,
    alarmMode: Boolean,
    taskDueAt: String?,
    taskOffsetMinutes: Int?,
    snoozedDueAt: String?,
    snoozedOffsetMinutes: Int,
): Boolean = !completed && hasReminder && alarmMode &&
    taskDueAt == snoozedDueAt && taskOffsetMinutes == snoozedOffsetMinutes

internal fun alarmSoundCandidateStrings(
    storedUri: String?,
    defaultAlarmUri: String?,
    defaultNotificationUri: String?,
): List<String> = listOf(storedUri, defaultAlarmUri, defaultNotificationUri)
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .distinct()

private data class SnoozedAlarm(
    val taskId: Int,
    val title: String,
    val description: String?,
    val steps: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int,
    val triggerAtMillis: Long,
)

object ReminderScheduler {
    const val CHANNEL_ID = "task_reminders"
    // A new channel migrates the old lock-screen-hidden policy. Android keeps
    // channel settings across app updates, so reusing "alarm_service" is unsafe.
    const val ALARM_SERVICE_CHANNEL_ID = "alarm_service_v2"
    private const val CHANNEL_NAME = "任务提醒"
    private const val ALARM_SERVICE_CHANNEL_NAME = "闹钟运行中"
    private const val PREFERENCES_NAME = "reminder_settings"
    private const val REMINDER_MODE_KEY = "reminder_mode"
    private const val ALARM_SOUND_URI_KEY = "alarm_sound_uri"
    private const val ALARM_SOUND_TITLE_KEY = "alarm_sound_title"
    private const val STORED_ALARM_IDS_KEY = "stored_alarm_ids"
    private const val STORED_ALARM_PREFIX = "stored_alarm_"
    private const val DISMISSED_ALARM_PREFIX = "dismissed_alarm_"
    private const val STORED_SNOOZE_IDS_KEY = "stored_snooze_ids"
    private const val STORED_SNOOZE_PREFIX = "stored_snooze_"
    internal const val ACTION_SNOOZED_ALARM = "com.todocloud.app.notification.SNOOZED_ALARM"
    private const val OPSTR_SCHEDULE_EXACT_ALARM_COMPAT = "android:schedule_exact_alarm"
    private const val LEGACY_PERMISSION_TEST_TASK_ID = -1

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
        if (mode == ReminderMode.ALARM) createAlarmServiceChannel(context)
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(REMINDER_MODE_KEY, mode.name)
            .apply()
    }

    fun selectedAlarmSoundUri(context: Context): Uri? {
        val stored = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(ALARM_SOUND_URI_KEY, null)
            ?.takeIf { it.isNotBlank() }
        return stored?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    fun saveAlarmSoundUri(context: Context, uri: Uri?, displayName: String? = null) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val previousUri = prefs.getString(ALARM_SOUND_URI_KEY, null)?.let(Uri::parse)
        prefs.edit()
            .apply {
                if (uri == null) remove(ALARM_SOUND_URI_KEY) else putString(ALARM_SOUND_URI_KEY, uri.toString())
                if (displayName.isNullOrBlank()) remove(ALARM_SOUND_TITLE_KEY)
                else putString(ALARM_SOUND_TITLE_KEY, displayName)
            }
            .apply()
        if (previousUri != null && previousUri != uri) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(previousUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun alarmSoundTitle(context: Context): String {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(ALARM_SOUND_TITLE_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val uri = selectedAlarmSoundUri(context) ?: return "系统默认闹钟声"
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.let { ringtone ->
                try {
                    ringtone.getTitle(context)
                } finally {
                    ringtone.stop()
                }
            }
        }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "系统默认闹钟声"
    }

    internal fun alarmSoundUris(context: Context): List<Uri> {
        val stored = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(ALARM_SOUND_URI_KEY, null)
        return alarmSoundCandidateStrings(
            storedUri = stored,
            defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString(),
            defaultNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString(),
        ).map(Uri::parse)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) return false

        // Some OEM and custom ROMs report true above while the underlying
        // AppOp is still denied. setAlarmClock then throws and an inexact
        // fallback loses the foreground-service exemption.
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            OPSTR_SCHEDULE_EXACT_ALARM_COMPAT,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }

    fun schedule(context: Context, task: TaskItem) {
        if (task.id <= 0) return cancel(context, task.id)
        val mode = loadReminderMode(context)
        loadSnoozedAlarm(context, task.id)?.let { snoozed ->
            val keepSnooze = shouldKeepSnoozedAlarm(
                completed = task.completed,
                hasReminder = task.dueAt != null && task.reminderOffsetMinutes != null,
                alarmMode = mode == ReminderMode.ALARM,
                taskDueAt = task.dueAt,
                taskOffsetMinutes = task.reminderOffsetMinutes,
                snoozedDueAt = snoozed.dueAt,
                snoozedOffsetMinutes = snoozed.reminderOffsetMinutes,
            )
            if (keepSnooze) {
                cancelAlarmOnly(context, task.id)
                context.getSystemService(NotificationManager::class.java).cancel(task.id)
                val refreshed = snoozed.copy(
                    title = task.title,
                    description = task.description,
                    steps = stepsJson(task.steps),
                )
                if (scheduleSnoozedAlarm(context, refreshed)) rememberSnoozedAlarm(context, refreshed)
                return
            }
            cancelSnoozeOnly(context, task.id)
            forgetSnoozedAlarm(context, task.id)
        }
        cancelAlarmOnly(context, task.id)
        context.getSystemService(NotificationManager::class.java).cancel(task.id)
        val dueAt = task.dueAt ?: return forgetStoredTask(context, task.id)
        val offset = task.reminderOffsetMinutes ?: return forgetStoredTask(context, task.id)
        if (task.completed) return forgetStoredTask(context, task.id)

        val dueMillis = runCatching { OffsetDateTime.parse(dueAt).toInstant().toEpochMilli() }.getOrNull()
            ?: return forgetStoredTask(context, task.id)
        val now = System.currentTimeMillis()
        if (mode != ReminderMode.ALARM) {
            forgetStoredTask(context, task.id)
        } else if (isAlarmDismissed(context, task)) {
            // A one-shot alarm must not ring again just because the task list
            // is reloaded after the user stops it.
            return
        } else {
            clearDismissedAlarm(context, task.id)
        }

        val triggerAt = reminderTriggerAtMillis(dueMillis, offset, now, mode)
            ?: return forgetStoredTask(context, task.id)
        val pendingIntent = pendingIntent(context, task, mode)
        rememberTask(context, task, mode)
        scheduleAlarm(context, task.id, mode, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, taskId: Int) {
        cancelAlarmOnly(context, taskId)
        cancelSnoozeOnly(context, taskId)
        context.getSystemService(NotificationManager::class.java).cancel(taskId)
        forgetStoredTask(context, taskId)
        forgetSnoozedAlarm(context, taskId)
    }

    fun restoreScheduledTasks(context: Context) {
        // Upgrade from permission-audit APKs without leaving their local-only
        // repeating reminder or notification behind. Real user tasks are untouched.
        cancel(context, LEGACY_PERMISSION_TEST_TASK_ID)
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
        restoreSnoozedAlarms(context)
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

    fun markAlarmDismissed(context: Context, taskId: Int, dueAt: String?, offsetMinutes: Int) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = runCatching {
            JSONObject(prefs.getString(STORED_ALARM_PREFIX + taskId, null).orEmpty())
        }.getOrNull()
        val effectiveDueAt = dueAt ?: stored?.optString("dueAt")?.takeIf { it.isNotBlank() }
        val effectiveOffset = if (dueAt == null) {
            stored?.optInt("offset", offsetMinutes) ?: offsetMinutes
        } else {
            offsetMinutes
        }
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                DISMISSED_ALARM_PREFIX + taskId,
                JSONObject()
                    .put("dueAt", effectiveDueAt)
                    .put("offset", effectiveOffset)
                    .toString(),
            )
            .apply()
    }

    fun snoozeAlarm(
        context: Context,
        taskId: Int,
        title: String,
        description: String?,
        steps: String?,
        dueAt: String?,
        reminderOffsetMinutes: Int,
    ): Boolean {
        if (taskId <= 0 || !canScheduleExactAlarms(context)) return false
        val snoozed = SnoozedAlarm(
            taskId = taskId,
            title = title,
            description = description,
            steps = steps,
            dueAt = dueAt,
            reminderOffsetMinutes = reminderOffsetMinutes,
            triggerAtMillis = snoozeTriggerAtMillis(System.currentTimeMillis()),
        )
        if (!scheduleSnoozedAlarm(context, snoozed)) return false
        rememberSnoozedAlarm(context, snoozed)
        return true
    }

    fun forgetSnoozedAlarm(context: Context, taskId: Int) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_SNOOZE_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids.remove(taskId.toString())
        prefs.edit()
            .putStringSet(STORED_SNOOZE_IDS_KEY, ids)
            .remove(STORED_SNOOZE_PREFIX + taskId)
            .apply()
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

    private fun cancelSnoozeOnly(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED_ALARM),
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

    private fun restoreSnoozedAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_SNOOZE_IDS_KEY, emptySet()).orEmpty().toList()
        val now = System.currentTimeMillis()
        ids.forEach { rawId ->
            val taskId = rawId.toIntOrNull() ?: return@forEach
            val stored = loadSnoozedAlarm(context, taskId) ?: return@forEach
            val restored = stored.copy(
                triggerAtMillis = restoredSnoozeTriggerAtMillis(stored.triggerAtMillis, now),
            )
            if (scheduleSnoozedAlarm(context, restored)) rememberSnoozedAlarm(context, restored)
        }
    }

    private fun loadSnoozedAlarm(context: Context, taskId: Int): SnoozedAlarm? {
        val raw = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(STORED_SNOOZE_PREFIX + taskId, null)
            ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val triggerAt = json.optLong("triggerAt", 0L)
        if (triggerAt <= 0L) return null
        return SnoozedAlarm(
            taskId = taskId,
            title = json.optString("title", "任务提醒"),
            description = json.optString("description").takeIf { it.isNotBlank() },
            steps = json.optString("steps").takeIf { it.isNotBlank() },
            dueAt = json.optString("dueAt").takeIf { it.isNotBlank() },
            reminderOffsetMinutes = json.optInt("offset", 0),
            triggerAtMillis = triggerAt,
        )
    }

    private fun rememberSnoozedAlarm(context: Context, snoozed: SnoozedAlarm) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(STORED_SNOOZE_IDS_KEY, emptySet()).orEmpty().toMutableSet()
        ids.add(snoozed.taskId.toString())
        val json = JSONObject()
            .put("title", snoozed.title)
            .put("offset", snoozed.reminderOffsetMinutes)
            .put("triggerAt", snoozed.triggerAtMillis)
        snoozed.description?.let { json.put("description", it) }
        snoozed.steps?.let { json.put("steps", it) }
        snoozed.dueAt?.let { json.put("dueAt", it) }
        prefs.edit()
            .putStringSet(STORED_SNOOZE_IDS_KEY, ids)
            .putString(STORED_SNOOZE_PREFIX + snoozed.taskId, json.toString())
            .apply()
    }

    private fun scheduleSnoozedAlarm(context: Context, snoozed: SnoozedAlarm): Boolean {
        val deliveryIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZED_ALARM
            putExtra(ReminderReceiver.EXTRA_TASK_ID, snoozed.taskId)
            putExtra(ReminderReceiver.EXTRA_TITLE, snoozed.title)
            putExtra(ReminderReceiver.EXTRA_DESCRIPTION, snoozed.description)
            putExtra(ReminderReceiver.EXTRA_STEPS, snoozed.steps)
            putExtra(ReminderReceiver.EXTRA_DUE_AT, snoozed.dueAt)
            putExtra(ReminderReceiver.EXTRA_REMINDER_OFFSET, snoozed.reminderOffsetMinutes)
            putExtra(ReminderReceiver.EXTRA_ALARM_MODE, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozed.taskId,
            deliveryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val showIntent = PendingIntent.getActivity(
            context,
            snoozed.taskId,
            MainActivity.intent(context, snoozed.taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return runCatching {
            context.getSystemService(AlarmManager::class.java).setAlarmClock(
                AlarmManager.AlarmClockInfo(snoozed.triggerAtMillis, showIntent),
                pendingIntent,
            )
        }.isSuccess
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
            // Alarm mode must remain exact: exact alarms are exempt from the
            // background foreground-service start restriction. Falling back
            // to an inexact alarm can display briefly and then crash when the
            // system rejects AlarmService.startForeground().
            if (mode != ReminderMode.ALARM) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }

    private fun isAlarmDismissed(context: Context, task: TaskItem): Boolean {
        val raw = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(DISMISSED_ALARM_PREFIX + task.id, null)
            ?: return false
        val dismissed = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        return dismissed.optString("dueAt") == task.dueAt &&
            dismissed.optInt("offset", Int.MIN_VALUE) == task.reminderOffsetMinutes
    }

    private fun clearDismissedAlarm(context: Context, taskId: Int) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(DISMISSED_ALARM_PREFIX + taskId)
            .apply()
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

    private fun stepsJson(steps: List<TaskStep>): String = JSONArray().apply {
        steps.forEach { step ->
            put(JSONObject().put("title", step.title).put("completed", step.completed))
        }
    }.toString()
}

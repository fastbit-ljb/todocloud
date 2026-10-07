package com.todocloud.app.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.todocloud.app.data.TaskItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

enum class MascotState(
    internal val launcherAlias: String,
) {
    DAILY("Daily"),
    IN_PROGRESS("Progress"),
    DUE_SOON("DueSoon"),
    OVERDUE("Overdue"),
    COMPLETE("Complete"),
    NO_TASKS("NoTasks"),
    HALF_FROZEN("HalfFrozen"),
    FROZEN("Frozen"),
}

data class MascotTransition(
    val from: MascotState,
    val to: MascotState,
)

object MascotStateManager {
    private const val PREFERENCES_NAME = "mascot_state"
    private const val KEY_LAST_ACTIVITY_AT = "last_meaningful_activity_at"
    private const val KEY_CURRENT_STATE = "current_state"
    private const val KEY_PENDING_STATE = "pending_state"
    private const val KEY_ALIASES_CONFIGURED = "aliases_configured"
    private const val KEY_TRANSITION_FROM_STATE = "transition_from_state"
    private const val KEY_TRANSITION_TO_STATE = "transition_to_state"
    private const val HALF_FROZEN_HOURS = 24L
    private const val FROZEN_HOURS = 72L

    @Volatile
    private var appInForeground = false

    fun onAppForegrounded() {
        appInForeground = true
    }

    fun onAppBackgrounded(context: Context) {
        appInForeground = false
        applyPendingLauncherState(context)
    }

    fun recordMeaningfulActivity(context: Context, at: Instant = Instant.now()) {
        preferences(context)
            .edit()
            .putLong(KEY_LAST_ACTIVITY_AT, at.toEpochMilli())
            .apply()
    }

    fun update(
        context: Context,
        tasks: List<TaskItem>,
        now: Instant = Instant.now(),
    ): MascotState {
        val prefs = preferences(context)
        val lastActivity = if (prefs.contains(KEY_LAST_ACTIVITY_AT)) {
            Instant.ofEpochMilli(prefs.getLong(KEY_LAST_ACTIVITY_AT, now.toEpochMilli()))
        } else {
            now.also { recordMeaningfulActivity(context, it) }
        }
        val state = resolve(tasks, lastActivity, now, ZoneId.systemDefault())
        queueLauncherState(context, state)
        if (!appInForeground) applyPendingLauncherState(context)
        return state
    }

    fun showReminderState(context: Context) {
        queueLauncherState(context, MascotState.DUE_SOON)
        if (!appInForeground) applyPendingLauncherState(context)
    }

    fun reset(context: Context) {
        val now = Instant.now()
        preferences(context)
            .edit()
            .putLong(KEY_LAST_ACTIVITY_AT, now.toEpochMilli())
            .putString(KEY_PENDING_STATE, MascotState.DAILY.name)
            .apply()
        if (!appInForeground) applyPendingLauncherState(context)
    }

    fun consumePendingTransition(context: Context): MascotTransition? {
        val prefs = preferences(context)
        val from = prefs.getString(KEY_TRANSITION_FROM_STATE, null)
            ?.let { stored -> MascotState.entries.firstOrNull { it.name == stored } }
        val to = prefs.getString(KEY_TRANSITION_TO_STATE, null)
            ?.let { stored -> MascotState.entries.firstOrNull { it.name == stored } }
        prefs.edit()
            .remove(KEY_TRANSITION_FROM_STATE)
            .remove(KEY_TRANSITION_TO_STATE)
            .apply()
        return if (from != null && to != null && from != to) {
            MascotTransition(from = from, to = to)
        } else {
            null
        }
    }

    internal fun resolve(
        tasks: List<TaskItem>,
        lastActivity: Instant,
        now: Instant,
        zoneId: ZoneId,
    ): MascotState {
        val pendingTasks = tasks.filterNot { it.completed }
        val datedPendingTasks = pendingTasks.mapNotNull { task ->
            parseInstant(task.dueAt, zoneId)?.let { dueAt -> task to dueAt }
        }

        if (datedPendingTasks.any { (_, dueAt) -> !dueAt.isAfter(now) }) {
            return MascotState.OVERDUE
        }

        val hasDueSoonTask = datedPendingTasks.any { (task, dueAt) ->
            val offsetMinutes = task.reminderOffsetMinutes ?: return@any false
            val reminderAt = dueAt.minusSeconds(offsetMinutes.toLong() * 60L)
            !now.isBefore(reminderAt) && now.isBefore(dueAt)
        }
        if (hasDueSoonTask) return MascotState.DUE_SOON

        val today = now.atZone(zoneId).toLocalDate()
        val completedSomethingToday = tasks.any { task ->
            task.completed && parseInstant(task.completedAt, zoneId)
                ?.atZone(zoneId)
                ?.toLocalDate() == today
        }
        val hasCurrentUnfinishedTask = pendingTasks.any { task ->
            val dueDate = parseInstant(task.dueAt, zoneId)?.atZone(zoneId)?.toLocalDate()
            dueDate == null || !dueDate.isAfter(today)
        }
        if (completedSomethingToday && !hasCurrentUnfinishedTask) {
            return MascotState.COMPLETE
        }

        if (pendingTasks.any { task -> task.steps.any { it.completed } }) {
            return MascotState.IN_PROGRESS
        }

        val inactiveHours = Duration.between(lastActivity, now).toHours().coerceAtLeast(0L)
        if (inactiveHours >= FROZEN_HOURS) return MascotState.FROZEN
        if (inactiveHours >= HALF_FROZEN_HOURS) return MascotState.HALF_FROZEN
        if (pendingTasks.isEmpty()) return MascotState.NO_TASKS
        return MascotState.DAILY
    }

    private fun queueLauncherState(context: Context, state: MascotState) {
        preferences(context).edit().putString(KEY_PENDING_STATE, state.name).apply()
    }

    private fun applyPendingLauncherState(context: Context) {
        val prefs = preferences(context)
        val state = prefs.getString(KEY_PENDING_STATE, null)
            ?.let { stored -> MascotState.entries.firstOrNull { it.name == stored } }
            ?: return
        val previous = prefs.getString(KEY_CURRENT_STATE, null)
        val configured = prefs.getBoolean(KEY_ALIASES_CONFIGURED, false)
        if (configured && previous == state.name) {
            prefs.edit().remove(KEY_PENDING_STATE).apply()
            return
        }

        val packageManager = context.packageManager
        fun component(candidate: MascotState) = ComponentName(
            context.packageName,
            "${context.packageName}.launcher.${candidate.launcherAlias}",
        )

        val applied = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val settings = MascotState.entries.map { candidate ->
                    PackageManager.ComponentEnabledSetting(
                        component(candidate),
                        if (candidate == state) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        },
                        PackageManager.DONT_KILL_APP,
                    )
                }
                packageManager.setComponentEnabledSettings(settings)
            } else {
                // Keep at least one launcher entry enabled throughout the
                // hand-off so older launchers never observe a missing icon.
                packageManager.setComponentEnabledSetting(
                    component(state),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                MascotState.entries.filterNot { it == state }.forEach { candidate ->
                    packageManager.setComponentEnabledSetting(
                        component(candidate),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }.isSuccess

        if (!applied) return
        val editor = prefs.edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .remove(KEY_PENDING_STATE)
            .putBoolean(KEY_ALIASES_CONFIGURED, true)
        if (configured && previous != null && previous != state.name) {
            editor
                .putString(KEY_TRANSITION_FROM_STATE, previous)
                .putString(KEY_TRANSITION_TO_STATE, state.name)
        }
        editor.apply()
    }

    private fun parseInstant(value: String?, zoneId: ZoneId): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

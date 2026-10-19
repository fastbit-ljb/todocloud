package com.todocloud.app.branding

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.todocloud.app.data.TaskItem
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class MascotState(
    internal val launcherAlias: String,
    val label: String,
) {
    DAILY("Daily", "日常"),
    IN_PROGRESS("Progress", "进行中"),
    DUE_SOON("DueSoon", "即将到期"),
    OVERDUE("Overdue", "已逾期"),
    COMPLETE("Complete", "已完成"),
    NO_TASKS("NoTasks", "暂无任务"),
    HALF_FROZEN("HalfFrozen", "半冻结"),
    FROZEN("Frozen", "完全冻结"),
}

object MascotStateManager {
    private const val PREFERENCES_NAME = "mascot_state"
    private const val KEY_LAST_ACTIVITY_AT = "last_meaningful_activity_at"
    private const val KEY_CURRENT_STATE = "current_state"
    private const val KEY_PENDING_STATE = "pending_state"
    private const val KEY_ALIASES_CONFIGURED = "aliases_configured"
    // The set picked in Settings → 吉祥物, and the set whose alias is enabled right now.
    private const val KEY_SELECTED_SET = "selected_set"
    private const val KEY_CURRENT_SET = "current_set"
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

    /** The set chosen in Settings → 吉祥物, or [MascotSets.DEFAULT] until one is chosen. */
    fun selectedSet(context: Context): MascotSet = selectedSet(preferences(context))

    // Read through getAll(): getString would throw if a damaged file held a non-string here.
    private fun selectedSet(prefs: SharedPreferences): MascotSet =
        MascotSets.fromStored(prefs.all[KEY_SELECTED_SET])

    /**
     * Saves [set] and queues the current state again so the launcher switches to the new
     * set's icon. Like every icon change it is applied when the app goes to the background.
     */
    fun selectSet(context: Context, set: MascotSet) {
        val prefs = preferences(context)
        prefs.edit()
            .putString(KEY_SELECTED_SET, set.id)
            .putString(KEY_PENDING_STATE, shownState(prefs).name)
            .apply()
        if (!appInForeground) applyPendingLauncherState(context)
    }

    /** The state the launcher icon shows, or will show once the app is in the background. */
    private fun shownState(prefs: SharedPreferences): MascotState {
        val stored = prefs.all
        return shownStateFrom(stored[KEY_PENDING_STATE], stored[KEY_CURRENT_STATE])
    }

    /**
     * The state the launcher icon switches to when the user leaves the app, shown by the
     * account avatar and the 吉祥物 page. Once [tasksLoaded], it is [resolve] on [tasks] right
     * now -- the same call, on the same inputs, that [update] makes when the app goes to the
     * background -- so it follows every task change at once. Before that it is what the
     * launcher shows.
     */
    fun displayState(
        context: Context,
        tasks: List<TaskItem>,
        tasksLoaded: Boolean,
        now: Instant = Instant.now(),
    ): MascotState {
        val stored = preferences(context).all
        return displayStateFrom(
            tasks = tasks,
            tasksLoaded = tasksLoaded,
            storedLastActivity = stored[KEY_LAST_ACTIVITY_AT],
            storedPending = stored[KEY_PENDING_STATE],
            storedCurrent = stored[KEY_CURRENT_STATE],
            now = now,
            zoneId = ZoneId.systemDefault(),
        )
    }

    /** [displayState] on the raw stored values; anything unreadable gives [MascotState.DAILY]. */
    internal fun displayStateFrom(
        tasks: List<TaskItem>,
        tasksLoaded: Boolean,
        storedLastActivity: Any?,
        storedPending: Any?,
        storedCurrent: Any?,
        now: Instant,
        zoneId: ZoneId,
    ): MascotState {
        if (!tasksLoaded) return shownStateFrom(storedPending, storedCurrent)
        val lastActivity = when (storedLastActivity) {
            // Nothing recorded yet: update() records "now" and resolves with that.
            null -> now
            is Long -> Instant.ofEpochMilli(storedLastActivity)
            else -> return MascotState.DAILY
        }
        return runCatching { resolve(tasks, lastActivity, now, zoneId) }.getOrDefault(MascotState.DAILY)
    }

    /**
     * The pending state, else the current one. A value that cannot be read (missing, unknown,
     * or not a string in a damaged preferences file) is skipped; when neither can be read it
     * is [MascotState.DAILY].
     */
    internal fun shownStateFrom(pending: Any?, current: Any?): MascotState =
        storedState(pending) ?: storedState(current) ?: MascotState.DAILY

    private fun storedState(value: Any?): MascotState? =
        (value as? String)?.let { name -> MascotState.entries.firstOrNull { it.name == name } }

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

        val today = now.atZone(zoneId).toLocalDate()
        val hasDueSoonTask = datedPendingTasks.any { (task, dueAt) ->
            if (dueAt.atZone(zoneId).toLocalDate() == today) return@any true
            val offsetMinutes = task.reminderOffsetMinutes ?: return@any false
            val reminderAt = dueAt.minusSeconds(offsetMinutes.toLong() * 60L)
            !now.isBefore(reminderAt) && now.isBefore(dueAt)
        }
        if (hasDueSoonTask) return MascotState.DUE_SOON

        // A repeat's next occurrence is created immediately after completion.
        // Future days/weeks/months must not undo the result of the current one.
        // Explicit starts, step progress and reminder/overdue states still count.
        val currentPendingTasks = pendingTasks.filterNot { task ->
            val period = recurrencePeriod(today, task.recurrence) ?: return@filterNot false
            val dueDate = parseInstant(task.dueAt, zoneId)?.atZone(zoneId)?.toLocalDate()
                ?: return@filterNot false
            if (dueDate.isBefore(period.second) || task.steps.any { it.completed }) return@filterNot false
            if (!task.startAt.isNullOrBlank()) {
                val start = parseInstant(task.startAt, zoneId) ?: return@filterNot false
                if (!start.isAfter(now)) return@filterNot false
            }
            true
        }
        if (currentPendingTasks.isEmpty()) {
            val hasCurrentCompletion = tasks.any { task ->
                if (!task.completed) return@any false
                val finishedDate = parseInstant(task.completedAt, zoneId)?.atZone(zoneId)?.toLocalDate()
                val dueDate = parseInstant(task.dueAt, zoneId)?.atZone(zoneId)?.toLocalDate()
                val period = recurrencePeriod(today, task.completedRecurrence ?: task.recurrence)
                if (period != null && finishedDate != null) {
                    !finishedDate.isBefore(period.first) && finishedDate.isBefore(period.second)
                } else {
                    // Old snapshots lack the completed repeat rule. Never guess a
                    // week/month from unrelated historical one-off completions.
                    finishedDate == today || dueDate == today
                }
            }
            return if (hasCurrentCompletion) {
                MascotState.COMPLETE
            } else {
                MascotState.NO_TASKS
            }
        }

        val hasPartialProgress = tasks.any { it.completed } ||
            currentPendingTasks.any { task -> task.steps.any { it.completed } }
        if (hasPartialProgress) {
            return MascotState.IN_PROGRESS
        }

        val inactiveHours = Duration.between(lastActivity, now).toHours().coerceAtLeast(0L)
        if (inactiveHours >= FROZEN_HOURS) return MascotState.FROZEN
        if (inactiveHours >= HALF_FROZEN_HOURS) return MascotState.HALF_FROZEN
        return MascotState.DAILY
    }

    /** Local calendar periods, with an exclusive end; weeks begin on Monday. */
    private fun recurrencePeriod(today: LocalDate, rule: String?): Pair<LocalDate, LocalDate>? = when (rule) {
        "daily", "weekdays" -> today to today.plusDays(1)
        "weekly" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
        "monthly" -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
        else -> null
    }

    private fun queueLauncherState(context: Context, state: MascotState) {
        preferences(context).edit().putString(KEY_PENDING_STATE, state.name).apply()
    }

    private fun applyPendingLauncherState(context: Context) {
        val prefs = preferences(context)
        val state = prefs.getString(KEY_PENDING_STATE, null)
            ?.let { stored -> MascotState.entries.firstOrNull { it.name == stored } }
            ?: return
        val set = selectedSet(prefs)
        if (
            alreadyShown(
                configured = prefs.getBoolean(KEY_ALIASES_CONFIGURED, false),
                currentState = prefs.getString(KEY_CURRENT_STATE, null),
                currentSetId = prefs.getString(KEY_CURRENT_SET, null),
                set = set,
                state = state,
            )
        ) {
            prefs.edit().remove(KEY_PENDING_STATE).apply()
            return
        }

        val packageManager = context.packageManager
        val applied = runCatching {
            // Only aliases whose setting differs are touched, so a switch usually changes
            // two of them rather than every alias of every set.
            val changes = launcherAliasPlan(context.packageName, set, state)
                .map { (alias, enabled) ->
                    ComponentName(context.packageName, alias) to if (enabled) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                }
                .filter { (component, setting) ->
                    packageManager.getComponentEnabledSetting(component) != setting
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (changes.isNotEmpty()) {
                    packageManager.setComponentEnabledSettings(
                        changes.map { (component, setting) ->
                            PackageManager.ComponentEnabledSetting(
                                component,
                                setting,
                                PackageManager.DONT_KILL_APP,
                            )
                        },
                    )
                }
            } else {
                // Keep at least one launcher entry enabled throughout the
                // hand-off so older launchers never observe a missing icon.
                changes
                    .sortedByDescending { (_, setting) ->
                        setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    }
                    .forEach { (component, setting) ->
                        packageManager.setComponentEnabledSetting(
                            component,
                            setting,
                            PackageManager.DONT_KILL_APP,
                        )
                    }
            }
        }.isSuccess

        if (!applied) return
        prefs.edit()
            .putString(KEY_CURRENT_STATE, state.name)
            .putString(KEY_CURRENT_SET, set.id)
            .remove(KEY_PENDING_STATE)
            .putBoolean(KEY_ALIASES_CONFIGURED, true)
            .apply()
    }

    /**
     * Every launcher alias of every set, each paired with whether it should be enabled:
     * only [set]'s alias for [state] is.
     */
    internal fun launcherAliasPlan(
        packageName: String,
        set: MascotSet,
        state: MascotState,
    ): List<Pair<String, Boolean>> = MascotSets.all.flatMap { candidateSet ->
        MascotState.entries.map { candidateState ->
            candidateSet.launcherAlias(packageName, candidateState) to
                (candidateSet.id == set.id && candidateState == state)
        }
    }

    /**
     * Whether the launcher already shows [state] in [set]. Versions before mascot sets stored
     * no set id; the only aliases they had are the classic set's.
     */
    internal fun alreadyShown(
        configured: Boolean,
        currentState: String?,
        currentSetId: String?,
        set: MascotSet,
        state: MascotState,
    ): Boolean = configured &&
        currentState == state.name &&
        (currentSetId ?: MascotSets.CLASSIC.id) == set.id

    private fun parseInstant(value: String?, zoneId: ZoneId): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zoneId).toInstant() }.getOrNull()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

package com.todocloud.app.ui

import com.todocloud.app.data.TaskDraft
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.LocalDateTime
import java.time.ZoneId

internal data class ReminderPreset(val minutes: Int?, val label: String)

/** Quick choices in the task editor; anything else goes through the custom picker. */
internal val REMINDER_PRESETS = listOf(
    ReminderPreset(null, "不提醒"),
    ReminderPreset(10, "10 分钟"),
    ReminderPreset(30, "30 分钟"),
    ReminderPreset(60, "1 小时"),
    ReminderPreset(24 * 60, "1 天"),
)

/** Reminder used for a brand-new manual task (same as the old "10" default). */
internal const val NEW_TASK_DEFAULT_REMINDER_MINUTES = 10

internal const val TASK_TITLE_MAX = 200
internal const val TASK_STEPS_MAX = 30

/** One editable step row; [completed] is kept so editing never resets progress. */
internal data class StepInput(val title: String, val completed: Boolean = false)

/** Why the editor cannot be saved yet, or null when it can. */
internal fun taskEditorError(
    title: String,
    startAt: LocalDateTime?,
    dueAt: LocalDateTime?,
): String? = when {
    title.isBlank() -> "请填写任务内容"
    startAt != null && dueAt != null && startAt.isAfter(dueAt) -> "开始时间不能晚于截止时间"
    else -> null
}

/**
 * Turns editor state into the payload sent to the server, or null when the input is invalid.
 * A reminder only makes sense with a due time, so it is dropped when there is none.
 */
internal fun buildTaskDraft(
    title: String,
    description: String,
    startAt: LocalDateTime?,
    dueAt: LocalDateTime?,
    durationMinutes: Int?,
    reminderOffsetMinutes: Int?,
    steps: List<StepInput>,
    zone: ZoneId = ZoneId.systemDefault(),
): TaskDraft? {
    if (taskEditorError(title, startAt, dueAt) != null) return null
    fun LocalDateTime.toInstantText() = atZone(zone).toInstant().toString()
    return TaskDraft(
        title = title.trim().take(TASK_TITLE_MAX),
        description = description.trim().ifBlank { null },
        startAt = startAt?.toInstantText(),
        dueAt = dueAt?.toInstantText(),
        durationMinutes = durationMinutes,
        reminderOffsetMinutes = if (dueAt == null) null else reminderOffsetMinutes,
        steps = steps
            .mapNotNull { step ->
                step.title.trim().takeIf(String::isNotEmpty)?.let { TaskStep(it.take(500), step.completed) }
            }
            .take(TASK_STEPS_MAX),
    )
}

/** Text for a reminder offset in places that list a task (calendar, editor). */
internal fun reminderLabel(offsetMinutes: Int?): String = when (offsetMinutes) {
    null -> "不提醒"
    0 -> "到点提醒"
    else -> formatReminderOffset(offsetMinutes)
}

/** Shown when a request fails without an HTTP answer (offline, timeout, DNS). */
internal const val NETWORK_ERROR_MESSAGE = "网络连接失败，请检查网络后下拉刷新重试"

/** The task list as shown while some deletions are still waiting for their undo window. */
internal fun withoutPendingDeletes(tasks: List<TaskItem>, pendingIds: Set<Int>): List<TaskItem> =
    if (pendingIds.isEmpty()) tasks else tasks.filterNot { it.id in pendingIds }

/** Puts an undone deletion back in its sorted place, without duplicating it. */
internal fun restoreTask(tasks: List<TaskItem>, task: TaskItem): List<TaskItem> =
    sortTasks(tasks.filterNot { it.id == task.id } + task)

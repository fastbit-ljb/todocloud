package com.todocloud.app.ui

import com.todocloud.app.data.PlanItem
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Incomplete tasks whose due time has already passed, earliest first. */
internal fun overdueTasks(tasks: List<TaskItem>, now: Instant = Instant.now()): List<TaskItem> =
    tasks.filter { task ->
        !task.completed && task.dueAt?.let(::parseInstant)?.isBefore(now) == true
    }.sortedBy { it.dueAt }

/** Request sent by the "帮我排今天" shortcut (always planned, never just answered). */
internal fun todayPlanMessage(today: LocalDate = LocalDate.now()): String =
    "请只安排今天（${today.monthValue}月${today.dayOfMonth}日）接下来的时间：从现在开始，" +
        "挑今天最该做、也做得完的事排进去，做不完的留到以后，不要排到明天之后。"

/** Request sent by "让 AI 重新排" for overdue tasks. */
internal fun overduePlanMessage(overdue: List<TaskItem>): String {
    val names = overdue.take(10).joinToString("、") { "「${it.title.take(40)}」" }
    val more = if (overdue.size > 10) "等 ${overdue.size} 件" else ""
    return "这些任务已经过了截止时间还没完成：$names$more。" +
        "请把它们重新安排到从现在开始合理的时间，太大的可以拆成步骤；其他任务尽量保持原来的安排。"
}

/**
 * What to send when accepting a plan. Null means "everything, unchanged" so the request
 * stays identical to older versions; otherwise the chosen items with any edits applied.
 */
internal fun planAcceptSelection(
    items: List<PlanItem>,
    excluded: Set<Int>,
    edits: Map<Int, PlanItem>,
): List<PlanItem>? {
    if (excluded.isEmpty() && edits.isEmpty()) return null
    return items.mapIndexedNotNull { index, item ->
        if (index in excluded) null else edits[index] ?: item
    }
}

/** Content of the morning notification, or null when there is nothing to say. */
internal data class DailyDigest(val title: String, val lines: List<String>)

internal fun dailyDigest(
    tasks: List<TaskItem>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): DailyDigest? {
    val todays = tasks
        .filter { !it.completed }
        .mapNotNull { task ->
            val due = task.dueAt?.let(::parseInstant)?.atZone(zone) ?: return@mapNotNull null
            if (due.toLocalDate() == today) due to task else null
        }
        .sortedBy { it.first }
    val overdueCount = overdueTasks(tasks, now).count { task ->
        task.dueAt?.let(::parseInstant)?.atZone(zone)?.toLocalDate()?.isBefore(today) == true
    }
    if (todays.isEmpty() && overdueCount == 0) return null
    val title = when {
        todays.isEmpty() -> "今天没有新安排，还有 $overdueCount 件过期待处理"
        overdueCount > 0 -> "今天 ${todays.size} 件事，另有 $overdueCount 件过期"
        else -> "今天 ${todays.size} 件事"
    }
    val lines = todays.take(6).map { (due, task) ->
        "%02d:%02d  %s".format(due.hour, due.minute, task.title.take(30))
    } + listOfNotNull(if (todays.size > 6) "还有 ${todays.size - 6} 件…" else null)
    return DailyDigest(title, lines)
}

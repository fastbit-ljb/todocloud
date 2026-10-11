package com.todocloud.app.ui

import com.todocloud.app.data.PlanItem
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PlanShortcutsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private val now = Instant.parse("2026-10-12T00:30:00Z") // 08:30 in Shanghai

    private fun task(id: Int, dueAt: String?, completed: Boolean = false, title: String = "任务$id") =
        TaskItem(id, title, null, dueAt, null, completed, null)

    private fun item(id: Int, start: String? = null) = PlanItem(
        sourceType = "task", sourceId = id, title = "安排$id", description = null,
        startAt = start, dueAt = null, durationMinutes = 30, reminderOffsetMinutes = null, note = "",
    )

    @Test
    fun overdueTasksAreIncompleteAndPastDueEarliestFirst() {
        val tasks = listOf(
            task(1, "2026-10-11T10:00:00Z"),
            task(2, "2026-10-10T10:00:00Z"),
            task(3, "2026-10-10T09:00:00Z", completed = true),
            task(4, "2026-10-12T10:00:00Z"),
            task(5, null),
        )
        assertEquals(listOf(2, 1), overdueTasks(tasks, now).map { it.id })
    }

    @Test
    fun shortcutMessagesNameTheDayAndTheTasks() {
        assertTrue("10月12日" in todayPlanMessage(today))
        val message = overduePlanMessage(listOf(task(1, null, title = "写周报"), task(2, null, title = "交作业")))
        assertTrue("「写周报」、「交作业」" in message)
        val many = (1..12).map { task(it, null) }
        assertTrue("等 12 件" in overduePlanMessage(many))
    }

    @Test
    fun acceptSelectionIsNullWhenUnchangedOtherwiseChosenItemsWithEdits() {
        val items = listOf(item(1), item(2), item(3))
        assertNull(planAcceptSelection(items, emptySet(), emptyMap()))
        val moved = item(3, start = "2026-10-12T06:00:00Z")
        assertEquals(listOf(item(1), moved), planAcceptSelection(items, setOf(1), mapOf(2 to moved)))
        assertEquals(listOf(item(1), item(2), moved), planAcceptSelection(items, emptySet(), mapOf(2 to moved)))
    }

    @Test
    fun dailyDigestListsTodaysTasksInTimeOrderAndCountsOverdue() {
        val tasks = listOf(
            task(1, "2026-10-12T06:00:00Z", title = "开会"),   // 14:00
            task(2, "2026-10-12T01:30:00Z", title = "写周报"), // 09:30
            task(3, "2026-10-11T10:00:00Z", title = "交作业"), // yesterday, overdue
            task(4, "2026-10-12T02:00:00Z", completed = true),
            task(5, "2026-10-13T02:00:00Z"),
        )
        val digest = dailyDigest(tasks, today, zone, now)!!
        assertEquals("今天 2 件事，另有 1 件过期", digest.title)
        assertEquals(listOf("09:30  写周报", "14:00  开会"), digest.lines)
    }

    @Test
    fun dailyDigestStaysQuietOnAnEmptyDay() {
        assertNull(dailyDigest(listOf(task(1, "2026-10-13T02:00:00Z")), today, zone, now))
        val onlyOverdue = dailyDigest(listOf(task(1, "2026-10-10T02:00:00Z")), today, zone, now)!!
        assertEquals("今天没有新安排，还有 1 件过期待处理", onlyOverdue.title)
        assertEquals(emptyList<String>(), onlyOverdue.lines)
    }
}

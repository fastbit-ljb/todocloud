package com.todocloud.app.branding

import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.Test

class MascotStateManagerTest {
    private val now = Instant.parse("2026-10-07T18:00:00Z")

    @Test
    fun overdueWinsOverFrozen() {
        val state = resolve(
            tasks = listOf(task(dueAt = "2026-10-07T17:00:00Z")),
            lastActivity = now.minusSeconds(4 * 24 * 60 * 60L),
        )

        assertEquals(MascotState.OVERDUE, state)
    }

    @Test
    fun reminderWindowWinsOverFrozen() {
        val state = resolve(
            tasks = listOf(
                task(
                    dueAt = "2026-10-07T18:10:00Z",
                    reminderOffsetMinutes = 15,
                ),
            ),
            lastActivity = now.minusSeconds(4 * 24 * 60 * 60L),
        )

        assertEquals(MascotState.DUE_SOON, state)
    }

    @Test
    fun partialStepsShowProgressBeforeInactivity() {
        val state = resolve(
            tasks = listOf(
                task(
                    steps = listOf(
                        TaskStep("第一步", completed = true),
                        TaskStep("第二步", completed = false),
                    ),
                ),
            ),
            lastActivity = now.minusSeconds(4 * 24 * 60 * 60L),
        )

        assertEquals(MascotState.IN_PROGRESS, state)
    }

    @Test
    fun inactivityTransitionsFromHalfToFullFrozen() {
        assertEquals(
            MascotState.HALF_FROZEN,
            resolve(listOf(task()), now.minusSeconds(25 * 60 * 60L)),
        )
        assertEquals(
            MascotState.FROZEN,
            resolve(listOf(task()), now.minusSeconds(73 * 60 * 60L)),
        )
    }

    @Test
    fun emptyListIsNoTasksBeforeFreezeThreshold() {
        assertEquals(
            MascotState.NO_TASKS,
            resolve(emptyList(), now.minusSeconds(60 * 60L)),
        )
    }

    private fun resolve(tasks: List<TaskItem>, lastActivity: Instant): MascotState =
        MascotStateManager.resolve(tasks, lastActivity, now, ZoneOffset.UTC)

    private fun task(
        dueAt: String? = null,
        reminderOffsetMinutes: Int? = null,
        steps: List<TaskStep> = emptyList(),
    ) = TaskItem(
        id = 1,
        title = "测试任务",
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = reminderOffsetMinutes,
        completed = false,
        completedAt = null,
        steps = steps,
    )
}

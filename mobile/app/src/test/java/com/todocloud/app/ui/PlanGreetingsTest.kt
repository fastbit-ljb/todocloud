package com.todocloud.app.ui

import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import org.junit.Test

class PlanGreetingsTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")

    @Test
    fun lateNightUsesLocalTimeAndHasClearBoundaries() {
        val tasks = listOf(task())
        for ((time, expected) in listOf(
            "2026-10-19T22:59:00Z" to PlanGreetingScene.GENERAL,
            "2026-10-19T23:00:00Z" to PlanGreetingScene.LATE_NIGHT,
            "2026-10-20T04:59:00Z" to PlanGreetingScene.LATE_NIGHT,
            "2026-10-20T05:00:00Z" to PlanGreetingScene.GENERAL,
        )) {
            assertEquals(expected, planGreetingScene(tasks, now = Instant.parse(time), zone = ZoneOffset.UTC))
        }
        assertEquals(
            PlanGreetingScene.LATE_NIGHT,
            planGreetingScene(tasks, now = Instant.parse("2026-10-19T15:00:00Z"), zone = ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun missingTaskDataDoesNotClaimCompletionOrOverdue() {
        val tasks = listOf(task().copy(completed = true, completedAt = now.toString()))
        assertEquals(PlanGreetingScene.GENERAL, scene(tasks, tasksKnown = false))
        assertEquals(PlanGreetingScene.GENERAL, scene(listOf(task(dueAt = "2026-10-18T12:00:00Z")), tasksKnown = false))
        assertEquals(PlanGreetingScene.EMPTY, scene(emptyList()))
    }

    @Test
    fun completionUsesTheCurrentRecurringPeriod() {
        val tasks = listOf(
            task(id = 1).copy(completed = true, completedAt = now.toString(), completedRecurrence = "weekly"),
            task(id = 2, dueAt = "2026-10-26T12:00:00Z").copy(recurrence = "weekly"),
        )
        assertEquals(PlanGreetingScene.COMPLETE, scene(tasks))
        assertNotEquals(
            PlanGreetingScene.COMPLETE,
            planGreetingScene(tasks, now = Instant.parse("2026-10-26T10:00:00Z"), zone = ZoneOffset.UTC),
        )
    }

    @Test
    fun addingPendingWorkRemovesCompletedScene() {
        val completed = task(id = 1).copy(completed = true, completedAt = now.toString())
        assertEquals(PlanGreetingScene.COMPLETE, scene(listOf(completed)))
        assertEquals(PlanGreetingScene.GENERAL, scene(listOf(completed, task(id = 2))))
    }

    @Test
    fun overdueWinsOverTaskVolume() {
        val tasks = (1..8).map { task(id = it) }
        assertEquals(PlanGreetingScene.MANY_TASKS, scene(tasks))
        assertEquals(
            PlanGreetingScene.RESTART,
            scene(tasks + task(id = 9, dueAt = "2026-10-19T10:00:00Z")),
        )
        assertEquals(
            PlanGreetingScene.GENERAL,
            scene(listOf(task()) + (2..10).map { task(id = it).copy(completed = true) }),
        )
    }

    @Test
    fun partialStepsUseContinuationInsteadOfStartingAgain() {
        assertEquals(
            PlanGreetingScene.IN_PROGRESS,
            scene(listOf(task().copy(steps = listOf(TaskStep("第一步", true), TaskStep("第二步"))))),
        )
    }

    @Test
    fun completedAndLateNightOpeningsDoNotAskForMoreWorkToday() {
        for (scene in listOf(PlanGreetingScene.COMPLETE, PlanGreetingScene.LATE_NIGHT)) {
            val opening = choosePlanOpening(scene)
            assertEquals("把力气留给重要的人和事，也留一点给自己。", opening.greeting)
            assertFalse(opening.question.contains("今天想完成"))
        }
    }

    private fun scene(tasks: List<TaskItem>, tasksKnown: Boolean = true) =
        planGreetingScene(tasks, tasksKnown, now, ZoneOffset.UTC)

    private fun task(id: Int = 1, dueAt: String? = null) = TaskItem(
        id = id,
        title = "任务 $id",
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = null,
        completed = false,
        completedAt = null,
    )
}

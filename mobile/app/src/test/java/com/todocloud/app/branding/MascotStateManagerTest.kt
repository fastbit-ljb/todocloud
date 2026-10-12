package com.todocloud.app.branding

import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun completedTaskWithRemainingWorkShowsProgress() {
        val state = resolve(
            tasks = listOf(
                task(id = 1, completed = true),
                task(id = 2, dueAt = "2026-10-09T18:00:00Z"),
            ),
            lastActivity = now,
        )

        assertEquals(MascotState.IN_PROGRESS, state)
    }

    @Test
    fun allTasksCompletedShowsComplete() {
        val state = resolve(
            tasks = listOf(
                task(id = 1, completed = true),
                task(id = 2, completed = true),
            ),
            lastActivity = now,
        )

        assertEquals(MascotState.COMPLETE, state)
    }

    @Test
    fun taskDueTodayWinsOverPartialProgress() {
        val state = resolve(
            tasks = listOf(
                task(id = 1, completed = true),
                task(id = 2, dueAt = "2026-10-07T23:00:00Z"),
            ),
            lastActivity = now,
        )

        assertEquals(MascotState.DUE_SOON, state)
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

    @Test
    fun launcherPlanEnablesOnlyTheChosenSetsAliasForTheState() {
        val plan = MascotStateManager.launcherAliasPlan(
            "com.todocloud.app",
            MascotSets.MOON_CAT,
            MascotState.OVERDUE,
        )

        assertEquals(MascotSets.all.size * MascotState.entries.size, plan.size)
        assertEquals(
            listOf("com.todocloud.app.launcher.mooncat.Overdue"),
            plan.filter { (_, enabled) -> enabled }.map { (alias, _) -> alias },
        )
    }

    @Test
    fun upgradeWithoutAStoredSetCountsAsClassic() {
        // Versions before mascot sets stored the state but no set; their alias is classic's.
        assertTrue(alreadyShown(currentState = "COMPLETE", currentSetId = null, set = MascotSets.CLASSIC))
        assertFalse(alreadyShown(currentState = "COMPLETE", currentSetId = null, set = MascotSets.MOON_CAT))
    }

    @Test
    fun switchingSetsReappliesEvenWhenTheStateIsUnchanged() {
        assertFalse(alreadyShown(currentState = "COMPLETE", currentSetId = "classic", set = MascotSets.MOON_CAT))
        assertTrue(alreadyShown(currentState = "COMPLETE", currentSetId = "mooncat", set = MascotSets.MOON_CAT))
        assertFalse(
            MascotStateManager.alreadyShown(
                configured = false,
                currentState = "COMPLETE",
                currentSetId = "mooncat",
                set = MascotSets.MOON_CAT,
                state = MascotState.COMPLETE,
            ),
        )
    }

    private fun alreadyShown(currentState: String, currentSetId: String?, set: MascotSet) =
        MascotStateManager.alreadyShown(
            configured = true,
            currentState = currentState,
            currentSetId = currentSetId,
            set = set,
            state = MascotState.COMPLETE,
        )

    private fun resolve(tasks: List<TaskItem>, lastActivity: Instant): MascotState =
        MascotStateManager.resolve(tasks, lastActivity, now, ZoneOffset.UTC)

    private fun task(
        id: Int = 1,
        dueAt: String? = null,
        reminderOffsetMinutes: Int? = null,
        steps: List<TaskStep> = emptyList(),
        completed: Boolean = false,
    ) = TaskItem(
        id = id,
        title = "测试任务",
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = reminderOffsetMinutes,
        completed = completed,
        completedAt = if (completed) "2026-10-07T17:30:00Z" else null,
        steps = steps,
    )
}

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
    fun allTasksDueTodayAndCompletedShowsComplete() {
        val state = resolve(
            tasks = listOf(
                task(id = 1, dueAt = "2026-10-07T08:00:00Z", completed = true),
                task(id = 2, dueAt = "2026-10-07T17:00:00Z", completed = true),
            ),
            lastActivity = now,
        )

        assertEquals(MascotState.COMPLETE, state)
    }

    @Test
    fun completedHistoryWithoutTasksTodayShowsNoTasks() {
        val state = resolve(
            tasks = listOf(
                task(id = 1, dueAt = "2026-10-05T17:00:00Z", completed = true),
                task(id = 2, dueAt = "2026-10-06T17:00:00Z", completed = true),
            ),
            lastActivity = now.minusSeconds(4 * 24 * 60 * 60L),
        )

        assertEquals(MascotState.NO_TASKS, state)
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
        for (set in MascotSets.all) {
            for (state in MascotState.entries) {
                val plan = MascotStateManager.launcherAliasPlan("com.todocloud.app", set, state)
                assertEquals(MascotSets.all.size * MascotState.entries.size, plan.size)
                assertEquals(plan.size, plan.map { (alias, _) -> alias }.toSet().size)
                assertEquals(
                    listOf(set.launcherAlias("com.todocloud.app", state)),
                    plan.filter { (_, enabled) -> enabled }.map { (alias, _) -> alias },
                    "${set.id} ${state.name}",
                )
            }
        }
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

    @Test
    fun avatarFollowsEveryTaskChangeWithoutWaitingForTheBackground() {
        var tasks = emptyList<TaskItem>()
        fun shows(expected: MascotState) {
            val state = displayState(tasks)
            assertEquals(expected, state, "tasks: $tasks")
            // Same state for either set; switching sets only swaps the artwork.
            for (set in MascotSets.all) assertEquals(set.pictures[expected], set.avatar(state), set.id)
        }

        shows(MascotState.NO_TASKS)
        tasks = listOf(task(id = 1, dueAt = "2026-10-10T09:00:00Z")) // added, due in three days
        shows(MascotState.DAILY)
        tasks = tasks.map { it.copy(dueAt = "2026-10-07T21:00:00Z") } // moved to tonight
        shows(MascotState.DUE_SOON)
        tasks = tasks.map { it.copy(dueAt = "2026-10-07T17:00:00Z") } // moved into the past
        shows(MascotState.OVERDUE)
        tasks = tasks.map { it.copy(completed = true, completedAt = "2026-10-07T17:30:00Z") } // completed
        shows(MascotState.COMPLETE)
        tasks = tasks + task(id = 2, dueAt = "2026-10-10T09:00:00Z") // a sync brings in another
        shows(MascotState.IN_PROGRESS)
        tasks = tasks.filterNot { it.id == 1 } // deleted
        shows(MascotState.DAILY)
        tasks = emptyList()
        shows(MascotState.NO_TASKS)
    }

    @Test
    fun avatarReachesTheFrozenStatesFromTheStoredActivityTime() {
        val idle = listOf(task())
        assertEquals(MascotState.HALF_FROZEN, displayState(idle, lastActivity = now.minusSeconds(25 * 60 * 60L)))
        assertEquals(MascotState.FROZEN, displayState(idle, lastActivity = now.minusSeconds(73 * 60 * 60L)))
        // Nothing recorded yet counts as active now, as in update().
        assertEquals(MascotState.DAILY, displayState(idle, storedLastActivity = null))
    }

    @Test
    fun avatarIsResolveOnTheSameInputsAsTheLauncherUpdate() {
        val cases = listOf(
            emptyList<TaskItem>(),
            listOf(task(dueAt = "2026-10-07T17:00:00Z")),
            listOf(task(dueAt = "2026-10-07T18:10:00Z", reminderOffsetMinutes = 15)),
            listOf(task(id = 1, completed = true), task(id = 2)),
        )
        for (tasks in cases) {
            for (lastActivity in listOf(now, now.minusSeconds(30 * 60 * 60L), now.minusSeconds(80 * 60 * 60L))) {
                assertEquals(resolve(tasks, lastActivity), displayState(tasks, lastActivity), "tasks: $tasks")
            }
        }
    }

    @Test
    fun beforeTasksLoadTheAvatarShowsWhatTheLauncherShows() {
        assertEquals(MascotState.OVERDUE, displayStateBeforeLoad(pending = "OVERDUE", current = "COMPLETE"))
        assertEquals(MascotState.COMPLETE, displayStateBeforeLoad(pending = null, current = "COMPLETE"))
    }

    @Test
    fun unreadableStoredStateFallsBackToDaily() {
        // Missing, unknown, or the wrong type in a damaged preferences file.
        assertEquals(MascotState.DAILY, displayStateBeforeLoad(pending = null, current = null))
        assertEquals(MascotState.DAILY, displayStateBeforeLoad(pending = "BOGUS", current = null))
        assertEquals(MascotState.DAILY, displayStateBeforeLoad(pending = 3, current = true))
        // An unreadable pending value is skipped like a missing one.
        assertEquals(MascotState.COMPLETE, displayStateBeforeLoad(pending = "BOGUS", current = "COMPLETE"))
        assertEquals(MascotState.COMPLETE, displayStateBeforeLoad(pending = 3, current = "COMPLETE"))
        // An unreadable activity time once tasks have loaded.
        val overdue = listOf(task(dueAt = "2026-10-07T17:00:00Z"))
        assertEquals(MascotState.DAILY, displayState(overdue, storedLastActivity = "yesterday"))
        assertEquals(MascotState.DAILY, displayState(overdue, storedLastActivity = 1.5f))
    }

    private fun displayState(
        tasks: List<TaskItem>,
        lastActivity: Instant = now,
        storedLastActivity: Any? = lastActivity.toEpochMilli(),
    ): MascotState = MascotStateManager.displayStateFrom(
        tasks = tasks,
        tasksLoaded = true,
        storedLastActivity = storedLastActivity,
        // Deliberately stale: once tasks have loaded the stored launcher state must not count.
        storedPending = MascotState.FROZEN.name,
        storedCurrent = MascotState.COMPLETE.name,
        now = now,
        zoneId = ZoneOffset.UTC,
    )

    private fun displayStateBeforeLoad(pending: Any?, current: Any?): MascotState =
        MascotStateManager.displayStateFrom(
            tasks = listOf(task(dueAt = "2026-10-07T17:00:00Z")), // overdue, but not loaded yet
            tasksLoaded = false,
            storedLastActivity = now.toEpochMilli(),
            storedPending = pending,
            storedCurrent = current,
            now = now,
            zoneId = ZoneOffset.UTC,
        )

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

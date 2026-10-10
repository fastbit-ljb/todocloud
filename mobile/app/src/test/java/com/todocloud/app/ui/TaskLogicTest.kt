package com.todocloud.app.ui

import com.todocloud.app.data.AiTaskCandidate
import com.todocloud.app.data.AiTaskStep
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class TaskLogicTest {
    private fun task(
        id: Int,
        title: String = "任务$id",
        dueAt: String? = null,
        completed: Boolean = false,
        completedAt: String? = null,
        steps: List<TaskStep> = emptyList(),
    ) = TaskItem(
        id = id,
        title = title,
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = null,
        completed = completed,
        completedAt = completedAt,
        steps = steps,
    )

    private fun candidate(
        title: String,
        dueAt: String? = null,
        steps: List<AiTaskStep> = emptyList(),
    ) = AiTaskCandidate(
        title = title,
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = null,
        confidence = 0.9,
        sourceText = null,
        steps = steps,
    )

    @Test
    fun sortTasks_putsOpenTasksFirstByDueDateThenNewestCompleted() {
        val sorted = sortTasks(
            listOf(
                task(1, completed = true, completedAt = "2026-10-01T08:00:00Z"),
                task(2, dueAt = null),
                task(3, dueAt = "2026-10-12T08:00:00Z"),
                task(4, completed = true, completedAt = "2026-10-05T08:00:00Z"),
                task(5, dueAt = "2026-10-11T08:00:00Z"),
            ),
        )
        assertEquals(listOf(5, 3, 2, 4, 1), sorted.map { it.id })
    }

    @Test
    fun homeHidesTasksCompletedMoreThanThreeDaysAgo() {
        val now = Instant.now()
        val recent = task(1, completed = true, completedAt = now.minus(1, ChronoUnit.DAYS).toString())
        val old = task(2, completed = true, completedAt = now.minus(4, ChronoUnit.DAYS).toString())
        val open = task(3)
        assertEquals(listOf(1, 3), visibleHomeTasks(listOf(recent, old, open)).map { it.id })
        assertEquals(listOf(1, 2, 3), visibleCalendarTasks(listOf(recent, old, open)).map { it.id })
    }

    @Test
    fun formatReminderOffset_usesLargestWholeUnit() {
        assertEquals("不提醒", formatReminderOffset(null))
        assertEquals("提前 0 天", formatReminderOffset(0))
        assertEquals("提前 15 分钟", formatReminderOffset(15))
        assertEquals("提前 2 小时", formatReminderOffset(120))
        assertEquals("提前 1 天", formatReminderOffset(24 * 60))
    }

    @Test
    fun normalizeTaskTitle_dropsPunctuationAndPoliteFillers() {
        assertEquals("交报告", normalizeTaskTitle(" 请帮我把交一下报告！"))
        assertEquals("callmom", normalizeTaskTitle("Call Mom."))
    }

    @Test
    fun titlesLikelySame_doesNotMergeShortDifferentTitles() {
        assertTrue(titlesLikelySame("提交季度报告", "提交季度报告。"))
        assertFalse(titlesLikelySame("买水", "买书"))
        assertFalse(titlesLikelySame("", "买书"))
    }

    @Test
    fun buildAiImportPlan_skipsDuplicatesAndExistingTasks() {
        val due = "2026-10-11T12:00:00+08:00"
        val plan = buildAiImportPlan(
            existingTasks = listOf(task(1, title = "提交季度报告", dueAt = "2026-10-11T04:00:30Z")),
            candidates = listOf(
                candidate("提交季度报告", due), // same as existing task (30 s apart, same instant zone-adjusted)
                candidate("预约牙医", due),
                candidate("预约牙医！", due), // duplicate within the batch
            ),
        )
        assertEquals(listOf("预约牙医"), plan.candidatesToCreate.map { it.title })
        assertEquals(2, plan.skippedCount)
    }

    @Test
    fun buildAiImportPlan_keepsCandidateThatAddsStepsToAStepLessTask() {
        val plan = buildAiImportPlan(
            existingTasks = listOf(task(1, title = "整理房间")),
            candidates = listOf(candidate("整理房间", steps = listOf(AiTaskStep("扫地")))),
        )
        assertEquals(1, plan.candidatesToCreate.size)
        assertEquals(0, plan.skippedCount)
    }

    @Test
    fun parseInstant_rejectsNonIsoText() {
        assertEquals(Instant.parse("2026-10-11T04:00:00Z"), parseInstant("2026-10-11T12:00:00+08:00"))
        assertEquals(null, parseInstant("明天"))
    }
}

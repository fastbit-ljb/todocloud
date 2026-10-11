package com.todocloud.app.ui

import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TaskEditingTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val start = LocalDateTime.of(2026, 10, 13, 13, 0)
    private val due = LocalDateTime.of(2026, 10, 13, 17, 0)

    private fun task(id: Int, dueAt: String?, completed: Boolean = false) = TaskItem(
        id = id,
        title = "任务$id",
        description = null,
        dueAt = dueAt,
        reminderOffsetMinutes = null,
        completed = completed,
        completedAt = null,
    )

    @Test
    fun draftTrimsTextConvertsTimesAndKeepsStepProgress() {
        val draft = buildTaskDraft(
            title = "  毕设开题现场汇报  ",
            description = "   ",
            startAt = start,
            dueAt = due,
            durationMinutes = 240,
            reminderOffsetMinutes = 30,
            steps = listOf(StepInput(" 做 PPT ", completed = true), StepInput("   "), StepInput("彩排")),
            zone = shanghai,
        )!!

        assertEquals("毕设开题现场汇报", draft.title)
        assertNull(draft.description)
        assertEquals("2026-10-13T05:00:00Z", draft.startAt)
        assertEquals("2026-10-13T09:00:00Z", draft.dueAt)
        assertEquals(240, draft.durationMinutes)
        assertEquals(30, draft.reminderOffsetMinutes)
        assertEquals(listOf(TaskStep("做 PPT", true), TaskStep("彩排", false)), draft.steps)
    }

    @Test
    fun reminderIsDroppedWithoutDueTime() {
        val draft = buildTaskDraft("读书", "", start, null, null, 10, emptyList(), shanghai)!!
        assertNull(draft.dueAt)
        assertNull(draft.reminderOffsetMinutes)
    }

    @Test
    fun invalidInputHasNoDraft() {
        assertNull(buildTaskDraft("   ", "", null, null, null, null, emptyList(), shanghai))
        assertEquals("请填写任务内容", taskEditorError(" ", null, null))
        assertNull(buildTaskDraft("汇报", "", due, start, null, null, emptyList(), shanghai))
        assertEquals("开始时间不能晚于截止时间", taskEditorError("汇报", due, start))
        assertNull(taskEditorError("汇报", start, start))
    }

    @Test
    fun stepsAreCappedAtThirty() {
        val steps = (1..40).map { StepInput("步骤$it") }
        val draft = buildTaskDraft("长任务", "", null, null, null, null, steps, shanghai)!!
        assertEquals(TASK_STEPS_MAX, draft.steps.size)
    }

    @Test
    fun reminderLabelsReadNaturally() {
        assertEquals("不提醒", reminderLabel(null))
        assertEquals("到点提醒", reminderLabel(0))
        assertEquals("提前 10 分钟", reminderLabel(10))
        assertEquals("提前 1 天", reminderLabel(1440)) // calendar used to say "提前 1440 分钟提醒"
    }

    @Test
    fun pendingDeletesAreHiddenAndUndoRestoresSortedWithoutDuplicates() {
        val early = task(1, "2026-10-12T01:00:00Z")
        val late = task(2, "2026-10-14T01:00:00Z")
        val middle = task(3, "2026-10-13T01:00:00Z")
        val all = listOf(early, late, middle)

        assertEquals(listOf(early, late), withoutPendingDeletes(all, setOf(3)))
        assertEquals(all, withoutPendingDeletes(all, emptySet()))

        val restored = restoreTask(listOf(early, late), middle)
        assertEquals(listOf(1, 3, 2), restored.map { it.id })
        assertEquals(listOf(1, 3, 2), restoreTask(restored, middle).map { it.id })
    }
}

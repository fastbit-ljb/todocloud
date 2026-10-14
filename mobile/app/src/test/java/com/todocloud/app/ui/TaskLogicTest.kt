package com.todocloud.app.ui

import androidx.compose.ui.graphics.luminance
import com.todocloud.app.data.AiTaskCandidate
import com.todocloud.app.data.AiTaskStep
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun pastDayFlame_redWhenAllDoneBlueWhenUnfinished() {
        val today = LocalDate.of(2026, 10, 14)
        val yesterday = today.minusDays(1)
        val done = task(1, completed = true, completedAt = "2026-10-13T09:00:00Z")
        val open = task(2)

        assertEquals(PastDayFlame.ALL_DONE, pastDayFlame(yesterday, today, listOf(done)))
        assertEquals(PastDayFlame.UNFINISHED, pastDayFlame(yesterday, today, listOf(done, open)))
        assertEquals(PastDayFlame.UNFINISHED, pastDayFlame(today.minusDays(30), today, listOf(open)))
        // No flame on a past day without tasks, on today or on a future day.
        assertNull(pastDayFlame(yesterday, today, emptyList()))
        assertNull(pastDayFlame(today, today, listOf(done)))
        assertNull(pastDayFlame(today, today, listOf(open)))
        assertNull(pastDayFlame(today.plusDays(1), today, listOf(open)))
    }

    @Test
    fun flameStreak_countsConsecutiveFullyDoneDaysAndTodayOnlyOnceItIsDone() {
        val today = LocalDate.of(2026, 10, 14)
        val now = Instant.now().toString()
        var nextId = 0
        fun due(daysAgo: Long, done: Boolean) = task(
            ++nextId,
            dueAt = "${today.minusDays(daysAgo)}T12:00:00Z",
            completed = done,
            completedAt = if (done) now else null,
        )

        // 3 days ago broke the streak; 2 days ago and yesterday were fully done; 4 days ago
        // had no tasks; the day before that does not count past the break.
        val history = listOf(
            due(5, done = true),
            due(3, done = false),
            due(2, done = true), due(2, done = true),
            due(1, done = true),
        )
        assertEquals(2, flameStreak(history, today))
        assertEquals(0, flameStreak(emptyList(), today))

        // All of today's tasks done: +1.
        val todayDone = history + due(0, done = true)
        assertEquals(3, flameStreak(todayDone, today))
        // A new task due today: back to -1, but the earlier streak is not broken.
        val todayReopened = todayDone + due(0, done = false)
        assertEquals(2, flameStreak(todayReopened, today))
        // Finishing it brings the +1 back.
        val todayFinished = todayDone + due(0, done = true)
        assertEquals(3, flameStreak(todayFinished, today))

        // A day without tasks is skipped, not a break; future and undated tasks are ignored.
        val withGapAndNoise = listOf(due(3, done = true), due(1, done = true)) +
            due(-1, done = false) + task(++nextId)
        assertEquals(2, flameStreak(withGapAndNoise, today))
        // An open task on an earlier day ends the streak even when yesterday had none.
        assertEquals(0, flameStreak(listOf(due(2, done = false)), today))
    }

    @Test
    fun pastDayFlameColor_deepensWithTheTaskCount() {
        fun redLuminance(count: Int) = pastDayFlameColor(PastDayFlame.ALL_DONE, count).luminance()
        fun blueLuminance(count: Int) = pastDayFlameColor(PastDayFlame.UNFINISHED, count).luminance()

        assertEquals(0f, flameDepth(1))
        assertEquals(1f, flameDepth(5))
        assertEquals(1f, flameDepth(12))
        assertEquals(0f, flameDepth(0))
        for (count in 1 until 5) {
            assertTrue(redLuminance(count + 1) < redLuminance(count), "red deepens at $count")
            assertTrue(blueLuminance(count + 1) < blueLuminance(count), "blue deepens at $count")
            val red = pastDayFlameColor(PastDayFlame.ALL_DONE, count)
            val blue = pastDayFlameColor(PastDayFlame.UNFINISHED, count)
            assertTrue(red.red > red.blue, "still red at $count")
            assertTrue(blue.blue > blue.red, "still blue at $count")
        }
        // Capped: five and more look the same.
        assertEquals(pastDayFlameColor(PastDayFlame.ALL_DONE, 5), pastDayFlameColor(PastDayFlame.ALL_DONE, 9))
        // The streak counter is grey with no streak and red from the first day on.
        assertEquals(FlameUnlitGrey, flameStreakColor(0))
        assertEquals(FlameRed, flameStreakColor(1))
        assertEquals(FlameRed, flameStreakColor(30))
        val grey = flameStreakColor(0)
        assertTrue(maxOf(grey.red, grey.green, grey.blue) - minOf(grey.red, grey.green, grey.blue) < 0.05f, "grey has no hue")
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
    fun formatDueAt_showsYearOnlyOutsideTheCurrentYear() {
        val zone = java.time.ZoneId.systemDefault()
        val thisYear = java.time.LocalDate.now().year
        fun at(year: Int) = java.time.ZonedDateTime.of(year, 10, 13, 17, 0, 0, 0, zone)
            .toOffsetDateTime().toString()
        assertEquals("10月13日 17:00", formatDueAt(at(thisYear)))
        assertEquals("${thisYear + 1}年10月13日 17:00", formatDueAt(at(thisYear + 1)))
    }

    @Test
    fun parseInstant_rejectsNonIsoText() {
        assertEquals(Instant.parse("2026-10-11T04:00:00Z"), parseInstant("2026-10-11T12:00:00+08:00"))
        assertEquals(null, parseInstant("明天"))
    }
}

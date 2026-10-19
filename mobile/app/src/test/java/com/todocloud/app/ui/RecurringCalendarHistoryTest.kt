package com.todocloud.app.ui

import com.todocloud.app.branding.MascotState
import com.todocloud.app.branding.MascotStateManager
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class RecurringCalendarHistoryTest {
    @Test
    fun returningToPendingInNextPeriodDoesNotUndoCalendarCompletion() {
        val finished = TaskItem(
            id = 1, title = "本周任务", description = null,
            dueAt = "2026-10-07T08:00:00Z", reminderOffsetMinutes = null,
            completed = true, completedAt = "2026-10-07T07:00:00Z", completedRecurrence = "weekly",
        )
        val next = finished.copy(id = 2, dueAt = "2026-10-14T08:00:00Z", completed = false,
            completedAt = null, recurrence = "weekly", completedRecurrence = null)
        val tasks = listOf(finished, next)
        val now = Instant.parse("2026-10-07T12:00:00Z")
        assertEquals(MascotState.COMPLETE, MascotStateManager.resolve(tasks, now, now, ZoneOffset.UTC))
        assertEquals(MascotState.IN_PROGRESS, MascotStateManager.resolve(tasks, now,
            Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC))

        val calendar = visibleCalendarTasks(tasks)
        assertEquals(finished, calendar.single { it.id == finished.id })
        assertTrue(calendar.single { it.id == finished.id }.completed)
        assertEquals("2026-10-07T07:00:00Z", calendar.single { it.id == finished.id }.completedAt)
        assertEquals(CalendarDayFlame.ALL_DONE, calendarDayFlame(LocalDate.parse("2026-10-07"),
            LocalDate.parse("2026-10-12"), calendar.filter { it.id == finished.id }))
        assertEquals(1, calendar.count { it.completed })
        assertEquals(1, calendar.count { !it.completed })
    }
}

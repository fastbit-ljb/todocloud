package com.todocloud.app.ui

import com.todocloud.app.data.IdeaDetail
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SearchTest {
    private fun task(
        id: Int,
        title: String,
        description: String? = null,
        steps: List<String> = emptyList(),
        completed: Boolean = false,
    ) = TaskItem(id, title, description, null, null, completed, null, steps.map { TaskStep(it) })

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(taskMatches(task(1, "买菜"), "   "))
        assertEquals(emptyList(), searchTerms(" \t "))
    }

    @Test
    fun matchesTitleDescriptionAndStepsCaseInsensitively() {
        val t = task(1, "Weekly Report", description = "发给导师", steps = listOf("整理数据"))
        assertTrue(taskMatches(t, "weekly"))
        assertTrue(taskMatches(t, "导师"))
        assertTrue(taskMatches(t, "整理"))
        assertFalse(taskMatches(t, "健身"))
    }

    @Test
    fun everyTermMustMatch() {
        val t = task(1, "毕设开题现场汇报")
        assertTrue(taskMatches(t, "毕设 汇报"))
        assertFalse(taskMatches(t, "毕设 答辩"))
    }

    @Test
    fun searchIncludesCompletedTasksAfterOpenOnes() {
        val result = searchTasks(
            listOf(task(1, "交作业 A", completed = true), task(2, "交作业 B")),
            "作业",
        )
        assertEquals(listOf(2, 1), result.map { it.id })
    }

    @Test
    fun ideasMatchContentAndDetails() {
        val idea = InboxIdea(1, "学吉他", null, null, null, listOf(IdeaDetail("买一本入门书")))
        assertTrue(ideaMatches(idea, "入门"))
        assertEquals(emptyList(), searchIdeas(listOf(idea), "钢琴"))
    }
}

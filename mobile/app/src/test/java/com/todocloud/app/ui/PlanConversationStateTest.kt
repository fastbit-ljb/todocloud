package com.todocloud.app.ui

import com.todocloud.app.data.PlanConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanConversationStateTest {
    private val first = summary("first")
    private val second = summary("second")

    @Test fun deletingActiveConversationStartsANewDraft() {
        val result = applyConversationDeletion(listOf(first, second), "first", setOf("first"))
        assertEquals(listOf(second), result.remaining)
        assertTrue(result.resetActive)
    }

    @Test fun deletingAnotherConversationKeepsCurrentConversation() {
        val result = applyConversationDeletion(listOf(first, second), "first", setOf("second"))
        assertEquals(listOf(first), result.remaining)
        assertFalse(result.resetActive)
    }

    private fun summary(id: String) = PlanConversationSummary(
        id = id, title = id, updatedAt = "2026-10-19T00:00:00Z",
        preview = "", messageCount = 1, revision = 1,
    )
}

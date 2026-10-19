package com.todocloud.app.data

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ConversationContextTest {
    private fun conversation(id: String, content: String) = PlanConversation(id, "会话$id", List(20) { PlanMessage("user", content) }, updatedAt = "2026-10-19T00:00:00Z")

    @Test fun referencesAreBoundedDeduplicatedAndReadOnly() {
        val sessions = (1..10).map { conversation(it.toString(), "讨论内容".repeat(2000)) }
        val context = conversationReferenceContext(listOf(sessions.first()) + sessions)
        assertTrue(context.length < 18_200)
        assertTrue("只读参考资料" in context)
        assertTrue("会话4" !in context)
        assertEquals("", conversationReferenceContext(emptyList()))
    }

    @Test fun titleComesFromFirstNonemptyLineAndIsBounded() {
        assertEquals("英语学习", conversationTitle("\n英语学习\n继续讨论"))
        assertEquals(60, conversationTitle("学".repeat(100)).length)
        assertEquals("新会话", conversationTitle("  "))
    }
}

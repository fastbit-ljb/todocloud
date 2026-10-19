package com.todocloud.app.ui

import com.todocloud.app.data.PlanConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

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

    @Test fun batchDeletionOnlyProcessesSelectedIdsAndKeepsSuccessfulProgress() = runBlocking {
        val attempted = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val result = deleteConversationBatch(linkedSetOf("first", "second", "third"), {
            attempted += it
            if (it == "second") error("网络中断")
        }, { removed += it })
        assertEquals(listOf("first", "second", "third"), attempted)
        assertEquals(setOf("first", "third"), result.deletedIds)
        assertEquals(listOf("first", "third"), removed)
        assertEquals(mapOf("second" to "网络中断"), result.failures)

        val retry = deleteConversationBatch(result.failures.keys, { attempted += it }, { removed += it })
        assertEquals(setOf("second"), retry.deletedIds)
        assertEquals(listOf("first", "second", "third", "second"), attempted)
        assertTrue(retry.failures.isEmpty())
    }

    @Test fun cancellingBatchDoesNotStartFurtherDeletions() {
        val attempted = mutableListOf<String>()
        val removed = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                deleteConversationBatch(linkedSetOf("first", "second", "third"), {
                    attempted += it
                    if (it == "second") throw CancellationException()
                }, { removed += it })
            }
        }
        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf("first"), removed)
    }

    private fun summary(id: String) = PlanConversationSummary(
        id = id, title = id, updatedAt = "2026-10-19T00:00:00Z",
        preview = "", messageCount = 1, revision = 1,
    )
}

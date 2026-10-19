package com.todocloud.app.ui

import com.todocloud.app.data.PlanConversationSummary
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ConversationPickerTest {
    @Test fun atStartsAReferenceButEmailDoesNot() {
        assertEquals("", mentionQuery("@"))
        assertEquals("英语", mentionQuery("参考 @英语"))
        assertNull(mentionQuery("mail@example.com"))
        assertNull(mentionQuery("@英语\n继续"))
    }

    @Test fun selectionRemovesOnlyTheActiveMention() {
        assertEquals("参考", removeMentionQuery("参考 @英语"))
        assertEquals("", removeMentionQuery("@"))
    }

    @Test fun searchMatchesTitleOrPreviewAndExcludesSelectedSessions() {
        val items = listOf(
            PlanConversationSummary("1", "英语学习", "", "每天半小时", 2, 1),
            PlanConversationSummary("2", "日常安排", "", "英语与运动", 4, 1),
        )
        assertEquals(listOf("1", "2"), matchingConversations(items, "英语", emptySet()).map { it.id })
        assertEquals(listOf("2"), matchingConversations(items, "英语", setOf("1")).map { it.id })
    }
}

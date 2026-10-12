package com.todocloud.app.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ShareIntakeTest {
    @Test
    fun bodyOnly() {
        assertEquals("下周二交报告", sharedText(null, "  下周二交报告 "))
    }

    @Test
    fun subjectIsPrependedUnlessBodyAlreadyHasIt() {
        assertEquals("会议通知\n周三 14:00 开会", sharedText("会议通知", "周三 14:00 开会"))
        assertEquals("会议通知：周三开会", sharedText("会议通知", "会议通知：周三开会"))
        assertEquals("只有标题", sharedText("只有标题", "  "))
    }

    @Test
    fun emptyShareGivesNull() {
        assertNull(sharedText(null, null))
        assertNull(sharedText(" ", "\n"))
    }

    @Test
    fun longTextIsCappedAtTheServerLimit() {
        val result = sharedText(null, "字".repeat(SHARED_TEXT_MAX_CHARS + 50))
        assertEquals(SHARED_TEXT_MAX_CHARS, result?.length)
    }
}

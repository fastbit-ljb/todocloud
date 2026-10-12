package com.todocloud.app.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AiKeyDialogTest {
    @Test
    fun keyFormat() {
        assertEquals("请填写 API 密钥", aiKeyFormatError("  "))
        assertEquals("密钥里不能有空格或换行", aiKeyFormatError("AIza abc def ghi jkl mno pqr"))
        assertEquals("密钥太短，请确认复制完整", aiKeyFormatError("AIza123"))
        assertNull(aiKeyFormatError("AIzaSyA1234567890abcdefghijklmnopqrs"))
    }
}

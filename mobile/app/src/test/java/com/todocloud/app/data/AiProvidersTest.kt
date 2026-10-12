package com.todocloud.app.data

import com.todocloud.app.ui.AiSaveRequest
import com.todocloud.app.ui.aiChoiceSummary
import com.todocloud.app.ui.aiSaveNeedsCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiProvidersTest {
    @Test
    fun presetsAreCompleteAndHttps() {
        val ids = AiProviders.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("gemini", "deepseek", "qwen", "glm", "kimi"), ids)
        AiProviders.all.forEach { provider ->
            assertTrue(provider.id, provider.baseUrl.startsWith("https://"))
            assertTrue(provider.id, provider.baseUrl.contains(provider.host))
            assertTrue(provider.id, provider.keyHelpUrl.startsWith("https://"))
            assertTrue(provider.id, provider.model.isNotBlank())
        }
        assertEquals(AiProviders.GEMINI, AiProviders.byId("unknown"))
        assertEquals(AiProviders.KIMI, AiProviders.byId("kimi"))
    }

    @Test
    fun textOnlyServiceRefusesImagesBeforeAnyRequest() {
        try {
            runBlocking { LocalAi(AiProviders.DEEPSEEK, "sk-test-key-that-is-long-enough").parseTasks(null, "data:image/png;base64,AA") }
            fail("expected ApiException")
        } catch (exception: ApiException) {
            assertTrue(exception.message!!.contains("不能识别图片"))
        }
        assertEquals("DeepSeek（不支持识别图片）", AiProviders.describe(AiProviders.DEEPSEEK))
        assertEquals("通义千问", AiProviders.describe(AiProviders.QWEN))
    }

    @Test
    fun summaryAndCheckRules() {
        val qwen = AiChoice(AiProviders.QWEN, keySet = true, model = "qwen3.7-plus", ownWhenSignedIn = false)
        assertEquals("使用 TodoCloud 服务器的 AI", aiChoiceSummary(qwen, signedIn = true))
        assertEquals("通义千问 · qwen3.7-plus", aiChoiceSummary(qwen, signedIn = false))
        assertEquals(
            "通义千问 · qwen3.7-plus · 在手机上直接调用",
            aiChoiceSummary(qwen.copy(ownWhenSignedIn = true), signedIn = true),
        )
        assertEquals(
            "未填写密钥，AI 计划和识别任务需要它",
            aiChoiceSummary(qwen.copy(keySet = false), signedIn = false),
        )

        val switchOff = AiSaveRequest(AiProviders.QWEN, key = null, model = "qwen3.7-plus", ownWhenSignedIn = false)
        assertFalse(aiSaveNeedsCheck(switchOff, signedIn = true)) // back to the server: nothing to test
        assertTrue(aiSaveNeedsCheck(switchOff, signedIn = false)) // local mode always uses the key
        assertTrue(aiSaveNeedsCheck(switchOff.copy(ownWhenSignedIn = true), signedIn = true))
    }
}

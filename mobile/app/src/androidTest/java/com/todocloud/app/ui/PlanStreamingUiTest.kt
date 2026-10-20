package com.todocloud.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.PlanItem
import com.todocloud.app.data.PlanMessage
import com.todocloud.app.ui.theme.TodoCloudTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanStreamingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val pending = PlanMessage("user", "帮我安排 TapTap 流程")
    private val draft = AiPlan(123, "draft", "测试安排", "先跑通登录", listOf(
        PlanItem("prompt", 1, "跑通 TapTap 登录", null, null, null, 30, null, note = "虚构测试，不操作真实任务"),
    ), emptyList())

    @Test fun userMessageAndActualPartialReplyAppearBeforePlanCanBeAdopted() {
        val text = mutableStateOf<String?>("")
        val loading = mutableStateOf(true)
        val messages = mutableStateOf(listOf(pending))
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = draft,
                        loading = loading.value, error = null, conversation = messages.value,
                        conversationKey = "stream-test", conversationTitle = "TapTap 安排", conversations = emptyList(), syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> }, streamingReply = text.value,
                    )
                } }
            } }
            compose.onNodeWithText(pending.content).assertIsDisplayed()
            compose.onNodeWithText("正在等待 AI 回复…").assertIsDisplayed()
            compose.onNodeWithText("采用计划").assertDoesNotExist()
            compose.onNodeWithContentDescription("新建会话").assertIsNotEnabled()
            compose.runOnIdle { text.value = "先跑通登录，再检查接入参数。" }
            compose.onNodeWithText(text.value!!).assertIsDisplayed()
            compose.onNodeWithText("正在回复…").assertIsDisplayed()
            compose.onNodeWithText("采用计划").assertDoesNotExist()
            screenshot("plan-streaming-light")
            compose.runOnIdle {
                messages.value = listOf(pending, PlanMessage("assistant", "先跑通登录"))
                text.value = null
                loading.value = false
            }
            compose.onNodeWithText("采用计划").assertIsDisplayed()
            compose.onNodeWithText("正在回复…").assertDoesNotExist()
        }
    }

    @Test fun failedPartialRemainsReadableWithoutBeingAnAdoptablePlanDarkLargeText() {
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    TodoCloudTheme(darkTheme = true) { Surface {
                        AiPlanScreen(
                            paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = draft,
                            loading = false, error = "AI 回复中断，请重试", conversation = listOf(pending),
                            conversationKey = "failed-stream", conversationTitle = "TapTap 安排", conversations = emptyList(), syncPending = false,
                            onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                            onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                            streamingReply = "先核对 TapTap 接入参数，再在测试环境完成登录。",
                        )
                    } }
                }
            } }
            compose.onNodeWithText("回复未完成，请重试；未保存新计划").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("采用计划").assertDoesNotExist()
            compose.onNodeWithText("整理成计划").assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).assertIsDisplayed()
            screenshot("plan-streaming-failed-dark-large")
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

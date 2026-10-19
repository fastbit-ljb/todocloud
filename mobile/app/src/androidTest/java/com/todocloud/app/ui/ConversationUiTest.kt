package com.todocloud.app.ui

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.todocloud.app.data.ConversationReference
import com.todocloud.app.data.PlanConversationSummary
import com.todocloud.app.data.PlanMessage
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.PlanItem
import com.todocloud.app.ui.theme.TodoCloudTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val old = PlanConversationSummary("ffffffff-ffff-ffff-ffff-fffffffffff1", "英语学习", "2026-10-19T00:00:00Z", "每天学习二十分钟", 4, 1)

    @Test fun typedAtAttachesActualConversationToNextMessage() {
        var sent = emptyList<ConversationReference>()
        var sentText = ""
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                        loading = false, error = null, conversation = emptyList(), conversationKey = "new",
                        conversationTitle = "新会话", conversations = listOf(old), syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { message, _, _, _, refs -> sentText = message; sent = refs }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                    )
                } }
            } }
            compose.onNode(hasSetTextAction()).performTextInput("@")
            compose.onNodeWithText("引用历史会话").assertIsDisplayed()
            compose.onNodeWithText(old.title).performClick()
            compose.onNodeWithText("@${old.title}").assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextInput("继续规划")
            compose.onNodeWithContentDescription("生成计划").performClick()
            compose.runOnIdle {
                assertEquals("继续规划", sentText)
                assertEquals(listOf(ConversationReference(old.id, old.title)), sent)
            }
            screenshot("conversation-reference")
        }
    }

    @Test fun historyOpensOldSessionAndNewSessionClearsDraft() {
        var opened = ""
        val key = mutableStateOf("first")
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                        loading = false, error = null, conversation = emptyList(), conversationKey = key.value,
                        conversationTitle = "新会话", conversations = listOf(old), syncPending = false,
                        onRetrySync = {}, onNewConversation = { key.value = "second" }, onOpenConversation = { opened = it }, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                    )
                } }
            } }
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithText(old.title).performClick()
            compose.runOnIdle { assertEquals(old.id, opened) }
            compose.onNode(hasSetTextAction()).performTextInput("不应泄露到下一会话的草稿")
            compose.onNodeWithContentDescription("新建会话").performClick()
            compose.onNodeWithText("告诉我你的计划…").assertIsDisplayed()
        }
    }

    @Test fun darkLargeTextKeepsHistoryAndReferencesReachable() {
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        TodoCloudTheme(darkTheme = true) { Surface {
                            AiPlanScreen(
                                paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                                loading = false, error = null, conversation = listOf(PlanMessage("assistant", "可以从二十分钟开始")), conversationKey = "existing",
                                conversationTitle = "当前讨论", conversations = listOf(old), syncPending = false,
                                onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                                onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                            )
                        } }
                    }
                }
            }
            compose.onNodeWithContentDescription("历史会话").assertIsDisplayed()
            compose.onNodeWithContentDescription("引用会话").assertIsDisplayed()
            compose.onNodeWithText("可以从二十分钟开始").assertIsDisplayed()
            compose.onNodeWithContentDescription("生成计划").assertIsDisplayed()
            screenshot("conversation-dark-landscape-large-text")
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithText(old.title).assertIsDisplayed().performClick()
        }
    }

    @Test fun adoptedPlanIsReadableInHistoryWithoutAdoptingItAgain() {
        val item = PlanItem("prompt", -1, "练习英语单词", null, null, null, 20, null, note = "")
        val plan = AiPlan(123, "accepted", "英语安排", "每天练习", listOf(item), emptyList())
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null, historyPlan = plan,
                        loading = false, error = null, conversation = listOf(PlanMessage("assistant", "计划准备好了")), conversationKey = "history",
                        conversationTitle = "英语安排", conversations = listOf(old), syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                    )
                } }
            } }
            compose.onNodeWithText("历史计划 · 已采用").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("1. 练习英语单词").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("采用计划").assertDoesNotExist()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Wait for the system's physical display-rotation transition to finish too.
        android.os.SystemClock.sleep(1_200)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

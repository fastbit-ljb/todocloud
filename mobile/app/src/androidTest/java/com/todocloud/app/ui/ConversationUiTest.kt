package com.todocloud.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextReplacement
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
    private val other = old.copy(id = "ffffffff-ffff-ffff-ffff-fffffffffff2", title = "健身安排", preview = "跑步半小时")
    private val third = old.copy(id = "ffffffff-ffff-ffff-ffff-fffffffffff3", title = "旅行计划", preview = "周末出发")

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
            compose.onNodeWithContentDescription("删除会话：${old.title}").assertDoesNotExist()
            compose.onNodeWithText("选择").assertDoesNotExist()
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

    @Test fun longPressSelectsConversationsAndBatchDeletionRequiresConfirmation() {
        var deleted = emptySet<String>()
        var opened = ""
        val conversations = mutableStateOf(listOf(old, other, third))
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                        loading = false, error = null, conversation = emptyList(), conversationKey = "new",
                        conversationTitle = "新会话", conversations = conversations.value, syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = { opened = it }, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                        onDeleteConversations = { ids -> deleted = ids; conversations.value = conversations.value.filterNot { it.id in ids } },
                    )
                } }
            } }
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithContentDescription("删除会话：${old.title}").assertDoesNotExist()
            compose.onNodeWithText(old.title).performTouchInput { longClick() }
            compose.onNodeWithText(old.title).assertIsOn()
            compose.onNodeWithText(other.title).assertIsOff().performClick().assertIsOn()
            compose.onNodeWithText(old.title).performClick().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithText("已选择 2 个").assertIsDisplayed()
            screenshot("conversation-multi-select")
            compose.onNodeWithText("删除（2）").performClick()
            compose.onNodeWithText("删除 2 个会话？").assertIsDisplayed()
            compose.onNodeWithText("会从所有设备删除会话消息，无法恢复。已采用的计划和任务会保留。").assertIsDisplayed()
            screenshot("conversation-delete-confirmation")
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertEquals(emptySet<String>(), deleted); assertEquals("", opened) }

            compose.onNodeWithText("删除（2）").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle { assertEquals(setOf(old.id, other.id), deleted); assertEquals("", opened) }
            compose.onNodeWithText(third.title).assertIsDisplayed().assertIsOff()
            compose.onNodeWithText("删除（0）").assertIsNotEnabled()
            compose.onNodeWithText("取消多选").performClick()
            compose.onNodeWithText(third.title).performClick()
            compose.runOnIdle { assertEquals(third.id, opened) }
        }
    }

    @Test fun failedHistoryDeletionStaysVisibleAndCanBeRetried() {
        val error = mutableStateOf<String?>(null)
        var attempts = 0
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                        loading = false, error = error.value, conversation = emptyList(), conversationKey = "new",
                        conversationTitle = "新会话", conversations = listOf(old), syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                        onDeleteConversations = { attempts += 1; error.value = "删除失败，请重试" },
                    )
                } }
            } }
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithText(old.title).performTouchInput { longClick() }
            compose.onNodeWithText("删除（1）").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("删除失败，请重试").assertIsDisplayed()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle { assertEquals(2, attempts) }
        }
    }

    @Test fun partialDeletionOfActiveSessionKeepsFailedSelectionAndRetriesOnlyRemaining() {
        val key = mutableStateOf(old.id)
        val conversations = mutableStateOf(listOf(old, other))
        val error = mutableStateOf<String?>(null)
        val attempts = mutableListOf<Set<String>>()
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    AiPlanScreen(
                        paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                        loading = false, error = error.value, conversation = emptyList(), conversationKey = key.value,
                        conversationTitle = "当前讨论", conversations = conversations.value, syncPending = false,
                        onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                        onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                        onDeleteConversations = { ids ->
                            attempts += ids
                            if (attempts.size == 1) {
                                conversations.value = listOf(other)
                                key.value = "new-draft"
                                error.value = "1 个会话删除失败，请重试。已删除 1 个。"
                            } else {
                                conversations.value = emptyList()
                                error.value = null
                            }
                        },
                    )
                } }
            } }
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithText(old.title).performTouchInput { longClick() }
            compose.onNodeWithText("全选").performClick()
            compose.onNodeWithText("删除（2）").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("删除 1 个会话？").assertIsDisplayed()
            compose.onNodeWithText("1 个会话删除失败，请重试。已删除 1 个。").assertIsDisplayed()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle { assertEquals(listOf(setOf(old.id, other.id), setOf(other.id)), attempts) }
            compose.onNodeWithText("还没有历史会话").assertIsDisplayed()
        }
    }

    @Test fun searchSelectionPreservesHiddenChoicesAndSelectAllOnlyAffectsVisibleResults() {
        var deleted = emptySet<String>()
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    ConversationPicker(
                        conversations = listOf(old, other, third), referenceMode = false, initialQuery = "", loading = false,
                        excludedIds = emptySet(), onPick = {}, onDismiss = {}, onDelete = { deleted = it },
                    )
                } }
            } }
            compose.onNodeWithText("选择").performClick()
            compose.onNodeWithText(old.title).performClick().assertIsOn()
            compose.onNodeWithText("搜索会话").performTextReplacement("健身")
            compose.onNodeWithText("全选搜索结果").performClick()
            compose.onNodeWithText("已选择 2 个").assertIsDisplayed()
            compose.onNodeWithText("取消全选").performClick()
            compose.onNodeWithText("已选择 1 个").assertIsDisplayed()
            compose.onNodeWithText("全选搜索结果").performClick()
            compose.onNodeWithText("删除（2）").performClick()
            compose.onNodeWithText("删除").performClick()
            compose.runOnIdle { assertEquals(setOf(old.id, other.id), deleted) }
        }
    }

    @Test fun deletedReferenceInOldMessageIsVisibleButCannotBeOpened() {
        var opened = ""
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    PlanConversation(
                        messages = listOf(PlanMessage("user", "继续安排", listOf(ConversationReference(old.id, old.title)))),
                        onOpenReference = { opened = it },
                        deletedConversationIds = setOf(old.id),
                    )
                } }
            } }
            compose.onNodeWithText("@${old.title}（已删除）").assertIsDisplayed().assertIsNotEnabled()
            compose.runOnIdle { assertEquals("", opened) }
        }
    }

    @Test fun darkSmallPortraitLargeTextKeepsHistoryAndReferencesReachable() {
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current
                    val smallPhoneDensity = density.density * LocalConfiguration.current.screenWidthDp / 375f
                    CompositionLocalProvider(LocalDensity provides Density(smallPhoneDensity, 2f)) {
                        TodoCloudTheme(darkTheme = true) { Surface {
                            AiPlanScreen(
                                paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                                loading = false, error = null, conversation = listOf(PlanMessage("assistant", "可以从二十分钟开始")), conversationKey = "existing",
                                conversationTitle = "当前讨论", conversations = listOf(old), syncPending = false,
                                onRetrySync = {}, onNewConversation = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                                onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                                onDeleteConversations = {},
                            )
                        } }
                    }
                }
            }
            compose.onNodeWithContentDescription("历史会话").assertIsDisplayed()
            compose.onNodeWithText("引用会话").assertIsDisplayed()
            compose.onNodeWithText("可以从二十分钟开始").assertIsDisplayed()
            compose.onNodeWithContentDescription("生成计划").assertIsDisplayed()
            screenshot("conversation-dark-portrait-large-text")
            compose.onNodeWithContentDescription("历史会话").performClick()
            compose.onNodeWithText(old.title).performTouchInput { longClick() }
            compose.onNodeWithText(old.title).assertIsOn()
            screenshot("conversation-multi-select-dark-large-text")
            compose.onNodeWithText("删除（1）").performClick()
            compose.onNodeWithText("删除 1 个会话？").assertIsDisplayed()
            screenshot("conversation-delete-dark-large-text")
            compose.onNodeWithText("取消").performClick()
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
        // Let the system window transition finish before visual verification.
        android.os.SystemClock.sleep(1_200)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

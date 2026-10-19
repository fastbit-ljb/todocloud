package com.todocloud.app.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todocloud.app.ui.theme.TodoCloudTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanGreetingUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun waitsForTaskContextThenKeepsOpeningWhileTypingAndRefreshing() {
        val scene = mutableStateOf<PlanGreetingScene?>(null)
        val conversationKey = mutableStateOf("first")
        val quietGreeting = "把力气留给重要的人和事，也留一点给自己。"
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    TodoCloudTheme {
                        Surface {
                            AiPlanScreen(
                                paddingValues = PaddingValues(), ideas = emptyList(), tasks = emptyList(), plan = null,
                                loading = false, error = null, conversation = emptyList(), conversationKey = conversationKey.value,
                                conversationTitle = "新会话", conversations = emptyList(), syncPending = false,
                                greetingScene = scene.value,
                                onRetrySync = {}, onOpenConversation = {}, onRefreshHistory = {}, onOpenInbox = {},
                                onNewConversation = {
                                    scene.value = PlanGreetingScene.RESTART
                                    conversationKey.value = "second"
                                },
                                onGenerate = { _, _, _, _, _ -> }, onRevise = { _, _, _, _ -> }, onAccept = { _, _ -> },
                            )
                        }
                    }
                }
            }
            compose.onNodeWithText(quietGreeting).assertDoesNotExist()
            compose.runOnIdle { scene.value = PlanGreetingScene.COMPLETE }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText(quietGreeting).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(quietGreeting).assertIsDisplayed()

            // New data and draft edits must not replace the current welcome text.
            compose.runOnIdle { scene.value = PlanGreetingScene.MANY_TASKS }
            compose.onNode(hasSetTextAction()).performTextInput("明天想做的事")
            // The keyboard may move the welcome area above the viewport; its text
            // must still be the same rather than being selected or typed again.
            compose.onNodeWithText(quietGreeting).assertExists()

            // A new conversation takes a fresh snapshot of the current scene.
            compose.onNodeWithContentDescription("新建会话").performClick()
            val restartGreetings = listOf(
                "重新开始的这一步，什么时候迈出都值得。",
                "先做成一件小事，让想法落地。",
                "把难的那件事，拆成今天能做的一小块。",
                "不用等准备好，先做一步看看。",
            )
            compose.waitUntil(15_000) {
                restartGreetings.any { text -> compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithText(quietGreeting).assertDoesNotExist()
        }
    }
}

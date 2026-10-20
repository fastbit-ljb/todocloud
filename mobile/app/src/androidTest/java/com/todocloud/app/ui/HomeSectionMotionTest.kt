package com.todocloud.app.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.TaskItem
import com.todocloud.app.ui.theme.TodoCloudTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSectionMotionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun rapidSwitchesShowOnlyCurrentListWithoutRunningTaskActions() {
        val section = mutableStateOf("tasks")
        var taskActions = 0
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    TaskHomeScreen(
                        paddingValues = PaddingValues(), section = section.value,
                        onSectionChange = { section.value = it },
                        tasks = listOf(TaskItem(1, "切换测试任务", null, null, null, false, null)),
                        deletingTaskIds = emptySet(),
                        ideas = listOf(InboxIdea(1, "切换测试想法", null, null, null)),
                        loading = false, showLoadingAnimation = false, error = null,
                        onToggle = { _, _ -> taskActions++ }, onStepToggle = { _, _, _ -> taskActions++ },
                        onReminderChange = { _, _ -> taskActions++ }, onDelete = { taskActions++ },
                        onEditTask = { taskActions++ }, onEditIdea = { taskActions++ }, onDeleteIdea = { taskActions++ },
                    )
                } }
            } }
            compose.mainClock.autoAdvance = false
            repeat(5) {
                compose.onNodeWithText("收件匣").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText("切换测试想法").assertExists()
                compose.onNodeWithText("切换测试任务").assertDoesNotExist()
                compose.onNodeWithText("任务").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText("切换测试任务").assertExists()
                compose.onNodeWithText("切换测试想法").assertDoesNotExist()
            }
            compose.onNodeWithText("收件匣").performClick()
            compose.mainClock.advanceTimeBy(240)
            compose.onNodeWithText("收件匣").assertIsSelected()
            compose.onNodeWithText("切换测试想法").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, taskActions) }
        }
    }

    @Test fun reducedMotionSwitchesImmediatelyAndKeepsOneContentComposition() {
        val section = mutableStateOf("tasks")
        var liveContents = 0
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    TaskSectionTransition(section.value, Modifier.fillMaxSize(), animationsEnabled = false) {
                        DisposableEffect(section.value) {
                            liveContents++
                            onDispose { liveContents-- }
                        }
                        Text(section.value)
                    }
                } }
            } }
            compose.mainClock.autoAdvance = false
            repeat(8) { index ->
                val target = if (index % 2 == 0) "inbox" else "tasks"
                compose.runOnIdle { section.value = target }
                // Apply one UI frame, not the 180ms animation duration.
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText(target).assertIsDisplayed()
                val left = compose.onNodeWithTag("task-section-content").getUnclippedBoundsInRoot().left
                assertEquals(0f, left.value, 0.1f)
                compose.runOnIdle { assertEquals(1, liveContents) }
            }
        }
    }

    @Test fun composerStartsCompactAndGrowsForMultilineText() {
        val draft = mutableStateOf("")
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                TodoCloudTheme { Surface {
                    PlanComposer(
                        value = draft.value, onValueChange = { draft.value = it }, loading = false,
                        canSubmit = hasPlanRequestContent(draft.value), onSubmit = {},
                        placeholder = "告诉我你的计划…", submitLabel = "生成计划",
                        onReference = {}, onAddImages = {}, onInboxClick = {},
                    )
                } }
            } }
            val collapsedHeight = compose.onNodeWithTag("plan-composer").getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue("Empty composer should stay compact: $collapsedHeight", collapsedHeight <= 80.dp)
            compose.onNodeWithContentDescription("添加内容").assertIsDisplayed()
            compose.onNodeWithContentDescription("记录收件匣想法").assertIsDisplayed()
            compose.onNodeWithContentDescription("生成计划").assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextReplacement("第一步\n第二步\n第三步\n第四步")
            val expandedHeight = compose.onNodeWithTag("plan-composer").getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue(expandedHeight > collapsedHeight)
        }
    }
}

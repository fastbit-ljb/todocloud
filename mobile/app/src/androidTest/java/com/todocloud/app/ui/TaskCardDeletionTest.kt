package com.todocloud.app.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todocloud.app.data.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskCardDeletionTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun deleteRequestReachesScreenBeforeCardCanLeaveComposition() {
        var showCard by mutableStateOf(true)
        var deleteRequests = 0
        val task = TaskItem(
            id = 42,
            title = "切页删除测试",
            description = null,
            dueAt = null,
            reminderOffsetMinutes = null,
            completed = false,
            completedAt = null,
        )

        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        if (showCard) {
                            TaskCard(
                                task = task,
                                deleting = false,
                                onToggle = {},
                                onStepToggle = {},
                                onReminderChange = {},
                                onDelete = {
                                    deleteRequests += 1
                                    // Models switching to another bottom tab immediately.
                                    showCard = false
                                },
                            )
                        }
                    }
                }
            }
            composeRule.mainClock.autoAdvance = false

            composeRule.onNodeWithContentDescription("删除任务").performClick()

            composeRule.runOnIdle {
                assertEquals(1, deleteRequests)
                assertFalse(showCard)
            }
        }
    }
}

package com.todocloud.app.notification

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses local alarm IDs only; never creates or changes a real task. */
@RunWith(AndroidJUnit4::class)
class AlarmInteractionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)

    @Before
    @After
    fun removeOnlyTestAlarms() {
        ReminderScheduler.cancel(context, TEST_TASK_ID)
        ReminderScheduler.cancel(context, SECOND_TEST_TASK_ID)
    }

    @Test
    fun longTaskKeepsSnoozeAndStopReachable() {
        ActivityScenario.launch<AlarmActivity>(
            AlarmActivity.intent(
                context,
                TEST_TASK_ID,
                "准备明天的学习计划与项目资料",
                "检查待办事项、整理项目资料，并为明天预留专注时间。".repeat(8),
            ),
        ).use {
            ensureDisplayed("稍后 5 分钟")
            ensureDisplayed("停止闹钟")
            ensureDisplayed("静音")
            saveScreenshot("alarm-actions")
            ensureDisplayed("停止闹钟")
            compose.onNodeWithText("停止闹钟").performClick()
            assertFalse(prefs.contains("stored_snooze_$TEST_TASK_ID"))
        }
    }

    @Test
    fun newTaskIntentSnoozesTheVisibleTaskForFiveMinutes() {
        assumeTrue(ReminderScheduler.canScheduleExactAlarms(context))
        ActivityScenario.launch<AlarmActivity>(
            AlarmActivity.intent(context, TEST_TASK_ID, "第一项闹钟"),
        ).use { scenario ->
            ensureDisplayed("第一项闹钟")
            scenario.onActivity { activity ->
                activity.startActivity(AlarmActivity.intent(context, SECOND_TEST_TASK_ID, "第二项闹钟"))
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("第二项闹钟").fetchSemanticsNodes().isNotEmpty() }
            ensureDisplayed("第二项闹钟")
            saveScreenshot("alarm-short")
            ensureDisplayed("稍后 5 分钟")
            val beforeClick = System.currentTimeMillis()
            compose.onNodeWithText("稍后 5 分钟").performClick()
            compose.waitUntil(5_000) { prefs.contains("stored_snooze_$SECOND_TEST_TASK_ID") }
            val afterClick = System.currentTimeMillis()
            val triggerAt = JSONObject(prefs.getString("stored_snooze_$SECOND_TEST_TASK_ID", "{}")!!)
                .getLong("triggerAt")
            assertTrue(triggerAt >= beforeClick + 300_000L)
            assertTrue(triggerAt <= afterClick + 300_000L)
            assertFalse(prefs.contains("stored_snooze_$TEST_TASK_ID"))

            // Opt-in real-time delivery check; the default UI suite stays fast.
            if (InstrumentationRegistry.getArguments().getString("verifySnoozeDelivery") == "true") {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                compose.waitUntil(315_000) {
                    runCatching {
                        AlarmActivity.hasFocusedAlarmWindow() &&
                            compose.onAllNodesWithText("第二项闹钟").fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false)
                }
                ensureDisplayed("停止闹钟")
                saveScreenshot("alarm-snooze-delivered")
                compose.onNodeWithText("停止闹钟").performClick()
            }
        }
    }

    @Test
    fun systemBackKeySnoozesForFiveMinutes() {
        assertSnoozedBySystemBack {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    @Test
    fun leftEdgeSystemBackGestureSnoozesForFiveMinutes() {
        swipeSystemBack(fromLeft = true)
    }

    @Test
    fun rightEdgeSystemBackGestureSnoozesForFiveMinutes() {
        swipeSystemBack(fromLeft = false)
    }

    private fun swipeSystemBack(fromLeft: Boolean) {
        assumeTrue(Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1) == 2)
        assertSnoozedBySystemBack {
            val metrics = context.resources.displayMetrics
            val startX = if (fromLeft) 5 else metrics.widthPixels - 5
            val endX = if (fromLeft) metrics.widthPixels / 3 else metrics.widthPixels * 2 / 3
            val y = metrics.heightPixels / 2
            val command = "input swipe $startX $y $endX $y 400"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {
                it.readBytes()
            }
        }
    }

    private fun assertSnoozedBySystemBack(backAction: () -> Unit) {
        assumeTrue(ReminderScheduler.canScheduleExactAlarms(context))
        assertFalse("A previous run must not satisfy the snooze check", prefs.contains("stored_snooze_$TEST_TASK_ID"))
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActivityScenario.launch<AlarmActivity>(
            AlarmActivity.intent(context, TEST_TASK_ID, "系统返回稍后提醒测试"),
        ).use {
            ensureDisplayed("系统返回稍后提醒测试")
            val beforeBack = System.currentTimeMillis()
            backAction()
            compose.waitUntil(5_000) { prefs.contains("stored_snooze_$TEST_TASK_ID") }
            val afterBack = System.currentTimeMillis()
            val triggerAt = JSONObject(prefs.getString("stored_snooze_$TEST_TASK_ID", "{}")!!)
                .getLong("triggerAt")
            val timing = "triggerAt=$triggerAt, beforeBack=$beforeBack, afterBack=$afterBack"
            assertTrue(timing, triggerAt >= beforeBack + 300_000L)
            assertTrue(timing, triggerAt <= afterBack + 300_000L)
            compose.waitUntil(5_000) { !AlarmActivity.hasFocusedAlarmWindow() }
        }
    }

    private fun ensureDisplayed(text: String) {
        val node = compose.onNodeWithText(text)
        try {
            node.assertIsDisplayed()
        } catch (_: AssertionError) {
            node.performScrollTo().assertIsDisplayed()
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        // The previous alarm's system toast must not cover the action buttons.
        SystemClock.sleep(2_500)
        val suffix = InstrumentationRegistry.getArguments().getString("snapshotName", "default")
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(context.getExternalFilesDir(null), "$name-$suffix.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    private companion object {
        const val TEST_TASK_ID = 2_147_483_000
        const val SECOND_TEST_TASK_ID = 2_147_483_001
    }
}

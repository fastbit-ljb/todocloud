package com.todocloud.app.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.lang.ref.WeakReference
import com.todocloud.app.ui.theme.TodoCloudTheme

class AlarmActivity : ComponentActivity() {
    private var alarmHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        showAlarmContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alarmHandled = false
        showAlarmContent()
    }

    private fun showAlarmContent() {
        val taskId = intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0)
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: "任务提醒"
        val description = intent.getStringExtra(ReminderReceiver.EXTRA_DESCRIPTION)
        val steps = parseSteps(intent.getStringExtra(ReminderReceiver.EXTRA_STEPS))
        setContent {
            TodoCloudTheme {
                AlarmScreen(
                    taskId = taskId,
                    title = title,
                    description = description,
                    steps = steps,
                    onStop = ::stopAlarm,
                    onSnooze = ::snoozeAlarm,
                    onMuteChanged = { muted ->
                        startService(
                            Intent(
                                this,
                                AlarmService::class.java,
                            ).setAction(if (muted) AlarmService.ACTION_MUTE else AlarmService.ACTION_UNMUTE),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(AlarmService.LOG_TAG, "alarm_page_resumed task=${intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0)}")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            focusedAlarmActivity = WeakReference(this)
        } else {
            clearFocusedAlarmWindow()
        }
        Log.i(AlarmService.LOG_TAG, "alarm_page_focus=$hasFocus")
    }

    override fun onPause() {
        clearFocusedAlarmWindow()
        super.onPause()
    }

    private fun clearFocusedAlarmWindow() {
        if (focusedAlarmActivity?.get() === this) focusedAlarmActivity = null
    }

    private fun stopAlarm() {
        if (alarmHandled) return
        alarmHandled = true
        closeAlarmPage()
    }

    private fun snoozeAlarm() {
        if (alarmHandled) return
        val scheduled = ReminderScheduler.snoozeAlarm(
            context = this,
            taskId = intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0),
            title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: "任务提醒",
            description = intent.getStringExtra(ReminderReceiver.EXTRA_DESCRIPTION),
            steps = intent.getStringExtra(ReminderReceiver.EXTRA_STEPS),
            dueAt = intent.getStringExtra(ReminderReceiver.EXTRA_DUE_AT),
            reminderOffsetMinutes = intent.getIntExtra(ReminderReceiver.EXTRA_REMINDER_OFFSET, 0),
        )
        if (!scheduled) {
            Toast.makeText(this, "无法稍后提醒，请检查精确闹钟权限", Toast.LENGTH_LONG).show()
            return
        }
        alarmHandled = true
        Toast.makeText(this, "将在 5 分钟后再次提醒", Toast.LENGTH_SHORT).show()
        closeAlarmPage()
    }

    private fun closeAlarmPage() {
        stopService(Intent(this, AlarmService::class.java))
        // AlarmActivity lives in its own temporary task. Removing that task
        // reveals whichever app the user was using before the alarm appeared.
        finishAndRemoveTask()
    }

    companion object {
        private var focusedAlarmActivity: WeakReference<AlarmActivity>? = null

        internal fun hasFocusedAlarmWindow(): Boolean =
            focusedAlarmActivity?.get()?.hasWindowFocus() == true

        fun intent(
            context: Context,
            taskId: Int,
            title: String,
            description: String? = null,
            steps: String? = null,
            dueAt: String? = null,
            reminderOffsetMinutes: Int = 0,
        ): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
                putExtra(ReminderReceiver.EXTRA_DESCRIPTION, description)
                putExtra(ReminderReceiver.EXTRA_STEPS, steps)
                putExtra(ReminderReceiver.EXTRA_DUE_AT, dueAt)
                putExtra(ReminderReceiver.EXTRA_REMINDER_OFFSET, reminderOffsetMinutes)
            }
    }
}

private data class AlarmStep(
    val title: String,
    val completed: Boolean,
)

private fun parseSteps(raw: String?): List<AlarmStep> = runCatching {
    if (raw.isNullOrBlank()) return emptyList()
    val array = JSONArray(raw)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            if (title.isNotEmpty()) add(AlarmStep(title, item.optBoolean("completed")))
        }
    }
}.getOrDefault(emptyList())

@Composable
private fun AlarmScreen(
    taskId: Int,
    title: String,
    description: String?,
    steps: List<AlarmStep>,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onMuteChanged: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var muted by rememberSaveable(taskId) { mutableStateOf(false) }
    val completedSteps = steps.count { it.completed }
    val hasDetails = !description.isNullOrBlank() || steps.isNotEmpty()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(colors.background),
    ) {
        val compactHeight = maxHeight < 480.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = 0.62f),
                            colors.background,
                            colors.surfaceVariant.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = colors.primary,
                    )
                }
                Text(
                    "TodoCloud · 任务闹钟",
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (compactHeight) Modifier else Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = colors.primaryContainer,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        modifier = Modifier.padding(22.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (muted) "已静音" else "到提醒时间了",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (hasDetails) {
                    Spacer(Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        if (!description.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .size(width = 4.dp, height = 44.dp)
                                        .background(colors.primary, RoundedCornerShape(4.dp)),
                                )
                                Text(
                                    description,
                                    modifier = Modifier.padding(start = 12.dp),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        if (steps.isNotEmpty()) {
                            if (!description.isNullOrBlank()) Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "执行进度",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "$completedSteps/${steps.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.primary,
                                )
                            }
                            Spacer(Modifier.height(7.dp))
                            LinearProgressIndicator(
                                progress = { completedSteps.toFloat() / steps.size },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp),
                                color = colors.primary,
                                trackColor = colors.primaryContainer.copy(alpha = 0.65f),
                            )
                            Spacer(Modifier.height(12.dp))
                            steps.take(4).forEach { step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (step.completed) colors.primary else colors.outline,
                                    )
                                    Text(
                                        step.title,
                                        modifier = Modifier.padding(start = 9.dp),
                                        color = if (step.completed) colors.onSurfaceVariant else colors.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (steps.size > 4) {
                                Text(
                                    "还有 ${steps.size - 4} 个步骤",
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(width = 4.dp, height = 44.dp)
                                .background(colors.primary, RoundedCornerShape(4.dp)),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                    "按计划，开始这项任务吧",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Text(
                                    "停止闹钟后，可在任务列表勾选完成",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            AlarmActions(
                muted = muted,
                onMute = {
                    muted = !muted
                    onMuteChanged(muted)
                },
                onSnooze = onSnooze,
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun AlarmActions(
    muted: Boolean,
    onMute: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onMute, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                if (muted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(if (muted) "恢复声音" else "静音", modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        val largeText = LocalDensity.current.fontScale > 1.3f
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 320.dp || largeText
            val snoozeButton: @Composable (Modifier) -> Unit = { modifier ->
                FilledTonalButton(
                    onClick = onSnooze,
                    modifier = modifier.heightIn(min = 60.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Outlined.Snooze, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text("稍后 5 分钟", modifier = Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
                }
            }
            val stopButton: @Composable (Modifier) -> Unit = { modifier ->
                Button(
                    onClick = onStop,
                    modifier = modifier.heightIn(min = 60.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text("停止闹钟", modifier = Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
                }
            }
            if (stackActions) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    snoozeButton(Modifier.fillMaxWidth())
                    stopButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    snoozeButton(Modifier.weight(1f))
                    stopButton(Modifier.weight(1f))
                }
            }
        }
        Text(
            "稍后提醒不会修改任务时间",
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

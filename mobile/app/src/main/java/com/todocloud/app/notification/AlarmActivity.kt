package com.todocloud.app.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import com.todocloud.app.MainActivity
import com.todocloud.app.ui.theme.TodoCloudTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val taskId = intent.getIntExtra(ReminderReceiver.EXTRA_TASK_ID, 0)
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: "任务提醒"
        val description = intent.getStringExtra(ReminderReceiver.EXTRA_DESCRIPTION)
        val steps = parseSteps(intent.getStringExtra(ReminderReceiver.EXTRA_STEPS))
        setContent {
            TodoCloudTheme {
                AlarmScreen(
                    title = title,
                    description = description,
                    steps = steps,
                    onStop = { stopAlarm(taskId) },
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

    private fun stopAlarm(taskId: Int) {
        stopService(Intent(this, AlarmService::class.java))
        startActivity(MainActivity.intent(this, taskId))
        finish()
    }

    companion object {
        fun intent(
            context: Context,
            taskId: Int,
            title: String,
            description: String? = null,
            steps: String? = null,
        ): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
                putExtra(ReminderReceiver.EXTRA_DESCRIPTION, description)
                putExtra(ReminderReceiver.EXTRA_STEPS, steps)
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
    title: String,
    description: String?,
    steps: List<AlarmStep>,
    onStop: () -> Unit,
    onMuteChanged: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var muted by rememberSaveable { mutableStateOf(false) }
    val pulseTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val iconScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alarm_icon_scale",
    )
    val completedSteps = steps.count { it.completed }
    val hasDetails = !description.isNullOrBlank() || steps.isNotEmpty()
    val muteContainerColor by animateColorAsState(
        targetValue = if (muted) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.8f),
        animationSpec = tween(220),
        label = "mute_button_color",
    )
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
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
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 30.dp),
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
                    "TodoCloud · 任务提醒",
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .size(116.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    shape = RoundedCornerShape(38.dp),
                    color = colors.primaryContainer,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        modifier = Modifier.padding(29.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(25.dp))
                Text("任务闹钟", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text("提醒正在响铃", color = colors.onSurfaceVariant)

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
                                "现在是执行时间",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Text(
                                "完成后返回任务列表勾选完成",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            muted = !muted
                            onMuteChanged(muted)
                        },
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = muteContainerColor,
                            contentColor = colors.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                            contentDescription = if (muted) "恢复闹钟声音" else "静音闹钟",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        if (muted) "已静音" else "声音",
                        modifier = Modifier.padding(top = 6.dp),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.size(36.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.StopCircle,
                            contentDescription = "停止闹钟",
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Text(
                        "停止",
                        modifier = Modifier.padding(top = 6.dp),
                        color = colors.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

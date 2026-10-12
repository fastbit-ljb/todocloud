package com.todocloud.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.todocloud.app.data.TaskDraft
import com.todocloud.app.data.TaskItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Create ([task] == null) or edit a task: title, note, start/due time, duration,
 * reminder and steps. Editing keeps the task's completion state and step progress.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskEditorDialog(
    task: TaskItem?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
) {
    val key = task?.id
    var title by rememberSaveable(key) { mutableStateOf(task?.title.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(task?.description.orEmpty()) }
    var startAt by remember(key) { mutableStateOf(task?.startAt?.let(::parseIdeaDateTime)) }
    var dueAt by remember(key) { mutableStateOf(task?.dueAt?.let(::parseIdeaDateTime)) }
    var durationMinutes by rememberSaveable(key) { mutableStateOf(task?.durationMinutes) }
    var reminder by rememberSaveable(key) {
        mutableStateOf(if (task == null) NEW_TASK_DEFAULT_REMINDER_MINUTES else task.reminderOffsetMinutes)
    }
    var recurrence by rememberSaveable(key) { mutableStateOf(task?.recurrence) }
    var steps by remember(key) {
        mutableStateOf(task?.steps?.map { StepInput(it.title, it.completed) }.orEmpty())
    }
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    var showCustomReminder by remember { mutableStateOf(false) }
    var showCustomDuration by remember { mutableStateOf(false) }
    var customDurationText by remember { mutableStateOf("") }
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA) }
    val validationError = taskEditorError(title, startAt, dueAt)
        ?.takeUnless { title.isBlank() } // a blank title just disables 保存, no red text

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(if (task == null) "新建任务" else "编辑任务") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingUnderlineTextField(
                    value = title,
                    onValueChange = { title = it.take(TASK_TITLE_MAX) },
                    label = "任务内容",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ThemeCardTextField(
                    value = description,
                    onValueChange = { description = it.take(10_000) },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "补充任务说明（可选）",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickerTarget = "start" }, modifier = Modifier.weight(1f)) {
                        Text(startAt?.format(formatter) ?: "开始时间")
                    }
                    OutlinedButton(onClick = { pickerTarget = "due" }, modifier = Modifier.weight(1f)) {
                        Text(dueAt?.format(formatter) ?: "截止时间")
                    }
                }
                if (startAt != null || dueAt != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (startAt != null) TextButton(onClick = { startAt = null }) { Text("清除开始") }
                        if (dueAt != null) TextButton(onClick = { dueAt = null }) { Text("清除截止") }
                    }
                }
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (dueAt != null) {
                    Text("提醒", style = MaterialTheme.typography.titleSmall)
                    val isCustom = REMINDER_PRESETS.none { it.minutes == reminder }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REMINDER_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = reminder == preset.minutes,
                                onClick = { reminder = preset.minutes },
                                label = { Text(preset.label) },
                            )
                        }
                        FilterChip(
                            selected = isCustom,
                            onClick = { showCustomReminder = true },
                            label = { Text(if (isCustom) reminderLabel(reminder) else "自定义…") },
                        )
                    }

                    Text("重复", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RECURRENCE_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = recurrence == option.rule,
                                onClick = { recurrence = option.rule },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    if (recurrence != null) {
                        Text(
                            "完成后会自动生成下一次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IdeaDurationSelector(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    onMore = {
                        customDurationText = durationMinutes
                            ?.takeUnless { value -> IDEA_DURATION_OPTIONS.any { it.minutes == value } }
                            ?.toString()
                            .orEmpty()
                        showCustomDuration = true
                    },
                )

                Text("步骤（可选）", style = MaterialTheme.typography.titleSmall)
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FloatingUnderlineTextField(
                            value = step.title,
                            onValueChange = { next ->
                                steps = steps.toMutableList().also { it[index] = step.copy(title = next.take(500)) }
                            },
                            label = if (step.completed) "步骤 ${index + 1}（已完成）" else "步骤 ${index + 1}",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(index) } }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除步骤 ${index + 1}")
                        }
                    }
                }
                if (steps.size < TASK_STEPS_MAX) {
                    TextButton(onClick = { steps = steps + StepInput("") }) { Text("＋ 添加一个步骤") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    buildTaskDraft(
                        title, description, startAt, dueAt, durationMinutes, reminder, steps,
                        recurrence = recurrence,
                    )?.let(onSave)
                },
                enabled = !loading && taskEditorError(title, startAt, dueAt) == null,
            ) { Text(if (loading) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )

    pickerTarget?.let { target ->
        TaskDateTimePickerDialog(
            title = if (target == "start") "设置开始时间" else "设置截止时间",
            initialDateTime = if (target == "start") startAt else dueAt,
            onDismiss = { pickerTarget = null },
            onConfirm = { selected: LocalDateTime ->
                if (target == "start") startAt = selected else dueAt = selected
                pickerTarget = null
            },
        )
    }

    if (showCustomReminder) {
        ReminderPickerDialog(
            currentOffsetMinutes = reminder,
            onDismiss = { showCustomReminder = false },
            onSelect = {
                reminder = it
                showCustomReminder = false
            },
        )
    }

    if (showCustomDuration) {
        AlertDialog(
            onDismissRequest = { showCustomDuration = false },
            title = { Text("自定义持续时间") },
            text = {
                FloatingUnderlineTextField(
                    value = customDurationText,
                    onValueChange = { customDurationText = it.filter(Char::isDigit).take(6) },
                    label = "分钟数",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = "最长可设置 365 天",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        durationMinutes = customDurationText.toIntOrNull()?.coerceIn(1, 525_600)
                        showCustomDuration = false
                    },
                    enabled = customDurationText.toIntOrNull()?.let { it in 1..525_600 } == true,
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCustomDuration = false }) { Text("取消") } },
        )
    }
}

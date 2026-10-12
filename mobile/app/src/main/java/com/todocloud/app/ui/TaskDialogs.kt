package com.todocloud.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TaskDateTimePickerDialog(
    title: String = "设置截止时间",
    initialDateTime: LocalDateTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val initial = remember(initialDateTime) {
        (initialDateTime ?: LocalDateTime.now())
            .withSecond(0)
            .withNano(0)
    }
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINA) }
    val today = LocalDate.now()
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        selectedDate.format(dateFormatter),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "%02d:%02d".format(Locale.US, hour, minute),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.displaySmall,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                            Text("‹", style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(
                            visibleMonth.format(monthFormatter),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                            Text("›", style = MaterialTheme.typography.headlineSmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    repeat(cellCount / 7) { rowIndex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                        ) {
                            repeat(7) { columnIndex ->
                                val cellIndex = rowIndex * 7 + columnIndex
                                val dayNumber = cellIndex - firstDayOffset + 1
                                if (dayNumber !in 1..visibleMonth.lengthOfMonth()) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    val date = visibleMonth.atDay(dayNumber)
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable { selectedDate = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "选择时间",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeAdjuster(
                            label = "小时",
                            value = hour,
                            range = 0..23,
                            onValueChange = { hour = it },
                        )
                        Text(
                            ":",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TimeAdjuster(
                            label = "分钟",
                            value = minute,
                            range = 0..59,
                            onValueChange = { minute = it },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Button(onClick = { onConfirm(selectedDate.atTime(hour, minute)) }) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimeAdjuster(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestValue = rememberUpdatedState(value)
    val stepDistance = with(LocalDensity.current) { 24.dp.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = "$label，可上下滑动调整"
        },
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 72.dp)
                .pointerInput(range) {
                    var dragDistance = 0f
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        dragDistance -= dragAmount
                        var nextValue = latestValue.value
                        while (dragDistance >= stepDistance) {
                            nextValue = if (nextValue == range.last) range.first else nextValue + 1
                            latestOnValueChange.value(nextValue)
                            dragDistance -= stepDistance
                        }
                        while (dragDistance <= -stepDistance) {
                            nextValue = if (nextValue == range.first) range.last else nextValue - 1
                            latestOnValueChange.value(nextValue)
                            dragDistance += stepDistance
                        }
                    }
                }
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%02d".format(Locale.US, value),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun AiCandidatesDialog(
    candidates: List<com.todocloud.app.data.AiTaskCandidate>,
    skippedCount: Int,
    loading: Boolean,
    defaultReminderOffsetMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (List<com.todocloud.app.data.AiTaskCandidate>) -> Unit,
) {
    var selectedIndexes by remember(candidates) { mutableStateOf(candidates.indices.toSet()) }
    var reminderPickerIndex by remember { mutableStateOf<Int?>(null) }
    var reminderOffsets by remember(candidates) {
        mutableStateOf(
            candidates.indices.associateWith { index ->
                candidates[index].dueAt?.let {
                    candidates[index].reminderOffsetMinutes ?: defaultReminderOffsetMinutes
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 识别结果") },
        text = {
            if (candidates.isEmpty()) {
                Text(if (skippedCount > 0) "识别到的任务都已经存在，无需重复创建" else "没有识别到明确的待办事项")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (skippedCount > 0) {
                        Text(
                            "已跳过 $skippedCount 条重复任务",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "请确认要添加的任务：",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(candidates) { index, candidate ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading) {
                                            selectedIndexes = selectedIndexes.toMutableSet().apply {
                                                if (!add(index)) remove(index)
                                            }
                                        }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GreenTaskCheckbox(
                                        checked = index in selectedIndexes,
                                        onCheckedChange = {
                                            selectedIndexes = selectedIndexes.toMutableSet().apply {
                                                if (!add(index)) remove(index)
                                            }
                                        },
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                                candidate.dueAt?.let {
                                                    Text(formatDueAt(it), color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            if (candidate.dueAt != null) {
                                                TextButton(
                                                    onClick = { reminderPickerIndex = index },
                                                    enabled = !loading,
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(32.dp),
                                                ) {
                                                    Text(
                                                        formatReminderOffset(reminderOffsets[index]),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "无截止时间",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        candidate.description?.let {
                                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (candidate.steps.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier.padding(top = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    "识别到 ${candidate.steps.size} 个步骤：",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                candidate.steps.forEachIndexed { stepIndex, step ->
                                                    Text(
                                                        "${stepIndex + 1}. ${step.title}",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                        candidate.sourceText?.let {
                                            Text("原文：$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        candidates.mapIndexedNotNull { index, candidate ->
                            if (index in selectedIndexes) {
                                candidate.copy(reminderOffsetMinutes = reminderOffsets[index])
                            } else {
                                null
                            }
                        },
                    )
                },
                enabled = !loading && selectedIndexes.isNotEmpty(),
            ) { Text(if (loading) "创建中…" else "确认创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )

    reminderPickerIndex?.let { index ->
        ReminderPickerDialog(
            currentOffsetMinutes = reminderOffsets[index],
            onDismiss = { reminderPickerIndex = null },
            onSelect = { offsetMinutes ->
                reminderOffsets = reminderOffsets.toMutableMap().apply {
                    this[index] = offsetMinutes
                }
                reminderPickerIndex = null
            },
        )
    }
}

@Composable
internal fun ReminderPickerDialog(
    currentOffsetMinutes: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val initialOffset = currentOffsetMinutes?.coerceIn(0, 7 * 24 * 60) ?: 0
    var days by remember { mutableStateOf(initialOffset / (24 * 60)) }
    var hours by remember { mutableStateOf((initialOffset % (24 * 60)) / 60) }
    var minutes by remember { mutableStateOf(initialOffset % 60) }

    fun setTime(nextDays: Int = days, nextHours: Int = hours, nextMinutes: Int = minutes) {
        val totalMinutes = (nextDays * 24 * 60 + nextHours * 60 + nextMinutes)
            .coerceIn(0, 7 * 24 * 60)
        days = totalMinutes / (24 * 60)
        hours = (totalMinutes % (24 * 60)) / 60
        minutes = totalMinutes % 60
    }

    val totalMinutes = days * 24 * 60 + hours * 60 + minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提前提醒") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "上下滑动数字，自由设置提醒时间（最长 7 天）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (totalMinutes == 0) "不提醒" else formatReminderOffset(totalMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeAdjuster(
                        label = "天",
                        value = days,
                        range = 0..7,
                        onValueChange = { setTime(nextDays = it) },
                    )
                    Text(
                        ":",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TimeAdjuster(
                        label = "小时",
                        value = hours,
                        range = 0..23,
                        onValueChange = { setTime(nextHours = it) },
                    )
                    Text(
                        ":",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TimeAdjuster(
                        label = "分钟",
                        value = minutes,
                        range = 0..59,
                        onValueChange = { setTime(nextMinutes = it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(totalMinutes.takeIf { it > 0 }) },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

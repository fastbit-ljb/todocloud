package com.todocloud.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import com.todocloud.app.data.TaskItem
import java.time.LocalDate
import java.time.YearMonth

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CalendarScreen(
    paddingValues: PaddingValues,
    tasks: List<TaskItem>,
    requestedDate: String? = null,
    onToggleTask: (TaskItem) -> Unit = {},
    onEditTask: (TaskItem) -> Unit = {},
    onToggleStep: (TaskItem, Int) -> Unit = { _, _ -> },
) {
    var expandedTaskIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val visibleMonth = remember(visibleMonthText) { YearMonth.parse(visibleMonthText) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val tasksByDate = visibleCalendarTasks(tasks).mapNotNull { task ->
        task.dueAt?.let { dueAt -> parseDueDate(dueAt)?.let { date -> date to task } }
    }.groupBy({ it.first }, { it.second })
    val pendingTaskCountsByDate = tasksByDate.mapValues { (_, datedTasks) ->
        datedTasks.count { !it.completed }
    }
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7
    val selectedTasks = tasksByDate[selectedDate].orEmpty().sortedBy { it.dueAt }

    LaunchedEffect(requestedDate) {
        val date = requestedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date != null) {
            visibleMonthText = YearMonth.from(date).toString()
            selectedDateText = date.toString()
        }
    }

    fun moveMonth(delta: Long) {
        val nextMonth = visibleMonth.plusMonths(delta)
        visibleMonthText = nextMonth.toString()
        selectedDateText = nextMonth.atDay(1).toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { moveMonth(-1) }) { Text("‹") }
                Text(
                    "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = { moveMonth(1) }) { Text("›") }
            }
            Text(
                "颜色越深表示当天未完成任务越多，格子下方数字是未完成任务数；今天有未完成任务时使用红色。",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until cellCount).chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { index ->
                            val day = index - firstDayOffset + 1
                            if (day in 1..visibleMonth.lengthOfMonth()) {
                                val date = visibleMonth.atDay(day)
                                val selected = date == selectedDate
                                val taskCount = pendingTaskCountsByDate[date] ?: 0
                                val isToday = date == LocalDate.now()
                                val taskColorFraction = (0.22f + (taskCount - 1).coerceAtLeast(0) * 0.12f)
                                    .coerceAtMost(0.62f)
                                val dayColor = if (taskCount == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    lerp(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primary,
                                        taskColorFraction,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(dayColor)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable { selectedDateText = date.toString() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            day.toString(),
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (taskCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = 22.dp)
                                                    .height(12.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (isToday) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        },
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    taskCount.toString(),
                                                    color = if (isToday) {
                                                        MaterialTheme.colorScheme.onError
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    },
                                                    fontSize = 8.sp,
                                                    lineHeight = 8.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(54.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (selectedTasks.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("这一天没有安排任务", modifier = Modifier.padding(18.dp))
                }
            }
        }
        items(selectedTasks, key = { it.id }) { task ->
            val expanded = task.id in expandedTaskIds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = if (expanded) "收起详情" else "展开详情",
                        onLongClickLabel = "编辑任务",
                        onLongClick = { onEditTask(task) },
                    ) {
                        expandedTaskIds = if (expanded) expandedTaskIds - task.id else expandedTaskIds + task.id
                    },
            ) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    GreenTaskCheckbox(
                        checked = task.completed,
                        onCheckedChange = { onToggleTask(task) },
                    )
                    Column(Modifier.weight(1f).padding(start = 6.dp, top = 10.dp)) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (task.completed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                        )
                        task.dueAt?.let {
                            Text(
                                formatDueAt(it),
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        task.reminderOffsetMinutes?.let {
                            Text(reminderLabel(it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!expanded && task.steps.isNotEmpty()) {
                            Text(
                                "${task.steps.count { it.completed }}/${task.steps.size} 步 · 点开查看",
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (expanded) {
                            task.steps.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleStep(task, index) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GreenTaskCheckbox(
                                        checked = step.completed,
                                        onCheckedChange = { onToggleStep(task, index) },
                                    )
                                    Text(
                                        "${index + 1}. ${step.title}",
                                        modifier = Modifier.padding(start = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            task.description?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onEditTask(task) }) { Text("编辑") }
                        }
                    }
                }
            }
        }
    }
}

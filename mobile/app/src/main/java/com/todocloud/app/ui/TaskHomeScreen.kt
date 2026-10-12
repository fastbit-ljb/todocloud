package com.todocloud.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.TaskItem
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TaskAddSpeedDial(
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManual: () -> Unit,
    onScreenshot: () -> Unit,
    onIdea: () -> Unit,
) {
    val symbolRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(360, easing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)),
        label = "addMenuSymbolRotation",
    )

    Box(modifier = Modifier.size(216.dp)) {
        TaskAddAction(
            icon = Icons.Outlined.EditNote,
            contentDescription = "手动添加任务",
            expanded = expanded,
            enabled = enabled,
            targetX = 0.dp,
            targetY = (-92).dp,
            openDelayMillis = 0,
            closeDelayMillis = 104,
            onClick = onManual,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        TaskAddAction(
            icon = Icons.Outlined.AddPhotoAlternate,
            contentDescription = "从截图识别任务",
            expanded = expanded,
            enabled = enabled,
            targetX = (-72).dp,
            targetY = (-64).dp,
            openDelayMillis = 52,
            closeDelayMillis = 52,
            onClick = onScreenshot,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        TaskAddAction(
            icon = Icons.Outlined.Inbox,
            contentDescription = "快速记录想法",
            expanded = expanded,
            enabled = enabled,
            targetX = (-92).dp,
            targetY = 0.dp,
            openDelayMillis = 104,
            closeDelayMillis = 0,
            onClick = onIdea,
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(66.dp)
                .zIndex(3f)
                .semantics {
                    contentDescription = if (expanded) "收起添加菜单" else "展开添加菜单"
                    role = Role.Button
                },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            val symbolColor = MaterialTheme.colorScheme.onPrimaryContainer
            Canvas(modifier = Modifier.size(30.dp)) {
                val armLength = 10.dp.toPx()
                val lineWidth = 2.6.dp.toPx()
                withTransform({ rotate(symbolRotation, pivot = center) }) {
                    drawLine(
                        color = symbolColor,
                        start = Offset(center.x - armLength, center.y),
                        end = Offset(center.x + armLength, center.y),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = symbolColor,
                        start = Offset(center.x, center.y - armLength),
                        end = Offset(center.x, center.y + armLength),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskAddAction(
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    enabled: Boolean,
    targetX: androidx.compose.ui.unit.Dp,
    targetY: androidx.compose.ui.unit.Dp,
    openDelayMillis: Int,
    closeDelayMillis: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val delayMillis = if (expanded) openDelayMillis else closeDelayMillis
    val xOffset by animateDpAsState(
        targetValue = if (expanded) targetX else 7.dp,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuXOffset",
    )
    val yOffset by animateDpAsState(
        targetValue = if (expanded) targetY else 7.dp,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuYOffset",
    )
    val expansionScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.34f,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuExpansionScale",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = tween(130, easing = UiverseEase),
        label = "addMenuPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 220 else 170,
            delayMillis = delayMillis,
            easing = UiverseEase,
        ),
        label = "addMenuAlpha",
    )

    Box(
        modifier = modifier
            .offset(x = xOffset, y = yOffset)
            .zIndex(2f)
            .size(52.dp)
            .graphicsLayer {
                scaleX = expansionScale * pressScale
                scaleY = expansionScale * pressScale
                this.alpha = alpha * if (enabled) 1f else 0.5f
                shadowElevation = 8.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(
                enabled = expanded && enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                if (expanded) {
                    this.contentDescription = contentDescription
                    role = Role.Button
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
internal fun TaskHomeScreen(
    paddingValues: PaddingValues,
    section: String,
    onSectionChange: (String) -> Unit,
    tasks: List<TaskItem>,
    ideas: List<InboxIdea>,
    loading: Boolean,
    showLoadingAnimation: Boolean,
    error: String?,
    onToggle: (TaskItem, () -> Unit) -> Unit,
    onStepToggle: (TaskItem, Int, () -> Unit) -> Unit,
    onReminderChange: (TaskItem, Int?) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onEditIdea: (InboxIdea) -> Unit,
    onDeleteIdea: (InboxIdea) -> Unit,
    onPlanToday: () -> Unit = {},
    onReplanOverdue: (List<TaskItem>) -> Unit = {},
    searching: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val activeQuery = if (searching) searchQuery else ""
    val filtering = activeQuery.isNotBlank()
    // While searching, every loaded task counts (old completed ones too).
    val homeTasks = remember(tasks, activeQuery) {
        if (filtering) searchTasks(tasks, activeQuery) else visibleHomeTasks(tasks)
    }
    val shownIdeas = remember(ideas, activeQuery) {
        if (filtering) searchIdeas(ideas, activeQuery) else ideas
    }
    val overdue = remember(tasks) { overdueTasks(tasks) }
    val canPlanToday = tasks.any { !it.completed } || ideas.isNotEmpty()
    val taskListState = rememberLazyListState()

    fun prepareViewportForReorder() {
        if (homeTasks.isEmpty()) return
        // Queue the index-based anchor before publishing the reordered list.
        // Doing this after the list changes cancels long-distance placement
        // animations and makes the cards between the two positions snap.
        taskListState.requestScrollToItem(
            index = taskListState.firstVisibleItemIndex.coerceAtMost(homeTasks.lastIndex),
            scrollOffset = taskListState.firstVisibleItemScrollOffset,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            TaskSectionSwitcher(
                selected = section,
                onSelected = onSectionChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (searching) {
                TaskSearchField(
                    query = searchQuery,
                    placeholder = if (section == "inbox") "搜索想法" else "搜索任务、备注和步骤",
                    onQueryChange = onSearchQueryChange,
                    onClose = onCloseSearch,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            if (showLoadingAnimation || error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (showLoadingAnimation) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BouncingTaskLoader()
                        }
                    }
                    error?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (section == "inbox") {
                InboxList(
                    ideas = shownIdeas,
                    loading = loading,
                    noMatchQuery = activeQuery.takeIf { filtering },
                    onEdit = onEditIdea,
                    onDelete = onDeleteIdea,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                state = taskListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Keep the FAB in its easy-to-reach corner while allowing the
                // final task card (and its delete button) to scroll fully above it.
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 4.dp,
                    end = 20.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (filtering && homeTasks.isEmpty()) {
                    item(key = "no-match") { NoSearchMatch(activeQuery) }
                }
                if (!filtering && !loading && (canPlanToday || overdue.isNotEmpty())) {
                    item(key = "plan-shortcuts") {
                        PlanShortcutBar(
                            overdueCount = overdue.size,
                            canPlanToday = canPlanToday,
                            onPlanToday = onPlanToday,
                            onReplanOverdue = { onReplanOverdue(overdue) },
                        )
                    }
                }
                if (!filtering && homeTasks.isEmpty() && !loading) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("从一个小任务开始", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "任务会自动保存到云端，之后可以继续加入 AI 创建和截图解析。",
                                    modifier = Modifier.padding(top = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                itemsIndexed(homeTasks, key = { _, task -> task.id }) { index, task ->
                    TaskCard(
                        modifier = Modifier.animateItem(
                            placementSpec = tween(durationMillis = 560, easing = UiverseEase),
                        ),
                        task = task,
                        entryIndex = index,
                        onToggle = {
                            onToggle(task, ::prepareViewportForReorder)
                        },
                        onStepToggle = { stepIndex ->
                            val nextCompleted = task.steps
                                .mapIndexed { currentIndex, step ->
                                    if (currentIndex == stepIndex) !step.completed else step.completed
                                }
                                .let { nextSteps -> nextSteps.isNotEmpty() && nextSteps.all { it } }
                            onStepToggle(task, stepIndex) {
                                if (nextCompleted != task.completed) {
                                    prepareViewportForReorder()
                                }
                            }
                        },
                        onReminderChange = { offsetMinutes -> onReminderChange(task, offsetMinutes) },
                        onDelete = { onDelete(task) },
                        onEdit = { onEditTask(task) },
                    )
                }
            }
            }
        }
    }
}

@Composable
internal fun PlanShortcutBar(
    overdueCount: Int,
    canPlanToday: Boolean,
    onPlanToday: () -> Unit,
    onReplanOverdue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (overdueCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "有 $overdueCount 件任务已过期",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.TextButton(onClick = onReplanOverdue) {
                        Text("让 AI 重新排", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        if (canPlanToday) {
            androidx.compose.material3.FilledTonalButton(
                onClick = onPlanToday,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("帮我排今天", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun TaskSectionSwitcher(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                Triple("tasks", "任务", Icons.Outlined.Checklist),
                Triple("inbox", "收件匣", Icons.Outlined.Inbox),
            ).forEach { (value, label, icon) ->
                val active = selected == value
                val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) com.todocloud.app.ui.theme.selectionIndicatorColor()
                            else Color.Transparent,
                        )
                        .clickable { onSelected(value) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
                    Text(label, modifier = Modifier.padding(start = 7.dp), color = contentColor)
                }
            }
        }
    }
}

@Composable
internal fun InboxList(
    ideas: List<InboxIdea>,
    loading: Boolean,
    onEdit: (InboxIdea) -> Unit,
    onDelete: (InboxIdea) -> Unit,
    modifier: Modifier = Modifier,
    noMatchQuery: String? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (noMatchQuery != null && ideas.isEmpty()) {
            item(key = "no-match") { NoSearchMatch(noMatchQuery) }
        } else if (ideas.isEmpty() && !loading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(
                            Icons.Outlined.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "先把念头放在这里",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "不必现在决定要不要做。记下一瞬间的灵感，等它值得行动时，再让 AI 帮你整理成计划。",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(ideas, key = { it.id }) { idea ->
            IdeaCard(idea = idea, onEdit = { onEdit(idea) }, onDelete = { onDelete(idea) })
        }
    }
}

@Composable
internal fun IdeaCard(
    idea: InboxIdea,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    idea.content,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                // 删除会立即同步到服务端且无法撤销，先确认，避免误触丢失想法
                var confirmingDelete by remember { mutableStateOf(false) }
                IconButton(onClick = { confirmingDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除想法")
                }
                if (confirmingDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmingDelete = false },
                        title = { Text("删除这条想法？") },
                        text = { Text("删除后无法恢复。") },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmingDelete = false
                                onDelete()
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmingDelete = false }) { Text("取消") }
                        },
                    )
                }
            }
            val timing = buildList {
                idea.durationMinutes?.let { add("持续 ${formatDuration(it)}") }
                idea.startAt?.let { add("最早 ${formatPlanDateTime(it)}") }
                idea.dueAt?.let { add("最晚 ${formatPlanDateTime(it)}") }
            }
            Text(
                timing.ifEmpty { listOf("还没有时间限制") }.joinToString(" · "),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (idea.items.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    idea.items.take(4).forEach { item ->
                        Text("• ${item.content}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (idea.items.size > 4) {
                        Text(
                            "还有 ${idea.items.size - 4} 项细节",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TaskSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(androidx.compose.material.icons.Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { if (query.isEmpty()) onClose() else onQueryChange("") }) {
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.Close,
                    contentDescription = if (query.isEmpty()) "关闭搜索" else "清空搜索",
                )
            }
        },
    )
}

@Composable
internal fun NoSearchMatch(query: String) {
    Text(
        "没有找到包含「${query.trim()}」的内容",
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
internal fun IdeaAddButton(enabled: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(66.dp).graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        val color = MaterialTheme.colorScheme.onPrimaryContainer
        Canvas(Modifier.size(30.dp)) {
            val arm = 10.dp.toPx()
            val width = 2.6.dp.toPx()
            drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), width, StrokeCap.Round)
        }
    }
}

internal fun formatDuration(minutes: Int): String = when {
    minutes >= 24 * 60 && minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} 天"
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "$minutes 分钟"
}

internal fun formatPlanDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
}.getOrElse { value }

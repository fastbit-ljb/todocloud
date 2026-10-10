package com.todocloud.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.DialogProperties
import com.todocloud.app.data.IdeaDetail
import com.todocloud.app.data.InboxIdea
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun IdeaEditorDialog(
    idea: InboxIdea?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int?, String?, String?, List<IdeaDetail>) -> Unit,
) {
    var content by rememberSaveable(idea?.id) { mutableStateOf(idea?.content.orEmpty()) }
    var durationMinutes by rememberSaveable(idea?.id) { mutableStateOf(idea?.durationMinutes) }
    var showCustomDuration by rememberSaveable(idea?.id) { mutableStateOf(false) }
    var customDurationText by rememberSaveable(idea?.id) {
        mutableStateOf(idea?.durationMinutes?.toString().orEmpty())
    }
    var startAt by remember(idea?.id) { mutableStateOf(idea?.startAt?.let(::parseIdeaDateTime)) }
    var dueAt by remember(idea?.id) { mutableStateOf(idea?.dueAt?.let(::parseIdeaDateTime)) }
    var itemTexts by remember(idea?.id) {
        mutableStateOf(idea?.items?.map { it.content }.orEmpty())
    }
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    val windowInvalid = startAt != null && dueAt != null && startAt!!.isAfter(dueAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(if (idea == null) "记下一个想法" else "编辑想法") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingUnderlineTextField(
                    value = content,
                    onValueChange = { content = it.take(10_000) },
                    label = "想法内容",
                    singleLine = false,
                )
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickerTarget = "start" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(startAt?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "选择开始时间")
                    }
                    OutlinedButton(
                        onClick = { pickerTarget = "due" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(dueAt?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "选择截止时间")
                    }
                }
                if (startAt != null || dueAt != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { startAt = null }) { Text("清除开始") }
                        TextButton(onClick = { dueAt = null }) { Text("清除截止") }
                    }
                }
                if (windowInvalid) {
                    Text("开始时间不能晚于截止时间", color = MaterialTheme.colorScheme.error)
                }
                Text("想法子项（可选）", style = MaterialTheme.typography.titleSmall)
                itemTexts.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FloatingUnderlineTextField(
                            value = value,
                            onValueChange = { next ->
                                itemTexts = itemTexts.toMutableList().also { it[index] = next.take(500) }
                            },
                            label = "细节 ${index + 1}",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                itemTexts = itemTexts.toMutableList().also { it.removeAt(index) }
                            },
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除子项")
                        }
                    }
                }
                if (itemTexts.size < 30) {
                    TextButton(onClick = { itemTexts = itemTexts + "" }) { Text("＋ 添加一个细节") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        content.trim(),
                        durationMinutes,
                        startAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                        dueAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                        itemTexts.mapNotNull { text ->
                            text.trim().takeIf(String::isNotBlank)?.let(::IdeaDetail)
                        },
                    )
                },
                enabled = !loading && content.isNotBlank() && !windowInvalid,
            ) { Text("保存到收件匣") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )

    pickerTarget?.let { target ->
        TaskDateTimePickerDialog(
            title = if (target == "start") "设置开始时间" else "设置截止时间",
            initialDateTime = if (target == "start") startAt else dueAt,
            onDismiss = { pickerTarget = null },
            onConfirm = { selected ->
                if (target == "start") startAt = selected else dueAt = selected
                pickerTarget = null
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
            dismissButton = {
                TextButton(onClick = { showCustomDuration = false }) { Text("取消") }
            },
        )
    }
}

internal data class IdeaDurationOption(val minutes: Int?, val label: String)

internal val IDEA_DURATION_OPTIONS = listOf(
    IdeaDurationOption(null, "∞"),
    IdeaDurationOption(15, "15"),
    IdeaDurationOption(30, "30"),
    IdeaDurationOption(45, "45"),
    IdeaDurationOption(60, "1h"),
    IdeaDurationOption(90, "1.5h"),
)

@Composable
internal fun IdeaDurationSelector(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    onMore: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = IDEA_DURATION_OPTIONS.indexOfFirst { it.minutes == value }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentHaptic by rememberUpdatedState(haptic)
    val isCustom = value != null && selectedIndex < 0
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) lastSelectedIndex = selectedIndex
    }

    fun select(index: Int) {
        val next = IDEA_DURATION_OPTIONS[index].minutes
        if (index != lastSelectedIndex) {
            lastSelectedIndex = index
            currentHaptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        if (next != currentValue) {
            currentOnValueChange(next)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预计持续时间", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onMore) {
                Text(if (isCustom) "当前 ${formatDuration(requireNotNull(value))}" else "更多…")
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val selectorWidth = maxWidth
            val slotWidth = selectorWidth / IDEA_DURATION_OPTIONS.size
            val indicatorSize = minOf(slotWidth - 10.dp, 44.dp)
            val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        fun updateFromPointer(x: Float) {
                            val pointerSlotWidthPx = size.width.toFloat() / IDEA_DURATION_OPTIONS.size
                            dragOffsetPx = (x - pointerSlotWidthPx / 2f)
                                .coerceIn(0f, size.width - pointerSlotWidthPx)
                            val index = ((x / size.width) * IDEA_DURATION_OPTIONS.size)
                                .toInt()
                                .coerceIn(0, IDEA_DURATION_OPTIONS.lastIndex)
                            select(index)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            updateFromPointer(down.position.x)
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break
                                    updateFromPointer(change.position.x)
                                    change.consume()
                                }
                            } finally {
                                isDragging = false
                            }
                        }
                    },
            ) {
                val indicatorOffsetPx by animateFloatAsState(
                targetValue = when {
                    selectedIndex < 0 -> 0f
                    isDragging -> dragOffsetPx
                    else -> slotWidthPx * selectedIndex
                },
                animationSpec = if (isDragging) {
                    snap()
                } else {
                    spring(dampingRatio = 0.72f, stiffness = 500f)
                },
                label = "idea-duration-indicator",
            )
            val animatedIndicatorSize by animateDpAsState(
                targetValue = if (isDragging) slotWidth else indicatorSize,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "idea-duration-size",
            )
            val animatedIndicatorCorner by animateDpAsState(
                targetValue = if (isDragging) 16.dp else 9.dp,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
                label = "idea-duration-corner",
            )
            if (selectedIndex >= 0) {
                val horizontalInsetPx = with(LocalDensity.current) { 4.dp.toPx() }
                val innerTrackWidthPx = with(LocalDensity.current) { selectorWidth.toPx() } - horizontalInsetPx * 2f
                val progressWidthPx = if (indicatorOffsetPx <= 0f) {
                    0f
                } else {
                    indicatorOffsetPx + slotWidthPx / 2f - horizontalInsetPx
                }
                val progressFraction = (progressWidthPx / innerTrackWidthPx)
                    .coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(indicatorSize)
                            .clip(RoundedCornerShape(9.dp))
                            .graphicsLayer {
                                scaleX = progressFraction
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    )
                }
            }
            Row(Modifier.fillMaxSize()) {
                IDEA_DURATION_OPTIONS.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { select(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (selectedIndex >= 0) {
                Box(
                    Modifier
                        .graphicsLayer { translationX = indicatorOffsetPx }
                        .width(slotWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(animatedIndicatorSize)
                            .clip(RoundedCornerShape(animatedIndicatorCorner))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            IDEA_DURATION_OPTIONS[selectedIndex].label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        }
        Text(
            if (value == null) "没有时间限制" else "预计 ${formatDuration(value)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun parseIdeaDateTime(value: String): LocalDateTime? = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
}.getOrNull()

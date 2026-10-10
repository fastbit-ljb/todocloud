package com.todocloud.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import kotlinx.coroutines.delay

@Composable
internal fun TaskCard(
    modifier: Modifier = Modifier,
    task: TaskItem,
    entryIndex: Int = 0,
    onToggle: () -> Unit,
    onStepToggle: (Int) -> Unit,
    onReminderChange: (Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(task.id) { mutableStateOf(false) }
    var reminderPickerOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    var titleTextWidth by remember(task.id) { mutableStateOf(0f) }
    var titleHasOverflow by remember(task.id) { mutableStateOf(false) }
    var flyAway by rememberSaveable(task.id) { mutableStateOf(false) }
    var entryStarted by rememberSaveable(task.id) { mutableStateOf(false) }
    val entryProgress by animateFloatAsState(
        targetValue = if (entryStarted) 1f else 0f,
        animationSpec = tween(durationMillis = TASK_ENTRY_DURATION_MILLIS, easing = UiverseEase),
        label = "task card entry",
    )
    val entryDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val flyProgress by animateFloatAsState(
        targetValue = if (flyAway) 1f else 0f,
        animationSpec = tween(durationMillis = CARD_FLY_AWAY_DURATION_MILLIS, easing = UiverseEase),
        label = "task card curved fly away",
    )

    LaunchedEffect(deleting) {
        if (deleting) {
            delay(TRASH_ANIMATION_BEAT_MILLIS)
            flyAway = true
            delay(CARD_FLY_AWAY_DURATION_MILLIS.toLong())
            onDelete()
        }
    }
    LaunchedEffect(task.id) {
        delay(entryIndex * TASK_ENTRY_STAGGER_MILLIS)
        entryStarted = true
    }
    val titleColor by animateColorAsState(
        targetValue = if (task.completed) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 300, easing = UiverseEase),
        label = "task title color",
    )
    val titleShift by animateDpAsState(
        targetValue = if (task.completed) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 300, delayMillis = 100, easing = UiverseEase),
        label = "task title shift",
    )
    val titleStrikeProgress by animateFloatAsState(
        targetValue = if (task.completed) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = UiverseEase),
        label = "task title strike",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = entryProgress.coerceIn(0f, 1f)
                val inverse = 1f - progress
                val eased = 1f - inverse * inverse * inverse
                translationX = entryDistance * (1f - eased)
                translationY = -12.dp.toPx() * (1f - eased)
                alpha = eased
                scaleX = 0.96f + 0.04f * eased
                scaleY = 0.96f + 0.04f * eased
                rotationZ = 0f
            },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val progress = flyProgress.coerceIn(0f, 1f)
                    val inverse = 1f - progress
                    val eased = 1f - inverse * inverse * inverse
                    val cardWidth = size.width
                    val cardHeight = size.height
                    val endX = cardWidth * 0.72f
                    val endY = -cardHeight * 1.45f
                    val controlX = cardWidth * 0.62f
                    val controlY = -cardHeight * 1.25f
                    translationX = inverse * inverse * 0f +
                        2f * inverse * progress * controlX + progress * progress * endX
                    translationY = inverse * inverse * 0f +
                        2f * inverse * progress * controlY + progress * progress * endY
                    val fadeProgress = ((eased - 0.24f) / 0.76f).coerceIn(0f, 1f)
                    alpha = 1f - fadeProgress * fadeProgress
                    scaleX = 1f - 0.92f * eased
                    scaleY = 1f - 0.92f * eased
                    // Keep the card level while it follows the curved path.
                    rotationZ = 0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .clickable(
                    enabled = !deleting && (task.steps.isNotEmpty() || titleHasOverflow),
                ) {
                    if (task.steps.isNotEmpty() || titleHasOverflow) {
                        expanded = !expanded
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GreenTaskCheckbox(
                    checked = task.completed,
                    onCheckedChange = { onToggle() },
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layoutResult ->
                                titleHasOverflow = layoutResult.hasVisualOverflow
                                val measuredWidth = if (layoutResult.lineCount > 0) {
                                    layoutResult.getLineRight(0)
                                } else {
                                    0f
                                }
                                if (titleTextWidth != measuredWidth) {
                                    titleTextWidth = measuredWidth
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .offset(x = titleShift)
                                .drawWithContent {
                                    drawContent()
                                    if (titleStrikeProgress > 0f && titleTextWidth > 0f) {
                                        // Use the measured text width, not the
                                        // weighted column width, so the line never
                                        // extends past the last character.
                                        val textWidth = titleTextWidth.coerceAtMost(size.width)
                                        val lineWidth = if (titleStrikeProgress < 0.6f) {
                                            val shortMark = 8.dp.toPx().coerceAtMost(textWidth)
                                            shortMark + (textWidth - shortMark) *
                                                (titleStrikeProgress / 0.6f)
                                        } else {
                                            textWidth
                                        }
                                        drawLine(
                                            color = titleColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.54f),
                                            end = androidx.compose.ui.geometry.Offset(
                                                lineWidth,
                                                size.height * 0.54f,
                                            ),
                                            strokeWidth = 1.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                },
                        )
                        task.dueAt?.let {
                            Text(
                                formatDueAt(it),
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    if (task.steps.isNotEmpty()) {
                        TaskStepProgress(steps = task.steps)
                    }
                    if (expanded) {
                        if (task.steps.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                task.steps.forEachIndexed { index, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !deleting) { onStepToggle(index) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        GreenTaskCheckbox(
                                            checked = step.completed,
                                            onCheckedChange = { onStepToggle(index) },
                                        )
                                        Text(
                                            text = "${index + 1}. ${step.title}",
                                            modifier = Modifier.padding(start = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (task.steps.isEmpty() && task.description.isNullOrBlank()) {
                            Text(
                                "这个任务还没有识别出步骤",
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        task.description?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    task.dueAt?.let {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = { reminderPickerOpen = true },
                                enabled = !deleting,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = "设置提醒时间",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    AnimatedTrashButton(
                        deleting = deleting,
                        onDeleteRequested = { deleting = true },
                    )
                }
            }
        }
        if (reminderPickerOpen) {
            ReminderPickerDialog(
                currentOffsetMinutes = task.reminderOffsetMinutes,
                onDismiss = { reminderPickerOpen = false },
                onSelect = { offsetMinutes ->
                    reminderPickerOpen = false
                    onReminderChange(offsetMinutes)
                },
            )
        }
    }
}

@Composable
internal fun TaskStepProgress(steps: List<TaskStep>) {
    val completedCount = steps.count { it.completed }
    val activeColor = MaterialTheme.colorScheme.primary
    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val currentColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                val isCurrent = !step.completed && steps.take(index).all { it.completed }
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 10.dp)
                        .background(
                            color = when {
                                step.completed -> activeColor
                                isCurrent -> currentColor
                                else -> pendingColor
                            },
                            shape = RoundedCornerShape(50),
                        ),
                )
                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                color = if (step.completed) activeColor else pendingColor,
                                shape = RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
        Text(
            text = "已完成 $completedCount/${steps.size} 步",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
        steps.firstOrNull { !it.completed }?.let { currentStep ->
            Text(
                text = "下一步：${currentStep.title}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AnimatedTrashButton(
    deleting: Boolean,
    onDeleteRequested: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val hoverProgress by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = UiverseEase),
        label = "trash hover lid",
    )
    val deleteProgress by animateFloatAsState(
        targetValue = if (deleting) 1f else 0f,
        animationSpec = tween(durationMillis = CARD_SHATTER_DURATION_MILLIS, easing = UiverseEase),
        label = "trash delete animation",
    )
    val lidProgress = if (!deleting) {
        hoverProgress
    } else {
        when {
            deleteProgress < 0.16f -> deleteProgress / 0.16f
            deleteProgress < 0.66f -> 1f
            deleteProgress < 0.86f -> 1f - (deleteProgress - 0.66f) / 0.20f
            else -> 0f
        }
    }
    val itemProgress = ((deleteProgress - 0.16f) / 0.42f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(48.dp)
            .hoverable(hoverSource)
            .clickable(
                enabled = !deleting,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDeleteRequested,
            )
            .semantics {
                contentDescription = "删除任务"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(28.dp)) {
            val stroke = 1.8.dp.toPx()
            val pivot = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.30f)
            val lidAngle = -24f * lidProgress

            if (deleting && itemProgress > 0f && itemProgress < 1f) {
                val paperAlpha = (1f - itemProgress).coerceIn(0f, 1f)
                drawRoundRect(
                    color = iconColor.copy(alpha = paperAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        size.width * 0.40f,
                        size.height * (0.05f + 0.35f * itemProgress),
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.20f, size.height * 0.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
            withTransform({
                rotate(lidAngle, pivot)
            }) {
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.28f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.14f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.14f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            drawRoundRect(
                color = iconColor,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.32f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.45f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.45f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

internal const val CARD_SHATTER_DURATION_MILLIS = 980

internal data class ShatterPiece(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val driftX: Float,
    val driftY: Float,
    val rotation: Float,
    val delay: Float,
    val jaggedness: Float,
    val dust: Boolean,
)

internal fun shardRandom(index: Int, salt: Int): Float {
    val seed = (index + 1) * 1103515245 + (salt + 17) * 12345
    return ((seed ushr 8) and 0xFFFF) / 65535f
}

internal fun createShatterPieces(): List<ShatterPiece> {
    val columns = 11
    val rows = 6
    return buildList {
        var index = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cellWidth = 1f / columns
                val cellHeight = 1f / rows
                val centerX = (column + 0.5f) * cellWidth +
                    (shardRandom(index, 1) - 0.5f) * cellWidth * 0.18f
                val centerY = (row + 0.5f) * cellHeight +
                    (shardRandom(index, 2) - 0.5f) * cellHeight * 0.18f
                add(
                    ShatterPiece(
                        centerX = centerX,
                        centerY = centerY,
                        width = cellWidth * (0.84f + shardRandom(index, 3) * 0.10f),
                        height = cellHeight * (0.84f + shardRandom(index, 4) * 0.10f),
                        driftX = (centerX - 0.5f) * 1.7f +
                            (shardRandom(index, 5) - 0.5f) * 0.22f,
                        driftY = (centerY - 0.5f) * 0.92f +
                            (shardRandom(index, 6) - 0.5f) * 0.20f,
                        rotation = (shardRandom(index, 7) - 0.5f) * 28f,
                        delay = shardRandom(index, 8) * 0.12f,
                        jaggedness = shardRandom(index, 9),
                        dust = false,
                    ),
                )
                index += 1
            }
        }

        // Small edge fragments make the break feel granular rather than like
        // a clean six-by-eleven grid.
        repeat(30) { particleIndex ->
            val randomX = shardRandom(particleIndex, 21)
            val randomY = shardRandom(particleIndex, 22)
            add(
                ShatterPiece(
                    centerX = randomX,
                    centerY = randomY,
                    width = 0.014f + shardRandom(particleIndex, 23) * 0.032f,
                    height = 0.020f + shardRandom(particleIndex, 24) * 0.048f,
                    driftX = (randomX - 0.5f) * 2.2f +
                        (shardRandom(particleIndex, 25) - 0.5f) * 0.60f,
                    driftY = (randomY - 0.5f) * 1.55f +
                        (shardRandom(particleIndex, 26) - 0.5f) * 0.48f,
                    rotation = (shardRandom(particleIndex, 27) - 0.5f) * 180f,
                    delay = 0.07f + shardRandom(particleIndex, 28) * 0.20f,
                    jaggedness = shardRandom(particleIndex, 29),
                    dust = true,
                ),
            )
        }
    }
}

internal fun shardPath(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    jaggedness: Float,
): Path = Path().apply {
    val notch = halfWidth * (0.16f + jaggedness * 0.18f)
    moveTo(centerX - halfWidth, centerY - halfHeight * 0.86f)
    lineTo(centerX - notch, centerY - halfHeight)
    lineTo(centerX + halfWidth * 0.92f, centerY - halfHeight * 0.82f)
    lineTo(centerX + halfWidth, centerY + halfHeight * 0.12f)
    lineTo(centerX + halfWidth * 0.32f, centerY + halfHeight)
    lineTo(centerX - halfWidth * 0.46f, centerY + halfHeight * 0.80f)
    lineTo(centerX - halfWidth, centerY + halfHeight * 0.08f)
    close()
}

@Composable
internal fun PageFlyAwayOverlay(
    request: CardShatterRequest,
    rootBounds: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalDuration = CARD_FLY_AWAY_DURATION_MILLIS
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = totalDuration, easing = UiverseEase),
        label = "card curved fly away",
    )

    LaunchedEffect(request.task.id) {
        delay(totalDuration.toLong())
        onFinished()
    }

    Canvas(modifier = modifier) {
        val startLeft = request.boundsInRoot.left - rootBounds.left
        val startTop = request.boundsInRoot.top - rootBounds.top
        val cardWidth = request.boundsInRoot.width
        val cardHeight = request.boundsInRoot.height
        val endLeft = size.width + cardWidth * 0.30f
        val endTop = -cardHeight * 1.25f
        val controlLeft = startLeft + (endLeft - startLeft) * 0.48f - cardWidth * 0.12f
        val controlTop = startTop - cardHeight * 1.35f
        val curveProgress = progress.coerceIn(0f, 1f)
        val inverse = 1f - curveProgress
        val left = inverse * inverse * startLeft +
            2f * inverse * curveProgress * controlLeft +
            curveProgress * curveProgress * endLeft
        val top = inverse * inverse * startTop +
            2f * inverse * curveProgress * controlTop +
            curveProgress * curveProgress * endTop
        val eased = 1f - inverse * inverse * inverse
        val fadeProgress = ((eased - 0.34f) / 0.66f).coerceIn(0f, 1f)
        val alpha = 1f - fadeProgress * fadeProgress
        val scale = 1f - 0.94f * eased
        val rotation = -4f + 18f * eased
        val center = androidx.compose.ui.geometry.Offset(cardWidth * 0.5f, cardHeight * 0.5f)
        val previousAlpha = request.cardLayer.alpha
        request.cardLayer.alpha = alpha

        withTransform({
            translate(left = left, top = top)
            rotate(degrees = rotation, pivot = center)
            scale(scaleX = scale, scaleY = scale, pivot = center)
        }) {
            drawLayer(request.cardLayer)
        }
        request.cardLayer.alpha = previousAlpha
    }
}

@Composable
internal fun ShatterCardOverlay(
    progress: Float,
    cardLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val pieces = remember { createShatterPieces() }
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (progress < 0.08f) {
            // Keep the card whole for the first beat, then let the cracks open.
            drawLayer(cardLayer)
            if (progress > 0.025f) {
                val crackAlpha = ((progress - 0.025f) / 0.055f).coerceIn(0f, 1f)
                val crackWidth = 1.dp.toPx()
                drawLine(
                    color = accent.copy(alpha = crackAlpha * 0.72f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.08f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.54f),
                    strokeWidth = crackWidth,
                )
                drawLine(
                    color = accent.copy(alpha = crackAlpha * 0.56f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.54f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.88f),
                    strokeWidth = crackWidth,
                )
            }
            return@Canvas
        }

        pieces.forEachIndexed { index, piece ->
            val localProgress = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@forEachIndexed

            val eased = 1f - (1f - localProgress) * (1f - localProgress) * (1f - localProgress)
            val fadeProgress = ((localProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val alpha = 1f - fadeProgress * fadeProgress
            val center = androidx.compose.ui.geometry.Offset(
                piece.centerX * size.width,
                piece.centerY * size.height,
            )
            val halfWidth = size.width * piece.width * 0.5f
            val halfHeight = size.height * piece.height * 0.5f
            val path = shardPath(
                centerX = center.x,
                centerY = center.y,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                jaggedness = piece.jaggedness,
            )
            val xOffset = piece.driftX * size.width * (0.08f + 0.92f * eased)
            val yOffset = piece.driftY * size.height * (0.08f + 0.92f * eased) +
                size.height * 0.32f * eased * eased
            val rotation = piece.rotation + (if (index % 2 == 0) 1f else -1f) * 125f * eased
            val scale = 1f - 0.50f * eased
            val previousAlpha = cardLayer.alpha
            cardLayer.alpha = alpha

            withTransform({
                translate(left = xOffset, top = yOffset)
                rotate(degrees = rotation, pivot = center)
                scale(scaleX = scale, scaleY = scale, pivot = center)
            }) {
                clipPath(path) {
                    drawLayer(cardLayer)
                }

                if (piece.dust) {
                    drawPath(
                        path = path,
                        color = surface.copy(alpha = alpha * 0.52f),
                    )
                } else {
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = alpha * 0.15f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.7.dp.toPx()),
                    )
                }
            }
            cardLayer.alpha = previousAlpha
        }
    }
}

@Composable
// Adapted from JkHuger's Uiverse checkbox (MIT License):
// https://uiverse.io/JkHuger/warm-panther-74
@OptIn(ExperimentalFoundationApi::class)
internal fun GreenTaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = UiverseEase),
        label = "task checkbox check",
    )
    val fireworkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100, easing = UiverseEase),
        label = "task checkbox firework",
    )
    val strokeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (checked) "标记为未完成" else "标记为完成"
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = null,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp).padding(3.dp)) {
            val checkStroke = 2.dp.toPx()

            // Uiverse's two pseudo-elements stay at width 0 for the first
            // half of the 400 ms animation, then draw both sides of the tick
            // together during the second half.
            val checkSegmentProgress = ((checkProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val initialSegmentProgress = (1f - checkProgress / 0.5f).coerceIn(0f, 1f)
            val left = size.width * 0.22f
            val middle = size.width * 0.43f
            val bottom = size.height * 0.66f
            val right = size.width * 0.80f
            val top = size.height * 0.30f
            val fireworkCenter = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.50f,
                y = size.height * 0.50f,
            )

            if (checkProgress < 1f) {
                // The source uses label::before { width: 8px }.
                val dashWidth = 8.dp.toPx()
                drawLine(
                    color = strokeColor.copy(alpha = 1f - checkProgress),
                    start = androidx.compose.ui.geometry.Offset(
                        (size.width - dashWidth) / 2f,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        (size.width + dashWidth) / 2f,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (checked && initialSegmentProgress > 0f) {
                val initialLength = 4.dp.toPx() * initialSegmentProgress
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(
                        middle - initialLength,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        middle,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(
                        middle,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        middle + initialLength,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (fireworkProgress > 0f) {
                val directions = listOf(
                    0f to -15f,
                    14f to -8f,
                    14f to 8f,
                    0f to 15f,
                    -14f to 8f,
                    -14f to -8f,
                )
                directions.forEach { (xDirection, yDirection) ->
                    val particleProgress = fireworkProgress
                    val fireworkOpacity = if (particleProgress <= 0.3f) {
                        1f
                    } else {
                        1f - ((particleProgress - 0.3f) / 0.7f)
                    }
                    drawCircle(
                        color = strokeColor.copy(alpha = fireworkOpacity),
                        radius = 2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            fireworkCenter.x + xDirection * fireworkProgress * 1.dp.toPx(),
                            fireworkCenter.y + yDirection * fireworkProgress * 1.dp.toPx(),
                        ),
                    )
                }
            }
            if (checkSegmentProgress > 0f) {
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(left, size.height * 0.50f),
                    end = androidx.compose.ui.geometry.Offset(
                        left + (middle - left) * checkSegmentProgress,
                        size.height * 0.50f + (bottom - size.height * 0.50f) * checkSegmentProgress,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (checkSegmentProgress > 0f) {
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(middle, bottom),
                    end = androidx.compose.ui.geometry.Offset(
                        middle + (right - middle) * checkSegmentProgress,
                        bottom + (top - bottom) * checkSegmentProgress,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun ThemeCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "请输入或粘贴需要提取的内容",
) {
    var focused by remember { mutableStateOf(false) }
    // Keep the theme tint visible, but soften it so the input feels lighter
    // than task cards and remains comfortable behind multi-line text.
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val activeColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) activeColor else contentColor.copy(alpha = 0.28f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp)
                    .padding(top = 8.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                cursorBrush = SolidColor(activeColor),
                singleLine = false,
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                placeholder,
                                color = contentColor.copy(alpha = 0.62f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

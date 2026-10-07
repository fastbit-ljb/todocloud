package com.todocloud.app.branding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todocloud.app.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MascotTransitionHost(
    initialTransition: MascotTransition?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var transition by remember { mutableStateOf(initialTransition) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && transition == null) {
                transition = MascotStateManager.consumePendingTransition(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        transition?.let { current ->
            MascotTransitionOverlay(
                transition = current,
                onFinished = { transition = null },
            )
        }
    }
}

@Composable
private fun MascotTransitionOverlay(
    transition: MascotTransition,
    onFinished: () -> Unit,
) {
    val progress = remember(transition) { Animatable(0f) }
    val overlayAlpha = remember(transition) { Animatable(1f) }

    LaunchedEffect(transition) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
        delay(120)
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 220),
        )
        onFinished()
    }

    val colors = MaterialTheme.colorScheme
    val accent = mascotAccent(transition.to)
    val eased = FastOutSlowInEasing.transform(progress.value)
    val oldAlpha = (1f - eased * 1.7f).coerceIn(0f, 1f) * overlayAlpha.value
    val newAlpha = ((eased - 0.12f) / 0.66f).coerceIn(0f, 1f) * overlayAlpha.value
    val coldProgress = when {
        transition.to == MascotState.HALF_FROZEN || transition.to == MascotState.FROZEN -> eased
        transition.from == MascotState.HALF_FROZEN || transition.from == MascotState.FROZEN -> 1f - eased
        else -> 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(colors.background.copy(alpha = overlayAlpha.value))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val glowRadius = size.minDimension * (0.18f + eased * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.22f * overlayAlpha.value),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )

            if (coldProgress > 0f) {
                repeat(12) { index ->
                    val angle = (index / 12f) * (2f * PI).toFloat() + eased * 0.45f
                    val distance = size.minDimension * (0.15f + (index % 3) * 0.035f)
                    val particleCenter = center.copy(
                        x = center.x + cos(angle) * distance,
                        y = center.y + sin(angle) * distance,
                    )
                    drawCircle(
                        color = Color(0xFF8DDCFF).copy(
                            alpha = coldProgress * overlayAlpha.value * 0.72f,
                        ),
                        radius = size.minDimension * (0.0045f + (index % 2) * 0.002f),
                        center = particleCenter,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(224.dp)
                .graphicsLayer {
                    alpha = overlayAlpha.value
                    scaleX = 0.94f + eased * 0.06f
                    scaleY = 0.94f + eased * 0.06f
                }
                .shadow(18.dp, RoundedCornerShape(58.dp))
                .clip(RoundedCornerShape(58.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.primaryContainer,
                            accent.copy(alpha = 0.32f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(transition.from.drawableResource()),
                contentDescription = null,
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        alpha = oldAlpha
                        scaleX = 1f + eased * 0.12f
                        scaleY = 1f + eased * 0.12f
                        rotationZ = -4f * eased
                    },
            )
            Image(
                painter = painterResource(transition.to.drawableResource()),
                contentDescription = null,
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        alpha = newAlpha
                        scaleX = 0.76f + eased * 0.24f
                        scaleY = 0.76f + eased * 0.24f
                        rotationZ = 4f * (1f - eased)
                    },
            )
        }
    }
}

@Composable
private fun mascotAccent(state: MascotState): Color = when (state) {
    MascotState.DUE_SOON -> Color(0xFFFFA454)
    MascotState.OVERDUE -> MaterialTheme.colorScheme.error
    MascotState.HALF_FROZEN,
    MascotState.FROZEN,
    -> Color(0xFF63C9F2)
    else -> MaterialTheme.colorScheme.primary
}

private fun MascotState.drawableResource(): Int = when (this) {
    MascotState.DAILY -> R.drawable.mascot_daily
    MascotState.IN_PROGRESS -> R.drawable.mascot_progress
    MascotState.DUE_SOON -> R.drawable.mascot_due_soon
    MascotState.OVERDUE -> R.drawable.mascot_overdue
    MascotState.COMPLETE -> R.drawable.mascot_complete
    MascotState.NO_TASKS -> R.drawable.mascot_no_tasks
    MascotState.HALF_FROZEN -> R.drawable.mascot_half_frozen
    MascotState.FROZEN -> R.drawable.mascot_frozen
}

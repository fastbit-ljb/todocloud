package com.todocloud.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal const val TASK_SECTION_TRANSITION_MILLIS = 180

/** Only the current list is composed; interrupted switches discard the old animation. */
@Composable
internal fun TaskSectionTransition(
    section: String,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    content: @Composable () -> Unit,
) {
    val progress = remember(section, animationsEnabled) { Animatable(if (animationsEnabled) 0f else 1f) }
    LaunchedEffect(section, animationsEnabled) {
        if (animationsEnabled) {
            progress.animateTo(1f, tween(TASK_SECTION_TRANSITION_MILLIS, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier.fillMaxWidth().testTag("task-section-content").graphicsLayer {
            alpha = 0.82f + 0.18f * progress.value
            translationX = (if (section == "inbox") 8.dp else (-8).dp).toPx() * (1f - progress.value)
        },
    ) {
        content()
    }
}

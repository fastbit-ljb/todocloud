package com.todocloud.app.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.TaskItem
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The one flame shape used everywhere: the calendar's past-day flames (which
 * change colour) and the streak counter (which carries a number). A solid
 * shape with no cut-out, so a number stays readable on its round lower half.
 */
internal val FlameIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Flame",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M12 1.6C13.4 4.6 18.4 7.6 19.4 12.6C20.4 17.9 16.8 22.4 12 22.4" +
                "C7.2 22.4 3.6 18.9 4.5 13.8C5 11.1 6.5 9.4 7.6 8.1" +
                "C7.9 9.8 8.7 11 9.9 11.5C9.4 7.7 10.5 4.3 12 1.6Z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

// Fixed colours: the user asked for red and ice-blue flames, so they do not
// follow the system theme colour (same reasoning as the fixed error red).
internal val FlameRed = Color(0xFFF0473A)
// The streak counter at 0: no streak yet, so the flame is unlit. Dark enough for the
// white "0" to stay readable, light enough to show on a dark top bar.
internal val FlameUnlitGrey = Color(0xFF868B90)
private val FlameRedLight = Color(0xFFFF8A7A)
private val FlameRedDeep = Color(0xFFB3140C)
private val FlameBlueLight = Color(0xFF7FD3F7)
private val FlameBlueDeep = Color(0xFF0A5DA8)

/** 0 for one task, rising in four steps to 1 at five or more tasks. */
internal fun flameDepth(count: Int): Float = (count - 1).coerceIn(0, 4) / 4f

/**
 * Calendar flame colour: the more tasks a fully-done day had, the deeper the
 * red; the more tasks a day left open, the deeper the ice blue.
 */
internal fun calendarDayFlameColor(flame: CalendarDayFlame, count: Int): Color = when (flame) {
    CalendarDayFlame.ALL_DONE -> lerp(FlameRedLight, FlameRedDeep, flameDepth(count))
    CalendarDayFlame.UNFINISHED -> lerp(FlameBlueLight, FlameBlueDeep, flameDepth(count))
}

/**
 * Streak = consecutive red-flame days: days with tasks due, every one of them
 * completed, counted back from today. Days with no tasks due are skipped (they
 * neither add nor break). Today counts as soon as all of today's tasks are done
 * and drops out again while any of them is open, without breaking the streak
 * built before it; any earlier day with an open task ends the streak.
 */
internal fun flameStreak(tasks: List<TaskItem>, today: LocalDate): Int {
    val tasksByDate = visibleCalendarTasks(tasks).mapNotNull { task ->
        task.dueAt?.let(::parseDueDate)?.let { date -> date to task }
    }.groupBy({ it.first }, { it.second })
    var streak = 0
    for (date in tasksByDate.keys.filter { !it.isAfter(today) }.sortedDescending()) {
        when {
            tasksByDate.getValue(date).all { it.completed } -> streak++
            date == today -> Unit
            else -> break
        }
    }
    return streak
}

/** Counter colour: grey while there is no streak, the fixed red from 1 up. */
internal fun flameStreakColor(count: Int): Color = if (count > 0) FlameRed else FlameUnlitGrey

/**
 * The streak counter: the flame with the count inside, red from 1 up and grey
 * at 0. When
 * the count changes it plays a short animation -- a bounce and a rising "+1"
 * when it goes up, a shake and a falling "-1" when it goes down. [lastShown]
 * is held by the caller so a change made while the counter was off screen
 * still animates the next time it appears; the first value after loading
 * ([animateChanges] false until then) is shown without animation. Skipped
 * when the system animator duration scale is 0.
 */
@Composable
internal fun FlameStreakBadge(
    count: Int,
    animateChanges: Boolean,
    lastShown: MutableState<Int?>,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motionOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) } // 0 -> 1 while the +1 / -1 label plays
    var burstDelta by remember { mutableIntStateOf(0) }
    // Fades between grey and red as the streak starts or ends, alongside the bounce/shake.
    val tint by animateColorAsState(
        targetValue = flameStreakColor(count),
        // Snapped, not faded, for the first count after loading (see lastShown below).
        animationSpec = tween(durationMillis = if (motionOff || lastShown.value == null) 0 else 300),
        label = "flameStreakTint",
    )

    LaunchedEffect(count, animateChanges) {
        // A restarted effect must not leave a half-played animation behind.
        scale.snapTo(1f)
        shake.snapTo(0f)
        burst.snapTo(1f)
        if (!animateChanges) return@LaunchedEffect
        val before = lastShown.value
        lastShown.value = count
        if (before == null || before == count || motionOff) return@LaunchedEffect
        burstDelta = count - before
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(durationMillis = 900))
        }
        if (count > before) {
            scale.animateTo(1.4f, tween(durationMillis = 160))
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        } else {
            launch {
                scale.animateTo(0.8f, tween(durationMillis = 140))
                scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            }
            for (x in listOf(-4f, 4f, -3f, 3f, -1.5f, 1.5f, 0f)) {
                shake.animateTo(x, tween(durationMillis = 45))
            }
        }
    }

    val clickModifier = if (onClick != null) Modifier.clickable(onClickLabel = "查看日历", onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .size(48.dp)
            .then(clickModifier)
            .semantics { contentDescription = "连续完成 $count 天" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = shake.value.dp.toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(FlameIcon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    } else {
                        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                    }
                },
                // The number sits on the flame's round lower half.
                modifier = Modifier.offset(y = size * 0.1f),
                label = "flameStreakNumber",
            ) { value ->
                val digits = value.toString().length
                // Sized from the flame in dp, so a large system font cannot push
                // the number outside the flame.
                val fontSize = with(density) {
                    (size * when {
                        digits >= 3 -> 0.26f
                        digits == 2 -> 0.32f
                        else -> 0.36f
                    }).toSp()
                }
                Text(
                    value.toString(),
                    color = Color.White,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (burst.value < 1f && burstDelta != 0) {
            Text(
                if (burstDelta > 0) "+$burstDelta" else "$burstDelta",
                color = if (burstDelta > 0) FlameRed else FlameBlueDeep,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { (size * 0.36f).toSp() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (if (burstDelta > 0) -10f else 10f).dp * burst.value)
                    .alpha(1f - burst.value),
            )
        }
    }
}

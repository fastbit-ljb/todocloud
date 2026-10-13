package com.todocloud.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todocloud.app.branding.MascotSet
import com.todocloud.app.branding.MascotSets
import com.todocloud.app.branding.MascotState
import kotlin.math.roundToInt

/**
 * Settings → 吉祥物: one preview card per entry in [MascotSets.all]. Every card has the same
 * fixed layout (a 64dp launcher icon and eight 56dp pictures), so switching sets or a state
 * change never moves anything on the page.
 */
@Composable
internal fun MascotSettingsScreen(
    paddingValues: PaddingValues,
    selected: MascotSet,
    shownState: MascotState,
    onSelect: (MascotSet) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .selectableGroup(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "吉祥物显示在桌面图标上，表情跟着任务状态变化。选择后立即保存，回到桌面就会换成新图标。",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(MascotSets.all, key = { it.id }) { set ->
            MascotSetCard(
                set = set,
                selected = set.id == selected.id,
                shownState = shownState,
                onClick = { onSelect(set) },
            )
        }
    }
}

@Composable
private fun MascotSetCard(
    set: MascotSet,
    selected: Boolean,
    shownState: MascotState,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        // Same width whether selected or not, so selecting only changes the colour.
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LauncherIconPreview(set.launcherIcons[shownState], Modifier.size(64.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(set.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "桌面图标预览 · ${shownState.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
            MascotSets.previewOrder.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { state ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Image(
                                painter = painterResource(set.pictures[state]),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text(
                                state.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws a launcher icon the way the system composes it: the shared background, the picture at
 * the shared inset, clipped by this device's icon mask.
 */
@Composable
private fun LauncherIconPreview(iconRes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context, iconRes) { context.getDrawable(iconRes) }
    Canvas(modifier) {
        val drawable = icon ?: return@Canvas
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
    }
}

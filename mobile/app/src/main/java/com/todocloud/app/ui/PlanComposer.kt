package com.todocloud.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.ConversationReference

/** One writing surface for prompts and revisions; attachments appear only when selected. */
@Composable
internal fun PlanComposer(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    placeholder: String,
    submitLabel: String,
    modifier: Modifier = Modifier,
    references: List<ConversationReference> = emptyList(),
    images: List<Uri> = emptyList(),
    onReference: (() -> Unit)? = null,
    onRemoveReference: (String) -> Unit = {},
    onAddImages: (() -> Unit)? = null,
    onRemoveImage: (Uri) -> Unit = {},
    ideaCount: Int = 0,
    selectedIdeaCount: Int = 0,
    onInboxClick: (() -> Unit)? = null,
) {
    var toolsExpanded by remember { mutableStateOf(false) }
    val inputInteraction = remember { MutableInteractionSource() }
    val inputFocused by inputInteraction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(if (inputFocused) 2.dp else 1.dp, if (inputFocused) colors.primary else colors.outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (references.isNotEmpty()) {
                ConversationReferenceBar(
                    references = references,
                    busy = loading,
                    onAdd = { onReference?.invoke() },
                    onRemove = onRemoveReference,
                    showAddButton = false,
                )
            }
            if (images.isNotEmpty()) {
                PlanImageBar(
                    images = images,
                    loading = loading,
                    onAdd = { onAddImages?.invoke() },
                    onRemove = onRemoveImage,
                    showAddButton = false,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = !loading,
                interactionSource = inputInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .semantics { contentDescription = placeholder },
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSubmit) onSubmit() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onReference != null || onAddImages != null || onInboxClick != null) {
                    Box {
                        IconButton(onClick = { toolsExpanded = true }, enabled = !loading) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加内容", tint = colors.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = toolsExpanded, onDismissRequest = { toolsExpanded = false }) {
                            if (onAddImages != null) {
                                DropdownMenuItem(
                                    text = { Text("添加图片") },
                                    leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                    enabled = !loading && images.size < MAX_PLAN_IMAGES,
                                    onClick = { toolsExpanded = false; onAddImages() },
                                )
                            }
                            if (onReference != null) {
                                DropdownMenuItem(
                                    text = { Text("引用会话") },
                                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null) },
                                    enabled = !loading && references.size < 3,
                                    onClick = { toolsExpanded = false; onReference() },
                                )
                            }
                            if (onInboxClick != null) {
                                DropdownMenuItem(
                                    text = { Text(if (ideaCount == 0) "记录收件匣想法" else "选择收件匣想法") },
                                    leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                                    onClick = { toolsExpanded = false; onInboxClick() },
                                    enabled = !loading,
                                )
                            }
                        }
                    }
                }
                if (ideaCount > 0 && onInboxClick != null) {
                    TextButton(
                        onClick = onInboxClick,
                        enabled = !loading,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "收件匣已选 $selectedIdeaCount 个想法，共 $ideaCount 个"
                        },
                    ) {
                        Text("想法 · $selectedIdeaCount", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(
                    onClick = onSubmit,
                    enabled = canSubmit,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (canSubmit) colors.primary else colors.surfaceContainerHighest, CircleShape),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = "AI 正在思考" },
                            strokeWidth = 2.dp,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = submitLabel,
                            tint = if (canSubmit) colors.onPrimary else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

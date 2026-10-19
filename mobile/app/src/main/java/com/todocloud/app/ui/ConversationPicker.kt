package com.todocloud.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.ConversationReference
import com.todocloud.app.data.PlanConversationSummary

internal fun mentionQuery(text: String): String? = Regex("(?:^|\\s)@([^@\\n]{0,60})$").find(text)?.groupValues?.get(1)
internal fun removeMentionQuery(text: String): String = Regex("@[^@\\n]{0,60}$").replace(text, "").trimEnd()
internal fun matchingConversations(items: List<PlanConversationSummary>, query: String, excluded: Set<String>) =
    items.filter { it.id !in excluded && (it.title.contains(query.trim(), ignoreCase = true) || it.preview.contains(query.trim(), ignoreCase = true)) }

@Composable
internal fun ConversationToolbar(title: String, busy: Boolean, onHistory: () -> Unit, onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onHistory, enabled = !busy) { Icon(Icons.Outlined.History, "历史会话") }
        IconButton(onClick = onNew, enabled = !busy) { Icon(Icons.Outlined.Add, "新建会话") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationPicker(
    conversations: List<PlanConversationSummary>,
    referenceMode: Boolean,
    initialQuery: String,
    loading: Boolean,
    excludedIds: Set<String>,
    onPick: (PlanConversationSummary) -> Unit,
    onDismiss: () -> Unit,
    onDelete: ((Set<String>) -> Unit)? = null,
    cloudHistory: Boolean = true,
    error: String? = null,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    val canManage = !referenceMode && onDelete != null
    LaunchedEffect(conversations, loading) {
        val liveIds = conversations.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.intersect(liveIds)
        if (!loading) {
            pendingDelete = pendingDelete?.intersect(liveIds)?.takeIf { it.isNotEmpty() }
            if (conversations.isEmpty()) selectionMode = false
        }
    }
    val results = remember(conversations, query, excludedIds) { matchingConversations(conversations, query, excludedIds) }
    val visibleIds = results.mapTo(linkedSetOf()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    val toggleSelection: (String) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Keep top/cutout insets outside the moving sheet. Applying top padding
        // inside it changes its measured height during a fast spring overshoot,
        // which moves the expanded anchor and restarts the same spring repeatedly.
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("conversation-history-panel")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectionMode) "已选择 ${selectedIds.size} 个" else if (referenceMode) "引用历史会话" else "历史会话",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                )
                if (canManage && conversations.isNotEmpty()) {
                    TextButton(onClick = {
                        selectionMode = !selectionMode
                        selectedIds = emptySet()
                    }, enabled = !loading) { Text(if (selectionMode) "取消多选" else "选择") }
                }
            }
            Text(
                if (referenceMode) "引用文字与计划；长会话节选，不含原图"
                else if (selectionMode) "点选要删除的会话"
                else if (canManage) "点击继续讨论，长按可多选删除"
                else "选择一段讨论，接着聊",
                Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null && pendingDelete == null) {
                Text(error, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth().padding(vertical = 16.dp), label = { Text("搜索会话") }, singleLine = true, enabled = !loading)
            if (selectionMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(modifier = Modifier.weight(1f), onClick = {
                        selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
                    }, enabled = !loading && visibleIds.isNotEmpty()) {
                        Text(if (allVisibleSelected) "取消全选" else if (query.isBlank()) "全选" else "全选搜索结果")
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { pendingDelete = selectedIds.toSet() }, enabled = !loading && selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(if (loading) "删除中…" else "删除（${selectedIds.size}）")
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("conversation-history-list")) {
                if (results.isEmpty()) item {
                    Text(if (conversations.isEmpty()) "还没有历史会话" else "没有找到匹配的会话", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { it.id }) { conversation ->
                    val selected = conversation.id in selectedIds
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selectionMode && selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .semantics(mergeDescendants = true) {
                                if (selectionMode) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                            }
                            .combinedClickable(
                                enabled = !loading && (!referenceMode || !conversation.syncPending),
                                role = if (selectionMode) Role.Checkbox else Role.Button,
                                onClick = { if (selectionMode) toggleSelection(conversation.id) else onPick(conversation) },
                                onLongClickLabel = if (canManage) "选择会话" else null,
                                onLongClick = if (canManage) ({ selectionMode = true; selectedIds = selectedIds + conversation.id }) else null,
                            ).padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      if (selectionMode) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
                      Column(Modifier.weight(1f)) {
                        Text(conversation.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(conversation.preview, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${conversation.updatedAt.take(10)} · ${conversation.messageCount} 条消息", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (conversation.syncPending) Text(if (referenceMode) "等待同步，暂不能引用" else "本机副本 · 等待同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                }
            }
        }
    }
    pendingDelete?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!loading) pendingDelete = null },
            title = { Text("删除 ${selected.size} 个会话？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (cloudHistory) "会从所有设备删除会话消息，无法恢复。已采用的计划和任务会保留。" else "会从本机删除会话消息，无法恢复。已采用的计划和任务会保留。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete?.invoke(selected) }, enabled = !loading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(if (loading) "删除中…" else "删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }, enabled = !loading) { Text("取消") } },
        )
    }
}

@Composable
internal fun ConversationReferenceBar(references: List<ConversationReference>, busy: Boolean, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            TextButton(onClick = onAdd, enabled = !busy && references.size < 3) {
                Icon(Icons.Outlined.AlternateEmail, contentDescription = null)
                Text("引用会话", Modifier.padding(start = 4.dp))
            }
        }
        items(references, key = { it.id }) { ref ->
            AssistChip(
                onClick = { onRemove(ref.id) }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text("@${ref.title.take(14)}", maxLines = 1) },
                trailingIcon = { Icon(Icons.Outlined.Close, "移除引用：${ref.title}") },
            )
        }
    }
}

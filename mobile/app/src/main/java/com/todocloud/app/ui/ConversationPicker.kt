package com.todocloud.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
) {
    var query by remember { mutableStateOf(initialQuery) }
    val results = remember(conversations, query, excludedIds) { matchingConversations(conversations, query, excludedIds) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(if (referenceMode) "引用历史会话" else "历史会话", style = MaterialTheme.typography.titleLarge)
            Text(if (referenceMode) "引用文字与计划；长会话节选，不含原图" else "选择一段讨论，接着聊", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth().padding(vertical = 16.dp), label = { Text("搜索会话") }, singleLine = true)
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (results.isEmpty()) item {
                    Text(if (conversations.isEmpty()) "还没有历史会话" else "没有找到匹配的会话", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(results, key = { it.id }) { conversation ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = !loading && (!referenceMode || !conversation.syncPending), role = Role.Button) { onPick(conversation) }.padding(vertical = 14.dp)) {
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

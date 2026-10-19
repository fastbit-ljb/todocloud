package com.todocloud.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.PlanItem
import com.todocloud.app.data.PlanMessage
import com.todocloud.app.data.ConversationReference
import com.todocloud.app.data.PlanConversationSummary
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import com.todocloud.app.data.TaskItem
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun AiPlanScreen(
    paddingValues: PaddingValues,
    ideas: List<InboxIdea>,
    tasks: List<TaskItem>,
    plan: AiPlan?,
    historyPlan: AiPlan? = plan,
    loading: Boolean,
    error: String?,
    conversation: List<PlanMessage>,
    conversationKey: String,
    conversationTitle: String,
    conversations: List<PlanConversationSummary>,
    syncPending: Boolean,
    onRetrySync: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRefreshHistory: () -> Unit,
    onOpenInbox: () -> Unit,
    onGenerate: (message: String, ideaIds: Set<Int>, forcePlan: Boolean, images: List<Uri>, references: List<ConversationReference>) -> Unit,
    onRevise: (Int, String, List<Uri>, List<ConversationReference>) -> Unit,
    onAccept: (planId: Int, items: List<PlanItem>?) -> Unit,
) {
    var message by rememberSaveable(conversationKey) { mutableStateOf("") }
    var images by remember(conversationKey) { mutableStateOf<List<Uri>>(emptyList()) }
    var references by remember(conversationKey) { mutableStateOf(emptyList<ConversationReference>()) }
    var picker by remember(conversationKey) { mutableStateOf<String?>(null) }
    var typedMention by remember(conversationKey) { mutableStateOf(false) }
    var showPlan by remember(conversationKey) { mutableStateOf(true) }
    var previousMessageCount by remember(conversationKey) { mutableIntStateOf(conversation.size) }
    val changeMessage: (String) -> Unit = { next ->
        if (mentionQuery(message) == null && mentionQuery(next) != null && references.size < 3) {
            typedMention = true
            picker = "reference"
        }
        message = next.take(4000)
    }
    LaunchedEffect(conversationKey, conversation.size) {
        if (conversation.size > previousMessageCount && conversation.lastOrNull()?.role == "assistant") {
            message = ""
            images = emptyList()
            references = emptyList()
        }
        previousMessageCount = conversation.size
    }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PLAN_IMAGES),
    ) { picked -> images = (images + picked).distinct().take(MAX_PLAN_IMAGES) }
    val onAddImages = {
        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    var showIdeaPicker by rememberSaveable { mutableStateOf(false) }
    val ideaKey = ideas.joinToString(",") { it.id.toString() }
    var selectedIdeaIds by remember(ideaKey) {
        mutableStateOf<Set<Int>>(ideas.mapTo(linkedSetOf()) { it.id })
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .padding(horizontal = 16.dp),
    ) {
        ConversationToolbar(conversationTitle, loading, onHistory = {
            picker = "history"
            onRefreshHistory()
        }, onNew = onNewConversation)
        if (syncPending) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("会话已保存在本机，等待同步", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetrySync, enabled = !loading) { Text("重试") }
            }
        }
        if (plan != null) {
            TextButton(onClick = { showPlan = !showPlan }, enabled = !loading) {
                Text(if (showPlan) "查看完整对话" else "查看计划")
            }
        }
        if (picker != null) {
            val referenceMode = picker == "reference"
            ConversationPicker(
                conversations = conversations,
                referenceMode = referenceMode,
                loading = loading,
                initialQuery = if (referenceMode && typedMention) mentionQuery(message).orEmpty() else "",
                excludedIds = if (referenceMode) references.mapTo(mutableSetOf(conversationKey)) { it.id } else emptySet(),
                onPick = { chosen ->
                    if (referenceMode && references.size < 3) {
                        references = references + ConversationReference(chosen.id, chosen.title)
                        if (typedMention) message = removeMentionQuery(message)
                    } else if (!referenceMode) onOpenConversation(chosen.id)
                    picker = null
                    typedMention = false
                },
                onDismiss = { picker = null; typedMention = false },
            )
        }
        error?.let {
            Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error)
        }

        if (plan == null || !showPlan) {
            val canGenerate = !loading && (
                message.isNotBlank() || images.isNotEmpty() || selectedIdeaIds.isNotEmpty() || tasks.isNotEmpty() || references.isNotEmpty()
            )
            val submitPlan = {
                val typed = message.trim()
                // An empty box means "just plan my stuff"; typed text may be a question.
                val request = typed.ifEmpty {
                    when {
                        images.isNotEmpty() ->
                            "请看看这些图片，结合我的收件匣和任务给出建议，需要的话帮我安排。"
                        selectedIdeaIds.isNotEmpty() ->
                            "请结合我这次选中的收件匣想法和现有未完成任务，生成一份现实可执行的计划。"
                        tasks.isNotEmpty() ->
                            "请结合现有未完成任务，生成一份现实可执行的计划。"
                        references.isNotEmpty() -> "请结合引用的历史会话，继续帮我规划。"
                        else -> ""
                    }
                }
                if (request.isNotBlank() && canGenerate) {
                    if (plan != null) onRevise(plan.id, request, images, references)
                    else onGenerate(request, selectedIdeaIds, typed.isEmpty() && images.isEmpty(), images, references)
                }
            }
            AiPlanLanding(
                modifier = Modifier.weight(1f),
                message = message,
                onMessageChange = changeMessage,
                ideas = ideas,
                selectedIdeaIds = selectedIdeaIds,
                loading = loading,
                canGenerate = canGenerate,
                conversation = conversation,
                historyPlan = historyPlan,
                images = images,
                onAddImages = onAddImages,
                onRemoveImage = { uri -> images = images - uri },
                onPlanFromConversation = {
                    onGenerate("请根据上面的对话，帮我生成一份现实可执行的计划。", selectedIdeaIds, true, emptyList(), references)
                },
                references = references,
                onReference = { typedMention = false; picker = "reference" },
                onRemoveReference = { id -> references = references.filterNot { it.id == id } },
                onOpenReference = onOpenConversation,
                onInboxClick = {
                    if (ideas.isEmpty()) onOpenInbox() else showIdeaPicker = true
                },
                onSubmit = submitPlan,
            )
            if (showIdeaPicker) {
                InboxIdeaPickerDialog(
                    ideas = ideas,
                    selectedIdeaIds = selectedIdeaIds,
                    onSelectionChange = { selectedIdeaIds = it },
                    onDismiss = { showIdeaPicker = false },
                    onOpenInbox = {
                        showIdeaPicker = false
                        onOpenInbox()
                    },
                )
            }
            return@Column
        }

        // Which proposed items to adopt and any time edits; reset whenever the plan changes.
        val planKey = plan.id to plan.items
        var excluded by remember(planKey) { mutableStateOf(emptySet<Int>()) }
        var edits by remember(planKey) { mutableStateOf(emptyMap<Int, PlanItem>()) }
        var editingIndex by remember(planKey) { mutableStateOf<Int?>(null) }
        val selectedCount = plan.items.size - excluded.size

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
        ) {
            item {
                AiPlanResultHeader(plan)
                Text(
                    "取消勾选不想要的安排，点一项可以改时间",
                    modifier = Modifier.padding(start = 6.dp, bottom = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            itemsIndexed(
                items = plan.items,
                key = { index, item -> "${item.sourceType}-${item.sourceId}-$index" },
            ) { index, item ->
                PlanTimelineItem(
                    item = edits[index] ?: item,
                    index = index + 1,
                    isLast = index == plan.items.lastIndex,
                    selected = index !in excluded,
                    edited = index in edits,
                    onSelectedChange = { keep ->
                        excluded = if (keep) excluded - index else excluded + index
                    },
                    onEdit = { editingIndex = index },
                )
            }
            if (plan.messages.size > 2) {
                item {
                    Text(
                        "修改记录",
                        modifier = Modifier.padding(start = 6.dp, top = 28.dp, bottom = 10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(plan.messages.drop(2).takeLast(8)) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (chat.role == "user") Arrangement.End else Arrangement.Start,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (chat.role == "user") {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(0.88f).padding(bottom = 8.dp),
                        ) {
                            Text(chat.content, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConversationReferenceBar(references, loading, onAdd = { typedMention = false; picker = "reference" }, onRemove = { id -> references = references.filterNot { it.id == id } })
            PlanImageBar(images = images, loading = loading, onAdd = onAddImages, onRemove = { uri -> images = images - uri })
            AiPlanRevisionField(
                value = message,
                onValueChange = changeMessage,
                loading = loading,
                onSubmit = {
                    val request = message.trim()
                    if (request.isNotEmpty()) {
                        onRevise(plan.id, request, images, references)
                    }
                },
            )
            Button(
                onClick = { onAccept(plan.id, planAcceptSelection(plan.items, excluded, edits)) },
                enabled = !loading && selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    if (excluded.isEmpty() && edits.isEmpty()) "采用计划" else "采用 $selectedCount 项",
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
        editingIndex?.let { index ->
            val original = plan.items.getOrNull(index)
            if (original != null) {
                PlanItemTimeDialog(
                    item = edits[index] ?: original,
                    onDismiss = { editingIndex = null },
                    onSave = { updated ->
                        edits = if (updated == original) edits - index else edits + (index to updated)
                        editingIndex = null
                    },
                )
            }
        }
    }
}

@Composable
internal fun AiPlanResultHeader(plan: AiPlan) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 30.dp),
    ) {
        Text(
            "计划准备好了",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "我这样安排，可以吗？",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 43.sp,
            ),
        )
        if (plan.summary.isNotBlank()) {
            Text(
                plan.summary,
                modifier = Modifier.padding(top = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 25.sp,
            )
        }
        Text(
            "${plan.items.size} 项安排",
            modifier = Modifier.padding(top = 22.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun AiPlanRevisionField(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    val canSubmit = !loading && value.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 12.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                enabled = !loading,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSubmit) onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                "告诉我哪里需要调整…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(onClick = onSubmit, enabled = canSubmit) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "发送调整要求",
                        tint = if (canSubmit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiPlanLanding(
    modifier: Modifier,
    message: String,
    onMessageChange: (String) -> Unit,
    ideas: List<InboxIdea>,
    selectedIdeaIds: Set<Int>,
    loading: Boolean,
    canGenerate: Boolean,
    conversation: List<PlanMessage>,
    historyPlan: AiPlan?,
    images: List<Uri>,
    onAddImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onPlanFromConversation: () -> Unit,
    references: List<ConversationReference>,
    onReference: () -> Unit,
    onRemoveReference: (String) -> Unit,
    onOpenReference: (String) -> Unit,
    onInboxClick: () -> Unit,
    onSubmit: () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (conversation.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 34.dp, start = 6.dp, end = 6.dp),
            ) {
                TypewriterPlanPrompt()
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
        ) {
            if (conversation.isNotEmpty()) {
                PlanConversation(
                    messages = conversation,
                    onOpenReference = onOpenReference,
                    historyPlan = historyPlan,
                    modifier = Modifier.weight(1f, fill = false).padding(top = 16.dp),
                )
                OutlinedButton(
                    onClick = onPlanFromConversation,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("直接帮我排成计划", modifier = Modifier.padding(start = 7.dp))
                }
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "AI 正在思考…",
                        modifier = Modifier.padding(start = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            ConversationReferenceBar(references, loading, onAdd = onReference, onRemove = onRemoveReference)
            PlanImageBar(images = images, loading = loading, onAdd = onAddImages, onRemove = onRemoveImage)
            AiPlanPromptField(
                modifier = Modifier.padding(bottom = 12.dp),
                value = message,
                onValueChange = onMessageChange,
                ideaCount = ideas.size,
                selectedIdeaCount = selectedIdeaIds.size,
                loading = loading,
                canSubmit = canGenerate,
                onInboxClick = onInboxClick,
                onSubmit = onSubmit,
            )
        }
    }
}

private const val MAX_PLAN_IMAGES = 3

/** "Add image" button plus removable thumbnails of the images attached to the next message. */
@Composable
internal fun PlanImageBar(
    images: List<Uri>,
    loading: Boolean,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        images.forEachIndexed { index, uri ->
            PlanImageThumbnail(uri = uri, index = index, onRemove = { onRemove(uri) })
        }
        if (images.size < MAX_PLAN_IMAGES) {
            TextButton(onClick = onAdd, enabled = !loading) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (images.isEmpty()) "添加图片" else "再加一张",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlanImageThumbnail(uri: Uri, index: Int, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(context, uri) }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "已添加的图片 ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除图片 ${index + 1}",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
            )
        }
    }
}

private fun decodeThumbnail(context: Context, uri: Uri): ImageBitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 168) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}.getOrNull()

@Composable
internal fun PlanConversation(messages: List<PlanMessage>, modifier: Modifier = Modifier, onOpenReference: (String) -> Unit = {}, historyPlan: AiPlan? = null) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull(), historyPlan?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        items(messages) { chat ->
            val fromUser = chat.role == "user"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (fromUser) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = (if (fromUser) "我：" else "AI：") + chat.content },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        chat.references.forEach { reference ->
                            TextButton(onClick = { onOpenReference(reference.id) }) { Text("@${reference.title}") }
                        }
                        Text(chat.content)
                    }
                }
            }
        }
        if (historyPlan != null) {
            item {
                Text(if (historyPlan.status == "accepted") "历史计划 · 已采用" else "历史计划 · 草稿", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            itemsIndexed(historyPlan.items, key = { index, _ -> "history-plan-${historyPlan.id}-$index" }) { index, item ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. ${item.title}", style = MaterialTheme.typography.titleSmall)
                        val timing = listOfNotNull(item.startAt?.let { "${formatPlanDateTime(it)} 开始" }, item.dueAt?.let { "${formatPlanDateTime(it)} 前完成" }).joinToString(" · ")
                        if (timing.isNotEmpty()) Text(timing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypewriterPlanPrompt() {
    val greetings = remember {
        listOf(
            "你好！",
            "嗨！",
            "欢迎回来！",
            "见到你真好！",
        )
    }
    val questions = remember {
        listOf(
            "你有什么计划吗？",
            "今天想完成什么？",
            "有什么想法想变成计划？",
            "哪些事情需要我帮你安排？",
            "接下来想做些什么？",
        )
    }
    val greeting = remember { greetings.random() }
    val question = remember { questions.random() }
    var displayedGreeting by remember { mutableStateOf("") }
    var displayedQuestion by remember { mutableStateOf("") }
    var activeLine by remember { mutableIntStateOf(0) }
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(120L)
        greeting.forEach { character ->
            delay(if (character == '！') 140L else 88L)
            displayedGreeting += character
        }
        delay(260L)
        activeLine = 1
        question.forEach { character ->
            delay(if (character == '，' || character == '？') 150L else 82L)
            displayedQuestion += character
        }
        repeat(4) {
            delay(280L)
            cursorVisible = !cursorVisible
        }
        cursorVisible = false
    }

    Text(
        text = displayedGreeting + if (activeLine == 0 && cursorVisible) "｜" else "",
        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = displayedQuestion + if (activeLine == 1 && cursorVisible) "｜" else "",
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 92.dp),
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 43.sp,
        ),
    )
}

@Composable
internal fun AiPlanPromptField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    ideaCount: Int,
    selectedIdeaCount: Int,
    loading: Boolean,
    canSubmit: Boolean,
    onInboxClick: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(46.dp)) {
                IconButton(onClick = onInboxClick, modifier = Modifier.align(Alignment.Center)) {
                    Icon(
                        Icons.Outlined.Inbox,
                        contentDescription = if (ideaCount == 0) "去收件匣记录想法" else "选择收件匣想法",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (ideaCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(19.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            selectedIdeaCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 15.dp),
                enabled = !loading,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSubmit) onSubmit() },
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                "告诉我你的计划…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(onClick = onSubmit, enabled = canSubmit) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "生成计划",
                        tint = if (canSubmit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun InboxIdeaPickerDialog(
    ideas: List<InboxIdea>,
    selectedIdeaIds: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    onOpenInbox: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
        title = { Text("选择本次参考的想法") },
        text = {
            Column {
                Text(
                    "未选中的想法不会交给 AI 参与这次规划。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { onSelectionChange(emptySet()) }) { Text("清空") }
                    TextButton(onClick = { onSelectionChange(ideas.mapTo(linkedSetOf()) { it.id }) }) {
                        Text("全选")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ideas, key = { it.id }) { idea ->
                        val selected = idea.id in selectedIdeaIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selected,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        onSelectionChange(
                                            if (selected) selectedIdeaIds - idea.id
                                            else selectedIdeaIds + idea.id,
                                        )
                                    },
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(idea.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    idea.durationMinutes?.let { duration ->
                                        Text(
                                            "持续 ${formatDuration(duration)}",
                                            modifier = Modifier.padding(top = 3.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface,
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onOpenInbox) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("记录新想法", modifier = Modifier.padding(start = 5.dp))
            }
        },
    )
}

@Composable
internal fun PlanTimelineItem(
    item: PlanItem,
    index: Int,
    isLast: Boolean,
    selected: Boolean = true,
    edited: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val scheduleText = when {
        item.startAt != null && item.dueAt != null ->
            "${formatPlanDateTime(item.startAt)} — ${formatPlanDateTime(item.dueAt)}"
        item.startAt != null -> "${formatPlanDateTime(item.startAt)} 开始"
        item.dueAt != null -> "${formatPlanDateTime(item.dueAt)} 前完成"
        else -> "暂不排期"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "修改时间", onClick = onEdit)
            .graphicsLayer { alpha = if (selected) 1f else 0.45f }
            .padding(start = 6.dp, end = 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp, bottom = 22.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    append(scheduleText)
                    item.durationMinutes?.let { append(" · ${formatDuration(it)}") }
                    recurrenceLabel(item.recurrence)?.let { append(" · $it") }
                    if (edited) append(" · 已修改")
                },
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.steps.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    item.steps.forEach { step ->
                        Text(
                            "• ${step.title}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)),
                )
            }
        }
        GreenTaskCheckbox(
            checked = selected,
            onCheckedChange = { onSelectedChange(!selected) },
        )
    }
}

/** Change when one proposed item happens, before the plan is accepted. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PlanItemTimeDialog(
    item: PlanItem,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit,
) {
    var startAt by remember(item) { mutableStateOf(item.startAt?.let(::parseIdeaDateTime)) }
    var dueAt by remember(item) { mutableStateOf(item.dueAt?.let(::parseIdeaDateTime)) }
    var duration by remember(item) { mutableStateOf(item.durationMinutes) }
    var recurrence by remember(item) { mutableStateOf(item.recurrence) }
    var picker by remember { mutableStateOf<String?>(null) }
    var showCustomDuration by remember { mutableStateOf(false) }
    var customDurationText by remember { mutableStateOf("") }
    val formatter = remember { java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm") }
    val invalid = startAt != null && dueAt != null && startAt!!.isAfter(dueAt)
    fun java.time.LocalDateTime.iso() =
        atZone(java.time.ZoneId.systemDefault()).toInstant().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = "start" }, modifier = Modifier.weight(1f)) {
                        Text(startAt?.format(formatter) ?: "开始时间")
                    }
                    OutlinedButton(onClick = { picker = "due" }, modifier = Modifier.weight(1f)) {
                        Text(dueAt?.format(formatter) ?: "截止时间")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (startAt != null) TextButton(onClick = { startAt = null }) { Text("清除开始") }
                    if (dueAt != null) TextButton(onClick = { dueAt = null }) { Text("清除截止") }
                }
                if (invalid) Text("开始时间不能晚于截止时间", color = MaterialTheme.colorScheme.error)
                IdeaDurationSelector(
                    value = duration,
                    onValueChange = { duration = it },
                    onMore = {
                        customDurationText = duration
                            ?.takeUnless { value -> IDEA_DURATION_OPTIONS.any { it.minutes == value } }
                            ?.toString()
                            .orEmpty()
                        showCustomDuration = true
                    },
                )
                if (dueAt != null) {
                    Text("重复", style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RECURRENCE_OPTIONS.forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = recurrence == option.rule,
                                onClick = { recurrence = option.rule },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            startAt = startAt?.iso(),
                            dueAt = dueAt?.iso(),
                            durationMinutes = duration,
                            recurrence = if (dueAt == null) null else recurrence,
                        ),
                    )
                },
                enabled = !invalid,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    picker?.let { target ->
        TaskDateTimePickerDialog(
            title = if (target == "start") "设置开始时间" else "设置截止时间",
            initialDateTime = if (target == "start") startAt else dueAt,
            onDismiss = { picker = null },
            onConfirm = { selected ->
                if (target == "start") startAt = selected else dueAt = selected
                picker = null
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
                        duration = customDurationText.toIntOrNull()?.coerceIn(1, 525_600)
                        showCustomDuration = false
                    },
                    enabled = customDurationText.toIntOrNull()?.let { it in 1..525_600 } == true,
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCustomDuration = false }) { Text("取消") } },
        )
    }
}

@Composable
internal fun BouncingTaskLoader(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val loaderHtml = remember(primaryColor, primaryContainerColor, labelColor) {
        UIVERSE_LOADER_HTML
            .replace(
                "linear-gradient(135deg, #23C4F8, #275EFE)",
                "linear-gradient(135deg, ${primaryColor.toCssHex()}, ${primaryContainerColor.toCssHex()})",
            )
            .replace(
                "rgba(39, 94, 254, 0.28)",
                primaryColor.toCssRgba(0.28f),
            )
            .replace(
                "#6C7486",
                labelColor.toCssHex(),
            )
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                tag = loaderHtml
                loadDataWithBaseURL(
                    "https://uiverse.io/",
                    loaderHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != loaderHtml) {
                webView.tag = loaderHtml
                webView.loadDataWithBaseURL(
                    "https://uiverse.io/",
                    loaderHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        modifier = modifier
            .size(width = 200.dp, height = 184.dp)
            .semantics { contentDescription = "正在加载任务" },
    )
}

internal fun Color.toCssHex(): String = String.format(
    Locale.US,
    "#%02X%02X%02X",
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)

internal fun Color.toCssRgba(alpha: Float): String = String.format(
    Locale.US,
    "rgba(%d, %d, %d, %.2f)",
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
    alpha.coerceIn(0f, 1f),
)

/** The original Uiverse.io by Nawsome loader, kept as HTML/CSS/SVG so the
 *  rotateY timing and page geometry are not approximated by a native rewrite. */
internal const val UIVERSE_PAGE_SVG = """<svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg>"""

internal val UIVERSE_PAGE_SVG_EXACT = UIVERSE_PAGE_SVG.replace(
    "C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33",
    "C74,34.1192881 72.8807119,33 71.5,33",
)

internal val UIVERSE_LOADER_HTML = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; width: 200px; height: 184px; overflow: visible; background: transparent; }
.loader {
  --background: linear-gradient(135deg, #23C4F8, #275EFE);
  --shadow: rgba(39, 94, 254, 0.28);
  --text: #6C7486;
  --page: rgba(255, 255, 255, 0.36);
  --page-fold: rgba(255, 255, 255, 0.52);
  --duration: 3s;
  width: 200px;
  height: 140px;
  position: relative;
}
.loader:before, .loader:after {
  --r: -6deg;
  content: "";
  position: absolute;
  bottom: 8px;
  width: 120px;
  top: 80%;
  box-shadow: 0 16px 12px var(--shadow);
  transform: rotate(var(--r));
}
.loader:before { left: 4px; }
.loader:after { --r: 6deg; right: 4px; }
.loader div {
  width: 100%;
  height: 100%;
  border-radius: 13px;
  position: relative;
  z-index: 1;
  perspective: 600px;
  box-shadow: 0 4px 6px var(--shadow);
  background-image: var(--background);
}
.loader div ul {
  margin: 0;
  padding: 0;
  list-style: none;
  position: relative;
}
.loader div ul li {
  --r: 180deg;
  --o: 0;
  --c: var(--page);
  position: absolute;
  top: 10px;
  left: 10px;
  transform-origin: 100% 50%;
  color: var(--c);
  opacity: var(--o);
  transform: rotateY(var(--r));
  -webkit-animation: var(--duration) ease infinite;
  animation: var(--duration) ease infinite;
}
.loader div ul li:nth-child(2) { --c: var(--page-fold); -webkit-animation-name: page-2; animation-name: page-2; }
.loader div ul li:nth-child(3) { --c: var(--page-fold); -webkit-animation-name: page-3; animation-name: page-3; }
.loader div ul li:nth-child(4) { --c: var(--page-fold); -webkit-animation-name: page-4; animation-name: page-4; }
.loader div ul li:nth-child(5) { --c: var(--page-fold); -webkit-animation-name: page-5; animation-name: page-5; }
.loader div ul li svg { width: 90px; height: 120px; display: block; }
.loader div ul li:first-child { --r: 0deg; --o: 1; }
.loader div ul li:last-child { --o: 1; }
.loader span {
  display: block;
  left: 0;
  right: 0;
  top: 100%;
  margin-top: 20px;
  text-align: center;
  color: var(--text);
  font: 14px sans-serif;
}
@keyframes page-2 {
  0% { transform: rotateY(180deg); opacity: 0; }
  20% { opacity: 1; }
  35%, 100% { opacity: 0; }
  50%, 100% { transform: rotateY(0deg); }
}
@keyframes page-3 {
  15% { transform: rotateY(180deg); opacity: 0; }
  35% { opacity: 1; }
  50%, 100% { opacity: 0; }
  65%, 100% { transform: rotateY(0deg); }
}
@keyframes page-4 {
  30% { transform: rotateY(180deg); opacity: 0; }
  50% { opacity: 1; }
  65%, 100% { opacity: 0; }
  80%, 100% { transform: rotateY(0deg); }
}
@keyframes page-5 {
  45% { transform: rotateY(180deg); opacity: 0; }
  65% { opacity: 1; }
  80%, 100% { opacity: 0; }
  95%, 100% { transform: rotateY(0deg); }
}
</style>
</head>
<body>
<div class="loader">
  <div>
    <ul>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,34.1192881 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,38.1192881 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
    </ul>
  </div>
  <span>Loading</span>
</div>
</body>
</html>
""".trimIndent().replace(
    Regex("<svg fill=\"currentColor\" viewBox=\"0 0 90 120\">.*?</svg>"),
    UIVERSE_PAGE_SVG_EXACT,
)

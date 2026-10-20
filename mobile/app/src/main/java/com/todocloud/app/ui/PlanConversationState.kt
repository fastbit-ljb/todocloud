package com.todocloud.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.ConversationReference
import com.todocloud.app.data.DeletedConversationException
import com.todocloud.app.data.PlanChatResult
import com.todocloud.app.data.PlanConversation
import com.todocloud.app.data.PlanConversationSummary
import com.todocloud.app.data.PlanMessage
import com.todocloud.app.data.LOCAL_SESSION_TOKEN
import com.todocloud.app.data.conversationReferenceContext
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.data.conversationTitle
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ConversationDeletionResult(
    val remaining: List<PlanConversationSummary>,
    val resetActive: Boolean,
)

internal fun applyConversationDeletion(
    conversations: List<PlanConversationSummary>,
    activeId: String?,
    deletedIds: Set<String>,
) = ConversationDeletionResult(
    remaining = conversations.filterNot { it.id in deletedIds },
    resetActive = activeId != null && activeId in deletedIds,
)

internal data class ConversationBatchDeletionResult(
    val deletedIds: Set<String>,
    val failures: Map<String, String>,
)

internal suspend fun deleteConversationBatch(
    ids: Set<String>,
    delete: suspend (String) -> Unit,
    onDeleted: (String) -> Unit,
): ConversationBatchDeletionResult {
    val deleted = linkedSetOf<String>()
    val failures = linkedMapOf<String, String>()
    for (id in ids) {
        try {
            delete(id)
            deleted += id
            onDeleted(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            failures[id] = exception.message ?: "连接失败"
        }
    }
    return ConversationBatchDeletionResult(deleted, failures)
}

/** Owns the selected conversation; a reply can never land in another session. */
internal class PlanConversationState(private val repository: TodoCloudRepository, private val token: String) {
    var conversations by mutableStateOf(emptyList<PlanConversationSummary>())
        private set
    var active by mutableStateOf<PlanConversation?>(null)
        private set
    var draftKey by mutableStateOf(UUID.randomUUID().toString())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var deletedIds by mutableStateOf(emptySet<String>())
        private set
    var pendingMessage by mutableStateOf<PlanMessage?>(null)
        private set
    var streamingText by mutableStateOf("")
        private set

    val displayedMessages: List<PlanMessage>
        get() = active?.messages.orEmpty() + listOfNotNull(pendingMessage)

    private fun clearPending() { pendingMessage = null; streamingText = "" }

    private suspend fun showPartial(text: String) = withContext(Dispatchers.Main.immediate) {
        streamingText = text
    }

    val key: String get() = active?.id ?: draftKey

    private suspend fun request(block: suspend () -> Unit) {
        if (loading) return
        loading = true
        error = null
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (deleted: DeletedConversationException) {
            forgetDeleted(setOf(deleted.conversationId))
            error = deleted.message
        }
        catch (exception: Exception) { error = exception.message ?: "连接失败，请稍后重试" }
        finally { loading = false }
    }

    suspend fun load() = request {
        refreshList()
        conversations.firstOrNull()?.let { active = repository.getPlanConversation(token, it.id) }
    }

    private suspend fun refreshList() {
        conversations = repository.listPlanConversations(token)
        forgetDeleted(repository.deletedPlanConversationIds(token))
    }

    private fun forgetDeleted(ids: Set<String>) {
        deletedIds = deletedIds + ids
        val result = applyConversationDeletion(conversations, active?.id, deletedIds)
        conversations = result.remaining
        if (result.resetActive) {
            clearPending()
            active = null
            draftKey = UUID.randomUUID().toString()
        }
    }

    suspend fun refreshHistory() = request { refreshList() }

    suspend fun delete(ids: Set<String>): Set<String> {
        var deleted = emptySet<String>()
        request {
            val result = deleteConversationBatch(ids, { repository.deletePlanConversation(token, it) }) { forgetDeleted(setOf(it)) }
            deleted = result.deletedIds
            if (result.failures.isNotEmpty()) {
                error = "${result.failures.size} 个会话删除失败，请重试。" +
                    (if (deleted.isNotEmpty()) "已删除 ${deleted.size} 个。" else "") +
                    "\n${result.failures.values.first()}"
            }
        }
        return deleted
    }

    fun newConversation() {
        if (loading) return
        clearPending()
        active = null
        draftKey = UUID.randomUUID().toString()
        error = null
    }

    suspend fun open(id: String) = request { clearPending(); active = repository.getPlanConversation(token, id) }

    private suspend fun persist(value: PlanConversation) {
        // Keep the completed reply visible even if its subsequent cloud save fails.
        active = value.copy(syncPending = token != LOCAL_SESSION_TOKEN)
        val saved = repository.savePlanConversation(token, value)
        active = saved
        conversations = (listOf(saved.summary()) + conversations.filterNot { it.id == saved.id }).sortedByDescending { it.updatedAt }
    }

    suspend fun retrySync() = request { active?.let { persist(it) } }

    suspend fun send(message: String, ideaIds: Set<Int>, forcePlan: Boolean, images: List<Uri>, references: List<ConversationReference>, onPlan: (AiPlan) -> Unit) = request {
        val current = active ?: PlanConversation(draftKey, conversationTitle(message), emptyList(), updatedAt = Instant.now().toString())
        check(current.messages.size < 998) { "会话较长，请新建会话后用 @ 引用这段讨论" }
        val history = if (current.plan != null) {
            current.messages.takeLast(11) + PlanMessage("assistant", conversationReferenceContext(listOf(current.copy(messages = emptyList()))).take(4000))
        } else current.messages.takeLast(12)
        val shown = if (images.isEmpty()) message else "$message\n（附图 ${images.size} 张）"
        streamingText = ""
        pendingMessage = PlanMessage("user", shown, references)
        val result = repository.chatPlan(token, message, ideaIds, history, forcePlan, images, references.map { it.id }, ::showPartial)
        val plan = (result as? PlanChatResult.Created)?.plan ?: current.plan
        if (result is PlanChatResult.Created) onPlan(result.plan)
        val reply = result.reply.ifBlank { plan?.summary.orEmpty() }
        clearPending()
        persist(current.copy(messages = current.messages + PlanMessage("user", shown, references) + PlanMessage("assistant", reply.ifBlank { "计划已生成" }), plan = plan))
    }

    suspend fun revise(planId: Int, message: String, images: List<Uri>, references: List<ConversationReference>, onPlan: (AiPlan) -> Unit) = request {
        val current = active ?: error("请先打开对应的会话")
        check(current.plan?.id == planId) { "请先打开对应的会话" }
        check(current.messages.size < 998) { "会话较长，请新建会话后用 @ 引用这段讨论" }
        val shown = if (images.isEmpty()) message else "$message\n（附图 ${images.size} 张）"
        streamingText = ""
        pendingMessage = PlanMessage("user", shown, references)
        val revised = repository.revisePlan(token, planId, message, images, references.map { it.id }, ::showPartial)
        onPlan(revised)
        clearPending()
        persist(current.copy(messages = current.messages + PlanMessage("user", shown, references) + PlanMessage("assistant", revised.messages.lastOrNull()?.content ?: revised.summary), plan = revised))
    }

    suspend fun updatePlan(plan: AiPlan) {
        val current = active ?: return
        if (current.plan?.id == plan.id) request { persist(current.copy(plan = plan)) }
    }
}

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
            active = null
            draftKey = UUID.randomUUID().toString()
        }
    }

    suspend fun refreshHistory() = request { refreshList() }

    suspend fun delete(id: String) = request {
        repository.deletePlanConversation(token, id)
        forgetDeleted(setOf(id))
    }

    fun newConversation() {
        if (loading) return
        active = null
        draftKey = UUID.randomUUID().toString()
        error = null
    }

    suspend fun open(id: String) = request { active = repository.getPlanConversation(token, id) }

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
        val result = repository.chatPlan(token, message, ideaIds, history, forcePlan, images, references.map { it.id })
        val plan = (result as? PlanChatResult.Created)?.plan ?: current.plan
        if (result is PlanChatResult.Created) onPlan(result.plan)
        val shown = if (images.isEmpty()) message else "$message\n（附图 ${images.size} 张）"
        val reply = result.reply.ifBlank { plan?.summary.orEmpty() }
        persist(current.copy(messages = current.messages + PlanMessage("user", shown, references) + PlanMessage("assistant", reply.ifBlank { "计划已生成" }), plan = plan))
    }

    suspend fun revise(planId: Int, message: String, images: List<Uri>, references: List<ConversationReference>, onPlan: (AiPlan) -> Unit) = request {
        val current = active ?: error("请先打开对应的会话")
        check(current.plan?.id == planId) { "请先打开对应的会话" }
        check(current.messages.size < 998) { "会话较长，请新建会话后用 @ 引用这段讨论" }
        val revised = repository.revisePlan(token, planId, message, images, references.map { it.id })
        onPlan(revised)
        val shown = if (images.isEmpty()) message else "$message\n（附图 ${images.size} 张）"
        persist(current.copy(messages = current.messages + PlanMessage("user", shown, references) + PlanMessage("assistant", revised.messages.lastOrNull()?.content ?: revised.summary), plan = revised))
    }

    suspend fun updatePlan(plan: AiPlan) {
        val current = active ?: return
        if (current.plan?.id == plan.id) request { persist(current.copy(plan = plan)) }
    }
}

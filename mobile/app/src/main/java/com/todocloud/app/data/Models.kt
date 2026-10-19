package com.todocloud.app.data

data class Session(
    val token: String,
    val refreshToken: String,
    val email: String,
    val displayName: String?,
)

data class TaskStep(
    val title: String,
    val completed: Boolean = false,
)

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int?,
    val completed: Boolean,
    val completedAt: String?,
    val steps: List<TaskStep> = emptyList(),
    val startAt: String? = null,
    val durationMinutes: Int? = null,
    /** Repeat rule: daily / weekdays / weekly / monthly; null = one-off. */
    val recurrence: String? = null,
)

/** Everything the task editor can set; times are ISO-8601 instants. */
data class TaskDraft(
    val title: String,
    val description: String?,
    val startAt: String?,
    val dueAt: String?,
    val durationMinutes: Int?,
    val reminderOffsetMinutes: Int?,
    val steps: List<TaskStep>,
    val recurrence: String? = null,
)

data class IdeaDetail(
    val content: String,
)

data class InboxIdea(
    val id: Int,
    val content: String,
    val durationMinutes: Int?,
    val startAt: String?,
    val dueAt: String?,
    val items: List<IdeaDetail> = emptyList(),
)

data class PlanStep(
    val title: String,
)

data class PlanItem(
    val sourceType: String,
    val sourceId: Int,
    val title: String,
    val description: String?,
    val startAt: String?,
    val dueAt: String?,
    val durationMinutes: Int?,
    val reminderOffsetMinutes: Int?,
    val steps: List<PlanStep> = emptyList(),
    val note: String,
    /** Repeat rule the adopted task gets (daily / weekdays / weekly / monthly); null = one-off. */
    val recurrence: String? = null,
)

data class PlanMessage(
    val role: String,
    val content: String,
    val references: List<ConversationReference> = emptyList(),
)

data class ConversationReference(val id: String, val title: String)

data class PlanConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: String,
    val preview: String,
    val messageCount: Int,
    val revision: Int,
    val syncPending: Boolean = false,
)

data class PlanConversation(
    val id: String,
    val title: String,
    val messages: List<PlanMessage>,
    val plan: AiPlan? = null,
    val updatedAt: String,
    val revision: Int = 0,
    val syncPending: Boolean = false,
) {
    fun summary() = PlanConversationSummary(id, title, updatedAt, messages.lastOrNull()?.content.orEmpty().take(120), messages.size, revision, syncPending)
}

data class AiPlan(
    val id: Int,
    val status: String,
    val title: String,
    val summary: String,
    val items: List<PlanItem>,
    val messages: List<PlanMessage>,
)

data class PlanAcceptResult(
    val plan: AiPlan,
    val tasks: List<TaskItem>,
)

data class PlanUndoResult(
    val plan: AiPlan,
    val deletedTaskIds: List<Int>,
    val restoredTasks: List<TaskItem>,
)

/** Result of one AI planning conversation turn: either an answer or a new draft plan. */
sealed interface PlanChatResult {
    val reply: String

    data class Answer(override val reply: String) : PlanChatResult

    data class Created(override val reply: String, val plan: AiPlan) : PlanChatResult
}

data class AiTaskStep(
    val title: String,
)

data class AiTaskCandidate(
    val title: String,
    val description: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int?,
    val confidence: Double,
    val sourceText: String?,
    val steps: List<AiTaskStep> = emptyList(),
)

data class AiParseResult(
    val attachmentId: Int?,
    val parseId: Int?,
    val candidates: List<AiTaskCandidate>,
)

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

/** Last successfully synced tasks and ideas, kept encrypted on the device for offline viewing. */
data class OfflineSnapshot(
    val tasks: List<TaskItem>,
    val ideas: List<InboxIdea>,
    val syncedAtMillis: Long,
)

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
)

data class PlanMessage(
    val role: String,
    val content: String,
)

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

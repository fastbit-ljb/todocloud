package com.todocloud.app.data

data class Session(
    val token: String,
    val refreshToken: String,
    val email: String,
    val displayName: String?,
)

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int?,
    val completed: Boolean,
    val completedAt: String?,
)

data class AiTaskCandidate(
    val title: String,
    val description: String?,
    val dueAt: String?,
    val reminderOffsetMinutes: Int?,
    val confidence: Double,
    val sourceText: String?,
)

data class AiParseResult(
    val attachmentId: Int?,
    val parseId: Int?,
    val candidates: List<AiTaskCandidate>,
)

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

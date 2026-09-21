package com.todocloud.app.data

data class Session(
    val token: String,
    val email: String,
    val displayName: String?,
)

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String?,
    val dueAt: String?,
    val completed: Boolean,
)

class ApiException(message: String) : Exception(message)

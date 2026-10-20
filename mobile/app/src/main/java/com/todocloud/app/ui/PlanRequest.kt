package com.todocloud.app.ui

/** Existing tasks are context, not an implicit request to send. */
internal fun hasPlanRequestContent(
    message: String,
    selectedIdeaCount: Int = 0,
    imageCount: Int = 0,
    referenceCount: Int = 0,
): Boolean = message.isNotBlank() || selectedIdeaCount > 0 || imageCount > 0 || referenceCount > 0

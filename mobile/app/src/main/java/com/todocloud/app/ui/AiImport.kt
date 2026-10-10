package com.todocloud.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.abs

internal data class AiImportPlan(
    val candidatesToCreate: List<com.todocloud.app.data.AiTaskCandidate>,
    val skippedCount: Int,
)

internal fun buildAiImportPlan(
    existingTasks: List<TaskItem>,
    candidates: List<com.todocloud.app.data.AiTaskCandidate>,
): AiImportPlan {
    val uniqueCandidates = buildList {
        candidates.forEach { candidate ->
            if (none { existing -> sameCandidate(existing, candidate) }) add(candidate)
        }
    }
    val candidatesToCreate = uniqueCandidates.filterNot { candidate ->
        existingTasks.any { task ->
            taskMatchesCandidate(task, candidate) &&
                !(task.steps.isEmpty() && candidate.steps.isNotEmpty())
        }
    }
    return AiImportPlan(
        candidatesToCreate = candidatesToCreate,
        skippedCount = candidates.size - candidatesToCreate.size,
    )
}

internal fun sameCandidate(
    first: com.todocloud.app.data.AiTaskCandidate,
    second: com.todocloud.app.data.AiTaskCandidate,
): Boolean {
    if (!titlesLikelySame(first.title, second.title)) return false
    val firstDue = first.dueAt?.let(::parseInstant)
    val secondDue = second.dueAt?.let(::parseInstant)
    return if (firstDue != null && secondDue != null) {
        abs(firstDue.epochSecond - secondDue.epochSecond) <= 60
    } else {
        first.dueAt == second.dueAt
    }
}

internal fun titlesLikelySame(first: String, second: String): Boolean {
    val firstNormalized = normalizeTaskTitle(first)
    val secondNormalized = normalizeTaskTitle(second)
    if (firstNormalized.isBlank() || secondNormalized.isBlank()) return false
    if (firstNormalized == secondNormalized) return true
    if (minOf(firstNormalized.length, secondNormalized.length) < 3) return false

    // Compare character counts rather than sets: common characters alone made unrelated
    // short Chinese task titles look like duplicates (for example, “买水” and “买书”).
    val firstCounts = firstNormalized.groupingBy { it }.eachCount()
    val secondCounts = secondNormalized.groupingBy { it }.eachCount()
    val overlap = firstCounts.entries.sumOf { (character, count) ->
        minOf(count, secondCounts[character] ?: 0)
    }
    val diceSimilarity = 2f * overlap / (firstNormalized.length + secondNormalized.length)
    return diceSimilarity >= 0.84f
}

internal fun normalizeTaskTitle(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\p{Punct}\\s，。！？、：；（）【】‘’“”]+"), "")
    .replace("请", "")
    .replace("帮我", "")
    .replace("一下", "")
    .replace("把", "")

internal fun taskMatchesCandidate(
    task: TaskItem,
    candidate: com.todocloud.app.data.AiTaskCandidate,
): Boolean {
    if (!titlesLikelySame(task.title, candidate.title)) return false
    val candidateDue = candidate.dueAt
    val taskDue = task.dueAt
    if (candidateDue == null || taskDue == null) return true
    val candidateInstant = parseInstant(candidateDue)
    val taskInstant = parseInstant(taskDue)
    return if (candidateInstant != null && taskInstant != null) {
        abs(candidateInstant.epochSecond - taskInstant.epochSecond) <= 60
    } else {
        candidateDue == taskDue
    }
}

internal fun parseInstant(value: String): java.time.Instant? = runCatching {
    OffsetDateTime.parse(value).toInstant()
}.getOrNull()

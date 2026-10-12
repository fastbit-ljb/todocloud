package com.todocloud.app.ui

import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.TaskItem
import java.util.Locale

/**
 * Local search over what is already loaded. The query is split on whitespace and
 * every term must appear somewhere in the item (case-insensitive), so "毕设 汇报"
 * finds "毕设开题现场汇报".
 */
internal fun searchTerms(query: String): List<String> =
    query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun matchesAll(haystack: String, terms: List<String>): Boolean {
    val text = haystack.lowercase(Locale.ROOT)
    return terms.all { it in text }
}

internal fun taskMatches(task: TaskItem, query: String): Boolean {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return true
    val haystack = buildString {
        append(task.title).append('\n')
        task.description?.let { append(it).append('\n') }
        task.steps.forEach { append(it.title).append('\n') }
    }
    return matchesAll(haystack, terms)
}

internal fun ideaMatches(idea: InboxIdea, query: String): Boolean {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return true
    val haystack = buildString {
        append(idea.content).append('\n')
        idea.items.forEach { append(it.content).append('\n') }
    }
    return matchesAll(haystack, terms)
}

/** All matching tasks, including completed ones of any age; open tasks first. */
internal fun searchTasks(tasks: List<TaskItem>, query: String): List<TaskItem> =
    sortTasks(tasks.filter { taskMatches(it, query) })

internal fun searchIdeas(ideas: List<InboxIdea>, query: String): List<InboxIdea> =
    ideas.filter { ideaMatches(it, query) }

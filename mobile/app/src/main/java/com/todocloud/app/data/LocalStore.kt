package com.todocloud.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Token of the "use on this phone without an account" session; never sent to the server. */
const val LOCAL_SESSION_TOKEN = "todocloud-local-device"

val Session.isLocal: Boolean get() = token == LOCAL_SESSION_TOKEN

/**
 * Tasks and ideas of local mode. Everything stays on the phone, encrypted with an
 * Android Keystore key, and mirrors what the server API would return so the UI does
 * not need to know which mode it is in. One instance per process (receivers, widgets
 * and the app share it) so concurrent writers cannot overwrite each other.
 */
internal class LocalStore private constructor(context: Context) {
    private val storage = SecureSessionStore(context, PREFERENCES_NAME)
    private val mutex = Mutex()
    private var loaded = false
    private var tasks = mutableListOf<TaskItem>()
    private var ideas = mutableListOf<InboxIdea>()
    private var nextId = 1

    suspend fun listTasks(): List<TaskItem> = read { tasks.toList() }

    suspend fun listIdeas(): List<InboxIdea> = read { ideas.toList() }

    suspend fun createTask(
        title: String,
        description: String?,
        dueAt: String?,
        reminderOffsetMinutes: Int?,
        steps: List<TaskStep>,
        startAt: String?,
        durationMinutes: Int?,
        recurrence: String?,
    ): TaskItem = write {
        TaskItem(
            id = nextId++,
            title = title.trim().take(200),
            description = description?.takeIf { it.isNotBlank() },
            dueAt = dueAt,
            reminderOffsetMinutes = reminderOffsetMinutes,
            completed = false,
            completedAt = null,
            steps = steps.take(30),
            startAt = startAt,
            durationMinutes = durationMinutes,
            recurrence = recurrence.takeIf { it in RECURRENCE_RULES && dueAt != null },
        ).also { tasks.add(it) }
    }

    suspend fun updateTask(
        id: Int,
        completed: Boolean?,
        dueAt: String?,
        reminderOffsetMinutes: Int?,
        steps: List<TaskStep>?,
        includeSchedule: Boolean,
    ): TaskItem = write {
        var task = findTask(id)
        if (includeSchedule) task = task.copy(dueAt = dueAt, reminderOffsetMinutes = reminderOffsetMinutes)
        if (steps != null) task = task.copy(steps = steps)
        if (completed != null) task = applyCompletion(task, completed)
        replace(task)
    }

    suspend fun editTask(id: Int, draft: TaskDraft): TaskItem = write {
        val task = findTask(id).copy(
            title = draft.title.trim().take(200),
            description = draft.description,
            startAt = draft.startAt,
            dueAt = draft.dueAt,
            durationMinutes = draft.durationMinutes,
            reminderOffsetMinutes = draft.reminderOffsetMinutes,
            steps = draft.steps.take(30),
            recurrence = draft.recurrence.takeIf { it in RECURRENCE_RULES && draft.dueAt != null },
        )
        replace(task)
    }

    suspend fun deleteTask(id: Int) = write {
        if (!tasks.removeAll { it.id == id }) throw ApiException("任务不存在", 404)
    }

    suspend fun createIdea(
        content: String,
        durationMinutes: Int?,
        startAt: String?,
        dueAt: String?,
        items: List<IdeaDetail>,
    ): InboxIdea = write {
        InboxIdea(nextId++, content.trim(), durationMinutes, startAt, dueAt, items.take(30))
            .also { ideas.add(0, it) }
    }

    suspend fun updateIdea(
        id: Int,
        content: String,
        durationMinutes: Int?,
        startAt: String?,
        dueAt: String?,
        items: List<IdeaDetail>,
    ): InboxIdea = write {
        val index = ideas.indexOfFirst { it.id == id }
        if (index < 0) throw ApiException("想法不存在", 404)
        InboxIdea(id, content.trim(), durationMinutes, startAt, dueAt, items.take(30)).also { ideas[index] = it }
    }

    suspend fun deleteIdea(id: Int) = write {
        if (!ideas.removeAll { it.id == id }) throw ApiException("想法不存在", 404)
    }

    /** Wipes every local task and idea (the user asked to clear this phone's data). */
    suspend fun clear() = write {
        tasks.clear()
        ideas.clear()
    }

    // Same completion rules as the server: finishing a recurring task creates the next
    // occurrence (steps reset) and the rule moves to it, so re-checking never duplicates.
    private fun applyCompletion(task: TaskItem, completed: Boolean): TaskItem {
        if (!completed) return task.copy(completed = false, completedAt = null)
        if (task.completed) return task
        val now = Instant.now()
        val rule = task.recurrence
        val due = task.dueAt?.let(::parseInstantText)
        if (rule != null && rule in RECURRENCE_RULES && due != null) {
            val nextDue = nextOccurrence(due, rule, ZoneId.systemDefault(), now)
            val shift = java.time.Duration.between(due, nextDue)
            tasks.add(
                task.copy(
                    id = nextId++,
                    dueAt = nextDue.toString(),
                    startAt = task.startAt?.let(::parseInstantText)?.plus(shift)?.toString(),
                    steps = task.steps.map { it.copy(completed = false) },
                    completed = false,
                    completedAt = null,
                ),
            )
            return task.copy(completed = true, completedAt = now.toString(), recurrence = null)
        }
        return task.copy(completed = true, completedAt = now.toString())
    }

    private fun findTask(id: Int): TaskItem =
        tasks.firstOrNull { it.id == id } ?: throw ApiException("任务不存在", 404)

    private fun replace(task: TaskItem): TaskItem {
        val index = tasks.indexOfFirst { it.id == task.id }
        tasks[index] = task
        return task
    }

    private suspend fun <T> read(block: () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) { ensureLoaded() }
        block()
    }

    private suspend fun <T> write(block: () -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            ensureLoaded()
            val result = block()
            persist()
            result
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        storage.get(KEY_DATA)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                tasks = json.getJSONArray("tasks").mapObjects(::decodeTask).toMutableList()
                ideas = json.getJSONArray("ideas").mapObjects(::decodeIdea).toMutableList()
                nextId = json.optInt("next_id", 1)
            }
        }
        val maxId = (tasks.map { it.id } + ideas.map { it.id }).maxOrNull() ?: 0
        nextId = maxOf(nextId, maxId + 1)
        loaded = true
    }

    private fun persist() {
        val json = JSONObject()
            .put("version", 1)
            .put("next_id", nextId)
            .put("tasks", JSONArray().apply { tasks.forEach { put(encodeTask(it)) } })
            .put("ideas", JSONArray().apply { ideas.forEach { put(encodeIdea(it)) } })
        storage.put(KEY_DATA, json.toString())
    }

    companion object {
        private const val PREFERENCES_NAME = "todocloud_local_data"
        private const val KEY_DATA = "data"

        @Volatile private var instance: LocalStore? = null

        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(context.applicationContext).also { instance = it }
            }
    }
}

private fun Any?.orNull(): Any = this ?: JSONObject.NULL

private fun JSONObject.optText(name: String): String? = if (!has(name) || isNull(name)) null else optString(name)

private fun JSONObject.optIntOrNull(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

private fun encodeTask(task: TaskItem): JSONObject = JSONObject()
    .put("id", task.id)
    .put("title", task.title)
    .put("description", task.description.orNull())
    .put("due_at", task.dueAt.orNull())
    .put("reminder_offset_minutes", task.reminderOffsetMinutes.orNull())
    .put("completed", task.completed)
    .put("completed_at", task.completedAt.orNull())
    .put("start_at", task.startAt.orNull())
    .put("duration_minutes", task.durationMinutes.orNull())
    .put("recurrence", task.recurrence.orNull())
    .put("steps", JSONArray().apply {
        task.steps.forEach { put(JSONObject().put("title", it.title).put("completed", it.completed)) }
    })

private fun decodeTask(json: JSONObject) = TaskItem(
    id = json.getInt("id"),
    title = json.getString("title"),
    description = json.optText("description"),
    dueAt = json.optText("due_at"),
    reminderOffsetMinutes = json.optIntOrNull("reminder_offset_minutes"),
    completed = json.optBoolean("completed"),
    completedAt = json.optText("completed_at"),
    steps = json.optJSONArray("steps")?.mapObjects {
        TaskStep(it.optString("title"), it.optBoolean("completed"))
    }.orEmpty(),
    startAt = json.optText("start_at"),
    durationMinutes = json.optIntOrNull("duration_minutes"),
    recurrence = json.optText("recurrence"),
)

private fun encodeIdea(idea: InboxIdea): JSONObject = JSONObject()
    .put("id", idea.id)
    .put("content", idea.content)
    .put("duration_minutes", idea.durationMinutes.orNull())
    .put("start_at", idea.startAt.orNull())
    .put("due_at", idea.dueAt.orNull())
    .put("items", JSONArray().apply { idea.items.forEach { put(JSONObject().put("content", it.content)) } })

private fun decodeIdea(json: JSONObject) = InboxIdea(
    id = json.getInt("id"),
    content = json.getString("content"),
    durationMinutes = json.optIntOrNull("duration_minutes"),
    startAt = json.optText("start_at"),
    dueAt = json.optText("due_at"),
    items = json.optJSONArray("items")?.mapObjects { IdeaDetail(it.optString("content")) }.orEmpty(),
)

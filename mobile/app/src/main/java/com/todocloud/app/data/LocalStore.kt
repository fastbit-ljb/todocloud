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
    private var plans = mutableListOf<JSONObject>()
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
        plans.clear()
    }

    // ---- AI plans (mirrors server/app/api/plans.py) ----

    /** Ideas and open tasks in the shape the planning prompt expects. */
    suspend fun planningSources(ideaIds: Collection<Int>?): Pair<List<JSONObject>, List<JSONObject>> = read {
        val chosen = if (ideaIds == null) ideas else ideas.filter { it.id in ideaIds }
        chosen.map(::encodeIdea) to tasks.filter { !it.completed }.map { task ->
            JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("description", task.description.orNull())
                .put("start_at", task.startAt.orNull())
                .put("due_at", task.dueAt.orNull())
                .put("duration_minutes", task.durationMinutes.orNull())
                .put("steps", JSONArray().apply {
                    task.steps.forEach { put(JSONObject().put("title", it.title).put("completed", it.completed)) }
                })
                .put("recurrence", task.recurrence.orNull())
        }
    }

    suspend fun listPlans(): List<AiPlan> = read { plans.takeLast(10).reversed().map(::decodePlan) }

    suspend fun allPlansForHistory(): List<AiPlan> = read { plans.reversed().map(::decodePlan) }

    suspend fun createPlan(proposal: JSONObject, messages: List<PlanMessage>): AiPlan = write {
        val plan = JSONObject()
            .put("id", nextId++)
            .put("status", "draft")
            .put("proposal", proposal)
            .put("messages", encodeMessages(messages))
        plans.add(plan)
        decodePlan(plan)
    }

    /** The draft's current proposal and conversation, for a revision turn. */
    suspend fun draftForRevision(planId: Int): Pair<JSONObject, List<PlanMessage>> = read {
        val plan = findPlan(planId)
        if (plan.optString("status") != "draft") throw ApiException("已采用的计划不能继续修改", 409)
        JSONObject(plan.getJSONObject("proposal").toString()) to decodeMessages(plan.optJSONArray("messages"))
    }

    /** Stores a revision turn: a new proposal (or null = question answered, plan unchanged). */
    suspend fun saveRevision(planId: Int, proposal: JSONObject?, messages: List<PlanMessage>): AiPlan = write {
        val plan = findPlan(planId)
        if (proposal != null) plan.put("proposal", proposal)
        plan.put("messages", encodeMessages(messages))
        decodePlan(plan)
    }

    suspend fun acceptPlan(planId: Int, chosen: List<PlanItem>?): PlanAcceptResult = write {
        val plan = findPlan(planId)
        if (plan.optString("status") != "draft") throw ApiException("这份计划已经采用", 409)
        val proposal = plan.getJSONObject("proposal")
        val proposed = decodeItems(proposal.optJSONArray("items"))
        val selected = if (chosen == null) proposed else {
            val keys = proposed.map { it.sourceType to it.sourceId }.toSet()
            if (chosen.any { (it.sourceType to it.sourceId) !in keys }) throw ApiException("包含不属于这份计划的安排", 422)
            if (chosen.isEmpty()) throw ApiException("请至少选择一项安排", 400)
            chosen
        }
        val createdIds = JSONArray()
        val convertedIdeas = JSONArray()
        val snapshots = JSONArray()
        val changed = mutableListOf<TaskItem>()
        for (item in selected) {
            val rule = item.recurrence?.takeIf { it in RECURRENCE_RULES && item.dueAt != null }
            val steps = item.steps.map { TaskStep(it.title) }
            when (item.sourceType) {
                "task" -> {
                    val task = tasks.firstOrNull { it.id == item.sourceId && !it.completed } ?: continue
                    snapshots.put(encodeTask(task))
                    val updated = task.copy(
                        startAt = item.startAt,
                        dueAt = item.dueAt,
                        durationMinutes = item.durationMinutes,
                        reminderOffsetMinutes = item.reminderOffsetMinutes ?: task.reminderOffsetMinutes,
                        steps = steps.ifEmpty { task.steps },
                        recurrence = rule ?: task.recurrence.takeIf { item.dueAt != null },
                    )
                    changed += replace(updated)
                }
                "prompt" -> {
                    val created = TaskItem(
                        id = nextId++, title = item.title, description = item.description,
                        dueAt = item.dueAt, reminderOffsetMinutes = item.reminderOffsetMinutes,
                        completed = false, completedAt = null, steps = steps,
                        startAt = item.startAt, durationMinutes = item.durationMinutes, recurrence = rule,
                    )
                    tasks.add(created)
                    createdIds.put(created.id)
                    changed += created
                }
                else -> {
                    val idea = ideas.firstOrNull { it.id == item.sourceId } ?: continue
                    ideas.remove(idea)
                    convertedIdeas.put(encodeIdea(idea))
                    val created = TaskItem(
                        id = nextId++, title = item.title,
                        description = item.description ?: idea.content.takeIf { it != item.title },
                        dueAt = item.dueAt, reminderOffsetMinutes = item.reminderOffsetMinutes,
                        completed = false, completedAt = null,
                        steps = steps.ifEmpty { idea.items.map { TaskStep(it.content) } },
                        startAt = item.startAt, durationMinutes = item.durationMinutes ?: idea.durationMinutes,
                        recurrence = rule,
                    )
                    tasks.add(created)
                    createdIds.put(created.id)
                    changed += created
                }
            }
        }
        plan.put("proposal", JSONObject()
            .put("summary", proposal.optString("summary"))
            .put("items", JSONArray().apply { selected.forEach { put(encodePlanItem(it)) } }))
        plan.put("accept_record", JSONObject()
            .put("created_task_ids", createdIds)
            .put("converted_ideas", convertedIdeas)
            .put("task_snapshots", snapshots)
            .put("original_items", proposal.optJSONArray("items") ?: JSONArray()))
        plan.put("status", "accepted")
        plan.put("accepted_at", System.currentTimeMillis())
        PlanAcceptResult(decodePlan(plan), changed)
    }

    suspend fun undoPlan(planId: Int): PlanUndoResult = write {
        val plan = findPlan(planId)
        val record = plan.optJSONObject("accept_record")
        if (plan.optString("status") != "accepted" || record == null) throw ApiException("这份计划不能撤回", 409)
        if (System.currentTimeMillis() - plan.optLong("accepted_at") > UNDO_WINDOW_MILLIS) {
            throw ApiException("采用超过 24 小时的计划不能撤回", 409)
        }
        val createdIds = record.optJSONArray("created_task_ids")?.let { a -> (0 until a.length()).map(a::getInt) }.orEmpty()
        val deleted = tasks.filter { it.id in createdIds }.map { it.id }
        tasks.removeAll { it.id in createdIds }
        record.optJSONArray("converted_ideas")?.mapObjects(::decodeIdea)?.forEach { idea ->
            if (ideas.none { it.id == idea.id }) ideas.add(0, idea)
        }
        val restored = mutableListOf<TaskItem>()
        record.optJSONArray("task_snapshots")?.mapObjects(::decodeTask)?.forEach { before ->
            val current = tasks.firstOrNull { it.id == before.id } ?: return@forEach
            restored += replace(
                current.copy(
                    startAt = before.startAt, dueAt = before.dueAt, durationMinutes = before.durationMinutes,
                    reminderOffsetMinutes = before.reminderOffsetMinutes, steps = before.steps,
                    recurrence = before.recurrence,
                ),
            )
        }
        plan.put("proposal", JSONObject()
            .put("summary", plan.getJSONObject("proposal").optString("summary"))
            .put("items", record.optJSONArray("original_items") ?: JSONArray()))
        plan.remove("accept_record")
        plan.remove("accepted_at")
        plan.put("status", "draft")
        PlanUndoResult(decodePlan(plan), deleted, restored)
    }

    private fun findPlan(id: Int): JSONObject =
        plans.firstOrNull { it.optInt("id") == id } ?: throw ApiException("计划不存在", 404)

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
                plans = json.optJSONArray("plans")?.mapObjects { it }?.toMutableList() ?: mutableListOf()
                nextId = json.optInt("next_id", 1)
            }
        }
        val maxId = (tasks.map { it.id } + ideas.map { it.id } + plans.map { it.optInt("id") }).maxOrNull() ?: 0
        nextId = maxOf(nextId, maxId + 1)
        loaded = true
    }

    private fun persist() {
        val json = JSONObject()
            .put("version", 1)
            .put("next_id", nextId)
            .put("tasks", JSONArray().apply { tasks.forEach { put(encodeTask(it)) } })
            .put("ideas", JSONArray().apply { ideas.forEach { put(encodeIdea(it)) } })
            .put("plans", JSONArray().apply { plans.takeLast(20).forEach { put(it) } })
        storage.put(KEY_DATA, json.toString())
    }

    companion object {
        private const val PREFERENCES_NAME = "todocloud_local_data"
        private const val KEY_DATA = "data"
        private const val UNDO_WINDOW_MILLIS = 24L * 60 * 60 * 1000

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

private fun encodePlanItem(item: PlanItem): JSONObject = JSONObject()
    .put("source_type", item.sourceType)
    .put("source_id", item.sourceId)
    .put("title", item.title)
    .put("description", item.description.orNull())
    .put("start_at", item.startAt.orNull())
    .put("due_at", item.dueAt.orNull())
    .put("duration_minutes", item.durationMinutes.orNull())
    .put("reminder_offset_minutes", item.reminderOffsetMinutes.orNull())
    .put("steps", JSONArray().apply { item.steps.forEach { put(JSONObject().put("title", it.title)) } })
    .put("note", item.note)
    .put("recurrence", item.recurrence.orNull())

private fun decodeItems(array: JSONArray?): List<PlanItem> = array?.mapObjects { item ->
    PlanItem(
        sourceType = item.getString("source_type"),
        sourceId = item.getInt("source_id"),
        title = item.getString("title"),
        description = item.optText("description"),
        startAt = item.optText("start_at"),
        dueAt = item.optText("due_at"),
        durationMinutes = item.optIntOrNull("duration_minutes"),
        reminderOffsetMinutes = item.optIntOrNull("reminder_offset_minutes"),
        steps = item.optJSONArray("steps")?.mapObjects { PlanStep(it.optString("title")) }.orEmpty(),
        note = item.optString("note"),
        recurrence = item.optText("recurrence"),
    )
}.orEmpty()

private fun encodeMessages(messages: List<PlanMessage>): JSONArray = JSONArray().apply {
    messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
}

private fun decodeMessages(array: JSONArray?): List<PlanMessage> =
    array?.mapObjects { PlanMessage(it.optString("role"), it.optString("content")) }.orEmpty()

private fun decodePlan(json: JSONObject): AiPlan {
    val proposal = json.optJSONObject("proposal") ?: JSONObject()
    return AiPlan(
        id = json.getInt("id"),
        status = json.optString("status", "draft"),
        title = "我的 AI 计划",
        summary = proposal.optString("summary"),
        items = decodeItems(proposal.optJSONArray("items")),
        messages = decodeMessages(json.optJSONArray("messages")),
    )
}

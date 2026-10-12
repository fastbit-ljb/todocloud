package com.todocloud.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.todocloud.app.BuildConfig
import com.todocloud.app.data.AiTaskStep
import com.todocloud.app.data.TaskStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.concurrent.TimeUnit

class TodoCloudRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val sessionStore = SecureSessionStore(context)
    private val offlineStore = SecureSessionStore(context, OFFLINE_PREFERENCES_NAME)
    private val local by lazy { LocalStore.get(appContext) }

    /** Start using the app on this phone only: no account, nothing sent to the server. */
    fun startLocalSession(): Session =
        Session(LOCAL_SESSION_TOKEN, LOCAL_SESSION_TOKEN, email = "", displayName = "本机").also(::saveSession)

    /** Deletes every task and idea kept in local mode. */
    suspend fun clearLocalData() = local.clear()

    // AI features run on the server, which local mode never talks to.
    private fun requireCloud(token: String) {
        if (token == LOCAL_SESSION_TOKEN) throw ApiException(LOCAL_AI_UNAVAILABLE)
    }
    private val refreshMutex = Mutex()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    fun savedSession(): Session? {
        val token = sessionStore.get(KEY_TOKEN) ?: return null
        val refreshToken = sessionStore.get(KEY_REFRESH_TOKEN) ?: return null
        return Session(
            token = token,
            refreshToken = refreshToken,
            email = sessionStore.get(KEY_EMAIL).orEmpty(),
            displayName = sessionStore.get(KEY_DISPLAY_NAME),
        )
    }

    fun saveSession(session: Session) {
        sessionStore.put(KEY_TOKEN, session.token)
        sessionStore.put(KEY_REFRESH_TOKEN, session.refreshToken)
        sessionStore.put(KEY_EMAIL, session.email)
        session.displayName?.let { sessionStore.put(KEY_DISPLAY_NAME, it) }
    }

    fun clearSession() {
        sessionStore.clear()
    }

    suspend fun authenticate(email: String, password: String, register: Boolean): Session {
        val path = if (register) "/auth/register" else "/auth/login"
        val payload = JSONObject().apply {
            put("email", email)
            put("password", password)
            if (register) put("timezone", "Asia/Shanghai")
        }
        val response = request(
            Request.Builder()
                .url(baseUrl + path)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build(),
        ) as JSONObject
        return parseSession(response)
    }

    suspend fun logout(session: Session) {
        if (session.isLocal) {
            // Leaving local mode keeps its data on the phone for the next time.
            clearSession()
            return
        }
        // The UI may still hold the session from sign-in while the repository
        // has since rotated its refresh token in the secure store.
        val refreshToken = savedSession()?.refreshToken ?: session.refreshToken
        runCatching {
            request(
                Request.Builder()
                    .url(baseUrl + "/auth/logout")
                    .post(
                        JSONObject().put("refresh_token", refreshToken)
                            .toString().toRequestBody(jsonMediaType),
                    )
                    .build(),
            )
        }
        clearSession()
        clearOfflineSnapshot()
    }

    /**
     * Saves the last synced lists in the server's own JSON shape, encrypted with the
     * same Keystore key as the session, so they can be shown when the network is down.
     */
    fun saveOfflineSnapshot(email: String, tasks: List<TaskItem>, ideas: List<InboxIdea>) {
        if (savedSession()?.isLocal == true) return // local mode is already on the phone
        val payload = JSONObject()
            .put("owner", email)
            .put("synced_at", System.currentTimeMillis())
            .put("tasks", JSONArray().apply { tasks.forEach { put(taskJson(it)) } })
            .put("ideas", JSONArray().apply { ideas.forEach { put(ideaJson(it)) } })
        runCatching { offlineStore.put(KEY_OFFLINE_SNAPSHOT, payload.toString()) }
    }

    /** The snapshot saved for [email], or null (none yet, another account's, or unreadable). */
    fun loadOfflineSnapshot(email: String): OfflineSnapshot? {
        val raw = offlineStore.get(KEY_OFFLINE_SNAPSHOT) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optString("owner") != email) {
                null
            } else {
                OfflineSnapshot(
                    tasks = json.getJSONArray("tasks").toTaskItems(),
                    ideas = json.getJSONArray("ideas").toIdeas(),
                    syncedAtMillis = json.getLong("synced_at"),
                )
            }
        }.getOrNull()
    }

    fun clearOfflineSnapshot() {
        offlineStore.remove(KEY_OFFLINE_SNAPSHOT)
    }

    private fun taskJson(task: TaskItem): JSONObject = JSONObject()
        .put("id", task.id)
        .put("title", task.title)
        .put("description", task.description ?: JSONObject.NULL)
        .put("due_at", task.dueAt ?: JSONObject.NULL)
        .put("reminder_offset_minutes", task.reminderOffsetMinutes ?: JSONObject.NULL)
        .put("completed", task.completed)
        .put("completed_at", task.completedAt ?: JSONObject.NULL)
        .put("start_at", task.startAt ?: JSONObject.NULL)
        .put("duration_minutes", task.durationMinutes ?: JSONObject.NULL)
        .put("recurrence", task.recurrence ?: JSONObject.NULL)
        .put("steps", JSONArray().apply {
            task.steps.forEach { put(JSONObject().put("title", it.title).put("completed", it.completed)) }
        })

    private fun ideaJson(idea: InboxIdea): JSONObject =
        ideaPayload(idea.content, idea.durationMinutes, idea.startAt, idea.dueAt, idea.items)
            .put("id", idea.id)

    private fun parseSession(response: JSONObject): Session {
        val user = response.getJSONObject("user")
        return Session(
            token = response.getString("access_token"),
            refreshToken = response.getString("refresh_token"),
            email = user.getString("email"),
            displayName = user.optNullableString("display_name"),
        )
    }

    suspend fun listTasks(token: String): List<TaskItem> {
        if (token == LOCAL_SESSION_TOKEN) return local.listTasks()
        val response = authorizedRequest(token) { authToken ->
            Request.Builder().url(baseUrl + "/tasks").authorized(authToken).get().build()
        }
        return (response as JSONArray).toTaskItems()
    }

    suspend fun createTask(
        token: String,
        title: String,
        description: String?,
        dueAt: String?,
        reminderOffsetMinutes: Int?,
        steps: List<TaskStep> = emptyList(),
        startAt: String? = null,
        durationMinutes: Int? = null,
        recurrence: String? = null,
    ): TaskItem {
        if (token == LOCAL_SESSION_TOKEN) {
            return local.createTask(
                title, description, dueAt, reminderOffsetMinutes, steps, startAt, durationMinutes, recurrence,
            )
        }
        val payload = JSONObject().put("title", title)
        if (!description.isNullOrBlank()) payload.put("description", description)
        if (startAt != null) payload.put("start_at", startAt)
        if (dueAt != null) payload.put("due_at", dueAt)
        if (durationMinutes != null) payload.put("duration_minutes", durationMinutes)
        if (recurrence != null) payload.put("recurrence", recurrence)
        if (reminderOffsetMinutes != null) {
            payload.put("reminder_offset_minutes", reminderOffsetMinutes)
        }
        payload.put("steps", JSONArray().apply {
            steps.forEach { step ->
                put(JSONObject().put("title", step.title).put("completed", step.completed))
            }
        })
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + "/tasks")
                .authorized(authToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parseTask(response as JSONObject)
    }

    suspend fun updateTask(
        token: String,
        id: Int,
        completed: Boolean? = null,
        dueAt: String? = null,
        reminderOffsetMinutes: Int? = null,
        steps: List<TaskStep>? = null,
        includeSchedule: Boolean = false,
    ): TaskItem {
        if (token == LOCAL_SESSION_TOKEN) {
            return local.updateTask(id, completed, dueAt, reminderOffsetMinutes, steps, includeSchedule)
        }
        val payload = JSONObject()
        completed?.let { payload.put("completed", it) }
        if (includeSchedule) {
            payload.put("due_at", dueAt ?: JSONObject.NULL)
            payload.put("reminder_offset_minutes", reminderOffsetMinutes ?: JSONObject.NULL)
        }
        steps?.let { values ->
            payload.put("steps", JSONArray().apply {
                values.forEach { step ->
                    put(JSONObject().put("title", step.title).put("completed", step.completed))
                }
            })
        }
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(authToken)
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parseTask(response as JSONObject)
    }

    /** Replaces every editable field of a task; null clears a field. Completion is untouched. */
    suspend fun editTask(token: String, id: Int, draft: TaskDraft): TaskItem {
        if (token == LOCAL_SESSION_TOKEN) return local.editTask(id, draft)
        fun Any?.orNull(): Any = this ?: JSONObject.NULL
        val payload = JSONObject()
            .put("title", draft.title)
            .put("description", draft.description.orNull())
            .put("start_at", draft.startAt.orNull())
            .put("due_at", draft.dueAt.orNull())
            .put("duration_minutes", draft.durationMinutes.orNull())
            .put("reminder_offset_minutes", draft.reminderOffsetMinutes.orNull())
            .put("recurrence", draft.recurrence.orNull())
            .put("steps", JSONArray().apply {
                draft.steps.forEach { step ->
                    put(JSONObject().put("title", step.title).put("completed", step.completed))
                }
            })
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(authToken)
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parseTask(response as JSONObject)
    }

    suspend fun deleteTask(token: String, id: Int) {
        if (token == LOCAL_SESSION_TOKEN) return local.deleteTask(id)
        authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(authToken)
                .delete()
                .build()
        }
    }

    suspend fun listIdeas(token: String): List<InboxIdea> {
        if (token == LOCAL_SESSION_TOKEN) return local.listIdeas()
        val response = authorizedRequest(token) { authToken ->
            Request.Builder().url(baseUrl + "/ideas").authorized(authToken).get().build()
        }
        return (response as JSONArray).toIdeas()
    }

    suspend fun createIdea(
        token: String,
        content: String,
        durationMinutes: Int?,
        startAt: String?,
        dueAt: String?,
        items: List<IdeaDetail>,
    ): InboxIdea {
        if (token == LOCAL_SESSION_TOKEN) return local.createIdea(content, durationMinutes, startAt, dueAt, items)
        val payload = ideaPayload(content, durationMinutes, startAt, dueAt, items)
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + "/ideas")
                .authorized(authToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parseIdea(response as JSONObject)
    }

    suspend fun updateIdea(
        token: String,
        id: Int,
        content: String,
        durationMinutes: Int?,
        startAt: String?,
        dueAt: String?,
        items: List<IdeaDetail>,
    ): InboxIdea {
        if (token == LOCAL_SESSION_TOKEN) return local.updateIdea(id, content, durationMinutes, startAt, dueAt, items)
        val payload = ideaPayload(content, durationMinutes, startAt, dueAt, items)
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/ideas/$id")
                .authorized(authToken)
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parseIdea(response as JSONObject)
    }

    suspend fun deleteIdea(token: String, id: Int) {
        if (token == LOCAL_SESSION_TOKEN) return local.deleteIdea(id)
        authorizedRequest(token) { authToken ->
            Request.Builder().url("$baseUrl/ideas/$id").authorized(authToken).delete().build()
        }
    }

    suspend fun listPlans(token: String): List<AiPlan> {
        if (token == LOCAL_SESSION_TOKEN) return emptyList()
        val response = authorizedRequest(token) { authToken ->
            Request.Builder().url(baseUrl + "/plans").authorized(authToken).get().build()
        }
        return (response as JSONArray).toPlans()
    }

    suspend fun generatePlan(token: String, message: String, ideaIds: Collection<Int>): AiPlan {
        requireCloud(token)
        val payload = planningPayload(message, ideaIds)
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + "/plans/generate")
                .authorized(authToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        }
        return parsePlan(response as JSONObject)
    }

    suspend fun chatPlan(
        token: String,
        message: String,
        ideaIds: Collection<Int>,
        history: List<PlanMessage>,
        forcePlan: Boolean,
        images: List<Uri> = emptyList(),
    ): PlanChatResult {
        requireCloud(token)
        val payload = planningPayload(message, ideaIds).apply {
            put("force_plan", forcePlan)
            put(
                "history",
                JSONArray().apply {
                    history.takeLast(12).forEach { item ->
                        put(JSONObject().put("role", item.role).put("content", item.content.take(4000)))
                    }
                },
            )
        }
        val body = planRequestBody(payload, images)
        val path = if (images.isEmpty()) "/plans/chat" else "/plans/chat/images"
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + path)
                .authorized(authToken)
                .post(body)
                .build()
        } as JSONObject
        return parsePlanChat(response)
    }

    /** JSON body without images; multipart (payload JSON + up to 3 compressed images) with them. */
    private suspend fun planRequestBody(payload: JSONObject, images: List<Uri>): RequestBody {
        if (images.isEmpty()) return payload.toString().toRequestBody(jsonMediaType)
        val prepared = withContext(Dispatchers.IO) {
            images.take(MAX_PLAN_IMAGES).map(::prepareScreenshotForUpload)
        }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload", payload.toString())
            .apply {
                prepared.forEachIndexed { index, (bytes, contentType) ->
                    addFormDataPart(
                        "files",
                        "plan-$index.${contentType.substringAfter('/', "jpg")}",
                        bytes.toRequestBody(contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()),
                    )
                }
            }
            .build()
    }

    suspend fun revisePlan(
        token: String,
        planId: Int,
        message: String,
        images: List<Uri> = emptyList(),
    ): AiPlan {
        requireCloud(token)
        val payload = planningPayload(message)
        val body = planRequestBody(payload, images)
        val suffix = if (images.isEmpty()) "messages" else "messages/images"
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/plans/$planId/$suffix")
                .authorized(authToken)
                .post(body)
                .build()
        }
        return parsePlan(response as JSONObject)
    }

    /** Adopt a draft plan; [items] = the chosen (possibly edited) items, null = all of them. */
    suspend fun acceptPlan(token: String, planId: Int, items: List<PlanItem>? = null): PlanAcceptResult {
        requireCloud(token)
        val body = JSONObject()
        if (items != null) {
            body.put("items", JSONArray().apply { items.forEach { put(planItemJson(it)) } })
        }
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/plans/$planId/accept")
                .authorized(authToken)
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        } as JSONObject
        return PlanAcceptResult(
            plan = parsePlan(response.getJSONObject("plan")),
            tasks = response.getJSONArray("tasks").toTaskItems(),
        )
    }

    /** Reverse an accepted plan: created tasks are deleted, the plan becomes a draft again. */
    suspend fun undoPlan(token: String, planId: Int): PlanUndoResult {
        requireCloud(token)
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/plans/$planId/undo")
                .authorized(authToken)
                .post("{}".toRequestBody(jsonMediaType))
                .build()
        } as JSONObject
        val deleted = response.getJSONArray("deleted_task_ids")
        return PlanUndoResult(
            plan = parsePlan(response.getJSONObject("plan")),
            deletedTaskIds = (0 until deleted.length()).map { deleted.getInt(it) },
            restoredTasks = response.getJSONArray("tasks").toTaskItems(),
        )
    }

    private fun planItemJson(item: PlanItem): JSONObject {
        fun Any?.orNull(): Any = this ?: JSONObject.NULL
        return JSONObject()
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
    }

    suspend fun parseScreenshot(token: String, imageUri: Uri): AiParseResult {
        requireCloud(token)
        val (imageBytes, contentType) = withContext(Dispatchers.IO) {
            prepareScreenshotForUpload(imageUri)
        }
        val extension = contentType.substringAfter('/', "jpg")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "screenshot.$extension",
                imageBytes.toRequestBody(contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()),
            )
            .addFormDataPart("reference_at", currentAiReferenceAt())
            .addFormDataPart("timezone_name", java.time.ZoneId.systemDefault().id)
            .build()
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + "/ai/parse-screenshot")
                .authorized(authToken)
                .post(body)
                .build()
        } as JSONObject
        val candidates = response.getJSONArray("candidates").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        AiTaskCandidate(
                            title = item.getString("title"),
                            description = item.optNullableString("description"),
                            dueAt = item.optNullableString("due_at"),
                            reminderOffsetMinutes = item.optNullableInt("reminder_offset_minutes"),
                            confidence = item.optDouble("confidence", 0.0),
                            sourceText = item.optNullableString("source_text"),
                            steps = item.optSteps(),
                        ),
                    )
                }
            }
        }
        return AiParseResult(
            attachmentId = response.optNullableInt("attachment_id"),
            parseId = response.optNullableInt("parse_id"),
            candidates = candidates,
        )
    }

    private fun prepareScreenshotForUpload(uri: Uri): Pair<ByteArray, String> {
        val resolver = appContext.contentResolver
        val original = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val originalType = resolver.getType(uri) ?: "image/jpeg"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        val maxOriginalEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxOriginalEdge <= MAX_AI_IMAGE_EDGE && original.size <= MAX_AI_IMAGE_BYTES) {
            return original to originalType
        }

        val options = BitmapFactory.Options()
        var sampleSize = 1
        while (maxOriginalEdge / (sampleSize * 2) > MAX_AI_IMAGE_EDGE * 2) {
            sampleSize *= 2
        }
        options.inSampleSize = sampleSize
        val decoded = BitmapFactory.decodeByteArray(original, 0, original.size, options)
            ?: return original to originalType

        val exif = runCatching { ExifInterface(ByteArrayInputStream(original)) }.getOrNull()
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ) ?: ExifInterface.ORIENTATION_NORMAL
        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                transform.setScale(-1f, 1f)
                transform.postRotate(180f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                transform.setScale(-1f, 1f)
                transform.postRotate(270f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                transform.setScale(-1f, 1f)
                transform.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(270f)
        }

        val oriented = if (!transform.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val scale = minOf(1f, MAX_AI_IMAGE_EDGE.toFloat() / maxOf(oriented.width, oriented.height))
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== oriented) oriented.recycle() }
        } else {
            oriented
        }
        val output = ByteArrayOutputStream()
        val compressed = resized.compress(Bitmap.CompressFormat.JPEG, 94, output)
        resized.recycle()
        val optimized = output.toByteArray()
        return if (compressed && optimized.size < original.size) {
            optimized to "image/jpeg"
        } else {
            original to originalType
        }
    }

    suspend fun parseText(token: String, text: String): AiParseResult {
        requireCloud(token)
        val payload = JSONObject().apply {
            put("text", text)
            put("reference_at", currentAiReferenceAt())
            put("timezone_name", java.time.ZoneId.systemDefault().id)
        }
        val response = authorizedRequest(token) { authToken ->
            Request.Builder()
                .url(baseUrl + "/ai/parse-text")
                .authorized(authToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
        } as JSONObject
        return parseAiResult(response)
    }

    private fun parseAiResult(response: JSONObject): AiParseResult {
        val candidates = response.getJSONArray("candidates").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        AiTaskCandidate(
                            title = item.getString("title"),
                            description = item.optNullableString("description"),
                            dueAt = item.optNullableString("due_at"),
                            reminderOffsetMinutes = item.optNullableInt("reminder_offset_minutes"),
                            confidence = item.optDouble("confidence", 0.0),
                            sourceText = item.optNullableString("source_text"),
                            steps = item.optSteps(),
                        ),
                    )
                }
            }
        }
        return AiParseResult(
            attachmentId = response.optNullableInt("attachment_id"),
            parseId = response.optNullableInt("parse_id"),
            candidates = candidates,
        )
    }

    private fun currentAiReferenceAt(): String = java.time.ZonedDateTime
        .now(java.time.ZoneId.systemDefault())
        .toOffsetDateTime()
        .toString()

    private suspend fun refreshSession(refreshToken: String): Session {
        val response = request(
            Request.Builder()
                .url(baseUrl + "/auth/refresh")
                .post(
                    JSONObject().put("refresh_token", refreshToken)
                        .toString().toRequestBody(jsonMediaType),
                )
                .build(),
        ) as JSONObject
        return parseSession(response).also(::saveSession)
    }

    private suspend fun authorizedRequest(
        token: String,
        requestFactory: (String) -> Request,
    ): Any {
        // The Composable may hold its original access token for hours. Always
        // start with the newest token, then rotate it after a 401. The mutex
        // lets concurrent requests reuse a token refreshed by another call.
        var attemptedToken = sessionStore.get(KEY_TOKEN) ?: token
        repeat(MAX_AUTH_ATTEMPTS) { attempt ->
            try {
                return request(requestFactory(attemptedToken))
            } catch (error: ApiException) {
                if (error.statusCode != 401 || attempt == MAX_AUTH_ATTEMPTS - 1) throw error
                val failedToken = attemptedToken
                attemptedToken = refreshMutex.withLock {
                    val latestToken = sessionStore.get(KEY_TOKEN)
                    if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                        latestToken
                    } else {
                        val refreshToken = sessionStore.get(KEY_REFRESH_TOKEN) ?: throw error
                        refreshSession(refreshToken).token
                    }
                }
            }
        }
        error("Authentication retry limit reached")
    }

    private suspend fun request(request: Request): Any = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw ApiException(
                    detail?.ifBlank { null } ?: "请求失败（${response.code}）",
                    response.code,
                )
            }
            when {
                body.isBlank() -> JSONObject()
                body.trimStart().startsWith("[") -> JSONArray(body)
                else -> JSONObject(body)
            }
        }
    }

    private fun Request.Builder.authorized(token: String): Request.Builder =
        addHeader("Authorization", "Bearer $token")

    private fun parseTask(json: JSONObject): TaskItem = TaskItem(
        id = json.getInt("id"),
        title = json.getString("title"),
        description = json.optNullableString("description"),
        dueAt = json.optNullableString("due_at"),
        reminderOffsetMinutes = json.optNullableInt("reminder_offset_minutes"),
        completed = json.optBoolean("completed"),
        completedAt = json.optNullableString("completed_at"),
        steps = json.optTaskSteps(),
        startAt = json.optNullableString("start_at"),
        durationMinutes = json.optNullableInt("duration_minutes"),
        recurrence = json.optNullableString("recurrence"),
    )

    private fun ideaPayload(
        content: String,
        durationMinutes: Int?,
        startAt: String?,
        dueAt: String?,
        items: List<IdeaDetail>,
    ): JSONObject = JSONObject().apply {
        put("content", content)
        put("duration_minutes", durationMinutes ?: JSONObject.NULL)
        put("start_at", startAt ?: JSONObject.NULL)
        put("due_at", dueAt ?: JSONObject.NULL)
        put("items", JSONArray().apply {
            items.forEach { item -> put(JSONObject().put("content", item.content)) }
        })
    }

    private fun planningPayload(
        message: String,
        ideaIds: Collection<Int>? = null,
    ): JSONObject = JSONObject().apply {
        put("message", message)
        if (ideaIds != null) {
            put("idea_ids", JSONArray().apply { ideaIds.forEach(::put) })
        }
        put("reference_at", currentAiReferenceAt())
        put("timezone_name", java.time.ZoneId.systemDefault().id)
    }

    private fun parseIdea(json: JSONObject): InboxIdea = InboxIdea(
        id = json.getInt("id"),
        content = json.getString("content"),
        durationMinutes = json.optNullableInt("duration_minutes"),
        startAt = json.optNullableString("start_at"),
        dueAt = json.optNullableString("due_at"),
        items = json.optJSONArray("items")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val content = array.optJSONObject(index)?.optString("content")?.trim().orEmpty()
                    if (content.isNotBlank()) add(IdeaDetail(content))
                }
            }
        }.orEmpty(),
    )

    private fun parsePlanChat(json: JSONObject): PlanChatResult {
        val reply = json.optString("reply")
        val plan = json.optJSONObject("plan")
        return if (json.optString("kind") == "plan" && plan != null) {
            PlanChatResult.Created(reply, parsePlan(plan))
        } else {
            PlanChatResult.Answer(reply)
        }
    }

    private fun parsePlan(json: JSONObject): AiPlan {
        val proposal = json.getJSONObject("proposal")
        return AiPlan(
            id = json.getInt("id"),
            status = json.getString("status"),
            title = json.getString("title"),
            summary = proposal.optString("summary"),
            items = proposal.optJSONArray("items")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            PlanItem(
                                sourceType = item.getString("source_type"),
                                sourceId = item.getInt("source_id"),
                                title = item.getString("title"),
                                description = item.optNullableString("description"),
                                startAt = item.optNullableString("start_at"),
                                dueAt = item.optNullableString("due_at"),
                                durationMinutes = item.optNullableInt("duration_minutes"),
                                reminderOffsetMinutes = item.optNullableInt("reminder_offset_minutes"),
                                steps = item.optJSONArray("steps")?.let { steps ->
                                    buildList {
                                        for (stepIndex in 0 until steps.length()) {
                                            val title = steps.optJSONObject(stepIndex)
                                                ?.optString("title")?.trim().orEmpty()
                                            if (title.isNotBlank()) add(PlanStep(title))
                                        }
                                    }
                                }.orEmpty(),
                                note = item.optString("note"),
                                recurrence = item.optNullableString("recurrence"),
                            ),
                        )
                    }
                }
            }.orEmpty(),
            messages = json.optJSONArray("messages")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(PlanMessage(item.optString("role"), item.optString("content")))
                    }
                }
            }.orEmpty(),
        )
    }

    private fun JSONObject.optTaskSteps(): List<TaskStep> {
        val array = optJSONArray("steps") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                if (title.isNotBlank()) {
                    add(TaskStep(title = title, completed = item.optBoolean("completed", false)))
                }
            }
        }
    }

    private fun JSONObject.optSteps(): List<AiTaskStep> {
        val array = optJSONArray("steps") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                val title = item?.optString("title")?.trim().orEmpty()
                if (title.isNotBlank()) add(AiTaskStep(title))
            }
        }
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name)
    }

    private fun JSONArray.toTaskItems(): List<TaskItem> = buildList {
        for (index in 0 until length()) add(parseTask(getJSONObject(index)))
    }

    private fun JSONArray.toIdeas(): List<InboxIdea> = buildList {
        for (index in 0 until length()) add(parseIdea(getJSONObject(index)))
    }

    private fun JSONArray.toPlans(): List<AiPlan> = buildList {
        for (index in 0 until length()) add(parsePlan(getJSONObject(index)))
    }

    private companion object {
        const val LOCAL_AI_UNAVAILABLE = "本机模式暂不能使用 AI 功能，登录账号后可用"
        const val OFFLINE_PREFERENCES_NAME = "todocloud_offline"
        const val KEY_OFFLINE_SNAPSHOT = "snapshot"
        const val MAX_AUTH_ATTEMPTS = 3
        const val MAX_AI_IMAGE_EDGE = 2048
        const val MAX_PLAN_IMAGES = 3
        const val MAX_AI_IMAGE_BYTES = 1_500_000
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}

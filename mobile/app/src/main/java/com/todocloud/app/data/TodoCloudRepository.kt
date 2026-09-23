package com.todocloud.app.data

import android.content.Context
import android.net.Uri
import com.todocloud.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class TodoCloudRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val sessionStore = SecureSessionStore(context)
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
        runCatching {
            request(
                Request.Builder()
                    .url(baseUrl + "/auth/logout")
                    .post(
                        JSONObject().put("refresh_token", session.refreshToken)
                            .toString().toRequestBody(jsonMediaType),
                    )
                    .build(),
            )
        }
        clearSession()
    }

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
    ): TaskItem {
        val payload = JSONObject().put("title", title)
        if (!description.isNullOrBlank()) payload.put("description", description)
        if (dueAt != null) payload.put("due_at", dueAt)
        if (reminderOffsetMinutes != null) {
            payload.put("reminder_offset_minutes", reminderOffsetMinutes)
        }
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
        includeSchedule: Boolean = false,
    ): TaskItem {
        val payload = JSONObject()
        completed?.let { payload.put("completed", it) }
        if (includeSchedule) {
            payload.put("due_at", dueAt ?: JSONObject.NULL)
            payload.put("reminder_offset_minutes", reminderOffsetMinutes ?: JSONObject.NULL)
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

    suspend fun deleteTask(token: String, id: Int) {
        authorizedRequest(token) { authToken ->
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(authToken)
                .delete()
                .build()
        }
    }

    suspend fun parseScreenshot(token: String, imageUri: Uri): AiParseResult {
        val contentResolver = appContext.contentResolver
        val imageBytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw ApiException("无法读取图片")
        val contentType = contentResolver.getType(imageUri) ?: "image/jpeg"
        val extension = contentType.substringAfter('/', "jpg")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "screenshot.$extension",
                imageBytes.toRequestBody(contentType.toMediaTypeOrNull() ?: "image/jpeg".toMediaType()),
            )
            .addFormDataPart("reference_at", Instant.now().toString())
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
                        ),
                    )
                }
            }
        }
        return AiParseResult(
            attachmentId = response.getInt("attachment_id"),
            parseId = response.getInt("parse_id"),
            candidates = candidates,
        )
    }

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
        return try {
            request(requestFactory(token))
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            val refreshed = refreshMutex.withLock {
                val currentToken = sessionStore.get(KEY_TOKEN)
                if (!currentToken.isNullOrBlank() && currentToken != token) {
                    savedSession()!!
                } else {
                    val refreshToken = sessionStore.get(KEY_REFRESH_TOKEN) ?: throw error
                    refreshSession(refreshToken)
                }
            }
            request(requestFactory(refreshed.token))
        }
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
    )

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

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}

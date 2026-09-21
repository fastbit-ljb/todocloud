package com.todocloud.app.data

import android.content.Context
import com.todocloud.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class TodoCloudRepository(context: Context) {
    private val client = OkHttpClient()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    fun savedSession(): Session? {
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        return Session(
            token = token,
            email = preferences.getString(KEY_EMAIL, "").orEmpty(),
            displayName = preferences.getString(KEY_DISPLAY_NAME, null),
        )
    }

    fun saveSession(session: Session) {
        preferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .apply()
    }

    fun clearSession() {
        preferences.edit().clear().apply()
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
        val user = response.getJSONObject("user")
        return Session(
            token = response.getString("access_token"),
            email = user.getString("email"),
            displayName = user.optString("display_name").ifBlank { null },
        )
    }

    suspend fun listTasks(token: String): List<TaskItem> {
        val response = request(
            Request.Builder().url(baseUrl + "/tasks").authorized(token).get().build(),
        )
        return (response as JSONArray).toTaskItems()
    }

    suspend fun createTask(token: String, title: String, description: String?): TaskItem {
        val payload = JSONObject().put("title", title)
        if (!description.isNullOrBlank()) payload.put("description", description)
        val response = request(
            Request.Builder()
                .url(baseUrl + "/tasks")
                .authorized(token)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build(),
        )
        return parseTask(response as JSONObject)
    }

    suspend fun updateTask(token: String, id: Int, completed: Boolean): TaskItem {
        val payload = JSONObject().put("completed", completed)
        val response = request(
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(token)
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build(),
        )
        return parseTask(response as JSONObject)
    }

    suspend fun deleteTask(token: String, id: Int) {
        request(
            Request.Builder()
                .url("$baseUrl/tasks/$id")
                .authorized(token)
                .delete()
                .build(),
        )
    }

    private suspend fun request(request: Request): Any = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw ApiException(detail?.ifBlank { null } ?: "请求失败（${response.code}）")
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
        description = json.optString("description").ifBlank { null },
        dueAt = json.optString("due_at").ifBlank { null },
        completed = json.optBoolean("completed"),
    )

    private fun JSONArray.toTaskItems(): List<TaskItem> = buildList {
        for (index in 0 until length()) add(parseTask(getJSONObject(index)))
    }

    private companion object {
        const val PREFERENCES_NAME = "todocloud_session"
        const val KEY_TOKEN = "token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}

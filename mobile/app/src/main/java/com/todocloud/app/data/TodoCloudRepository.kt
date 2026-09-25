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
        steps: List<TaskStep> = emptyList(),
    ): TaskItem {
        val payload = JSONObject().put("title", title)
        if (!description.isNullOrBlank()) payload.put("description", description)
        if (dueAt != null) payload.put("due_at", dueAt)
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
        val payload = JSONObject().apply {
            put("text", text)
            put("reference_at", Instant.now().toString())
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
    )

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

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 3
        const val MAX_AI_IMAGE_EDGE = 2048
        const val MAX_AI_IMAGE_BYTES = 1_500_000
        const val KEY_TOKEN = "token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}

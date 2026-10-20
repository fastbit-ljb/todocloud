package com.todocloud.app.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal const val MAX_PLAN_STREAM_CHARS = 1_000_000

/** Decode only top-level user-facing strings, never JSON syntax, steps or reasoning. */
internal class PlanTextProjection {
    private var depth = 0
    private var inString = false
    private var expectKey = false
    private var wantValue = false
    private var mode = ""
    private var key = ""
    private val keyChars = StringBuilder()
    private val values = mutableMapOf<String, StringBuilder>()
    private var escaped = false
    private var unicodeDigits: StringBuilder? = null
    private var highSurrogate: Char? = null

    private fun append(char: Char) {
        if (mode == "key") keyChars.append(char)
        else if (mode == "value") {
            if (char.isHighSurrogate()) { highSurrogate = char; return }
            val high = highSurrogate
            highSurrogate = null
            if (char.isLowSurrogate()) {
                if (high != null) values.getValue(key).append(high).append(char)
            } else values.getValue(key).append(char)
        }
    }

    fun feed(text: String) {
        for (char in text) {
            if (inString) {
                val digits = unicodeDigits
                when {
                    digits != null -> {
                        digits.append(char)
                        if (digits.length == 4) {
                            append(digits.toString().toIntOrNull(16)?.toChar() ?: throw ApiException("AI 返回的文字格式无效"))
                            unicodeDigits = null
                        }
                    }
                    escaped -> {
                        escaped = false
                        if (char == 'u') unicodeDigits = StringBuilder()
                        else append(when (char) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000c'; else -> char })
                    }
                    char == '\\' -> escaped = true
                    char == '"' -> {
                        inString = false
                        if (mode == "key") { key = keyChars.toString(); expectKey = false }
                        else wantValue = false
                        mode = ""
                        highSurrogate = null
                    }
                    else -> append(char)
                }
                continue
            }
            when {
                char == '{' || char == '[' -> { depth++; if (depth == 1) expectKey = true }
                char == '}' || char == ']' -> depth--
                char == '"' && depth > 0 -> {
                    inString = true
                    mode = if (depth == 1 && expectKey) "key" else ""
                    if (mode == "key") keyChars.clear()
                    else if (depth == 1 && wantValue && key in setOf("intent", "reply", "summary")) {
                        mode = "value"
                        values[key] = StringBuilder()
                    }
                }
                depth == 1 && char == ':' -> wantValue = true
                depth == 1 && char == ',' -> { expectKey = true; wantValue = false }
            }
        }
    }

    val text: String get() {
        fun value(name: String) = values[name]?.toString().orEmpty()
        val plan = value("intent").trim().equals("plan", ignoreCase = true)
        val shown = (if (plan) value("summary") else value("reply").ifEmpty { value("summary") }).take(if (plan) 1000 else 4000)
        return if (shown.lastOrNull()?.isHighSurrogate() == true) shown.dropLast(1) else shown
    }
}

/** SSE framing is independent of network packet boundaries and UTF-8 splits. */
internal suspend fun consumeSse(source: BufferedSource, onEvent: suspend (String, String) -> Boolean) {
    var event = "message"
    val data = StringBuilder()
    suspend fun dispatch(): Boolean {
        if (data.isEmpty()) { event = "message"; return true }
        val keepReading = onEvent(event, data.toString().removeSuffix("\n"))
        data.clear()
        event = "message"
        return keepReading
    }
    while (true) {
        currentCoroutineContext().ensureActive()
        val line = source.readUtf8Line() ?: break
        if (line.length > MAX_PLAN_STREAM_CHARS) throw ApiException("AI 返回内容过长")
        when {
            line.isEmpty() -> if (!dispatch()) return
            line.startsWith("event:") -> event = line.substring(6).removePrefix(" ")
            line.startsWith("data:") -> {
                data.append(line.substring(5).removePrefix(" ")).append('\n')
                if (data.length > MAX_PLAN_STREAM_CHARS) throw ApiException("AI 返回内容过长")
            }
        }
    }
    dispatch()
}

internal fun completionContent(message: JSONObject): String = when (val content = message.opt("content")) {
    is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
    is String -> content
    else -> ""
}

/** A provider stream returns one complete JSON document, with early text snapshots. */
internal suspend fun readModelStream(response: Response, onText: suspend (String) -> Unit): JSONObject {
    val body = response.body ?: throw ApiException("AI 返回内容为空")
    if (!response.header("Content-Type").orEmpty().contains("text/event-stream", true)) {
        return extractJson(completionContent(JSONObject(body.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message")))
    }
    val content = StringBuilder()
    val projection = PlanTextProjection()
    var finished = false
    var sent = ""
    var lastSent = 0L
    consumeSse(body.source()) { _, data ->
        if (data == "[DONE]") { finished = true; false }
        else {
            val chunk = JSONObject(data)
            if (chunk.has("error")) throw ApiException("AI 回复中断，请重试")
            val choice = chunk.optJSONArray("choices")?.optJSONObject(0)
            val reason = choice?.optString("finish_reason")?.takeUnless { it == "null" || it.isEmpty() }
            if (reason != null && reason != "stop") throw ApiException("AI 回复未完成，请重试")
            if (reason == "stop") finished = true
            val text = choice?.optJSONObject("delta")?.let(::completionContent).orEmpty()
            if (text.isNotEmpty()) {
                content.append(text)
                if (content.length > MAX_PLAN_STREAM_CHARS) throw ApiException("AI 返回内容过长")
                projection.feed(text)
                val shown = projection.text
                val now = System.nanoTime()
                if (shown.isNotEmpty() && shown != sent && now - lastSent >= 50_000_000L) {
                    onText(shown)
                    sent = shown
                    lastSent = now
                }
            }
            true
        }
    }
    if (!finished) throw ApiException("AI 回复中断，未保存新计划，请重试")
    if (projection.text.isNotEmpty() && projection.text != sent) onText(projection.text)
    return extractJson(content.toString())
}

/** Server streams have a mandatory final result; EOF and error events are not success. */
internal suspend fun readCloudPlanStream(source: BufferedSource, onText: suspend (String) -> Unit): JSONObject {
    var result: JSONObject? = null
    consumeSse(source) { event, data ->
        val value = JSONObject(data)
        when (event) {
            "text" -> onText(value.getString("text"))
            "result" -> result = value
            "error" -> throw ApiException(value.optString("detail").ifBlank { "AI 回复中断，请重试" })
        }
        result == null
    }
    return result ?: throw ApiException("AI 回复中断，未保存新计划，请重试")
}

/** Cancellation closes an in-progress socket read, not just its coroutine after EOF. */
internal suspend fun <T> cancellableHttp(call: Call, block: suspend (Response) -> T): T = coroutineScope {
    val cancellation = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { call.cancel() }
    }
    try { withContext(Dispatchers.IO) {
        try { call.execute().use { block(it) } }
        catch (error: IOException) { currentCoroutineContext().ensureActive(); throw error }
    } }
    finally { cancellation.cancel() }
}

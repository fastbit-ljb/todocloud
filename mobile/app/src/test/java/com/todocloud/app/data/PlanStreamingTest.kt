package com.todocloud.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanStreamingTest {
    private fun packet(text: String = "", reason: String? = null): String = "data: " + JSONObject().put(
        "choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", text)).put("finish_reason", reason ?: JSONObject.NULL)),
    ) + "\n\n"

    private fun response(body: String, type: String = "text/event-stream") = Response.Builder()
        .request(Request.Builder().url("https://test.invalid").build()).protocol(Protocol.HTTP_1_1)
        .code(200).message("OK").header("Content-Type", type).body(body.toResponseBody()).build()

    @Test fun partialStringsHandleEverySplitEscapesEmojiAndIgnoreNestedKeys() {
        val projection = PlanTextProjection()
        val raw = """```json
            {"intent":"answer","items":[{"reply":"不能泄漏"}],"reply":"先做\n\u4e00件\"小事\"\ud83d\udca1","summary":"不显示"}
        """.trimIndent()
        for (char in raw) {
            projection.feed(char.toString())
            assertFalse(projection.text.contains("不能泄漏"))
            assertFalse(projection.text.lastOrNull()?.isHighSurrogate() == true)
        }
        assertEquals("先做\n一件\"小事\"💡", projection.text)
    }

    @Test fun planStreamsSummaryNotItemsOrOtherReply() {
        val projection = PlanTextProjection()
        projection.feed("""{"intent":"plan","reply":"其他说明","summary":"先测试登录","items":[{"title":"不显示 JSON"}]}""")
        assertEquals("先测试登录", projection.text)
    }

    @Test fun providerRoleReasoningUsageAndHeartbeatAreNotShown() = runBlocking {
        val data = ": ping\n\n" +
            "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"reasoning_content\":\"private reasoning\"}}]}\n\n" +
            "data: {\"choices\":[],\"usage\":{}}\n\n" +
            packet("""{"intent":"answer","reply":"先做一件事"}""", "stop") + "data: [DONE]\n\n"
        val updates = mutableListOf<String>()
        val result = response(data).use { readModelStream(it) { updates += it } }
        assertEquals("先做一件事", result.getString("reply"))
        assertEquals("先做一件事", updates.last())
        assertFalse(updates.any { "private" in it || "intent" in it })
    }

    @Test fun eofWithoutTerminalMarkerCannotBecomeAPlan(): Unit = runBlocking {
        val validJsonButBrokenStream = packet("""{"intent":"plan","summary":"x","items":[]}""")
        assertFailsWith<ApiException> { response(validJsonButBrokenStream).use { readModelStream(it) {} } }
        assertFailsWith<ApiException> { response(packet("{}", "length") + "data: [DONE]\n\n").use { readModelStream(it) {} } }
    }

    @Test fun cloudRequiresResultAndKeepsPartialOnErrorWithoutAnHttpRetryCode(): Unit = runBlocking {
        val updates = mutableListOf<String>()
        val buffer = Buffer().writeUtf8("event: text\ndata: {\"text\":\"先做\"}\n\nevent: error\ndata: {\"detail\":\"断流\"}\n\n")
        val error = assertFailsWith<ApiException> { readCloudPlanStream(buffer) { updates += it } }
        assertEquals(listOf("先做"), updates)
        assertEquals(null, error.statusCode)
        assertFailsWith<ApiException> { readCloudPlanStream(Buffer().writeUtf8("event: start\ndata: {}\n\n")) {} }
    }

    @Test fun multilineSseAndFinalEofEventAreDecoded() = runBlocking {
        val events = mutableListOf<Pair<String, String>>()
        consumeSse(Buffer().writeUtf8(": ping\nevent: text\ndata: {\ndata: \"text\":\"中文\"}\n\nevent: result\ndata: {}")) { event, data ->
            events += event to data
            true
        }
        assertEquals(listOf("text" to "{\n\"text\":\"中文\"}", "result" to "{}"), events)
    }

    @Test fun providerIgnoringStreamUsesItsSingleCompletedResponse() = runBlocking {
        val full = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", """{"intent":"answer","reply":"ok"}"""))))
        assertEquals("ok", response(full.toString(), "application/json").use { readModelStream(it) {} }.getString("reply"))
    }

    @Test fun actualDeepSeekCompatibleRequestShowsTextBeforeLastNetworkBytes() = runBlocking {
        MockWebServer().use { server ->
            val first = packet("""{"intent":"answer","reply":"先做""")
            val body = first + packet("一件事\"}", "stop") + "data: [DONE]\n\n"
            // QueueDispatcher.peek() would also throttle the large incoming prompt.
            // Delay response bytes only, so this tests output streaming, not upload speed.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setHeader("Content-Type", "text/event-stream").setBody(body)
                    .throttleBody(first.toByteArray().size.toLong(), 1, TimeUnit.SECONDS)
            }
            val ai = LocalAi(AiProviders.DEEPSEEK.copy(baseUrl = server.url("/").toString(), model = "test-model"), "not-a-real-key")
            val partial = CompletableDeferred<String>()
            val completed = async {
                ai.planTurn(emptyList(), emptyList(), "给个建议", null, emptyList(), false, onText = { partial.complete(it) })
            }
            val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) }
            if (request == null && completed.isCompleted) completed.await()
            assertTrue(request != null, "Provider must receive the request before any reply is expected")
            assertTrue(JSONObject(request.body.readUtf8()).getBoolean("stream"))
            assertEquals("先做", withTimeout(5000) { partial.await() })
            assertFalse(completed.isCompleted)
            assertEquals("先做一件事", withTimeout(8000) { completed.await() }.getString("reply"))
            assertEquals("/chat/completions", request.path)
        }
    }

    @Test fun cancellingAnHttpReadClosesTheSocketPromptly() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) { cancellableHttp(call) { it.body?.string() } }
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
            withTimeout(1500) { waiting.cancelAndJoin() }
            assertTrue(call.isCanceled())
        }
    }
}

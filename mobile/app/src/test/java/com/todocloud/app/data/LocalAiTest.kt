package com.todocloud.app.data

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Test

class LocalAiTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, zone)

    @Test
    fun extractsJsonFromFencesAndProse() {
        assertEquals(1, extractJson("```json\n{\"a\": 1}\n```").getInt("a"))
        assertEquals(2, extractJson("好的，结果如下：{\"a\": {\"b\": 2}} 希望有帮助").getJSONObject("a").getInt("b"))
        assertFailsWith<ApiException> { extractJson("没有 JSON") }
    }

    @Test
    fun proposalKeepsOnlyKnownSourcesAndValidRules() {
        val raw = JSONObject(
            """{"summary":"","items":[
              {"source_type":"prompt","source_id":2,"title":"组会","due_at":"2026-10-14T20:00:00+08:00","recurrence":"Weekly"},
              {"source_type":"prompt","source_id":1,"title":"背单词","recurrence":"daily"},
              {"source_type":"idea","source_id":3,"title":"整理照片","start_at":"2026-10-12T14:00:00+08:00"},
              {"source_type":"idea","source_id":3,"title":"重复"},
              {"source_type":"task","source_id":99,"title":"伪造"}]}""",
        )
        val ideas = listOf(JSONObject().put("id", 3).put("content", "整理旅行照片"))
        val items = normalizeProposal(raw, ideas, emptyList(), now, allowPromptItems = true).getJSONArray("items")
        assertEquals(3, items.length())
        val byTitle = (0 until items.length()).associate { items.getJSONObject(it).let { o -> o.getString("title") to o } }
        assertEquals("weekly", byTitle.getValue("组会").getString("recurrence"))
        assertTrue(byTitle.getValue("背单词").isNull("recurrence")) // no due time
        assertEquals("整理照片", items.getJSONObject(0).getString("title")) // sorted by time
        assertTrue(normalizeProposal(raw, ideas, emptyList(), now, allowPromptItems = false)
            .getJSONArray("items").length() == 1)
    }

    @Test
    fun questionsStayAnswersUnlessForced() {
        val raw = JSONObject("""{"intent":"answer","reply":"这周不多"}""")
        val answer = normalizeTurn(raw, emptyList(), emptyList(), now, forcePlan = false, allowPromptItems = true)
        assertEquals("answer", answer.getString("intent"))
        assertEquals("这周不多", answer.getString("reply"))
        val empty = normalizeTurn(JSONObject("""{"intent":"plan","items":[]}"""), emptyList(), emptyList(), now, false, true)
        assertEquals("answer", empty.getString("intent"))
        val forced = normalizeTurn(raw, emptyList(), emptyList(), now, forcePlan = true, allowPromptItems = true)
        assertEquals("plan", forced.getString("intent"))
    }

    @Test
    fun parsedTaskRepairsMorningHourAndRelativeDay() {
        val task = JSONObject(
            """{"title":"把 word 给老师","due_at":"2026-10-14T18:00:00+08:00","source_text":"明天6点把word给我"}""",
        )
        val candidate = normalizeParsedTask(task, now, repairRelativeDates = true)!!
        assertEquals("2026-10-13T06:00:00+08:00", candidate.dueAt)
        assertNull(normalizeParsedTask(JSONObject("""{"title":"  "}"""), now, true))
    }

    @Test
    fun orderedStepsAreInferred() {
        assertEquals(
            listOf("打开开发者选项", "打开 USB 调试", "连接电脑"),
            inferOrderedSteps("先打开开发者选项，然后打开 USB 调试，最后连接电脑"),
        )
        assertEquals(emptyList(), inferOrderedSteps("只有一句话"))
    }

    /** The numbered rules must stay word-for-word the same as the server prompt. */
    @Test
    fun planPromptRulesMatchTheServer() {
        val server = listOf(File("../../server/app/plan_ai.py"), File("server/app/plan_ai.py")).firstOrNull { it.exists() }
            ?: return // not run from the repository checkout
        val serverText = server.readText()
        val prompt = buildPlanPrompt(emptyList(), emptyList(), now, zone.id, "x", null, emptyList(), forcePlan = false, imageCount = 0)
        val rules = prompt.lines().filter { Regex("^\\d\\. ").containsMatchIn(it) }
        assertTrue(rules.size >= 20)
        rules.forEach { line -> assertTrue(line in serverText, "out of sync with server prompt: $line") }
    }
}

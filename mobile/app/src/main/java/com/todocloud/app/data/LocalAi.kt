package com.todocloud.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * One model service the user can bring a key for. Every preset speaks the OpenAI-style
 * /chat/completions protocol; [extraParams] switches off slow "thinking" where the
 * service has it, since planning needs a quick JSON answer, not a long reasoning trace.
 */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val fallbackModel: String? = null,
    val supportsImages: Boolean,
    val keyHelpUrl: String,
    val keyHelpLabel: String,
    val host: String,
    val extraParams: Map<String, Any> = emptyMap(),
)

object AiProviders {
    val GEMINI = AiProvider(
        id = "gemini",
        label = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        model = "gemini-3.8-flash",
        fallbackModel = "gemini-3.6-flash",
        supportsImages = true,
        keyHelpUrl = "https://aistudio.google.com/apikey",
        keyHelpLabel = "Google AI Studio",
        host = "generativelanguage.googleapis.com",
        extraParams = mapOf("reasoning_effort" to "low"),
    )
    val DEEPSEEK = AiProvider(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/",
        model = "deepseek-v4-flash",
        supportsImages = false,
        keyHelpUrl = "https://platform.deepseek.com/api_keys",
        keyHelpLabel = "DeepSeek 开放平台",
        host = "api.deepseek.com",
    )
    val QWEN = AiProvider(
        id = "qwen",
        label = "通义千问",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        model = "qwen3.7-plus",
        fallbackModel = "qwen-plus",
        supportsImages = true,
        keyHelpUrl = "https://bailian.console.aliyun.com/?tab=model#/api-key",
        keyHelpLabel = "阿里云百炼",
        host = "dashscope.aliyuncs.com",
        extraParams = mapOf("enable_thinking" to false),
    )
    val GLM = AiProvider(
        id = "glm",
        label = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        model = "glm-4.6v-flash",
        supportsImages = true,
        keyHelpUrl = "https://open.bigmodel.cn/usercenter/apikeys",
        keyHelpLabel = "智谱开放平台",
        host = "open.bigmodel.cn",
        extraParams = mapOf("thinking" to mapOf("type" to "disabled")),
    )
    val KIMI = AiProvider(
        id = "kimi",
        label = "Kimi",
        baseUrl = "https://api.moonshot.cn/v1/",
        model = "kimi-k2.6",
        supportsImages = true,
        keyHelpUrl = "https://platform.moonshot.cn/console/api-keys",
        keyHelpLabel = "Moonshot 开放平台",
        host = "api.moonshot.cn",
        extraParams = mapOf("thinking" to mapOf("type" to "disabled")),
    )

    val all = listOf(GEMINI, DEEPSEEK, QWEN, GLM, KIMI)

    fun byId(id: String?): AiProvider = all.firstOrNull { it.id == id } ?: GEMINI

    /** Display text, e.g. "DeepSeek（不支持识别图片）". */
    fun describe(provider: AiProvider): String =
        if (provider.supportsImages) provider.label else "${provider.label}（不支持识别图片）"
}

/** What the settings screen shows about the user's own model. */
data class AiChoice(
    val provider: AiProvider,
    val keySet: Boolean,
    val model: String,
    /** Signed-in users: AI runs on the phone with this key instead of the server. */
    val ownWhenSignedIn: Boolean,
)

/**
 * The user's own model keys, one per service, plus which service is active. Stored
 * encrypted with an Android Keystore key; a key is only ever sent to its own service.
 */
class AiKeyStore(context: Context) {
    private val store = SecureSessionStore(context, "todocloud_ai_key")

    init {
        // Builds from before the provider choice kept a single Gemini key under "api_key".
        store.get(LEGACY_KEY)?.takeIf { it.isNotBlank() }?.let { legacy ->
            if (store.get(keyName(AiProviders.GEMINI.id)) == null) store.put(keyName(AiProviders.GEMINI.id), legacy)
            store.remove(LEGACY_KEY)
        }
    }

    val provider: AiProvider get() = AiProviders.byId(store.get(PROVIDER))

    fun apiKey(providerId: String): String? = store.get(keyName(providerId))?.takeIf { it.isNotBlank() }

    /** The model name to call; the preset's own model unless the user changed it. */
    fun model(providerId: String): String =
        store.get(modelName(providerId))?.takeIf { it.isNotBlank() } ?: AiProviders.byId(providerId).model

    val ownWhenSignedIn: Boolean get() = store.get(OWN_SIGNED_IN) == "1" && apiKey(provider.id) != null

    fun choice(): AiChoice = provider.let { AiChoice(it, apiKey(it.id) != null, model(it.id), ownWhenSignedIn) }

    /** Makes [providerId] active; a null [key] keeps the one already saved for it. */
    fun save(providerId: String, key: String?, model: String, ownWhenSignedIn: Boolean) {
        val preset = AiProviders.byId(providerId)
        store.put(PROVIDER, preset.id)
        key?.trim()?.takeIf { it.isNotEmpty() }?.let { store.put(keyName(preset.id), it) }
        val cleanModel = model.trim()
        if (cleanModel.isEmpty() || cleanModel == preset.model) store.remove(modelName(preset.id))
        else store.put(modelName(preset.id), cleanModel)
        if (ownWhenSignedIn) store.put(OWN_SIGNED_IN, "1") else store.remove(OWN_SIGNED_IN)
    }

    fun setOwnWhenSignedIn(enabled: Boolean) {
        if (enabled) store.put(OWN_SIGNED_IN, "1") else store.remove(OWN_SIGNED_IN)
    }

    fun clearKey(providerId: String) {
        store.remove(keyName(providerId))
        store.remove(modelName(providerId))
    }

    private companion object {
        const val LEGACY_KEY = "api_key"
        const val PROVIDER = "provider"
        const val OWN_SIGNED_IN = "own_when_signed_in"
        fun keyName(id: String) = "api_key_$id"
        fun modelName(id: String) = "model_$id"
    }
}

/**
 * On-device port of server/app/plan_ai.py and ai_parser.py: same prompts and the same
 * clean-up of the model's output, so AI on the phone plans like the cloud does. Keep the
 * prompt text in sync with the server when either changes.
 */
internal class LocalAi(
    private val provider: AiProvider,
    private val apiKey: String,
    private val model: String = provider.model,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /** One planning turn: {"intent", "reply", "proposal" (null for answers)}. */
    suspend fun planTurn(
        ideas: List<JSONObject>,
        tasks: List<JSONObject>,
        userMessage: String,
        previousProposal: JSONObject?,
        conversation: List<PlanMessage>,
        forcePlan: Boolean,
        imageDataUrls: List<String> = emptyList(),
        onText: (suspend (String) -> Unit)? = null,
    ): JSONObject {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS)
        val prompt = buildPlanPrompt(
            ideas, tasks, now, zone.id, userMessage, previousProposal, conversation, forcePlan, imageDataUrls.size,
        )
        val raw = callModel(SYSTEM_PLAN, prompt, imageDataUrls, maxTokens = 8192, onText = onText)
        return normalizeTurn(raw, ideas, tasks, now, forcePlan, allowPromptItems = userMessage.isNotBlank())
    }

    /** Task candidates from chat text or a screenshot, like /ai/parse-text and /ai/parse-screenshot. */
    suspend fun parseTasks(text: String?, imageDataUrl: String?): List<AiTaskCandidate> {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS)
        val isImage = imageDataUrl != null
        val prompt = buildParsePrompt(now, zone.id, isImage).let { if (isImage) it else "$it\n\n聊天文字：\n$text" }
        val raw = callModel(SYSTEM_PARSE, prompt, listOfNotNull(imageDataUrl), maxTokens = 4096)
        val tasks = raw.optJSONArray("tasks") ?: throw ApiException("AI 返回结果缺少任务列表")
        return (0 until tasks.length()).mapNotNull { tasks.optJSONObject(it) }.mapNotNull { task ->
            normalizeParsedTask(task, now, repairRelativeDates = !isImage)
        }
    }

    /** A tiny request to check the key and model work before saving them. Any reply counts. */
    suspend fun check() {
        callModel("你是测试助手。", "只回复 ok", emptyList(), maxTokens = 256, fallback = false, parseJson = false)
    }

    private suspend fun callModel(
        system: String,
        prompt: String,
        imageDataUrls: List<String>,
        maxTokens: Int,
        fallback: Boolean = true,
        parseJson: Boolean = true,
        onText: (suspend (String) -> Unit)? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (imageDataUrls.isNotEmpty() && !provider.supportsImages) {
            throw ApiException("${provider.label} 不能识别图片。请在设置的「AI 模型」里换成通义千问、智谱、Kimi 或 Gemini")
        }
        // The fallback only makes sense for the preset's own model, not one the user typed.
        val models = listOfNotNull(model, provider.fallbackModel.takeIf { fallback && model == provider.model })
        val userContent: Any = if (imageDataUrls.isEmpty()) {
            prompt
        } else {
            JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                imageDataUrls.forEach { put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", it))) }
            }
        }
        var lastCode: Int? = null
        var lastWasNetwork = false
        var lastError: ApiException? = null
        var partialShown = false
        for ((modelIndex, model) in models.withIndex()) {
            val attempts = if (modelIndex == 0) 2 else 1
            for (attempt in 0 until attempts) {
                val payload = JSONObject()
                    .put("model", model)
                    .put("max_tokens", maxTokens)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", userContent)))
                provider.extraParams.forEach { (name, value) -> payload.put(name, jsonValue(value)) }
                if (onText != null) payload.put("stream", true)
                val request = Request.Builder()
                    .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                try {
                    cancellableHttp(client.newCall(request)) { response ->
                        if (!response.isSuccessful) {
                            val body = response.body?.string().orEmpty()
                            lastCode = response.code
                            lastWasNetwork = false
                            val keyProblem = response.code == 401 || response.code == 403 ||
                                (response.code == 400 && body.contains("API key", ignoreCase = true))
                            if (keyProblem) throw ApiException(KEY_INVALID)
                            return@cancellableHttp null
                        }
                        if (!parseJson) return@cancellableHttp JSONObject()
                        if (onText != null) return@cancellableHttp readModelStream(response) { text ->
                            partialShown = true
                            onText(text)
                        }
                        val message = JSONObject(response.body?.string().orEmpty()).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        val content = message.opt("content").let { value ->
                            if (value is JSONArray) {
                                (0 until value.length()).joinToString("") { value.optJSONObject(it)?.optString("text").orEmpty() }
                            } else {
                                value?.toString().orEmpty()
                            }
                        }
                        extractJson(content)
                    }?.let { return@withContext it }
                    if (lastCode?.let { it !in RETRYABLE } == true) break // bad model name etc: try the fallback
                } catch (exception: ApiException) {
                    if (exception.message == KEY_INVALID || partialShown) throw exception
                    lastError = exception
                } catch (_: IOException) {
                    currentCoroutineContext().ensureActive()
                    if (partialShown) throw ApiException("AI 回复中断，未保存新计划，请重试")
                    lastWasNetwork = true
                    lastCode = null
                } catch (_: org.json.JSONException) {
                    if (partialShown) throw ApiException("AI 回复格式无效，未保存新计划，请重试")
                    lastError = ApiException("AI 返回的结果无法识别，请稍后重试")
                }
                if (attempt + 1 < attempts) delay(1000)
            }
        }
        throw when {
            lastCode == 429 -> ApiException("${provider.label} 请求较多或密钥额度已用完，请稍后再试")
            lastCode == 402 -> ApiException("${provider.label} 账户余额不足，请先在平台充值")
            lastCode == 400 || lastCode == 404 ->
                ApiException("模型 ${this@LocalAi.model} 暂时不可用或已下线，可在设置的「AI 模型」里换一个模型名")
            lastWasNetwork -> ApiException("连接不上 ${provider.label}（${provider.host}），请检查网络")
            lastError != null -> lastError!!
            else -> ApiException("${provider.label} 暂时不可用，请稍后重试")
        }
    }

    private fun jsonValue(value: Any): Any = when (value) {
        is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> if (v != null) put(k.toString(), jsonValue(v)) } }
        else -> value
    }

    companion object {
        private val RETRYABLE = setOf(408, 429, 500, 502, 503, 504)
        private const val KEY_INVALID = "API 密钥无效或没有权限，请在设置里检查密钥"
        private const val SYSTEM_PLAN = "你是尊重生活节律、善于留白的个人计划助手。"
        private const val SYSTEM_PARSE = "你是可靠的待办事项抽取助手。"
        const val ANSWER_FALLBACK = "我没太确定你是想咨询还是想让我直接安排。如果需要排成计划，告诉我“帮我安排”就好。"

        fun imageDataUrl(bytes: ByteArray, contentType: String): String =
            "data:$contentType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

// ---- Prompts (keep in sync with server/app/plan_ai.py and ai_parser.py) ----

private fun JSONArray.asList(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

internal fun buildPlanPrompt(
    ideas: List<JSONObject>,
    tasks: List<JSONObject>,
    referenceAt: ZonedDateTime,
    timezoneName: String,
    userMessage: String,
    previousProposal: JSONObject?,
    conversation: List<PlanMessage>,
    forcePlan: Boolean,
    imageCount: Int,
): String {
    val previousText = previousProposal?.toString() ?: "无"
    val conversationText = JSONArray().apply {
        conversation.takeLast(12).forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
    }.toString()
    val intentRules = if (forcePlan) {
        "用户已明确要求生成计划：intent 必须是 \"plan\"。"
    } else {
        """先判断用户这次的意图，再决定输出：
1. intent = "plan"：用户明确要你安排、规划、排期、生成或调整计划（例如“帮我排一下明天”“把阅读挪到晚上”“按这些生成计划”）。此时按下面的规则输出 items。
2. intent = "answer"：用户在提问、咨询、征求意见、闲聊，或只是想了解信息（例如“我这周任务多吗”“考研英语怎么复习”“这个计划是不是太满了”）。此时不要输出 items，在 reply 中直接、具体地回答，可以结合收件匣和任务给建议；如果适合，最后用一句话提示用户可以让你把它排成计划。
3. 拿不准时选 "answer"，并在 reply 中问清楚用户是否需要你直接安排。
4. 上一版计划存在时，intent = "answer" 表示保持原计划不变，只回答问题。"""
    }
    val imageRules = if (imageCount == 0) "" else """
用户这次附带了 $imageCount 张图片（可能是课程表、待办清单、聊天记录、通知或日程截图）。
请仔细读取图片内容并结合文字理解用户意图：回答问题时引用图片中的信息；生成计划时，把图片中识别出的、尚不在收件匣和任务里的具体事项作为 prompt 新事项安排，图片中的固定时间（上课、会议、考试、车次）要严格遵守。
看不清或无法判断的内容不要编造，在 reply 或 note 中说明。
"""
    val ideasText = JSONArray(ideas).toString()
    val tasksText = JSONArray(tasks).toString()
    val now = referenceAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    return """
你是有生活常识、会留白的个人计划助手，帮助有一堆待办但懒得规划的用户。你既能回答关于时间安排的问题，也能在用户需要时把收件匣中的想法和现有未完成任务整理成一份现实可执行的计划。

$intentRules
$imageRules
当前用户当地时间：$now
IANA 时区：$timezoneName
图片或文字里只写了月日、没写年份的日期（例如“10月13日”“13号”），按当前时间之后最近的那一天理解，年份通常就是今年。
“2027届”“2023级”“2026年级”这类是届别、年级或群名，不是日期，不能拿来当年份。日期旁边写了星期（如“下周二（10月13日）”）时，选出的年份必须让日期和星期对得上。

必须遵守的生活边界：
1. 普通工作不得安排在早餐 07:30-08:30、午餐 12:00-13:00、晚餐 18:00-19:00。
2. 普通工作不得安排在睡眠时段 23:00-次日 07:00。
3. 只有吃饭本身、旅行/交通、赶火车或飞机、就医、明确且真正紧急的固定事项，才可以占用上述保护时段。
4. 尊重每条内容已有的开始时间、截止时间和持续时间；不要制造冲突，不要把任务排到截止时间之后。
5. 没有持续时间时可以结合内容合理估计；如果无法安全判断，可以保留 start_at 和 due_at 为 null，并在 note 中说明需要用户补充，不要假装精确。
6. 安排休息与切换余量，不要把一天塞满。已明确固定时间的任务优先，紧急任务优先。
7. 想法不是承诺。只选择适合现在执行的想法；暂不安排的想法不要强行加入计划。

目标展开规则：
1. “帮我制定……计划”“给我安排……”是在要求你直接产出并排好可执行内容，不是让你创建一个名为“制定计划”或“安排计划”的任务。
2. 每个 item 都必须是用户可以在该时间段直接执行、并实际推进目标的行动。除非用户明确要求只做前期规划，否则禁止输出“制定规划、列出科目、细化计划、整理计划、确定学习时长”等元任务。
3. 例如用户要求“一天的考研学习计划”，应直接安排英语、数学或专业课等具体复习/练习时段，而不是安排“制定考研复习全局规划”。
4. 用户没有给出全部细节时，可以基于常见场景做保守且明确的默认假设，在 summary 或 note 中说明，并允许用户继续调整；不要用准备计划代替真正执行。
5. 标题使用“动作 + 具体对象”，步骤写本时段真正要完成的内容，避免空泛表述。

重复规则：
1. 只有用户明确表达周期性时才设置 recurrence，例如“每天背单词”用 daily，“工作日早上跑步”用 weekdays，“每周三组会”用 weekly，“每月 15 号交房租”用 monthly。
2. 重复事项只输出一个 item，start_at 和 due_at 填最近一次的时间（weekly 要落在指定的星期几，monthly 要落在指定的日期）；App 会在完成后自动生成下一次，不要把每一次都单独列出来。
3. 一次性的事项、“这周每天都要复习”这类只覆盖当前计划范围的安排，recurrence 为 null。设置了 recurrence 的 item 必须有 due_at。
4. 现有任务里已经带 recurrence 的，保持原规则，不要改成 null。

收件匣想法：
$ideasText

现有未完成任务：
$tasksText

上一版计划：
$previousText

最近计划对话：
$conversationText

用户这次的要求：
$userMessage

只返回 JSON，不要附加解释，格式必须是：
{
  "intent": "plan 或 answer",
  "reply": "intent 为 answer 时的中文回答；intent 为 plan 时可为空",
  "summary": "intent 为 plan 时，一两句话说明这版计划的思路",
  "items": [
    {
      "source_type": "idea、task 或 prompt",
      "source_id": 1,
      "title": "可执行标题",
      "description": null,
      "start_at": "带 UTC 偏移的 ISO-8601 或 null",
      "due_at": "带 UTC 偏移的 ISO-8601 或 null",
      "duration_minutes": 60,
      "reminder_offset_minutes": 10,
      "steps": [{"title": "步骤"}],
      "recurrence": "daily、weekdays、weekly、monthly 或 null",
      "note": "为什么这样安排或为什么暂未排期"
    }
  ]
}
现有任务使用 task 且 source_id 必须来自输入；收件匣想法使用 idea 且 source_id 必须来自输入。
用户在“这次的要求”里直接描述、但不属于输入来源的新事项使用 prompt，source_id 从 1 开始依次编号。
不要把普通安排要求本身误当作新事项，也不要重复同一 source_type + source_id。
所有具体时间必须使用 $timezoneName 当前对应的 UTC 偏移。标题简短，步骤具体，summary 和 note 使用中文。
""".trim()
}

internal fun buildParsePrompt(referenceAt: ZonedDateTime, timezoneName: String, isImage: Boolean): String {
    val source = if (isImage) "聊天截图" else "聊天文字"
    val today = referenceAt.toLocalDate()
    val timeRule = if (isImage) {
        "如果截图中能辨认出聊天消息旁的发送日期或时间，优先用该消息时间作为‘明天、下周一’等相对时间的基准；" +
            "截图没有可辨认的聊天时间时，才使用当前参考时间。不要把手机状态栏时间当成聊天消息时间。"
    } else {
        "文字中的相对时间必须使用下面给出的用户当地日期作为基准。"
    }
    return "请从这份${source}中提取需要完成的待办事项。只返回 JSON，不要解释。" +
        "格式必须是 {\"tasks\":[{\"title\":\"...\",\"description\":null," +
        "\"due_at\":\"ISO-8601 或 null\",\"reminder_offset_minutes\":null," +
        "\"confidence\":0.0,\"source_text\":\"原文片段\"," +
        "\"steps\":[{\"title\":\"按顺序执行的步骤\"}]}]}。" +
        "当前用户当地时间是 ${referenceAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}，IANA 时区是 $timezoneName，" +
        "当地日期是 $today；今天=$today，明天=${today.plusDays(1)}，后天=${today.plusDays(2)}。" +
        timeRule +
        "聊天中出现‘明天、下周一、今晚’等相对时间时，必须以参考时间和用户时区换算成 ISO-8601；" +
        "due_at 必须带明确的 UTC 偏移，不得返回无时区时间；" +
        "如果只写‘6点’、‘8点’等时间而没有明确上午、下午、晚上、中午、凌晨或 24 小时制，默认按上午处理；" +
        "例如‘明天6点把word给我’必须是次日 06:00，不得擅自解释成 18:00；只有‘下午6点’或‘晚上6点’才是 18:00。" +
        "没有明确时间就填 null。标题要简短可执行，不要把聊天寒暄当成任务。" +
        "如果同一个任务包含多个有先后顺序的动作（例如‘先打开开发者选项，然后打开 USB 调试，再打开 OEM 解锁，最后连接电脑’），" +
        "请把主任务放在 title，把每个独立动作按原顺序拆成 steps 数组；每个步骤只保留一个可执行动作。" +
        "没有明确的多步骤关系时 steps 必须返回空数组，不要把普通描述强行拆成步骤。" +
        "只有确实包含待办行动的内容才提取；不要猜测被遮挡/看不清的字，不要补造任务、对象或时间。" +
        "同一事项即使在聊天中重复出现或只是不同说法，也只返回一条，优先保留信息更完整的一条；" +
        "但不要合并不同内容或不同截止时间的任务。"
}

// ---- Output clean-up ----

/** First JSON object in the model's reply, tolerating ``` fences and surrounding prose. */
internal fun extractJson(content: String): JSONObject {
    val fenced = Regex("```(?:json)?\\s*(.*?)\\s*```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        .find(content)?.groupValues?.get(1)
    for (candidate in listOfNotNull(fenced, content.trim().trimStart('\uFEFF'))) {
        var start = candidate.indexOf('{')
        while (start >= 0) {
            val parsed = runCatching { JSONTokener(candidate.substring(start)).nextValue() }.getOrNull()
            if (parsed is JSONObject) return parsed
            start = candidate.indexOf('{', start + 1)
        }
    }
    throw ApiException("AI 返回的结果不是有效 JSON，请稍后重试")
}

private fun normalizeIso(value: Any?, zone: ZoneId): String? {
    val text = value?.toString()?.trim()
    if (text.isNullOrEmpty() || text == "null") return null
    val parsed = runCatching { OffsetDateTime.parse(text.replace("Z", "+00:00")) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(text).atZone(zone).toOffsetDateTime() }.getOrNull()
        ?: throw ApiException("AI 计划返回了无效时间")
    return parsed.atZoneSameInstant(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

private fun JSONObject.intOrNull(name: String): Int? =
    if (isNull(name)) null else opt(name)?.toString()?.toDoubleOrNull()?.toInt()

internal fun normalizeProposal(
    raw: JSONObject,
    ideas: List<JSONObject>,
    tasks: List<JSONObject>,
    referenceAt: ZonedDateTime,
    allowPromptItems: Boolean,
): JSONObject {
    val validIds = mapOf(
        "idea" to ideas.map { it.getInt("id") }.toSet(),
        "task" to tasks.map { it.getInt("id") }.toSet(),
        "prompt" to if (allowPromptItems) (1..30).toSet() else emptySet(),
    )
    val zone = referenceAt.zone
    val summary = raw.optString("summary").trim().ifEmpty { "我整理了一版可执行计划，你可以继续告诉我怎么调整。" }
    val seen = mutableSetOf<Pair<String, Int>>()
    val items = mutableListOf<JSONObject>()
    for (item in raw.optJSONArray("items")?.asList().orEmpty()) {
        val sourceType = item.optString("source_type").trim().lowercase()
        val sourceId = item.opt("source_id")?.toString()?.toDoubleOrNull()?.toInt() ?: continue
        val key = sourceType to sourceId
        if (validIds[sourceType]?.contains(sourceId) != true || key in seen) continue
        val title = item.optString("title").trim()
        if (title.isEmpty()) continue
        seen += key
        val steps = JSONArray()
        item.optJSONArray("steps")?.let { array ->
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                val text = (if (value is JSONObject) value.optString("title") else value?.toString()).orEmpty().trim()
                if (text.isNotEmpty() && steps.length() < 30) steps.put(JSONObject().put("title", text.take(500)))
            }
        }
        val dueAt = normalizeIso(item.opt("due_at"), zone)
        val recurrence = item.optString("recurrence").trim().lowercase()
            .takeIf { it in RECURRENCE_RULES && dueAt != null }
        items += JSONObject()
            .put("source_type", sourceType)
            .put("source_id", sourceId)
            .put("title", title.take(200))
            .put("description", item.optString("description").trim().takeIf { it.isNotEmpty() && it != "null" } ?: JSONObject.NULL)
            .put("start_at", normalizeIso(item.opt("start_at"), zone) ?: JSONObject.NULL)
            .put("due_at", dueAt ?: JSONObject.NULL)
            .put("duration_minutes", item.intOrNull("duration_minutes")?.coerceIn(1, 525_600) ?: JSONObject.NULL)
            .put("reminder_offset_minutes", item.intOrNull("reminder_offset_minutes")?.coerceIn(0, 10_080) ?: JSONObject.NULL)
            .put("steps", steps)
            .put("note", item.optString("note").trim().take(1000))
            .put("recurrence", recurrence ?: JSONObject.NULL)
    }
    items.sortBy { (it.optString("start_at").takeIf { s -> s != "null" && s.isNotEmpty() } ?: it.optString("due_at").takeIf { s -> s != "null" && s.isNotEmpty() } ?: "9999") }
    return JSONObject().put("summary", summary.take(1000)).put("items", JSONArray(items))
}

internal fun normalizeTurn(
    raw: JSONObject,
    ideas: List<JSONObject>,
    tasks: List<JSONObject>,
    referenceAt: ZonedDateTime,
    forcePlan: Boolean,
    allowPromptItems: Boolean,
): JSONObject {
    val intent = raw.optString("intent").trim().lowercase()
    if (forcePlan || intent == "plan") {
        val proposal = normalizeProposal(raw, ideas, tasks, referenceAt, allowPromptItems)
        if (forcePlan || proposal.getJSONArray("items").length() > 0) {
            return JSONObject().put("intent", "plan").put("reply", proposal.getString("summary")).put("proposal", proposal)
        }
    }
    val reply = (raw.optString("reply").trim().ifEmpty { raw.optString("summary").trim() }).ifEmpty { LocalAi.ANSWER_FALLBACK }
    return JSONObject().put("intent", "answer").put("reply", reply.take(4000)).put("proposal", JSONObject.NULL)
}

private val RELATIVE_DAYS = listOf("大后天" to 3, "后天" to 2, "明天|明早|明晚" to 1, "今天|今日|今晚|今早|今晨" to 0)

/** Candidate from one parsed task, with the server's date and step repairs. */
internal fun normalizeParsedTask(task: JSONObject, referenceAt: ZonedDateTime, repairRelativeDates: Boolean): AiTaskCandidate? {
    val title = task.optString("title").trim().takeIf { it.isNotEmpty() } ?: return null
    val sourceText = task.optString("source_text").trim().takeIf { it.isNotEmpty() && it != "null" }
    val zone = referenceAt.zone
    var due = task.optString("due_at").trim().takeIf { it.isNotEmpty() && it != "null" }?.let { text ->
        runCatching { OffsetDateTime.parse(text.replace("Z", "+00:00")).atZoneSameInstant(zone) }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(text).atZone(zone) }.getOrNull()
    }
    if (due != null && repairRelativeDates && sourceText != null) {
        RELATIVE_DAYS.firstOrNull { Regex(it.first).containsMatchIn(sourceText) }?.let { (_, offset) ->
            val expected = referenceAt.toLocalDate().plusDays(offset.toLong())
            due = due!!.with(expected)
        }
    }
    // "6点" with no 上午/下午 means morning, as in the prompt.
    if (due != null && sourceText != null &&
        !Regex("上午|下午|中午|晚上|傍晚|凌晨|\\b(?:am|pm)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sourceText)
    ) {
        Regex("(?<!\\d)(1[0-2]|[0-9])\\s*(?:点|时|[:：])").find(sourceText)?.groupValues?.get(1)?.toInt()?.let { hour ->
            if (due!!.hour == hour + 12) due = due!!.withHour(hour)
        }
    }
    val steps = mutableListOf<AiTaskStep>()
    task.optJSONArray("steps")?.let { array ->
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            val text = (if (value is JSONObject) value.optString("title") else value?.toString()).orEmpty().trim()
            if (text.isNotEmpty()) steps += AiTaskStep(text)
        }
    }
    if (steps.isEmpty()) {
        for (source in listOf(task.optString("description"), sourceText.orEmpty())) {
            val inferred = inferOrderedSteps(source)
            if (inferred.isNotEmpty()) {
                steps += inferred.map(::AiTaskStep)
                break
            }
        }
    }
    return AiTaskCandidate(
        title = title.take(200),
        description = task.optString("description").trim().takeIf { it.isNotEmpty() && it != "null" },
        dueAt = due?.toOffsetDateTime()?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        reminderOffsetMinutes = task.intOrNull("reminder_offset_minutes"),
        confidence = task.optDouble("confidence", 0.0).takeIf { !it.isNaN() } ?: 0.0,
        sourceText = sourceText,
        steps = steps.take(30),
    )
}

/** "先…然后…最后…" in a description becomes ordered steps (at least two). */
internal fun inferOrderedSteps(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val markers = Regex("(?:^|[，,。；;\\n])\\s*(先|首先|然后|接着|再|随后|之后|最后|最终)\\s*").findAll(text).toList()
    if (markers.size < 2) return emptyList()
    val steps = markers.mapIndexedNotNull { index, marker ->
        val end = if (index + 1 < markers.size) markers[index + 1].range.first else text.length
        text.substring(marker.range.last + 1, end).trim(' ', '\t', '\r', '\n', '，', ',', '。', '；', ';').takeIf { it.isNotEmpty() }
    }
    return if (steps.size >= 2) steps.take(30) else emptyList()
}

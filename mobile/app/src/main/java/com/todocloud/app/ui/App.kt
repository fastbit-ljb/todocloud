package com.todocloud.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import com.todocloud.app.data.ApiException
import com.todocloud.app.data.AiParseResult
import com.todocloud.app.data.Session
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.notification.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private data class TabItem(
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun TodoCloudApp() {
    val context = LocalContext.current
    val repository = remember(context) { TodoCloudRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(repository.savedSession()) }
    var tasks by remember { mutableStateOf(emptyList<TaskItem>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<AiParseResult?>(null) }

    fun runRequest(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                action()
            } catch (exception: ApiException) {
                error = exception.message
            } catch (_: Exception) {
                error = "网络连接失败，请确认服务端已启动"
            } finally {
                loading = false
            }
        }
    }

    fun scheduleTasks(items: List<TaskItem>) {
        items.forEach { ReminderScheduler.schedule(context, it) }
    }

    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val token = session?.token
        if (uri != null && token != null) {
            runRequest {
                aiResult = repository.parseScreenshot(token, uri)
            }
        }
    }

    LaunchedEffect(session?.token) {
        val currentSession = session
        if (currentSession == null) {
            tasks = emptyList()
        } else {
            loading = true
            error = null
            try {
                tasks = repository.listTasks(currentSession.token).also(::scheduleTasks)
            } catch (exception: ApiException) {
                error = exception.message
            } catch (_: Exception) {
                error = "网络连接失败，请确认服务端已启动"
            } finally {
                loading = false
            }
        }
    }

    if (session == null) {
        LoginScreen(
            loading = loading,
            error = error,
            onSubmit = { email, password, register ->
                runRequest {
                    val newSession = repository.authenticate(email, password, register)
                    repository.saveSession(newSession)
                    session = newSession
                }
            },
        )
        return
    }

    val currentSession = session ?: return
    val tabs = listOf(
        TabItem("任务") { Icon(Icons.Outlined.Checklist, contentDescription = "任务") },
        TabItem("日历") { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历") },
        TabItem("设置") { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (selectedTab == 0) "我的任务" else tabs[selectedTab].label) })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = tab.icon,
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showComposer = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建任务")
                }
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            0 -> TaskHomeScreen(
                paddingValues = paddingValues,
                session = currentSession,
                tasks = tasks,
                loading = loading,
                error = error,
                onImportScreenshot = { screenshotLauncher.launch("image/*") },
                onRefresh = {
                    runRequest {
                        tasks = repository.listTasks(currentSession.token).also(::scheduleTasks)
                    }
                },
                onToggle = { task ->
                    runRequest {
                        val updated = repository.updateTask(
                            currentSession.token,
                            task.id,
                            completed = !task.completed,
                        )
                        ReminderScheduler.schedule(context, updated)
                        tasks = tasks.map { if (it.id == updated.id) updated else it }
                    }
                },
                onDelete = { task ->
                    runRequest {
                        repository.deleteTask(currentSession.token, task.id)
                        ReminderScheduler.cancel(context, task.id)
                        tasks = tasks.filterNot { it.id == task.id }
                    }
                },
            )

            1 -> CalendarScreen(paddingValues, tasks)
            else -> SettingsScreen(
                paddingValues = paddingValues,
                session = currentSession,
                onLogout = {
                    repository.clearSession()
                    session = null
                },
            )
        }
    }

    if (showComposer) {
        CreateTaskDialog(
            context = context,
            loading = loading,
            onDismiss = { if (!loading) showComposer = false },
            onCreate = { title, description, dueAt, reminderOffsetMinutes ->
                runRequest {
                    val created = repository.createTask(
                        currentSession.token,
                        title,
                        description,
                        dueAt,
                        reminderOffsetMinutes,
                    )
                    ReminderScheduler.schedule(context, created)
                    tasks = listOf(created) + tasks
                    showComposer = false
                }
            },
        )
    }

    aiResult?.let { result ->
        val importPlan = buildAiImportPlan(tasks, result.candidates)
        AiCandidatesDialog(
            candidates = importPlan.candidatesToCreate,
            skippedCount = importPlan.skippedCount,
            loading = loading,
            onDismiss = { if (!loading) aiResult = null },
            onConfirm = {
                runRequest {
                    val plan = buildAiImportPlan(tasks, result.candidates)
                    val created = plan.candidatesToCreate.map { candidate ->
                        repository.createTask(
                            currentSession.token,
                            candidate.title,
                            candidate.description,
                            candidate.dueAt,
                            candidate.reminderOffsetMinutes,
                        )
                    }
                    scheduleTasks(created)
                    tasks = created + tasks
                    aiResult = null
                }
            },
        )
    }
}

@Composable
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("TodoCloud", style = MaterialTheme.typography.displaySmall)
            Text(
                if (registerMode) "创建账号，开始管理你的任务" else "登录后在云端同步你的任务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FloatingUnderlineTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = "邮箱",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            FloatingUnderlineTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = "密码",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onSubmit(email.trim(), password, registerMode) },
                enabled = !loading && email.isNotBlank() && password.length >= 8,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) CircularProgressIndicator(strokeWidth = 2.dp) else Text(if (registerMode) "注册并登录" else "登录")
            }
            TextButton(onClick = { registerMode = !registerMode }) {
                Text(if (registerMode) "已有账号？返回登录" else "没有账号？立即注册")
            }
        }
    }
}

// Adapted from liyaxu123's Uiverse input (MIT License):
// https://uiverse.io/liyaxu123/warm-eel-62
@Composable
private fun FloatingUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotEmpty()
    val activeColor = Color(0xFF9ADCF7)
    val idleColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor by animateColorAsState(
        targetValue = if (active) activeColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "input label color",
    )
    val underlineColor by animateColorAsState(
        targetValue = if (active) activeColor else idleColor,
        animationSpec = tween(300),
        label = "input underline color",
    )
    val labelEasing = remember { CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (singleLine) 72.dp else 104.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = if (singleLine) 54.dp else 88.dp)
                    .padding(top = 15.dp, bottom = 12.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(activeColor),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label.forEachIndexed { index, character ->
                    val characterOffset by animateDpAsState(
                        targetValue = if (active) (-30).dp else 0.dp,
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50,
                            easing = labelEasing,
                        ),
                        label = "input label position $index",
                    )
                    Text(
                        text = character.toString(),
                        color = labelColor,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .widthIn(min = 5.dp)
                            .offset(y = characterOffset),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .background(underlineColor),
            )
        }
        supportingText?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TaskHomeScreen(
    paddingValues: PaddingValues,
    session: Session,
    tasks: List<TaskItem>,
    loading: Boolean,
    error: String?,
    onImportScreenshot: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = session.displayName?.let { "你好，$it" } ?: session.email,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (tasks.isEmpty()) "还没有任务，点击右下角创建第一个任务" else "今天也向目标前进一点",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onImportScreenshot,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("从截图识别任务")
            }
        }
        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (loading && tasks.isEmpty()) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (tasks.isEmpty() && !loading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("从一个小任务开始", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "任务会自动保存到云端，之后可以继续加入 AI 创建和截图解析。",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(tasks, key = { it.id }) { task ->
            TaskCard(task = task, onToggle = { onToggle(task) }, onDelete = { onDelete(task) })
        }
        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                Text(if (loading) "同步中…" else "刷新任务")
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GreenTaskCheckbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                )
                task.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                task.dueAt?.let {
                    Text(formatDueAt(it), color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除任务")
            }
        }
    }
}

@Composable
// Adapted from SelfMadeSystem's Uiverse checkbox (MIT License):
// https://uiverse.io/SelfMadeSystem/green-bobcat-29
private fun GreenTaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dashLength by animateFloatAsState(
        targetValue = if (checked) 70.509666f else 241f,
        animationSpec = tween(durationMillis = 500),
        label = "task checkbox dash length",
    )
    val dashOffset by animateFloatAsState(
        targetValue = if (checked) -262.27234f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "task checkbox dash offset",
    )
    val strokeColor = if (checked) Color(0xFF49C96B) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (checked) "标记为未完成" else "标记为完成"
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp).padding(2.dp)) {
            val scale = minOf(size.width, size.height) / 64f
            val checkboxPath = Path().apply {
                moveTo(0f, 16f * scale)
                lineTo(0f, 56f * scale)
                arcTo(Rect(0f, 48f * scale, 16f * scale, 64f * scale), 180f, -90f, false)
                lineTo(56f * scale, 64f * scale)
                arcTo(Rect(48f * scale, 48f * scale, 64f * scale, 64f * scale), 90f, -90f, false)
                lineTo(64f * scale, 8f * scale)
                arcTo(Rect(48f * scale, 0f, 64f * scale, 16f * scale), 0f, -90f, false)
                lineTo(8f * scale, 0f)
                arcTo(Rect(0f, 0f, 16f * scale, 16f * scale), 270f, -90f, false)
                lineTo(0f, 16f * scale)
                lineTo(32f * scale, 48f * scale)
                lineTo(64f * scale, 16f * scale)
                lineTo(64f * scale, 8f * scale)
                arcTo(Rect(48f * scale, 0f, 64f * scale, 16f * scale), 0f, -90f, false)
                lineTo(8f * scale, 0f)
                arcTo(Rect(0f, 0f, 16f * scale, 16f * scale), 270f, -90f, false)
                lineTo(0f, 56f * scale)
                arcTo(Rect(0f, 48f * scale, 16f * scale, 64f * scale), 180f, -90f, false)
                lineTo(56f * scale, 64f * scale)
                arcTo(Rect(48f * scale, 48f * scale, 64f * scale, 64f * scale), 90f, -90f, false)
                lineTo(64f * scale, 16f * scale)
            }
            drawPath(
                path = checkboxPath,
                color = strokeColor,
                style = Stroke(
                    width = 6f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(dashLength * scale, 9_999_999f),
                        phase = dashOffset * scale,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun CreateTaskDialog(
    context: Context,
    loading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?, Int?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reminderText by rememberSaveable { mutableStateOf("10") }
    var selectedDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm") }

    fun chooseDueDate() {
        val initial = selectedDateTime ?: LocalDateTime.now().plusHours(1)
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute -> selectedDateTime = date.atTime(hour, minute) },
                    initial.hour,
                    initial.minute,
                    true,
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingUnderlineTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "任务内容",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                FloatingUnderlineTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )
                OutlinedButton(onClick = ::chooseDueDate, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDateTime?.format(dateFormatter) ?: "设置截止时间（可选）")
                }
                if (selectedDateTime != null) {
                    FloatingUnderlineTextField(
                        value = reminderText,
                        onValueChange = { reminderText = it.filter(Char::isDigit).take(5) },
                        label = "提前提醒分钟数",
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "例如 10 表示提前 10 分钟提醒",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    TextButton(onClick = { selectedDateTime = null }) { Text("清除截止时间") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dueAt = selectedDateTime
                        ?.atZone(ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toString()
                    val reminder = if (dueAt == null) null else reminderText.toIntOrNull()?.coerceIn(0, 10_080)
                    onCreate(title.trim(), description.trim().ifBlank { null }, dueAt, reminder)
                },
                enabled = !loading && title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

@Composable
private fun AiCandidatesDialog(
    candidates: List<com.todocloud.app.data.AiTaskCandidate>,
    skippedCount: Int,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 识别结果") },
        text = {
            if (candidates.isEmpty()) {
                Text(if (skippedCount > 0) "识别到的任务都已经存在，无需重复创建" else "没有识别到明确的待办事项")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (skippedCount > 0) {
                        Text(
                            "已跳过 $skippedCount 条重复任务",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "请确认后写入云端任务：",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates) { candidate ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                    candidate.dueAt?.let {
                                        Text(formatDueAt(it), color = MaterialTheme.colorScheme.primary)
                                    }
                                    candidate.description?.let {
                                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    candidate.sourceText?.let {
                                        Text("原文：$it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !loading && candidates.isNotEmpty(),
            ) { Text(if (loading) "创建中…" else "确认创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

private data class AiImportPlan(
    val candidatesToCreate: List<com.todocloud.app.data.AiTaskCandidate>,
    val skippedCount: Int,
)

private fun buildAiImportPlan(
    existingTasks: List<TaskItem>,
    candidates: List<com.todocloud.app.data.AiTaskCandidate>,
): AiImportPlan {
    val uniqueCandidates = candidates.filterIndexed { index, candidate ->
        candidates.indexOfFirst { other -> sameCandidate(other, candidate) } == index
    }
    val candidatesToCreate = uniqueCandidates.filterNot { candidate ->
        existingTasks.any { task -> taskMatchesCandidate(task, candidate) }
    }
    return AiImportPlan(
        candidatesToCreate = candidatesToCreate,
        skippedCount = candidates.size - candidatesToCreate.size,
    )
}

private fun sameCandidate(
    first: com.todocloud.app.data.AiTaskCandidate,
    second: com.todocloud.app.data.AiTaskCandidate,
): Boolean {
    if (first.title.trim().lowercase(Locale.ROOT) != second.title.trim().lowercase(Locale.ROOT)) return false
    val firstDue = first.dueAt?.let(::parseInstant)
    val secondDue = second.dueAt?.let(::parseInstant)
    return if (firstDue != null && secondDue != null) {
        abs(firstDue.epochSecond - secondDue.epochSecond) <= 60
    } else {
        first.dueAt == second.dueAt
    }
}

private fun taskMatchesCandidate(
    task: TaskItem,
    candidate: com.todocloud.app.data.AiTaskCandidate,
): Boolean {
    if (task.title.trim().lowercase(Locale.ROOT) != candidate.title.trim().lowercase(Locale.ROOT)) return false
    val candidateDue = candidate.dueAt ?: return true
    val taskDue = task.dueAt ?: return false
    val candidateInstant = parseInstant(candidateDue)
    val taskInstant = parseInstant(taskDue)
    return if (candidateInstant != null && taskInstant != null) {
        abs(candidateInstant.epochSecond - taskInstant.epochSecond) <= 60
    } else {
        candidateDue == taskDue
    }
}

private fun parseInstant(value: String): java.time.Instant? = runCatching {
    OffsetDateTime.parse(value).toInstant()
}.getOrNull()

@Composable
private fun CalendarScreen(paddingValues: PaddingValues, tasks: List<TaskItem>) {
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val visibleMonth = remember(visibleMonthText) { YearMonth.parse(visibleMonthText) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val tasksByDate = tasks.mapNotNull { task ->
        task.dueAt?.let { dueAt -> parseDueDate(dueAt)?.let { date -> date to task } }
    }.groupBy({ it.first }, { it.second })
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7
    val selectedTasks = tasksByDate[selectedDate].orEmpty().sortedBy { it.dueAt }

    fun moveMonth(delta: Long) {
        val nextMonth = visibleMonth.plusMonths(delta)
        visibleMonthText = nextMonth.toString()
        selectedDateText = nextMonth.atDay(1).toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { moveMonth(-1) }) { Text("‹") }
                Text(
                    "${visibleMonth.year}年${visibleMonth.monthValue}月",
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = { moveMonth(1) }) { Text("›") }
            }
            Text(
                "点击日期查看当天任务，带提醒的任务会在到期前通知。",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until cellCount).chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { index ->
                            val day = index - firstDayOffset + 1
                            if (day in 1..visibleMonth.lengthOfMonth()) {
                                val date = visibleMonth.atDay(day)
                                val selected = date == selectedDate
                                val hasTasks = tasksByDate[date].orEmpty().isNotEmpty()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable { selectedDateText = date.toString() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            day.toString(),
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (hasTasks) {
                                            Text(
                                                "•",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f).height(54.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (selectedTasks.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("这一天没有安排任务", modifier = Modifier.padding(18.dp))
                }
            }
        }
        items(selectedTasks, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    task.dueAt?.let {
                        Text(
                            formatDueAt(it),
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    task.reminderOffsetMinutes?.let {
                        Text("提前 $it 分钟提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(paddingValues: PaddingValues, session: Session, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("账号", style = MaterialTheme.typography.titleLarge)
        Text(session.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("退出登录")
        }
    }
}

private fun formatDueAt(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}.getOrElse { value.replace("T", " ").removeSuffix("Z") }

private fun parseDueDate(value: String): LocalDate? = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .toLocalDate()
}.getOrNull()

package com.todocloud.app.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.lerp
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
import com.todocloud.app.notification.ReminderReceiver
import com.todocloud.app.notification.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private data class TabItem(
    val label: String,
    val icon: @Composable () -> Unit,
)

private fun sortTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.sortedWith { left, right ->
    if (left.completed != right.completed) {
        return@sortedWith left.completed.compareTo(right.completed)
    }
    if (!left.completed) {
        return@sortedWith compareNullableStrings(left.dueAt, right.dueAt)
    }
    compareNullableStrings(right.completedAt, left.completedAt)
}

private fun compareNullableStrings(left: String?, right: String?): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    return left.compareTo(right)
}

private fun isVisibleWithinCompletedDays(task: TaskItem, days: Long): Boolean {
    if (!task.completed) return true
    val completedAt = task.completedAt?.let(::parseInstant) ?: return true
    return completedAt.isAfter(Instant.now().minus(days, ChronoUnit.DAYS))
}

private fun visibleHomeTasks(tasks: List<TaskItem>): List<TaskItem> = sortTasks(
    tasks.filter { isVisibleWithinCompletedDays(it, 3) },
)

private fun visibleCalendarTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter {
    isVisibleWithinCompletedDays(it, 365)
}

private data class InputStyleValues(
    val labelFloatOffsetDp: Float = 10f,
    val textBottomPaddingDp: Float = 2f,
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LABEL_FLOAT_OFFSET, labelFloatOffsetDp)
            .putFloat(KEY_TEXT_BOTTOM_PADDING, textBottomPaddingDp)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "input_style_debug"
        private const val KEY_LABEL_FLOAT_OFFSET = "label_float_offset_dp"
        private const val KEY_TEXT_BOTTOM_PADDING = "text_bottom_padding_dp"

        fun load(context: Context): InputStyleValues {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            return InputStyleValues(
                labelFloatOffsetDp = preferences.getFloat(KEY_LABEL_FLOAT_OFFSET, 10f),
                textBottomPaddingDp = preferences.getFloat(KEY_TEXT_BOTTOM_PADDING, 2f),
            )
        }
    }
}

private val LocalInputStyleValues = compositionLocalOf { InputStyleValues() }

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
    var showAiTextComposer by rememberSaveable { mutableStateOf(false) }
    var showInputStyleDebug by rememberSaveable { mutableStateOf(false) }
    var inputStyleValues by remember(context) { mutableStateOf(InputStyleValues.load(context)) }
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
                tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
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
        CompositionLocalProvider(LocalInputStyleValues provides inputStyleValues) {
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
        }
        return
    }

    val currentSession = session ?: return
    val tabs = listOf(
        TabItem("任务") { Icon(Icons.Outlined.Checklist, contentDescription = "任务") },
        TabItem("日历") { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历") },
        TabItem("设置") { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
    )

    CompositionLocalProvider(LocalInputStyleValues provides inputStyleValues) {
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
                onImportText = { showAiTextComposer = true },
                onRefresh = {
                    runRequest {
                        tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
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
                        tasks = sortTasks(tasks.map { if (it.id == updated.id) updated else it })
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
                context = context,
                paddingValues = paddingValues,
                session = currentSession,
                onOpenInputStyleDebug = { showInputStyleDebug = true },
                onLogout = {
                    scope.launch {
                        repository.logout(currentSession)
                        session = null
                    }
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
                    tasks = sortTasks(listOf(created) + tasks)
                    showComposer = false
                }
            },
        )
        }

        if (showAiTextComposer) {
        AiTextDialog(
            loading = loading,
            error = error,
            onDismiss = { if (!loading) showAiTextComposer = false },
            onParse = { text ->
                runRequest {
                    aiResult = repository.parseText(currentSession.token, text)
                    showAiTextComposer = false
                }
            },
        )
        }

        if (showInputStyleDebug) {
            InputStyleDebugDialog(
                values = inputStyleValues,
                onValuesChange = {
                    inputStyleValues = it
                    it.save(context)
                },
                onDismiss = { showInputStyleDebug = false },
            )
        }
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
                    tasks = sortTasks(created + tasks)
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
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("TodoCloud", style = MaterialTheme.typography.displaySmall)
            Text(
                if (registerMode) "创建账号，开始管理你的任务" else "登录后在云端同步你的任务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
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
    val inputStyle = LocalInputStyleValues.current
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotEmpty()
    val activeColor = MaterialTheme.colorScheme.primary
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
                    .height(if (singleLine) 56.dp else 80.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = if (singleLine) 40.dp else 64.dp)
                    // Keep the caret close to the shared underline on every form.
                    .padding(
                        top = 8.dp,
                        bottom = inputStyle.textBottomPaddingDp.dp,
                    )
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
                    .align(Alignment.BottomStart)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label.forEachIndexed { index, character ->
                    val characterOffset by animateDpAsState(
                        // Restore the Uiverse-style float animation while keeping
                        // the focused label close to the underline.
                        targetValue = if (active) (-inputStyle.labelFloatOffsetDp).dp else 0.dp,
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
    onImportText: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    val homeTasks = remember(tasks) { visibleHomeTasks(tasks) }
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
                text = if (homeTasks.isEmpty()) "还没有任务，点击右下角创建第一个任务" else "今天也向目标前进一点",
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
            OutlinedButton(
                onClick = onImportText,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("语音/文字提取任务")
            }
        }
        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (loading && homeTasks.isEmpty()) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (homeTasks.isEmpty() && !loading) {
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
        items(homeTasks, key = { it.id }) { task ->
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
    val strokeColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

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
private fun InputStyleDebugDialog(
    values: InputStyleValues,
    onValuesChange: (InputStyleValues) -> Unit,
    onDismiss: () -> Unit,
) {
    var previewText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入框样式调试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "以下调整会实时应用到登录、AI 提取和新建任务的所有输入框，并自动保存到本机。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("浮动标签上移：${values.labelFloatOffsetDp.toInt()}dp")
                Slider(
                    value = values.labelFloatOffsetDp,
                    onValueChange = { onValuesChange(values.copy(labelFloatOffsetDp = it)) },
                    valueRange = 0f..24f,
                    steps = 23,
                )
                Text("文字到底部横线内距：${values.textBottomPaddingDp.toInt()}dp")
                Slider(
                    value = values.textBottomPaddingDp,
                    onValueChange = { onValuesChange(values.copy(textBottomPaddingDp = it)) },
                    valueRange = 0f..12f,
                    steps = 11,
                )
                FloatingUnderlineTextField(
                    value = previewText,
                    onValueChange = { previewText = it },
                    label = "示例输入",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "调好后告诉我，我会删除这个调试入口和相关代码，只保留最终样式。",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("保存并关闭") } },
    )
}

@Composable
private fun ThemeCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val activeColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) activeColor else contentColor.copy(alpha = 0.28f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp)
                    .padding(top = 8.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                cursorBrush = SolidColor(activeColor),
                singleLine = false,
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                "请输入聊天内容，或点击下方按钮转成文字",
                                color = contentColor.copy(alpha = 0.62f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun AiTextDialog(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onParse: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var speechBaseText by remember { mutableStateOf("") }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun applyTranscript(transcript: String) {
        if (transcript.isBlank()) return
        text = if (speechBaseText.isBlank()) transcript else "$speechBaseText $transcript"
        speechError = null
    }

    val externalRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let(::applyTranscript)
        }
    }

    DisposableEffect(recognizer) {
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    listening = true
                    speechError = null
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    listening = false
                }

                override fun onError(error: Int) {
                    listening = false
                    speechError = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "没有听清内容，请再试一次"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
                        else -> "语音识别失败，请检查系统语音服务或改用文字"
                    }
                }

                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(::applyTranscript)
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        }
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    fun startListening() {
        speechBaseText = text.trimEnd()
        speechError = null
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        val speechRecognizer = recognizer
        if (speechRecognizer != null) {
            speechRecognizer.cancel()
            speechRecognizer.startListening(speechIntent)
        } else if (speechIntent.resolveActivity(context.packageManager) != null) {
            externalRecognizerLauncher.launch(speechIntent)
        } else {
            speechError = "此设备没有可用的语音识别服务，请直接输入文字或使用键盘语音"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListening() else speechError = "需要录音权限才能使用语音转文字"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 提取任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "先把语音转成文字，再由 AI 根据聊天时间提取任务。不会上传录音。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeCardTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "聊天内容",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        if (listening) {
                            recognizer?.stopListening()
                            listening = false
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (listening) "停止录音" else "开始语音转文字")
                }
                speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (error != null && !loading) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onParse(text.trim()) },
                enabled = !loading && text.isNotBlank(),
            ) { Text(if (loading) "提取中…" else "开始提取") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
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
    val tasksByDate = visibleCalendarTasks(tasks).mapNotNull { task ->
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
                "颜色越深表示当天任务越多，小圆点表示今天。带提醒的任务会在到期前通知。",
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
                                val taskCount = tasksByDate[date].orEmpty().size
                                val isToday = date == LocalDate.now()
                                val taskColorFraction = (0.22f + (taskCount - 1).coerceAtLeast(0) * 0.12f)
                                    .coerceAtMost(0.62f)
                                val dayColor = if (taskCount == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    lerp(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.primary,
                                        taskColorFraction,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(dayColor)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp),
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
                                        if (isToday) {
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
private fun SettingsScreen(
    context: Context,
    paddingValues: PaddingValues,
    session: Session,
    onOpenInputStyleDebug: () -> Unit,
    onLogout: () -> Unit,
) {
    val notificationManager = remember(context) {
        context.getSystemService(NotificationManager::class.java)
    }
    val alarmManager = remember(context) {
        context.getSystemService(AlarmManager::class.java)
    }
    val notificationsEnabled = notificationManager.areNotificationsEnabled()
    val exactAlarmsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("账号", style = MaterialTheme.typography.titleLarge)
        Text(session.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = onOpenInputStyleDebug,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("调试输入框样式")
        }
        Text("提醒", style = MaterialTheme.typography.titleLarge)
        Text(
            if (notificationsEnabled) "系统通知已开启" else "系统通知已关闭，请在系统设置中允许 TodoCloud 通知",
            color = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!notificationsEnabled) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("打开通知设置")
            }
        }
        if (!exactAlarmsEnabled) {
            Text(
                "精确闹钟权限未开启，提醒可能延迟。",
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开启精确提醒权限")
            }
        }
        OutlinedButton(
            onClick = {
                ReminderReceiver.showNotification(
                    context = context,
                    taskId = Int.MAX_VALUE,
                    title = "这是一条 TodoCloud 系统通知测试",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("发送测试通知")
        }
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

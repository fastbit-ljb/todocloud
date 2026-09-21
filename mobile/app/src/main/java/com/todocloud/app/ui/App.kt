package com.todocloud.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.ApiException
import com.todocloud.app.data.Session
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.notification.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
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

@Composable
private fun TaskHomeScreen(
    paddingValues: PaddingValues,
    session: Session,
    tasks: List<TaskItem>,
    loading: Boolean,
    error: String?,
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
                            "任务会自动保存到云端，之后可以继续加入日期、提醒和 AI 创建。",
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
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
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
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("任务内容") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("备注（可选）") })
                OutlinedButton(onClick = ::chooseDueDate, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedDateTime?.format(dateFormatter) ?: "设置截止时间（可选）")
                }
                if (selectedDateTime != null) {
                    OutlinedTextField(
                        value = reminderText,
                        onValueChange = { reminderText = it.filter(Char::isDigit).take(5) },
                        label = { Text("提前提醒分钟数") },
                        supportingText = { Text("例如 10 表示提前 10 分钟提醒") },
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
private fun CalendarScreen(paddingValues: PaddingValues, tasks: List<TaskItem>) {
    val upcoming = tasks
        .filter { it.dueAt != null }
        .sortedBy { it.dueAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("近期安排", style = MaterialTheme.typography.headlineSmall)
            Text(
                "带截止时间的任务会显示在这里，并按时间排序。",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (upcoming.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("还没有安排日期的任务", modifier = Modifier.padding(18.dp))
                }
            }
        }
        items(upcoming, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDueAt(task.dueAt.orEmpty()),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
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

package com.todocloud.app.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.todocloud.app.notification.ReminderAccessibilityService
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.todocloud.app.BuildConfig
import com.todocloud.app.R
import com.todocloud.app.data.ApiException
import com.todocloud.app.data.AiTaskStep
import com.todocloud.app.data.AiParseResult
import com.todocloud.app.data.Session
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.notification.ReminderScheduler
import com.todocloud.app.notification.ReminderScheduler.ReminderMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
import kotlin.math.roundToInt

private data class TabItem(
    val label: String,
    val icon: @Composable () -> Unit,
)

private val UiverseEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private const val TASK_REORDER_DELAY_MILLIS = 550
private const val TRASH_ANIMATION_BEAT_MILLIS = 220L
private const val CARD_FLY_AWAY_DURATION_MILLIS = 900
private const val TASK_ENTRY_STAGGER_MILLIS = 100L
private const val TASK_ENTRY_DURATION_MILLIS = 420
private const val INPUT_LABEL_FLOAT_OFFSET_DP = 24f
private const val INPUT_TEXT_BOTTOM_PADDING_DP = 10f
private const val DEFAULT_AI_REMINDER_OFFSET_MINUTES = 10
private const val REMINDER_PREFERENCES_NAME = "reminder_settings"
private const val DEFAULT_REMINDER_KEY = "default_offset_minutes"

private data class CardShatterRequest(
    val task: TaskItem,
    val cardLayer: GraphicsLayer,
    val boundsInRoot: Rect,
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

// The state list is kept in its current order while the checkbox animation
// plays. The caller applies sortTasks after that animation has completed.
private fun visibleHomeTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter {
    isVisibleWithinCompletedDays(it, 3)
}

private fun visibleCalendarTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter {
    isVisibleWithinCompletedDays(it, 365)
}

private fun formatReminderOffset(offsetMinutes: Int?): String = when {
    offsetMinutes == null -> "不提醒"
    offsetMinutes % (24 * 60) == 0 -> "提前 ${offsetMinutes / (24 * 60)} 天"
    offsetMinutes % 60 == 0 -> "提前 ${offsetMinutes / 60} 小时"
    else -> "提前 $offsetMinutes 分钟"
}

private fun loadDefaultReminderOffset(context: Context): Int? {
    val storedValue = context
        .getSharedPreferences(REMINDER_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(DEFAULT_REMINDER_KEY, DEFAULT_AI_REMINDER_OFFSET_MINUTES)
    return if (storedValue < 0) null else storedValue.coerceIn(0, 7 * 24 * 60)
}

private fun saveDefaultReminderOffset(context: Context, offsetMinutes: Int?) {
    context
        .getSharedPreferences(REMINDER_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(DEFAULT_REMINDER_KEY, offsetMinutes ?: -1)
        .apply()
}

private fun requestAlarmPermissions(context: Context) {
    if (!Settings.canDrawOverlays(context)) {
        android.app.AlertDialog.Builder(context)
            .setTitle("开启闹钟提醒")
            .setMessage("允许 TodoCloud 显示在其他应用上层，闹钟才能在后台或锁屏时及时显示。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("去开启") { _, _ ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            .show()
        return
    }

    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        android.app.AlertDialog.Builder(context)
            .setTitle("开启精确闹钟")
            .setMessage("允许 TodoCloud 在设定的时间准确响铃。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("去开启") { _, _ ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
            .show()
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (!notificationManager.canUseFullScreenIntent()) {
            android.app.AlertDialog.Builder(context)
                .setTitle("允许全屏闹钟")
                .setMessage("允许闹钟在锁屏或使用其他应用时显示停止界面。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去开启") { _, _ ->
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
                .show()
        }
    }
}

private fun requestBackgroundRunPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val packageUri = Uri.parse("package:${context.packageName}")
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
        )
    }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
    }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
    )
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val packageUri = Uri.parse("package:${context.packageName}")
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
}

private fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedService = ComponentName(
        context,
        ReminderAccessibilityService::class.java,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expectedService, ignoreCase = true) }
}

private fun isChineseOem(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return listOf(
        "xiaomi",
        "redmi",
        "huawei",
        "honor",
        "oppo",
        "vivo",
        "oneplus",
        "realme",
        "meizu",
        "zte",
        "nubia",
        "blackshark",
        "transsion",
    ).any(manufacturer::contains)
}

private fun oemBackgroundGuidance(): String {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "小米/红米：应用信息 → 自启动 → 允许；电池 → 无限制。"
        manufacturer.contains("huawei") ->
            "华为：应用启动 → TodoCloud → 关闭自动管理，并允许自启动、后台活动和关联启动。"
        manufacturer.contains("honor") ->
            "荣耀：应用启动管理 → TodoCloud → 允许自启动和后台活动；电池 → 不限制。"
        manufacturer.contains("oppo") ->
            "OPPO：应用管理 → 自启动 → 允许；耗电管理 → 允许后台运行/不限制。"
        manufacturer.contains("vivo") ->
            "vivo：应用与权限管理 → 自启动 → 允许；电量管理 → 不限制。"
        manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
            "一加/realme：应用信息 → 电池 → 不限制，并允许自启动和后台活动。"
        manufacturer.contains("meizu") ->
            "魅族：应用管理 → 权限管理 → 自启动，并将电池策略设为不限制。"
        else ->
            "系统设置 → 应用 → TodoCloud → 电池/自启动，允许后台活动并选择不限制。"
    }
}

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun TodoCloudApp() {
    val context = LocalContext.current
    val repository = remember(context) { TodoCloudRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(repository.savedSession()) }
    var tasks by remember { mutableStateOf(emptyList<TaskItem>()) }
    var loading by remember { mutableStateOf(false) }
    var taskHeaderLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var settingsPage by rememberSaveable { mutableStateOf("main") }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var showAiTextComposer by rememberSaveable { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<AiParseResult?>(null) }
    var defaultReminderOffsetMinutes by remember(context) {
        mutableStateOf(loadDefaultReminderOffset(context))
    }
    var reminderMode by remember(context) {
        mutableStateOf(ReminderScheduler.loadReminderMode(context))
    }

    fun handleApiError(exception: ApiException) {
        if (exception.statusCode == 401 && session != null) {
            // The refresh token may have expired or been revoked as well. Do
            // not leave the user on an empty task screen with an auth error.
            repository.clearSession()
            session = null
            aiResult = null
            showComposer = false
            showAiTextComposer = false
            error = "登录已过期，请重新登录"
        } else {
            error = exception.message
        }
    }

    fun runRequest(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                action()
            } catch (exception: ApiException) {
                handleApiError(exception)
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
                taskHeaderLoading = true
                try {
                    aiResult = repository.parseScreenshot(token, uri)
                } finally {
                    taskHeaderLoading = false
                }
            }
        }
    }

    LaunchedEffect(session?.token) {
        val currentSession = session
        if (currentSession == null) {
            tasks = emptyList()
            taskHeaderLoading = false
        } else {
            loading = true
            taskHeaderLoading = true
            error = null
            try {
                tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
            } catch (exception: ApiException) {
                handleApiError(exception)
            } catch (_: Exception) {
                error = "网络连接失败，请确认服务端已启动"
            } finally {
                loading = false
                taskHeaderLoading = false
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
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectedTab == 0 -> "我的任务"
                            selectedTab == 2 && settingsPage == "permissions" -> "权限"
                            selectedTab == 2 && settingsPage == "sponsor" -> "赞助开发"
                            else -> tabs[selectedTab].label
                        },
                    )
                },
                navigationIcon = {
                    if (selectedTab == 2 && settingsPage != "main") {
                        IconButton(onClick = { settingsPage = "main" }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回设置")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedTab != 2 || settingsPage == "main") {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                if (index != 2) settingsPage = "main"
                            },
                            icon = tab.icon,
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showComposer = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
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
                showLoadingAnimation = taskHeaderLoading,
                error = error,
                onImportScreenshot = { screenshotLauncher.launch("image/*") },
                onImportText = { showAiTextComposer = true },
                onRefresh = {
                    runRequest {
                        taskHeaderLoading = true
                        try {
                            tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
                        } finally {
                            taskHeaderLoading = false
                        }
                    }
                },
                onToggle = { task ->
                    runRequest {
                        val nextCompleted = !task.completed
                        val nextSteps = task.steps.takeIf { it.isNotEmpty() }
                            ?.map { step -> step.copy(completed = nextCompleted) }
                        val updated = repository.updateTask(
                            currentSession.token,
                            task.id,
                            completed = nextCompleted,
                            steps = nextSteps,
                        )
                        ReminderScheduler.schedule(context, updated)
                        // Replace in place first so the checkbox/strike animation
                        // can finish before the item changes position.
                        tasks = tasks.map { if (it.id == updated.id) updated else it }
                        delay(TASK_REORDER_DELAY_MILLIS.toLong())
                        tasks = sortTasks(tasks)
                    }
                },
                onStepToggle = { task, stepIndex ->
                    runRequest {
                        val nextSteps = task.steps.mapIndexed { index, step ->
                            if (index == stepIndex) step.copy(completed = !step.completed) else step
                        }
                        val nextCompleted = nextSteps.isNotEmpty() && nextSteps.all { it.completed }
                        val updated = repository.updateTask(
                            currentSession.token,
                            task.id,
                            completed = nextCompleted,
                            steps = nextSteps,
                        )
                        ReminderScheduler.schedule(context, updated)
                        tasks = tasks.map { if (it.id == updated.id) updated else it }
                        delay(TASK_REORDER_DELAY_MILLIS.toLong())
                        tasks = sortTasks(tasks)
                    }
                },
                onReminderChange = { task, reminderOffsetMinutes ->
                    runRequest {
                        val updated = repository.updateTask(
                            currentSession.token,
                            task.id,
                            dueAt = task.dueAt,
                            reminderOffsetMinutes = reminderOffsetMinutes,
                            includeSchedule = true,
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
            else -> when (settingsPage) {
                "permissions" -> PermissionSettingsScreen(
                    context = context,
                    paddingValues = paddingValues,
                    reminderMode = reminderMode,
                )
                "sponsor" -> SponsorSettingsScreen(paddingValues)
                else -> SettingsHomeScreen(
                    context = context,
                    paddingValues = paddingValues,
                    session = currentSession,
                    reminderMode = reminderMode,
                    defaultReminderOffsetMinutes = defaultReminderOffsetMinutes,
                    onDefaultReminderChange = { offsetMinutes ->
                        defaultReminderOffsetMinutes = offsetMinutes
                        saveDefaultReminderOffset(context, offsetMinutes)
                    },
                    onReminderModeChange = { mode ->
                        reminderMode = mode
                        ReminderScheduler.saveReminderMode(context, mode)
                        scheduleTasks(tasks)
                    },
                    onOpenPermissions = { settingsPage = "permissions" },
                    onOpenSponsor = { settingsPage = "sponsor" },
                    onLogout = {
                        scope.launch {
                            repository.logout(currentSession)
                            session = null
                        }
                    },
                )
            }
        }
        }

        if (showComposer) {
        CreateTaskDialog(
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
                    taskHeaderLoading = true
                    try {
                        aiResult = repository.parseText(currentSession.token, text)
                        showAiTextComposer = false
                    } finally {
                        taskHeaderLoading = false
                    }
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
            defaultReminderOffsetMinutes = defaultReminderOffsetMinutes,
            onDismiss = { if (!loading) aiResult = null },
            onConfirm = { selectedCandidates ->
                runRequest {
                    val imported = selectedCandidates.map { candidate ->
                        val existing = tasks.firstOrNull { task ->
                            task.steps.isEmpty() && taskMatchesCandidate(task, candidate)
                        }
                        if (existing != null) {
                            repository.updateTask(
                                currentSession.token,
                                existing.id,
                                dueAt = candidate.dueAt ?: existing.dueAt,
                                reminderOffsetMinutes = candidate.reminderOffsetMinutes,
                                steps = candidate.steps.map { TaskStep(it.title) },
                                includeSchedule = true,
                            )
                        } else {
                            repository.createTask(
                                currentSession.token,
                                candidate.title,
                                candidate.description,
                                candidate.dueAt,
                                candidate.reminderOffsetMinutes,
                                steps = candidate.steps.map { TaskStep(it.title) },
                            )
                        }
                    }
                    scheduleTasks(imported)
                    tasks = sortTasks(
                        imported.fold(tasks) { current, updated ->
                            if (current.any { it.id == updated.id }) {
                                current.map { if (it.id == updated.id) updated else it }
                            } else {
                                listOf(updated) + current
                            }
                        },
                    )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            TodoCloudBrandMark()
            Spacer(Modifier.height(14.dp))
            Text(
                "TodoCloud",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "把想法变成下一步行动",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (registerMode) "创建账号" else "欢迎回来",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (registerMode) "创建账号，开始管理你的任务"
                        else "登录后在云端同步你的任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
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
                    Text(
                        "密码至少 8 位",
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    error?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                            ),
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onSubmit(email.trim(), password, registerMode) },
                        enabled = !loading && email.isNotBlank() && password.length >= 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(21.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (registerMode) "注册并登录" else "登录")
                        }
                    }
                    TextButton(
                        onClick = { registerMode = !registerMode },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (registerMode) "已有账号？返回登录" else "没有账号？立即注册")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "任务、提醒与步骤，随时云端同步",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TodoCloudBrandMark() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
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
                        bottom = INPUT_TEXT_BOTTOM_PADDING_DP.dp,
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
                        targetValue = if (active) (-INPUT_LABEL_FLOAT_OFFSET_DP).dp else 0.dp,
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
    showLoadingAnimation: Boolean,
    error: String?,
    onImportScreenshot: () -> Unit,
    onImportText: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (TaskItem) -> Unit,
    onStepToggle: (TaskItem, Int) -> Unit,
    onReminderChange: (TaskItem, Int?) -> Unit,
    onDelete: (TaskItem) -> Unit,
) {
    val homeTasks = remember(tasks) { visibleHomeTasks(tasks) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Keep the account summary and import actions fixed while only the
            // task area below participates in scrolling.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.displayName?.let { "你好，$it" } ?: session.email,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    RefreshTaskButton(
                        loading = loading,
                        onClick = onRefresh,
                    )
                }
                Text(
                    text = if (homeTasks.isEmpty()) "还没有任务，点击右下角创建第一个任务" else "今天也向目标前进一点",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showLoadingAnimation) {
                    BouncingTaskLoader(Modifier.fillMaxWidth().padding(top = 4.dp))
                } else {
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
                    Text(
                        message,
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

                itemsIndexed(homeTasks, key = { _, task -> task.id }) { index, task ->
                    TaskCard(
                        modifier = Modifier.animateItem(
                            placementSpec = tween(durationMillis = 480, easing = UiverseEase),
                        ),
                        task = task,
                        entryIndex = index,
                        onToggle = { onToggle(task) },
                        onStepToggle = { index -> onStepToggle(task, index) },
                        onReminderChange = { offsetMinutes -> onReminderChange(task, offsetMinutes) },
                        onDelete = { onDelete(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshTaskButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "refresh-task-icon")
    val spinningRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "refresh-task-icon-rotation",
    )
    val buttonShape = RoundedCornerShape(18.dp)

    IconButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .size(52.dp)
            .clip(buttonShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = if (loading) "正在刷新任务" else "刷新任务",
            modifier = Modifier
                .size(25.dp)
                .rotate(if (loading) spinningRotation else 0f),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun BouncingTaskLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "task-loader")
    val delays = listOf(0, 200, 300)
    val bounceProgresses = delays.mapIndexed { index, delay ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                // CSS `ease` = cubic-bezier(0.25, 0.1, 0.25, 1).
                animation = tween(durationMillis = 500, easing = UiverseEase),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = delay),
            ),
            label = "task-loader-ball-$index",
        ).value
    }
    val ballColor = MaterialTheme.colorScheme.primaryContainer
    val shadowColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier.height(92.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .width(200.dp)
                .height(76.dp)
                .semantics { contentDescription = "正在加载任务" },
        ) {
            val unit = size.width / 200f
            val ballWidth = 20f * unit
            val ballHeightMax = 20f * unit
            // Match the supplied .wrapper/.circle/.shadow geometry exactly.
            val floorTop = 60f * unit
            val shadowTop = 62f * unit
            val centers = listOf(0.20f, 0.50f, 0.80f)

            bounceProgresses.forEachIndexed { index, rawProgress ->
                val progress = rawProgress.coerceIn(0f, 1f)
                // Keyframes from circle7124:
                // 0%: top 60px, height 5px, scaleX 1.7
                // 40%: height 20px, scaleX 1
                // 100%: top 0
                val landingProgress = (progress / 0.4f).coerceIn(0f, 1f)
                val jumpProgress = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val ballHeight = (5f + 15f * landingProgress) * unit
                val horizontalStretch = 1.7f - 0.7f * landingProgress
                val top = if (progress <= 0.4f) {
                    floorTop
                } else {
                    floorTop * (1f - jumpProgress)
                }
                val centerX = size.width * centers[index]

                // Keyframes from shadow046:
                // 0% scaleX 1.5, 40% scaleX 1 / alpha .7,
                // 100% scaleX .2 / alpha .4.
                val shadowScale = if (progress <= 0.4f) {
                    1.5f - 0.5f * landingProgress
                } else {
                    1f - 0.8f * jumpProgress
                }
                val shadowAlpha = if (progress <= 0.4f) {
                    0.9f - 0.2f * landingProgress
                } else {
                    0.7f - 0.3f * jumpProgress
                }
                drawOval(
                    color = shadowColor.copy(alpha = shadowAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = centerX - ballWidth * shadowScale / 2f,
                        y = shadowTop,
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = ballWidth * shadowScale,
                        height = 4f * unit,
                    ),
                )
                drawOval(
                    color = ballColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = centerX - ballWidth * horizontalStretch / 2f,
                        y = top,
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = ballWidth * horizontalStretch,
                        height = ballHeight.coerceAtMost(ballHeightMax),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    modifier: Modifier = Modifier,
    task: TaskItem,
    entryIndex: Int = 0,
    onToggle: () -> Unit,
    onStepToggle: (Int) -> Unit,
    onReminderChange: (Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(task.id) { mutableStateOf(false) }
    var reminderPickerOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    var titleTextWidth by remember(task.id) { mutableStateOf(0f) }
    var titleHasOverflow by remember(task.id) { mutableStateOf(false) }
    var flyAway by rememberSaveable(task.id) { mutableStateOf(false) }
    var entryStarted by rememberSaveable(task.id) { mutableStateOf(false) }
    val entryProgress by animateFloatAsState(
        targetValue = if (entryStarted) 1f else 0f,
        animationSpec = tween(durationMillis = TASK_ENTRY_DURATION_MILLIS, easing = UiverseEase),
        label = "task card entry",
    )
    val entryDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val flyProgress by animateFloatAsState(
        targetValue = if (flyAway) 1f else 0f,
        animationSpec = tween(durationMillis = CARD_FLY_AWAY_DURATION_MILLIS, easing = UiverseEase),
        label = "task card curved fly away",
    )

    LaunchedEffect(deleting) {
        if (deleting) {
            delay(TRASH_ANIMATION_BEAT_MILLIS)
            flyAway = true
            delay(CARD_FLY_AWAY_DURATION_MILLIS.toLong())
            onDelete()
        }
    }
    LaunchedEffect(task.id) {
        delay(entryIndex * TASK_ENTRY_STAGGER_MILLIS)
        entryStarted = true
    }
    val titleColor by animateColorAsState(
        targetValue = if (task.completed) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 300, easing = UiverseEase),
        label = "task title color",
    )
    val titleShift by animateDpAsState(
        targetValue = if (task.completed) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 300, delayMillis = 100, easing = UiverseEase),
        label = "task title shift",
    )
    val titleStrikeProgress by animateFloatAsState(
        targetValue = if (task.completed) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = UiverseEase),
        label = "task title strike",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = entryProgress.coerceIn(0f, 1f)
                val inverse = 1f - progress
                val eased = 1f - inverse * inverse * inverse
                translationX = entryDistance * (1f - eased)
                translationY = -12.dp.toPx() * (1f - eased)
                alpha = eased
                scaleX = 0.96f + 0.04f * eased
                scaleY = 0.96f + 0.04f * eased
                rotationZ = 0f
            },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val progress = flyProgress.coerceIn(0f, 1f)
                    val inverse = 1f - progress
                    val eased = 1f - inverse * inverse * inverse
                    val cardWidth = size.width
                    val cardHeight = size.height
                    val endX = cardWidth * 0.72f
                    val endY = -cardHeight * 1.45f
                    val controlX = cardWidth * 0.62f
                    val controlY = -cardHeight * 1.25f
                    translationX = inverse * inverse * 0f +
                        2f * inverse * progress * controlX + progress * progress * endX
                    translationY = inverse * inverse * 0f +
                        2f * inverse * progress * controlY + progress * progress * endY
                    val fadeProgress = ((eased - 0.24f) / 0.76f).coerceIn(0f, 1f)
                    alpha = 1f - fadeProgress * fadeProgress
                    scaleX = 1f - 0.92f * eased
                    scaleY = 1f - 0.92f * eased
                    // Keep the card level while it follows the curved path.
                    rotationZ = 0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .clickable(
                    enabled = !deleting && (task.steps.isNotEmpty() || titleHasOverflow),
                ) {
                    if (task.steps.isNotEmpty() || titleHasOverflow) {
                        expanded = !expanded
                    }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GreenTaskCheckbox(
                    checked = task.completed,
                    onCheckedChange = { onToggle() },
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layoutResult ->
                                titleHasOverflow = layoutResult.hasVisualOverflow
                                val measuredWidth = if (layoutResult.lineCount > 0) {
                                    layoutResult.getLineRight(0)
                                } else {
                                    0f
                                }
                                if (titleTextWidth != measuredWidth) {
                                    titleTextWidth = measuredWidth
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .offset(x = titleShift)
                                .drawWithContent {
                                    drawContent()
                                    if (titleStrikeProgress > 0f && titleTextWidth > 0f) {
                                        // Use the measured text width, not the
                                        // weighted column width, so the line never
                                        // extends past the last character.
                                        val textWidth = titleTextWidth.coerceAtMost(size.width)
                                        val lineWidth = if (titleStrikeProgress < 0.6f) {
                                            val shortMark = 8.dp.toPx().coerceAtMost(textWidth)
                                            shortMark + (textWidth - shortMark) *
                                                (titleStrikeProgress / 0.6f)
                                        } else {
                                            textWidth
                                        }
                                        drawLine(
                                            color = titleColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.54f),
                                            end = androidx.compose.ui.geometry.Offset(
                                                lineWidth,
                                                size.height * 0.54f,
                                            ),
                                            strokeWidth = 1.5.dp.toPx(),
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                },
                        )
                        task.dueAt?.let {
                            Text(
                                formatDueAt(it),
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    if (task.steps.isNotEmpty()) {
                        TaskStepProgress(steps = task.steps)
                    }
                    if (expanded) {
                        if (task.steps.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                task.steps.forEachIndexed { index, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !deleting) { onStepToggle(index) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        GreenTaskCheckbox(
                                            checked = step.completed,
                                            onCheckedChange = { onStepToggle(index) },
                                        )
                                        Text(
                                            text = "${index + 1}. ${step.title}",
                                            modifier = Modifier.padding(start = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (task.steps.isEmpty() && task.description.isNullOrBlank()) {
                            Text(
                                "这个任务还没有识别出步骤",
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        task.description?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    task.dueAt?.let {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = { reminderPickerOpen = true },
                                enabled = !deleting,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = "设置提醒时间",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    AnimatedTrashButton(
                        deleting = deleting,
                        onDeleteRequested = { deleting = true },
                    )
                }
            }
        }
        if (reminderPickerOpen) {
            ReminderPickerDialog(
                currentOffsetMinutes = task.reminderOffsetMinutes,
                onDismiss = { reminderPickerOpen = false },
                onSelect = { offsetMinutes ->
                    reminderPickerOpen = false
                    onReminderChange(offsetMinutes)
                },
            )
        }
    }
}

@Composable
private fun TaskStepProgress(steps: List<TaskStep>) {
    val completedCount = steps.count { it.completed }
    val activeColor = MaterialTheme.colorScheme.primary
    val pendingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val currentColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, step ->
                val isCurrent = !step.completed && steps.take(index).all { it.completed }
                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 10.dp)
                        .background(
                            color = when {
                                step.completed -> activeColor
                                isCurrent -> currentColor
                                else -> pendingColor
                            },
                            shape = RoundedCornerShape(50),
                        ),
                )
                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                color = if (step.completed) activeColor else pendingColor,
                                shape = RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
        Text(
            text = "已完成 $completedCount/${steps.size} 步",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
        steps.firstOrNull { !it.completed }?.let { currentStep ->
            Text(
                text = "下一步：${currentStep.title}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AnimatedTrashButton(
    deleting: Boolean,
    onDeleteRequested: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val hoverProgress by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = UiverseEase),
        label = "trash hover lid",
    )
    val deleteProgress by animateFloatAsState(
        targetValue = if (deleting) 1f else 0f,
        animationSpec = tween(durationMillis = CARD_SHATTER_DURATION_MILLIS, easing = UiverseEase),
        label = "trash delete animation",
    )
    val lidProgress = if (!deleting) {
        hoverProgress
    } else {
        when {
            deleteProgress < 0.16f -> deleteProgress / 0.16f
            deleteProgress < 0.66f -> 1f
            deleteProgress < 0.86f -> 1f - (deleteProgress - 0.66f) / 0.20f
            else -> 0f
        }
    }
    val itemProgress = ((deleteProgress - 0.16f) / 0.42f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(48.dp)
            .hoverable(hoverSource)
            .clickable(
                enabled = !deleting,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDeleteRequested,
            )
            .semantics {
                contentDescription = "删除任务"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(28.dp)) {
            val stroke = 1.8.dp.toPx()
            val pivot = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.30f)
            val lidAngle = -24f * lidProgress

            if (deleting && itemProgress > 0f && itemProgress < 1f) {
                val paperAlpha = (1f - itemProgress).coerceIn(0f, 1f)
                drawRoundRect(
                    color = iconColor.copy(alpha = paperAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        size.width * 0.40f,
                        size.height * (0.05f + 0.35f * itemProgress),
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.20f, size.height * 0.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
            }
            withTransform({
                rotate(lidAngle, pivot)
            }) {
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.28f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.14f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.14f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            drawRoundRect(
                color = iconColor,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.32f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.45f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = iconColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.45f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.57f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val CARD_SHATTER_DURATION_MILLIS = 980

private data class ShatterPiece(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val driftX: Float,
    val driftY: Float,
    val rotation: Float,
    val delay: Float,
    val jaggedness: Float,
    val dust: Boolean,
)

private fun shardRandom(index: Int, salt: Int): Float {
    val seed = (index + 1) * 1103515245 + (salt + 17) * 12345
    return ((seed ushr 8) and 0xFFFF) / 65535f
}

private fun createShatterPieces(): List<ShatterPiece> {
    val columns = 11
    val rows = 6
    return buildList {
        var index = 0
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cellWidth = 1f / columns
                val cellHeight = 1f / rows
                val centerX = (column + 0.5f) * cellWidth +
                    (shardRandom(index, 1) - 0.5f) * cellWidth * 0.18f
                val centerY = (row + 0.5f) * cellHeight +
                    (shardRandom(index, 2) - 0.5f) * cellHeight * 0.18f
                add(
                    ShatterPiece(
                        centerX = centerX,
                        centerY = centerY,
                        width = cellWidth * (0.84f + shardRandom(index, 3) * 0.10f),
                        height = cellHeight * (0.84f + shardRandom(index, 4) * 0.10f),
                        driftX = (centerX - 0.5f) * 1.7f +
                            (shardRandom(index, 5) - 0.5f) * 0.22f,
                        driftY = (centerY - 0.5f) * 0.92f +
                            (shardRandom(index, 6) - 0.5f) * 0.20f,
                        rotation = (shardRandom(index, 7) - 0.5f) * 28f,
                        delay = shardRandom(index, 8) * 0.12f,
                        jaggedness = shardRandom(index, 9),
                        dust = false,
                    ),
                )
                index += 1
            }
        }

        // Small edge fragments make the break feel granular rather than like
        // a clean six-by-eleven grid.
        repeat(30) { particleIndex ->
            val randomX = shardRandom(particleIndex, 21)
            val randomY = shardRandom(particleIndex, 22)
            add(
                ShatterPiece(
                    centerX = randomX,
                    centerY = randomY,
                    width = 0.014f + shardRandom(particleIndex, 23) * 0.032f,
                    height = 0.020f + shardRandom(particleIndex, 24) * 0.048f,
                    driftX = (randomX - 0.5f) * 2.2f +
                        (shardRandom(particleIndex, 25) - 0.5f) * 0.60f,
                    driftY = (randomY - 0.5f) * 1.55f +
                        (shardRandom(particleIndex, 26) - 0.5f) * 0.48f,
                    rotation = (shardRandom(particleIndex, 27) - 0.5f) * 180f,
                    delay = 0.07f + shardRandom(particleIndex, 28) * 0.20f,
                    jaggedness = shardRandom(particleIndex, 29),
                    dust = true,
                ),
            )
        }
    }
}

private fun shardPath(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    jaggedness: Float,
): Path = Path().apply {
    val notch = halfWidth * (0.16f + jaggedness * 0.18f)
    moveTo(centerX - halfWidth, centerY - halfHeight * 0.86f)
    lineTo(centerX - notch, centerY - halfHeight)
    lineTo(centerX + halfWidth * 0.92f, centerY - halfHeight * 0.82f)
    lineTo(centerX + halfWidth, centerY + halfHeight * 0.12f)
    lineTo(centerX + halfWidth * 0.32f, centerY + halfHeight)
    lineTo(centerX - halfWidth * 0.46f, centerY + halfHeight * 0.80f)
    lineTo(centerX - halfWidth, centerY + halfHeight * 0.08f)
    close()
}

@Composable
private fun PageFlyAwayOverlay(
    request: CardShatterRequest,
    rootBounds: Rect,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalDuration = CARD_FLY_AWAY_DURATION_MILLIS
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = totalDuration, easing = UiverseEase),
        label = "card curved fly away",
    )

    LaunchedEffect(request.task.id) {
        delay(totalDuration.toLong())
        onFinished()
    }

    Canvas(modifier = modifier) {
        val startLeft = request.boundsInRoot.left - rootBounds.left
        val startTop = request.boundsInRoot.top - rootBounds.top
        val cardWidth = request.boundsInRoot.width
        val cardHeight = request.boundsInRoot.height
        val endLeft = size.width + cardWidth * 0.30f
        val endTop = -cardHeight * 1.25f
        val controlLeft = startLeft + (endLeft - startLeft) * 0.48f - cardWidth * 0.12f
        val controlTop = startTop - cardHeight * 1.35f
        val curveProgress = progress.coerceIn(0f, 1f)
        val inverse = 1f - curveProgress
        val left = inverse * inverse * startLeft +
            2f * inverse * curveProgress * controlLeft +
            curveProgress * curveProgress * endLeft
        val top = inverse * inverse * startTop +
            2f * inverse * curveProgress * controlTop +
            curveProgress * curveProgress * endTop
        val eased = 1f - inverse * inverse * inverse
        val fadeProgress = ((eased - 0.34f) / 0.66f).coerceIn(0f, 1f)
        val alpha = 1f - fadeProgress * fadeProgress
        val scale = 1f - 0.94f * eased
        val rotation = -4f + 18f * eased
        val center = androidx.compose.ui.geometry.Offset(cardWidth * 0.5f, cardHeight * 0.5f)
        val previousAlpha = request.cardLayer.alpha
        request.cardLayer.alpha = alpha

        withTransform({
            translate(left = left, top = top)
            rotate(degrees = rotation, pivot = center)
            scale(scaleX = scale, scaleY = scale, pivot = center)
        }) {
            drawLayer(request.cardLayer)
        }
        request.cardLayer.alpha = previousAlpha
    }
}

@Composable
private fun ShatterCardOverlay(
    progress: Float,
    cardLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val pieces = remember { createShatterPieces() }
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (progress < 0.08f) {
            // Keep the card whole for the first beat, then let the cracks open.
            drawLayer(cardLayer)
            if (progress > 0.025f) {
                val crackAlpha = ((progress - 0.025f) / 0.055f).coerceIn(0f, 1f)
                val crackWidth = 1.dp.toPx()
                drawLine(
                    color = accent.copy(alpha = crackAlpha * 0.72f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.08f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.54f),
                    strokeWidth = crackWidth,
                )
                drawLine(
                    color = accent.copy(alpha = crackAlpha * 0.56f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.54f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.88f),
                    strokeWidth = crackWidth,
                )
            }
            return@Canvas
        }

        pieces.forEachIndexed { index, piece ->
            val localProgress = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (localProgress <= 0f) return@forEachIndexed

            val eased = 1f - (1f - localProgress) * (1f - localProgress) * (1f - localProgress)
            val fadeProgress = ((localProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val alpha = 1f - fadeProgress * fadeProgress
            val center = androidx.compose.ui.geometry.Offset(
                piece.centerX * size.width,
                piece.centerY * size.height,
            )
            val halfWidth = size.width * piece.width * 0.5f
            val halfHeight = size.height * piece.height * 0.5f
            val path = shardPath(
                centerX = center.x,
                centerY = center.y,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                jaggedness = piece.jaggedness,
            )
            val xOffset = piece.driftX * size.width * (0.08f + 0.92f * eased)
            val yOffset = piece.driftY * size.height * (0.08f + 0.92f * eased) +
                size.height * 0.32f * eased * eased
            val rotation = piece.rotation + (if (index % 2 == 0) 1f else -1f) * 125f * eased
            val scale = 1f - 0.50f * eased
            val previousAlpha = cardLayer.alpha
            cardLayer.alpha = alpha

            withTransform({
                translate(left = xOffset, top = yOffset)
                rotate(degrees = rotation, pivot = center)
                scale(scaleX = scale, scaleY = scale, pivot = center)
            }) {
                clipPath(path) {
                    drawLayer(cardLayer)
                }

                if (piece.dust) {
                    drawPath(
                        path = path,
                        color = surface.copy(alpha = alpha * 0.52f),
                    )
                } else {
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = alpha * 0.15f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.7.dp.toPx()),
                    )
                }
            }
            cardLayer.alpha = previousAlpha
        }
    }
}

@Composable
// Adapted from JkHuger's Uiverse checkbox (MIT License):
// https://uiverse.io/JkHuger/warm-panther-74
@OptIn(ExperimentalFoundationApi::class)
private fun GreenTaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = UiverseEase),
        label = "task checkbox check",
    )
    val fireworkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 100, easing = UiverseEase),
        label = "task checkbox firework",
    )
    val strokeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (checked) "标记为未完成" else "标记为完成"
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = null,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(32.dp).padding(3.dp)) {
            val checkStroke = 2.dp.toPx()

            // Uiverse's two pseudo-elements stay at width 0 for the first
            // half of the 400 ms animation, then draw both sides of the tick
            // together during the second half.
            val checkSegmentProgress = ((checkProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val initialSegmentProgress = (1f - checkProgress / 0.5f).coerceIn(0f, 1f)
            val left = size.width * 0.22f
            val middle = size.width * 0.43f
            val bottom = size.height * 0.66f
            val right = size.width * 0.80f
            val top = size.height * 0.30f
            val fireworkCenter = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.50f,
                y = size.height * 0.50f,
            )

            if (checkProgress < 1f) {
                // The source uses label::before { width: 8px }.
                val dashWidth = 8.dp.toPx()
                drawLine(
                    color = strokeColor.copy(alpha = 1f - checkProgress),
                    start = androidx.compose.ui.geometry.Offset(
                        (size.width - dashWidth) / 2f,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        (size.width + dashWidth) / 2f,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (checked && initialSegmentProgress > 0f) {
                val initialLength = 4.dp.toPx() * initialSegmentProgress
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(
                        middle - initialLength,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        middle,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(
                        middle,
                        size.height * 0.50f,
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        middle + initialLength,
                        size.height * 0.50f,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (fireworkProgress > 0f) {
                val directions = listOf(
                    0f to -15f,
                    14f to -8f,
                    14f to 8f,
                    0f to 15f,
                    -14f to 8f,
                    -14f to -8f,
                )
                directions.forEach { (xDirection, yDirection) ->
                    val particleProgress = fireworkProgress
                    val fireworkOpacity = if (particleProgress <= 0.3f) {
                        1f
                    } else {
                        1f - ((particleProgress - 0.3f) / 0.7f)
                    }
                    drawCircle(
                        color = strokeColor.copy(alpha = fireworkOpacity),
                        radius = 2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            fireworkCenter.x + xDirection * fireworkProgress * 1.dp.toPx(),
                            fireworkCenter.y + yDirection * fireworkProgress * 1.dp.toPx(),
                        ),
                    )
                }
            }
            if (checkSegmentProgress > 0f) {
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(left, size.height * 0.50f),
                    end = androidx.compose.ui.geometry.Offset(
                        left + (middle - left) * checkSegmentProgress,
                        size.height * 0.50f + (bottom - size.height * 0.50f) * checkSegmentProgress,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
            if (checkSegmentProgress > 0f) {
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(middle, bottom),
                    end = androidx.compose.ui.geometry.Offset(
                        middle + (right - middle) * checkSegmentProgress,
                        bottom + (top - bottom) * checkSegmentProgress,
                    ),
                    strokeWidth = checkStroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ThemeCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "请输入聊天内容，或点击下方按钮转成文字",
) {
    var focused by remember { mutableStateOf(false) }
    // Keep the theme tint visible, but soften it so the input feels lighter
    // than task cards and remains comfortable behind multi-line text.
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
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
                                placeholder,
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
    loading: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?, Int?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reminderText by rememberSaveable { mutableStateOf("10") }
    var selectedDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var showDateTimePicker by rememberSaveable { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA) }

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
                ThemeCardTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "备注（可选）",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "补充任务说明或执行步骤（可选）",
                )
                OutlinedButton(
                    onClick = { showDateTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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

    if (showDateTimePicker) {
        TaskDateTimePickerDialog(
            initialDateTime = selectedDateTime,
            onDismiss = { showDateTimePicker = false },
            onConfirm = {
                selectedDateTime = it
                showDateTimePicker = false
            },
        )
    }
}

@Composable
private fun TaskDateTimePickerDialog(
    initialDateTime: LocalDateTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    val initial = remember(initialDateTime) {
        (initialDateTime ?: LocalDateTime.now())
            .withSecond(0)
            .withNano(0)
    }
    var selectedDate by remember { mutableStateOf(initial.toLocalDate()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINA) }
    val today = LocalDate.now()
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        "设置截止时间",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        selectedDate.format(dateFormatter),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "%02d:%02d".format(Locale.US, hour, minute),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.displaySmall,
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                            Text("‹", style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(
                            visibleMonth.format(monthFormatter),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                            Text("›", style = MaterialTheme.typography.headlineSmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    repeat(cellCount / 7) { rowIndex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                        ) {
                            repeat(7) { columnIndex ->
                                val cellIndex = rowIndex * 7 + columnIndex
                                val dayNumber = cellIndex - firstDayOffset + 1
                                if (dayNumber !in 1..visibleMonth.lengthOfMonth()) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    val date = visibleMonth.atDay(dayNumber)
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable { selectedDate = date },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "选择时间",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeAdjuster(
                            label = "小时",
                            value = hour,
                            range = 0..23,
                            onValueChange = { hour = it },
                        )
                        Text(
                            ":",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TimeAdjuster(
                            label = "分钟",
                            value = minute,
                            range = 0..59,
                            onValueChange = { minute = it },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Button(onClick = { onConfirm(selectedDate.atTime(hour, minute)) }) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAdjuster(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val latestValue = rememberUpdatedState(value)
    val stepDistance = with(LocalDensity.current) { 24.dp.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = "$label，可上下滑动调整"
        },
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 72.dp)
                .pointerInput(range) {
                    var dragDistance = 0f
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        dragDistance -= dragAmount
                        var nextValue = latestValue.value
                        while (dragDistance >= stepDistance) {
                            nextValue = if (nextValue == range.last) range.first else nextValue + 1
                            latestOnValueChange.value(nextValue)
                            dragDistance -= stepDistance
                        }
                        while (dragDistance <= -stepDistance) {
                            nextValue = if (nextValue == range.first) range.last else nextValue - 1
                            latestOnValueChange.value(nextValue)
                            dragDistance += stepDistance
                        }
                    }
                }
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%02d".format(Locale.US, value),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AiCandidatesDialog(
    candidates: List<com.todocloud.app.data.AiTaskCandidate>,
    skippedCount: Int,
    loading: Boolean,
    defaultReminderOffsetMinutes: Int?,
    onDismiss: () -> Unit,
    onConfirm: (List<com.todocloud.app.data.AiTaskCandidate>) -> Unit,
) {
    var selectedIndexes by remember(candidates) { mutableStateOf(candidates.indices.toSet()) }
    var reminderPickerIndex by remember { mutableStateOf<Int?>(null) }
    var reminderOffsets by remember(candidates) {
        mutableStateOf(
            candidates.indices.associateWith { index ->
                candidates[index].dueAt?.let {
                    candidates[index].reminderOffsetMinutes ?: defaultReminderOffsetMinutes
                }
            },
        )
    }

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
                        itemsIndexed(candidates) { index, candidate ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading) {
                                            selectedIndexes = selectedIndexes.toMutableSet().apply {
                                                if (!add(index)) remove(index)
                                            }
                                        }
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GreenTaskCheckbox(
                                        checked = index in selectedIndexes,
                                        onCheckedChange = {
                                            selectedIndexes = selectedIndexes.toMutableSet().apply {
                                                if (!add(index)) remove(index)
                                            }
                                        },
                                    )
                                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                                                candidate.dueAt?.let {
                                                    Text(formatDueAt(it), color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            if (candidate.dueAt != null) {
                                                TextButton(
                                                    onClick = { reminderPickerIndex = index },
                                                    enabled = !loading,
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(32.dp),
                                                ) {
                                                    Text(
                                                        formatReminderOffset(reminderOffsets[index]),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "无截止时间",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        candidate.description?.let {
                                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (candidate.steps.isNotEmpty()) {
                                            Column(
                                                modifier = Modifier.padding(top = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    "识别到 ${candidate.steps.size} 个步骤：",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                candidate.steps.forEachIndexed { stepIndex, step ->
                                                    Text(
                                                        "${stepIndex + 1}. ${step.title}",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        candidates.mapIndexedNotNull { index, candidate ->
                            if (index in selectedIndexes) {
                                candidate.copy(reminderOffsetMinutes = reminderOffsets[index])
                            } else {
                                null
                            }
                        },
                    )
                },
                enabled = !loading && selectedIndexes.isNotEmpty(),
            ) { Text(if (loading) "创建中…" else "确认创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )

    reminderPickerIndex?.let { index ->
        ReminderPickerDialog(
            currentOffsetMinutes = reminderOffsets[index],
            onDismiss = { reminderPickerIndex = null },
            onSelect = { offsetMinutes ->
                reminderOffsets = reminderOffsets.toMutableMap().apply {
                    this[index] = offsetMinutes
                }
                reminderPickerIndex = null
            },
        )
    }
}

@Composable
private fun ReminderPickerDialog(
    currentOffsetMinutes: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val initialOffset = currentOffsetMinutes?.coerceIn(0, 7 * 24 * 60) ?: 0
    var days by remember { mutableStateOf(initialOffset / (24 * 60)) }
    var hours by remember { mutableStateOf((initialOffset % (24 * 60)) / 60) }
    var minutes by remember { mutableStateOf(initialOffset % 60) }

    fun setTime(nextDays: Int = days, nextHours: Int = hours, nextMinutes: Int = minutes) {
        val totalMinutes = (nextDays * 24 * 60 + nextHours * 60 + nextMinutes)
            .coerceIn(0, 7 * 24 * 60)
        days = totalMinutes / (24 * 60)
        hours = (totalMinutes % (24 * 60)) / 60
        minutes = totalMinutes % 60
    }

    val totalMinutes = days * 24 * 60 + hours * 60 + minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提前提醒") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "上下滑动数字，自由设置提醒时间（最长 7 天）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (totalMinutes == 0) "不提醒" else formatReminderOffset(totalMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeAdjuster(
                        label = "天",
                        value = days,
                        range = 0..7,
                        onValueChange = { setTime(nextDays = it) },
                    )
                    Text(
                        ":",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TimeAdjuster(
                        label = "小时",
                        value = hours,
                        range = 0..23,
                        onValueChange = { setTime(nextHours = it) },
                    )
                    Text(
                        ":",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TimeAdjuster(
                        label = "分钟",
                        value = minutes,
                        range = 0..59,
                        onValueChange = { setTime(nextMinutes = it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(totalMinutes.takeIf { it > 0 }) },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
    val uniqueCandidates = buildList {
        candidates.forEach { candidate ->
            if (none { existing -> sameCandidate(existing, candidate) }) add(candidate)
        }
    }
    val candidatesToCreate = uniqueCandidates.filterNot { candidate ->
        existingTasks.any { task ->
            taskMatchesCandidate(task, candidate) &&
                !(task.steps.isEmpty() && candidate.steps.isNotEmpty())
        }
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
    if (!titlesLikelySame(first.title, second.title)) return false
    val firstDue = first.dueAt?.let(::parseInstant)
    val secondDue = second.dueAt?.let(::parseInstant)
    return if (firstDue != null && secondDue != null) {
        abs(firstDue.epochSecond - secondDue.epochSecond) <= 60
    } else {
        first.dueAt == second.dueAt
    }
}

private fun titlesLikelySame(first: String, second: String): Boolean {
    val firstNormalized = normalizeTaskTitle(first)
    val secondNormalized = normalizeTaskTitle(second)
    if (firstNormalized.isBlank() || secondNormalized.isBlank()) return false
    if (firstNormalized == secondNormalized) return true
    if (minOf(firstNormalized.length, secondNormalized.length) < 3) return false

    // Compare character counts rather than sets: common characters alone made unrelated
    // short Chinese task titles look like duplicates (for example, “买水” and “买书”).
    val firstCounts = firstNormalized.groupingBy { it }.eachCount()
    val secondCounts = secondNormalized.groupingBy { it }.eachCount()
    val overlap = firstCounts.entries.sumOf { (character, count) ->
        minOf(count, secondCounts[character] ?: 0)
    }
    val diceSimilarity = 2f * overlap / (firstNormalized.length + secondNormalized.length)
    return diceSimilarity >= 0.84f
}

private fun normalizeTaskTitle(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\p{Punct}\\s，。！？、：；（）【】‘’“”]+"), "")
    .replace("请", "")
    .replace("帮我", "")
    .replace("一下", "")
    .replace("把", "")

private fun taskMatchesCandidate(
    task: TaskItem,
    candidate: com.todocloud.app.data.AiTaskCandidate,
): Boolean {
    if (!titlesLikelySame(task.title, candidate.title)) return false
    val candidateDue = candidate.dueAt
    val taskDue = task.dueAt
    if (candidateDue == null || taskDue == null) return true
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
private fun SettingsHomeScreen(
    context: Context,
    paddingValues: PaddingValues,
    session: Session,
    reminderMode: ReminderMode,
    defaultReminderOffsetMinutes: Int?,
    onDefaultReminderChange: (Int?) -> Unit,
    onReminderModeChange: (ReminderMode) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSponsor: () -> Unit,
    onLogout: () -> Unit,
) {
    var showDefaultReminderPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("账号", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(session.email, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已登录 · 任务会继续云端同步",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onLogout) { Text("退出") }
            }
        }

        Text("提醒设置", style = MaterialTheme.typography.titleMedium)
        Text(
            "选择任务到点后的提醒方式",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ReminderModeCard(
            title = "通知栏提醒",
            description = "沿用原来的通知栏提醒，可点击通知进入应用",
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            selected = reminderMode == ReminderMode.NOTIFICATION,
            onClick = { onReminderModeChange(ReminderMode.NOTIFICATION) },
        )
        ReminderModeCard(
            title = "闹钟提醒",
            description = "到点响铃并显示停止界面，不发送任务通知",
            icon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
            selected = reminderMode == ReminderMode.ALARM,
            onClick = {
                onReminderModeChange(ReminderMode.ALARM)
                requestAlarmPermissions(context)
            },
        )
        OutlinedButton(
            onClick = { showDefaultReminderPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("AI 识别任务默认：${formatReminderOffset(defaultReminderOffsetMinutes)}")
        }

        Text("更多设置", style = MaterialTheme.typography.titleMedium)
        SettingsEntry(
            title = "权限",
            description = "通知、闹钟、后台运行和无障碍辅助",
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            onClick = onOpenPermissions,
        )
        SettingsEntry(
            title = "赞助开发",
            description = "查看收款码，支持 TodoCloud 持续开发",
            icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
            onClick = onOpenSponsor,
        )
        Text("关于 TodoCloud", style = MaterialTheme.typography.titleMedium)
        Text(
            "当前版本 ${BuildConfig.VERSION_NAME}\n任务、提醒与云端同步",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 20.dp),
        )
    }

    if (showDefaultReminderPicker) {
        ReminderPickerDialog(
            currentOffsetMinutes = defaultReminderOffsetMinutes,
            onDismiss = { showDefaultReminderPicker = false },
            onSelect = {
                onDefaultReminderChange(it)
                showDefaultReminderPicker = false
            },
        )
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "打开$title")
        }
    }
}

@Composable
private fun PermissionSettingsScreen(
    context: Context,
    paddingValues: PaddingValues,
    reminderMode: ReminderMode,
) {
    val notificationManager = remember(context) {
        context.getSystemService(NotificationManager::class.java)
    }
    val alarmManager = remember(context) {
        context.getSystemService(AlarmManager::class.java)
    }
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshToken by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefreshToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(permissionRefreshToken) {
        notificationManager.areNotificationsEnabled()
    }
    val exactAlarmsEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }
    val overlayEnabled = remember(permissionRefreshToken) { Settings.canDrawOverlays(context) }
    val fullScreenIntentEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
    }
    val backgroundRunAllowed = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val chineseOem = remember { isChineseOem() }
    val accessibilityEnabled = remember(permissionRefreshToken) {
        isAccessibilityServiceEnabled(context)
    }
    var showReminderPermissionGuide by rememberSaveable { mutableStateOf(false) }
    var showBackgroundLockGuide by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "系统权限",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "权限由手机系统管理。国产手机还需要允许自启动、后台活动，并在最近任务中锁定 TodoCloud。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        PermissionStateRow("通知权限", notificationsEnabled, "通知栏提醒需要")
        if (!notificationsEnabled) {
            OutlinedButton(
                onClick = { openNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开通知设置") }
        }
        PermissionStateRow("精确闹钟", exactAlarmsEnabled, "让闹钟尽量准时触发")
        if (!exactAlarmsEnabled) {
            OutlinedButton(
                onClick = { openExactAlarmSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开启精确闹钟权限") }
        }
        if (reminderMode == ReminderMode.ALARM) {
            PermissionStateRow("悬浮窗", overlayEnabled, "后台或锁屏时显示停止界面")
            if (!overlayEnabled) {
                OutlinedButton(
                    onClick = { requestAlarmPermissions(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开启闹钟显示权限") }
            }
            PermissionStateRow("全屏闹钟", fullScreenIntentEnabled, "锁屏时显示停止闹钟界面")
            if (!fullScreenIntentEnabled) {
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开启全屏闹钟提醒") }
            }
        }

        Text("后台运行", style = MaterialTheme.typography.titleMedium)
        PermissionStateRow("电池优化", backgroundRunAllowed, "允许 TodoCloud 在后台工作")
        if (!backgroundRunAllowed) {
            OutlinedButton(
                onClick = { requestBackgroundRunPermission(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许后台运行") }
        }
        if (chineseOem) {
            Text(
                oemBackgroundGuidance(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { openAppDetailsSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开自启动和后台设置") }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("建议锁定 TodoCloud 后台", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "部分国产系统会清理未锁定的后台应用。请在最近任务中向下滑 TodoCloud，点击锁形图标锁定，否则可能收不到通知或闹钟。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Image(
                        painter = painterResource(R.drawable.background_lock_guide),
                        contentDescription = "在最近任务中锁定 TodoCloud 的操作示意图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    TextButton(
                        onClick = { showBackgroundLockGuide = true },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("查看大图和说明") }
                }
            }
            PermissionStateRow(
                "无障碍后台辅助",
                accessibilityEnabled,
                "仅用于部分国产系统的提醒恢复，不读取屏幕内容",
            )
            OutlinedButton(
                onClick = { openAccessibilitySettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (accessibilityEnabled) "管理无障碍辅助" else "尝试开启无障碍辅助") }
        }
        OutlinedButton(
            onClick = { showReminderPermissionGuide = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("查看完整权限指引") }
        Spacer(Modifier.height(20.dp))
    }

    if (showReminderPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showReminderPermissionGuide = false },
            title = { Text("提醒权限设置") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("请按下面的项目检查设置，国产手机尤其需要确认电池、自启动和后台活动。")
                    Text(if (notificationsEnabled) "✓ 通知权限已开启" else "未开启通知权限")
                    Text(if (backgroundRunAllowed) "✓ 电池优化已放行" else "未设置为不限制电池使用")
                    Text(if (exactAlarmsEnabled) "✓ 精确闹钟权限已开启" else "未开启精确闹钟权限")
                    if (chineseOem) {
                        Text("自启动和后台活动无法由应用代替开启，请在系统设置中手动确认。")
                        Text(oemBackgroundGuidance(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderPermissionGuide = false }) { Text("完成") }
            },
        )
    }

    if (showBackgroundLockGuide) {
        AlertDialog(
            onDismissRequest = { showBackgroundLockGuide = false },
            title = { Text("锁定 TodoCloud 后台") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("打开最近任务界面，找到 TodoCloud 后向下滑动，点击锁形图标。锁定后不要点击清除全部。")
                    Image(
                        painter = painterResource(R.drawable.background_lock_guide),
                        contentDescription = "锁定 TodoCloud 后台的操作示意图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundLockGuide = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun PermissionStateRow(
    title: String,
    enabled: Boolean,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (enabled) "已开启" else "需设置",
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SponsorSettingsScreen(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "如果 TodoCloud 对你有帮助，欢迎支持开发。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Image(
                painter = painterResource(R.drawable.wechat_donation_qr),
                contentDescription = "微信收款码",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            "感谢你的支持",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SettingsScreen(
    context: Context,
    paddingValues: PaddingValues,
    session: Session,
    reminderMode: ReminderMode,
    defaultReminderOffsetMinutes: Int?,
    onDefaultReminderChange: (Int?) -> Unit,
    onReminderModeChange: (ReminderMode) -> Unit,
    onLogout: () -> Unit,
) {
    val notificationManager = remember(context) {
        context.getSystemService(NotificationManager::class.java)
    }
    val alarmManager = remember(context) {
        context.getSystemService(AlarmManager::class.java)
    }
    val powerManager = remember(context) {
        context.getSystemService(PowerManager::class.java)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshToken by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(permissionRefreshToken) {
        notificationManager.areNotificationsEnabled()
    }
    val exactAlarmsEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }
    val overlayEnabled = remember(permissionRefreshToken) { Settings.canDrawOverlays(context) }
    val fullScreenIntentEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
    }
    val backgroundRunAllowed = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    val chineseOem = remember { isChineseOem() }
    val accessibilityEnabled = remember(permissionRefreshToken) {
        isAccessibilityServiceEnabled(context)
    }
    val needsReminderPermissionGuide = !notificationsEnabled ||
        !backgroundRunAllowed ||
        !exactAlarmsEnabled ||
        chineseOem
    var showDefaultReminderPicker by rememberSaveable { mutableStateOf(false) }
    var showDonationQr by rememberSaveable { mutableStateOf(false) }
    var showReminderPermissionGuide by rememberSaveable { mutableStateOf(false) }
    var showBackgroundLockGuide by rememberSaveable { mutableStateOf(false) }
    var expandedSettingsSection by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsSection(
            title = "账号",
            description = "当前登录账号与云端同步状态",
            expanded = expandedSettingsSection == "account",
            onToggle = {
                expandedSettingsSection = if (expandedSettingsSection == "account") null else "account"
            },
        ) {
            Text(session.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("已登录，任务会继续与云端同步", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }
        }

        SettingsSection(
            title = "提醒设置",
            description = "选择任务到点后的提醒方式，以及 AI 识别任务的默认提醒时间",
            expanded = expandedSettingsSection == "reminder",
            onToggle = {
                expandedSettingsSection = if (expandedSettingsSection == "reminder") null else "reminder"
            },
        ) {
            ReminderModeCard(
                title = "通知栏提醒",
                description = "沿用原来的通知栏提醒，可点击通知进入应用",
                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                selected = reminderMode == ReminderMode.NOTIFICATION,
                onClick = { onReminderModeChange(ReminderMode.NOTIFICATION) },
            )
            ReminderModeCard(
                title = "闹钟提醒",
                description = "到点响铃并显示停止界面，不发送任务通知",
                icon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                selected = reminderMode == ReminderMode.ALARM,
                onClick = {
                    onReminderModeChange(ReminderMode.ALARM)
                    requestAlarmPermissions(context)
                },
            )
            OutlinedButton(
                onClick = { showDefaultReminderPicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("AI 识别任务默认：${formatReminderOffset(defaultReminderOffsetMinutes)}")
            }
        }

        SettingsSection(
            title = "权限",
            description = "通知、闹钟和后台运行权限；权限关闭时提醒可能无法正常显示",
            expanded = expandedSettingsSection == "permissions",
            onToggle = {
                expandedSettingsSection = if (expandedSettingsSection == "permissions") null else "permissions"
            },
        ) {
            if (needsReminderPermissionGuide) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "有权限需要确认",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        if (!notificationsEnabled) {
                            Text("• 通知权限：未开启", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (!backgroundRunAllowed) {
                            Text("• 电池使用：可能受到优化限制", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (!exactAlarmsEnabled) {
                            Text("• 精确闹钟：未开启", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (chineseOem) {
                            Text("• 自启动/后台活动：请手动确认", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (!accessibilityEnabled) {
                                Text("• 无障碍后台辅助：未开启", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
            Text(
                if (notificationsEnabled) "系统通知已开启" else "系统通知已关闭，请在系统设置中允许 TodoCloud 通知",
                color = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!notificationsEnabled) {
                OutlinedButton(
                    onClick = { openNotificationSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开通知设置")
                }
            }
            Text(
                if (exactAlarmsEnabled) "精确闹钟权限已开启" else "精确闹钟权限未开启，提醒可能延迟",
                color = if (exactAlarmsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!exactAlarmsEnabled) {
                OutlinedButton(
                    onClick = { openExactAlarmSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开启精确提醒权限")
                }
            }
            if (reminderMode == ReminderMode.ALARM && !overlayEnabled) {
                Text("悬浮窗权限未开启，闹钟可能无法在后台或锁屏时显示。", color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = { requestAlarmPermissions(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开启闹钟显示权限")
                }
            }
            if (reminderMode == ReminderMode.ALARM && !fullScreenIntentEnabled) {
                Text("全屏提醒权限未开启，锁屏时可能无法显示停止闹钟界面。", color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("开启全屏闹钟提醒")
                }
            }
            OutlinedButton(
                onClick = { showReminderPermissionGuide = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看完整权限指引")
            }
            Text(
                if (backgroundRunAllowed) "后台活动不受电量优化限制" else "后台活动可能被系统限制",
                color = if (backgroundRunAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!backgroundRunAllowed) {
                OutlinedButton(
                    onClick = { requestBackgroundRunPermission(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("允许后台运行")
                }
            }
            if (chineseOem) {
                Text(
                    oemBackgroundGuidance(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { openAppDetailsSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开自启动和后台设置")
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "建议锁定 TodoCloud 后台",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "部分国产系统会清理未锁定的后台应用。请在最近任务中向下滑 TodoCloud，点击锁形图标锁定，否则可能收不到通知或闹钟。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Image(
                            painter = painterResource(R.drawable.background_lock_guide),
                            contentDescription = "在最近任务中锁定 TodoCloud 的操作示意图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        TextButton(
                            onClick = { showBackgroundLockGuide = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("查看大图和说明")
                        }
                    }
                }
                Text("实验性后台辅助", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (accessibilityEnabled) {
                        "无障碍后台辅助已开启；它不读取屏幕内容，只用于在服务重连时恢复提醒。"
                    } else {
                        "如果前台正常、后台仍没有提醒，可以尝试开启无障碍后台辅助进行验证。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { openAccessibilitySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (accessibilityEnabled) "管理无障碍后台辅助" else "尝试开启无障碍辅助")
                }
            }
        }

        SettingsSection(
            title = "赞助开发",
            description = "如果 TodoCloud 对你有帮助，欢迎支持开发",
            expanded = expandedSettingsSection == "sponsor",
            onToggle = {
                expandedSettingsSection = if (expandedSettingsSection == "sponsor") null else "sponsor"
            },
        ) {
            Image(
                painter = painterResource(R.drawable.wechat_donation_qr),
                contentDescription = "微信收款码",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
            )
            Button(
                onClick = { showDonationQr = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("查看收款码")
            }
        }

        SettingsSection(
            title = "关于 TodoCloud",
            description = "任务、提醒与云端同步",
            expanded = expandedSettingsSection == "about",
            onToggle = {
                expandedSettingsSection = if (expandedSettingsSection == "about") null else "about"
            },
        ) {
            Text("当前版本 ${BuildConfig.VERSION_NAME}")
            Text(
                "提醒功能依赖手机系统的通知、闹钟和后台运行设置。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showDefaultReminderPicker) {
        ReminderPickerDialog(
            currentOffsetMinutes = defaultReminderOffsetMinutes,
            onDismiss = { showDefaultReminderPicker = false },
            onSelect = {
                onDefaultReminderChange(it)
                showDefaultReminderPicker = false
            },
        )
    }

    if (showDonationQr) {
        AlertDialog(
            onDismissRequest = { showDonationQr = false },
            title = { Text("微信赞助") },
            text = {
                Image(
                    painter = painterResource(R.drawable.wechat_donation_qr),
                    contentDescription = "微信收款码",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDonationQr = false }) { Text("关闭") }
            },
        )
    }

    if (showReminderPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showReminderPermissionGuide = false },
            title = { Text("提醒权限设置") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("请按下面的项目检查设置，国产手机尤其需要确认电池、自启动和后台活动。")
                    Text(
                        if (notificationsEnabled) "✓ 通知权限已开启" else "未开启通知权限",
                        color = if (notificationsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (!notificationsEnabled) {
                        OutlinedButton(
                            onClick = { openNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开启通知权限")
                        }
                    }
                    Text(
                        if (backgroundRunAllowed) "✓ 电池优化已放行" else "未设置为不限制电池使用",
                        color = if (backgroundRunAllowed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (!backgroundRunAllowed) {
                        OutlinedButton(
                            onClick = { requestBackgroundRunPermission(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("允许不受电量优化限制")
                        }
                    }
                    Text(
                        if (exactAlarmsEnabled) "✓ 精确闹钟权限已开启" else "未开启精确闹钟权限",
                        color = if (exactAlarmsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (!exactAlarmsEnabled) {
                        OutlinedButton(
                            onClick = { openExactAlarmSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开启精确闹钟权限")
                        }
                    }
                    if (chineseOem) {
                        Text("自启动和后台活动无法由应用代替开启，请在系统设置中手动确认。")
                        Text(
                            oemBackgroundGuidance(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { openAppDetailsSettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("打开应用后台设置")
                        }
                        Text(
                            if (accessibilityEnabled) {
                                "✓ 无障碍后台辅助已开启"
                            } else {
                                "无障碍后台辅助未开启（实验性）"
                            },
                            color = if (accessibilityEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        OutlinedButton(
                            onClick = { openAccessibilitySettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (accessibilityEnabled) "管理无障碍辅助" else "尝试开启无障碍辅助")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderPermissionGuide = false }) {
                    Text("完成")
                }
            },
        )
    }

    if (showBackgroundLockGuide) {
        AlertDialog(
            onDismissRequest = { showBackgroundLockGuide = false },
            title = { Text("锁定 TodoCloud 后台") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("打开最近任务界面，找到 TodoCloud 后向下滑动，点击锁形图标。锁定后不要点击清除全部。")
                    Image(
                        painter = painterResource(R.drawable.background_lock_guide),
                        contentDescription = "锁定 TodoCloud 后台的操作示意图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                    Text(
                        "不同品牌的图标位置可能不同，但目标都是让 TodoCloud 保持在最近任务中。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundLockGuide = false }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起$title" else "展开$title",
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ReminderModeCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

private fun formatDueAt(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}.getOrElse { value.replace("T", " ").removeSuffix("Z") }

private fun parseDueDate(value: String): LocalDate? = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .toLocalDate()
}.getOrNull()

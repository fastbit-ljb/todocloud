package com.todocloud.app.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import com.todocloud.app.notification.ReminderAccessibilityService
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.todocloud.app.BuildConfig
import com.todocloud.app.R
import com.todocloud.app.branding.MascotStateManager
import com.todocloud.app.data.ApiException
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.AiTaskStep
import com.todocloud.app.data.AiParseResult
import com.todocloud.app.data.IdeaDetail
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.PlanItem
import com.todocloud.app.data.Session
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.notification.ReminderScheduler
import com.todocloud.app.notification.ReminderScheduler.ReminderMode
import com.todocloud.app.widget.TaskCalendarWidget
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
fun TodoCloudApp(
    requestedTab: Int = 0,
    requestedCalendarDate: String? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { TodoCloudRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(repository.savedSession()) }
    var tasks by remember { mutableStateOf(emptyList<TaskItem>()) }
    var ideas by remember { mutableStateOf(emptyList<InboxIdea>()) }
    var plans by remember { mutableStateOf(emptyList<AiPlan>()) }
    var tasksLoaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var taskHeaderLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(requestedTab.coerceIn(0, 3)) }
    var taskSection by rememberSaveable { mutableStateOf("tasks") }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf("main") }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var showIdeaComposer by rememberSaveable { mutableStateOf(false) }
    var editingIdea by remember { mutableStateOf<InboxIdea?>(null) }
    var showAiTextComposer by rememberSaveable { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<AiParseResult?>(null) }
    var planLoading by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var defaultReminderOffsetMinutes by remember(context) {
        mutableStateOf(loadDefaultReminderOffset(context))
    }
    var reminderMode by remember(context) {
        mutableStateOf(ReminderScheduler.loadReminderMode(context))
    }

    LaunchedEffect(requestedTab, requestedCalendarDate) {
        selectedTab = requestedTab.coerceIn(0, 3)
        showAddMenu = false
    }

    LaunchedEffect(session?.token, tasks, tasksLoaded) {
        if (session == null) {
            TaskCalendarWidget.clear(context)
        } else if (tasksLoaded) {
            TaskCalendarWidget.updateCachedTasks(context, tasks)
        }
    }

    BackHandler(enabled = selectedTab == 3 && settingsPage != "main") {
        settingsPage = "main"
    }

    BackHandler(enabled = selectedTab == 0 && showAddMenu) {
        showAddMenu = false
    }

    fun handleApiError(exception: ApiException) {
        if (exception.statusCode == 401 && session != null) {
            // The refresh token may have expired or been revoked as well. Do
            // not leave the user on an empty task screen with an auth error.
            repository.clearSession()
            session = null
            aiResult = null
            showComposer = false
            showIdeaComposer = false
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
            ideas = emptyList()
            plans = emptyList()
            tasksLoaded = false
            taskHeaderLoading = false
            MascotStateManager.reset(context)
        } else {
            tasksLoaded = false
            loading = true
            taskHeaderLoading = true
            error = null
            try {
                tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
                ideas = repository.listIdeas(currentSession.token)
                plans = repository.listPlans(currentSession.token)
                tasksLoaded = true
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

    DisposableEffect(lifecycleOwner, tasks, tasksLoaded) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && tasksLoaded) {
                // Re-evaluate against the latest task snapshot only after the
                // user leaves TodoCloud. The process lifecycle applies the
                // queued alias change once every app window is in background.
                MascotStateManager.update(context, tasks)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    val planningTasks = tasks.filterNot { it.completed }
    val planningTaskIds = planningTasks.mapTo(mutableSetOf()) { it.id }
    val planningIdeaIds = ideas.mapTo(mutableSetOf()) { it.id }
    val hasPlanningSources = planningTaskIds.isNotEmpty() || planningIdeaIds.isNotEmpty()
    val visiblePlan = plans.firstOrNull()?.takeIf { plan ->
        hasPlanningSources && (
            plan.status != "draft" || (
                plan.items.isNotEmpty() && plan.items.all { item ->
                    when (item.sourceType) {
                        "task" -> item.sourceId in planningTaskIds
                        "idea" -> item.sourceId in planningIdeaIds
                        else -> false
                    }
                }
            )
        )
    }
    val tabs = listOf(
        TabItem("任务") { Icon(Icons.Outlined.Checklist, contentDescription = "任务") },
        TabItem("日历") { Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历") },
        TabItem("AI计划") { Icon(Icons.Outlined.AutoAwesome, contentDescription = "AI计划") },
        TabItem("设置") { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectedTab == 0 && taskSection == "inbox" -> "收件匣"
                            selectedTab == 0 -> "我的任务"
                            selectedTab == 3 && settingsPage == "permissions" -> "权限"
                            selectedTab == 3 && settingsPage == "sponsor" -> "赞助开发"
                            else -> tabs[selectedTab].label
                        },
                    )
                },
                navigationIcon = {
                    if (selectedTab == 3 && settingsPage != "main") {
                        IconButton(onClick = { settingsPage = "main" }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回设置")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedTab != 3 || settingsPage == "main") {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                showAddMenu = false
                                if (index != 3) settingsPage = "main"
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
            if (selectedTab == 0 && taskSection == "tasks") {
                TaskAddSpeedDial(
                    expanded = showAddMenu,
                    enabled = !loading,
                    onExpandedChange = { showAddMenu = it },
                    onManual = {
                        showAddMenu = false
                        showComposer = true
                    },
                    onScreenshot = {
                        showAddMenu = false
                        screenshotLauncher.launch("image/*")
                    },
                    onText = {
                        showAddMenu = false
                        showAiTextComposer = true
                    },
                )
            } else if (selectedTab == 0) {
                IdeaAddButton(
                    enabled = !loading,
                    onClick = {
                        editingIdea = null
                        showIdeaComposer = true
                    },
                )
            }
        },
    ) { paddingValues ->
        when (selectedTab) {
            0 -> TaskHomeScreen(
                    paddingValues = paddingValues,
                    section = taskSection,
                    onSectionChange = {
                        taskSection = it
                        showAddMenu = false
                    },
                    tasks = tasks,
                    ideas = ideas,
                    loading = loading,
                    showLoadingAnimation = taskHeaderLoading,
                    error = error,
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
                        if (nextCompleted) {
                            MascotStateManager.recordMeaningfulActivity(context)
                        }
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
                        val completedStep = !task.steps[stepIndex].completed
                        val nextCompleted = nextSteps.isNotEmpty() && nextSteps.all { it.completed }
                        val updated = repository.updateTask(
                            currentSession.token,
                            task.id,
                            completed = nextCompleted,
                            steps = nextSteps,
                        )
                        if (completedStep) {
                            MascotStateManager.recordMeaningfulActivity(context)
                        }
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
                    onEditIdea = { idea ->
                        editingIdea = idea
                        showIdeaComposer = true
                    },
                    onDeleteIdea = { idea ->
                        runRequest {
                            repository.deleteIdea(currentSession.token, idea.id)
                            ideas = ideas.filterNot { it.id == idea.id }
                        }
                    },
                )

            1 -> CalendarScreen(
                paddingValues = paddingValues,
                tasks = tasks,
                requestedDate = requestedCalendarDate,
            )
            2 -> AiPlanScreen(
                paddingValues = paddingValues,
                ideas = ideas,
                tasks = planningTasks,
                plan = visiblePlan,
                loading = planLoading,
                error = planError,
                onOpenInbox = {
                    selectedTab = 0
                    taskSection = "inbox"
                },
                onGenerate = { message ->
                    scope.launch {
                        planLoading = true
                        planError = null
                        try {
                            val plan = repository.generatePlan(currentSession.token, message)
                            plans = listOf(plan) + plans.filterNot { it.id == plan.id }
                        } catch (exception: ApiException) {
                            if (exception.statusCode == 401) handleApiError(exception)
                            else planError = exception.message
                        } catch (_: Exception) {
                            planError = "网络连接失败，请稍后重试"
                        } finally {
                            planLoading = false
                        }
                    }
                },
                onRevise = { planId, message ->
                    scope.launch {
                        planLoading = true
                        planError = null
                        try {
                            val revised = repository.revisePlan(currentSession.token, planId, message)
                            plans = listOf(revised) + plans.filterNot { it.id == revised.id }
                        } catch (exception: ApiException) {
                            if (exception.statusCode == 401) handleApiError(exception)
                            else planError = exception.message
                        } catch (_: Exception) {
                            planError = "网络连接失败，请稍后重试"
                        } finally {
                            planLoading = false
                        }
                    }
                },
                onAccept = { planId ->
                    scope.launch {
                        planLoading = true
                        planError = null
                        try {
                            val accepted = repository.acceptPlan(currentSession.token, planId)
                            plans = listOf(accepted.plan) + plans.filterNot { it.id == accepted.plan.id }
                            tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
                            ideas = repository.listIdeas(currentSession.token)
                            MascotStateManager.recordMeaningfulActivity(context)
                        } catch (exception: ApiException) {
                            if (exception.statusCode == 401) handleApiError(exception)
                            else planError = exception.message
                        } catch (_: Exception) {
                            planError = "网络连接失败，请稍后重试"
                        } finally {
                            planLoading = false
                        }
                    }
                },
            )
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
                            MascotStateManager.reset(context)
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
                    MascotStateManager.recordMeaningfulActivity(context)
                    ReminderScheduler.schedule(context, created)
                    tasks = sortTasks(listOf(created) + tasks)
                    showComposer = false
                }
            },
        )
        }

        if (showIdeaComposer) {
        IdeaEditorDialog(
            idea = editingIdea,
            loading = loading,
            onDismiss = {
                if (!loading) {
                    showIdeaComposer = false
                    editingIdea = null
                }
            },
            onSave = { content, durationMinutes, startAt, dueAt, items ->
                runRequest {
                    val existing = editingIdea
                    val saved = if (existing == null) {
                        repository.createIdea(
                            currentSession.token,
                            content,
                            durationMinutes,
                            startAt,
                            dueAt,
                            items,
                        )
                    } else {
                        repository.updateIdea(
                            currentSession.token,
                            existing.id,
                            content,
                            durationMinutes,
                            startAt,
                            dueAt,
                            items,
                        )
                    }
                    ideas = listOf(saved) + ideas.filterNot { it.id == saved.id }
                    showIdeaComposer = false
                    editingIdea = null
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
                    if (imported.isNotEmpty()) {
                        MascotStateManager.recordMeaningfulActivity(context)
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

    if (!singleLine) {
        val fieldShape = RoundedCornerShape(16.dp)
        Column(modifier = modifier) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(fieldShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = underlineColor,
                        shape = fieldShape,
                    )
                    .padding(horizontal = 16.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(activeColor),
                singleLine = true,
                maxLines = 1,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                                fontSize = 18.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    Column(modifier = modifier) {
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 40.dp)
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
private fun TaskAddSpeedDial(
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManual: () -> Unit,
    onScreenshot: () -> Unit,
    onText: () -> Unit,
) {
    val symbolRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(360, easing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)),
        label = "addMenuSymbolRotation",
    )

    Box(modifier = Modifier.size(216.dp)) {
        TaskAddAction(
            icon = Icons.Outlined.EditNote,
            contentDescription = "手动添加任务",
            expanded = expanded,
            enabled = enabled,
            targetX = 0.dp,
            targetY = (-92).dp,
            openDelayMillis = 0,
            closeDelayMillis = 104,
            onClick = onManual,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        TaskAddAction(
            icon = Icons.Outlined.AddPhotoAlternate,
            contentDescription = "从截图识别任务",
            expanded = expanded,
            enabled = enabled,
            targetX = (-72).dp,
            targetY = (-64).dp,
            openDelayMillis = 52,
            closeDelayMillis = 52,
            onClick = onScreenshot,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        TaskAddAction(
            icon = Icons.Outlined.TextFields,
            contentDescription = "从文字提取任务",
            expanded = expanded,
            enabled = enabled,
            targetX = (-92).dp,
            targetY = 0.dp,
            openDelayMillis = 104,
            closeDelayMillis = 0,
            onClick = onText,
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(66.dp)
                .zIndex(3f)
                .semantics {
                    contentDescription = if (expanded) "收起添加菜单" else "展开添加菜单"
                    role = Role.Button
                },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            val symbolColor = MaterialTheme.colorScheme.onSecondaryContainer
            Canvas(modifier = Modifier.size(30.dp)) {
                val armLength = 10.dp.toPx()
                val lineWidth = 2.6.dp.toPx()
                withTransform({ rotate(symbolRotation, pivot = center) }) {
                    drawLine(
                        color = symbolColor,
                        start = Offset(center.x - armLength, center.y),
                        end = Offset(center.x + armLength, center.y),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = symbolColor,
                        start = Offset(center.x, center.y - armLength),
                        end = Offset(center.x, center.y + armLength),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskAddAction(
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    enabled: Boolean,
    targetX: androidx.compose.ui.unit.Dp,
    targetY: androidx.compose.ui.unit.Dp,
    openDelayMillis: Int,
    closeDelayMillis: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val delayMillis = if (expanded) openDelayMillis else closeDelayMillis
    val xOffset by animateDpAsState(
        targetValue = if (expanded) targetX else 7.dp,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuXOffset",
    )
    val yOffset by animateDpAsState(
        targetValue = if (expanded) targetY else 7.dp,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuYOffset",
    )
    val expansionScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.34f,
        animationSpec = tween(430, delayMillis, UiverseEase),
        label = "addMenuExpansionScale",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = tween(130, easing = UiverseEase),
        label = "addMenuPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) 220 else 170,
            delayMillis = delayMillis,
            easing = UiverseEase,
        ),
        label = "addMenuAlpha",
    )

    Box(
        modifier = modifier
            .offset(x = xOffset, y = yOffset)
            .zIndex(2f)
            .size(52.dp)
            .graphicsLayer {
                scaleX = expansionScale * pressScale
                scaleY = expansionScale * pressScale
                this.alpha = alpha * if (enabled) 1f else 0.5f
                shadowElevation = 8.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(
                enabled = expanded && enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                if (expanded) {
                    this.contentDescription = contentDescription
                    role = Role.Button
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
private fun TaskHomeScreen(
    paddingValues: PaddingValues,
    section: String,
    onSectionChange: (String) -> Unit,
    tasks: List<TaskItem>,
    ideas: List<InboxIdea>,
    loading: Boolean,
    showLoadingAnimation: Boolean,
    error: String?,
    onToggle: (TaskItem) -> Unit,
    onStepToggle: (TaskItem, Int) -> Unit,
    onReminderChange: (TaskItem, Int?) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onEditIdea: (InboxIdea) -> Unit,
    onDeleteIdea: (InboxIdea) -> Unit,
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
            TaskSectionSwitcher(
                selected = section,
                onSelected = onSectionChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (showLoadingAnimation || error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (showLoadingAnimation) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BouncingTaskLoader()
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
            }

            if (section == "inbox") {
                InboxList(
                    ideas = ideas,
                    loading = loading,
                    onEdit = onEditIdea,
                    onDelete = onDeleteIdea,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Keep the FAB in its easy-to-reach corner while allowing the
                // final task card (and its delete button) to scroll fully above it.
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 4.dp,
                    end = 20.dp,
                    bottom = 96.dp,
                ),
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
}

@Composable
private fun TaskSectionSwitcher(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                Triple("tasks", "任务", Icons.Outlined.Checklist),
                Triple("inbox", "收件匣", Icons.Outlined.Inbox),
            ).forEach { (value, label, icon) ->
                val active = selected == value
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .clickable { onSelected(value) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(label, modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun InboxList(
    ideas: List<InboxIdea>,
    loading: Boolean,
    onEdit: (InboxIdea) -> Unit,
    onDelete: (InboxIdea) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ideas.isEmpty() && !loading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(
                            Icons.Outlined.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "先把念头放在这里",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "不必现在决定要不要做。记下一瞬间的灵感，等它值得行动时，再让 AI 帮你整理成计划。",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(ideas, key = { it.id }) { idea ->
            IdeaCard(idea = idea, onEdit = { onEdit(idea) }, onDelete = { onDelete(idea) })
        }
    }
}

@Composable
private fun IdeaCard(
    idea: InboxIdea,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    idea.content,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除想法")
                }
            }
            val timing = buildList {
                idea.durationMinutes?.let { add("持续 ${formatDuration(it)}") }
                idea.startAt?.let { add("最早 ${formatPlanDateTime(it)}") }
                idea.dueAt?.let { add("最晚 ${formatPlanDateTime(it)}") }
            }
            Text(
                timing.ifEmpty { listOf("还没有时间限制") }.joinToString(" · "),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (idea.items.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    idea.items.take(4).forEach { item ->
                        Text("• ${item.content}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (idea.items.size > 4) {
                        Text(
                            "还有 ${idea.items.size - 4} 项细节",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdeaAddButton(enabled: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(66.dp).graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        val color = MaterialTheme.colorScheme.onSecondaryContainer
        Canvas(Modifier.size(30.dp)) {
            val arm = 10.dp.toPx()
            val width = 2.6.dp.toPx()
            drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), width, StrokeCap.Round)
            drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), width, StrokeCap.Round)
        }
    }
}

private fun formatDuration(minutes: Int): String = when {
    minutes >= 24 * 60 && minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} 天"
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "$minutes 分钟"
}

private fun formatPlanDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
}.getOrElse { value }

@Composable
private fun AiPlanScreen(
    paddingValues: PaddingValues,
    ideas: List<InboxIdea>,
    tasks: List<TaskItem>,
    plan: AiPlan?,
    loading: Boolean,
    error: String?,
    onOpenInbox: () -> Unit,
    onGenerate: (String) -> Unit,
    onRevise: (Int, String) -> Unit,
    onAccept: (Int) -> Unit,
) {
    var message by rememberSaveable(plan?.id) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "默认避开三餐和 23:00–07:00 睡眠；吃饭、交通、就医和真正紧急的固定事项除外。",
                    modifier = Modifier.padding(start = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        error?.let {
            Text(it, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error)
        }

        if (plan == null) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (ideas.isEmpty() && tasks.isEmpty()) Icons.Outlined.Inbox else Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(58.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (ideas.isEmpty() && tasks.isEmpty()) "计划从一个念头开始" else "让零散想法开始行动",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (ideas.isEmpty() && tasks.isEmpty()) {
                        "先在收件匣留下想法。它可以没有日期，也不代表你承诺一定完成。"
                    } else {
                        "AI 会结合收件匣、现有任务和持续时间，整理出一份可以继续对话修改的计划。"
                    },
                    modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = {
                        if (ideas.isEmpty() && tasks.isEmpty()) onOpenInbox()
                        else onGenerate("请结合我的收件匣和现有任务，生成一份现实可执行的计划。")
                    },
                    enabled = !loading,
                    modifier = Modifier.padding(top = 22.dp),
                ) {
                    Text(if (ideas.isEmpty() && tasks.isEmpty()) "去收件匣记录想法" else "生成我的计划")
                }
                if (loading) CircularProgressIndicator(modifier = Modifier.padding(top = 18.dp))
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (plan.status == "accepted") "这份计划已开始执行" else "当前方案",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            plan.summary,
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(plan.items, key = { "${it.sourceType}-${it.sourceId}" }) { item ->
                PlanItemCard(item)
            }
            if (plan.messages.size > 2) {
                item {
                    Text(
                        "调整记录",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(plan.messages.drop(2).takeLast(8)) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (chat.role == "user") Arrangement.End else Arrangement.Start,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (chat.role == "user") {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(0.86f),
                        ) {
                            Text(chat.content, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }

        if (plan.status == "draft") {
            FloatingUnderlineTextField(
                value = message,
                onValueChange = { message = it.take(4000) },
                label = "告诉 AI 你想怎么调整",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onAccept(plan.id) },
                    enabled = !loading && plan.items.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("采用这份计划") }
                IconButton(
                    onClick = {
                        val request = message.trim()
                        if (request.isNotEmpty()) {
                            onRevise(plan.id, request)
                            message = ""
                        }
                    },
                    enabled = !loading && message.isNotBlank(),
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Outlined.Send, contentDescription = "发送调整要求")
                }
            }
        } else {
            Button(
                onClick = { onGenerate("请根据我现在的收件匣和任务重新生成计划。") },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) { Text("重新规划") }
        }
    }
}

@Composable
private fun PlanItemCard(item: PlanItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            val scheduleText = when {
                item.startAt != null && item.dueAt != null ->
                    "${formatPlanDateTime(item.startAt)} — ${formatPlanDateTime(item.dueAt)}"
                item.startAt != null -> "${formatPlanDateTime(item.startAt)} 开始"
                item.dueAt != null -> "${formatPlanDateTime(item.dueAt)} 前完成"
                else -> "暂不排期"
            }
            Text(
                buildString {
                    append(scheduleText)
                    item.durationMinutes?.let { append(" · ${formatDuration(it)}") }
                },
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.steps.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    item.steps.forEach { step -> Text("• ${step.title}") }
                }
            }
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BouncingTaskLoader(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val loaderHtml = remember(primaryColor, primaryContainerColor, labelColor) {
        UIVERSE_LOADER_HTML
            .replace(
                "linear-gradient(135deg, #23C4F8, #275EFE)",
                "linear-gradient(135deg, ${primaryColor.toCssHex()}, ${primaryContainerColor.toCssHex()})",
            )
            .replace(
                "rgba(39, 94, 254, 0.28)",
                primaryColor.toCssRgba(0.28f),
            )
            .replace(
                "#6C7486",
                labelColor.toCssHex(),
            )
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                tag = loaderHtml
                loadDataWithBaseURL(
                    "https://uiverse.io/",
                    loaderHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != loaderHtml) {
                webView.tag = loaderHtml
                webView.loadDataWithBaseURL(
                    "https://uiverse.io/",
                    loaderHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        modifier = modifier
            .size(width = 200.dp, height = 184.dp)
            .semantics { contentDescription = "正在加载任务" },
    )
}

private fun Color.toCssHex(): String = String.format(
    Locale.US,
    "#%02X%02X%02X",
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)

private fun Color.toCssRgba(alpha: Float): String = String.format(
    Locale.US,
    "rgba(%d, %d, %d, %.2f)",
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
    alpha.coerceIn(0f, 1f),
)

/** The original Uiverse.io by Nawsome loader, kept as HTML/CSS/SVG so the
 *  rotateY timing and page geometry are not approximated by a native rewrite. */
private const val UIVERSE_PAGE_SVG = """<svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg>"""

private val UIVERSE_PAGE_SVG_EXACT = UIVERSE_PAGE_SVG.replace(
    "C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33",
    "C74,34.1192881 72.8807119,33 71.5,33",
)

private val UIVERSE_LOADER_HTML = """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; width: 200px; height: 184px; overflow: visible; background: transparent; }
.loader {
  --background: linear-gradient(135deg, #23C4F8, #275EFE);
  --shadow: rgba(39, 94, 254, 0.28);
  --text: #6C7486;
  --page: rgba(255, 255, 255, 0.36);
  --page-fold: rgba(255, 255, 255, 0.52);
  --duration: 3s;
  width: 200px;
  height: 140px;
  position: relative;
}
.loader:before, .loader:after {
  --r: -6deg;
  content: "";
  position: absolute;
  bottom: 8px;
  width: 120px;
  top: 80%;
  box-shadow: 0 16px 12px var(--shadow);
  transform: rotate(var(--r));
}
.loader:before { left: 4px; }
.loader:after { --r: 6deg; right: 4px; }
.loader div {
  width: 100%;
  height: 100%;
  border-radius: 13px;
  position: relative;
  z-index: 1;
  perspective: 600px;
  box-shadow: 0 4px 6px var(--shadow);
  background-image: var(--background);
}
.loader div ul {
  margin: 0;
  padding: 0;
  list-style: none;
  position: relative;
}
.loader div ul li {
  --r: 180deg;
  --o: 0;
  --c: var(--page);
  position: absolute;
  top: 10px;
  left: 10px;
  transform-origin: 100% 50%;
  color: var(--c);
  opacity: var(--o);
  transform: rotateY(var(--r));
  -webkit-animation: var(--duration) ease infinite;
  animation: var(--duration) ease infinite;
}
.loader div ul li:nth-child(2) { --c: var(--page-fold); -webkit-animation-name: page-2; animation-name: page-2; }
.loader div ul li:nth-child(3) { --c: var(--page-fold); -webkit-animation-name: page-3; animation-name: page-3; }
.loader div ul li:nth-child(4) { --c: var(--page-fold); -webkit-animation-name: page-4; animation-name: page-4; }
.loader div ul li:nth-child(5) { --c: var(--page-fold); -webkit-animation-name: page-5; animation-name: page-5; }
.loader div ul li svg { width: 90px; height: 120px; display: block; }
.loader div ul li:first-child { --r: 0deg; --o: 1; }
.loader div ul li:last-child { --o: 1; }
.loader span {
  display: block;
  left: 0;
  right: 0;
  top: 100%;
  margin-top: 20px;
  text-align: center;
  color: var(--text);
  font: 14px sans-serif;
}
@keyframes page-2 {
  0% { transform: rotateY(180deg); opacity: 0; }
  20% { opacity: 1; }
  35%, 100% { opacity: 0; }
  50%, 100% { transform: rotateY(0deg); }
}
@keyframes page-3 {
  15% { transform: rotateY(180deg); opacity: 0; }
  35% { opacity: 1; }
  50%, 100% { opacity: 0; }
  65%, 100% { transform: rotateY(0deg); }
}
@keyframes page-4 {
  30% { transform: rotateY(180deg); opacity: 0; }
  50% { opacity: 1; }
  65%, 100% { opacity: 0; }
  80%, 100% { transform: rotateY(0deg); }
}
@keyframes page-5 {
  45% { transform: rotateY(180deg); opacity: 0; }
  65% { opacity: 1; }
  80%, 100% { opacity: 0; }
  95%, 100% { transform: rotateY(0deg); }
}
</style>
</head>
<body>
<div class="loader">
  <div>
    <ul>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,34.1192881 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,36.8807119 74,35.5 C74,34.1192881 72.9684641,33.0899613 71.6643757,33.0053177 L71.5,33 Z"></path></svg></li>
      <li><svg fill="currentColor" viewBox="0 0 90 120"><path d="M90,0 L90,120 L11,120 C4.92486775,120 0,115.075132 0,109 L0,11 C0,4.92486775 4.92486775,0 11,0 L90,0 Z M71.5,81 L18.5,81 C17.1192881,81 16,82.1192881 16,83.5 C16,84.8254834 17.0315359,85.9100387 18.3356243,85.9946823 L18.5,86 L71.5,86 C72.8807119,86 74,84.8807119 74,83.5 C74,82.1745166 72.9684641,81.0899613 71.6643757,81.0053177 L71.5,81 Z M71.5,57 L18.5,57 C17.1192881,57 16,58.1192881 16,59.5 C16,60.8254834 17.0315359,61.9100387 18.3356243,61.9946823 L18.5,62 L71.5,62 C72.8807119,62 74,60.8807119 74,59.5 C74,58.1192881 72.8807119,57 71.5,57 Z M71.5,33 L18.5,33 C17.1192881,33 16,34.1192881 16,35.5 C16,36.8254834 17.0315359,37.9100387 18.3356243,37.9946823 L18.5,38 L71.5,38 C72.8807119,38 74,38.1192881 74,35.5 C74,34.1192881 72.8807119,33 71.5,33 Z"></path></svg></li>
    </ul>
  </div>
  <span>Loading</span>
</div>
</body>
</html>
""".trimIndent().replace(
    Regex("<svg fill=\"currentColor\" viewBox=\"0 0 90 120\">.*?</svg>"),
    UIVERSE_PAGE_SVG_EXACT,
)

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
    placeholder: String = "请输入或粘贴需要提取的内容",
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
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 文字提取") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "输入或粘贴聊天、课程或安排内容，AI 会提取其中的任务和时间。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeCardTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "聊天内容",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null && !loading) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onParse(text.trim()) },
                enabled = !loading && text.isNotBlank(),
            ) { Text(if (loading) "创建中…" else "创建计划") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}

@Composable
private fun IdeaEditorDialog(
    idea: InboxIdea?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int?, String?, String?, List<IdeaDetail>) -> Unit,
) {
    var content by rememberSaveable(idea?.id) { mutableStateOf(idea?.content.orEmpty()) }
    var durationMinutes by rememberSaveable(idea?.id) { mutableStateOf(idea?.durationMinutes) }
    var showCustomDuration by rememberSaveable(idea?.id) { mutableStateOf(false) }
    var customDurationText by rememberSaveable(idea?.id) {
        mutableStateOf(idea?.durationMinutes?.toString().orEmpty())
    }
    var startAt by remember(idea?.id) { mutableStateOf(idea?.startAt?.let(::parseIdeaDateTime)) }
    var dueAt by remember(idea?.id) { mutableStateOf(idea?.dueAt?.let(::parseIdeaDateTime)) }
    var itemTexts by remember(idea?.id) {
        mutableStateOf(idea?.items?.map { it.content }.orEmpty())
    }
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    val windowInvalid = startAt != null && dueAt != null && startAt!!.isAfter(dueAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(if (idea == null) "记下一个想法" else "编辑想法") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingUnderlineTextField(
                    value = content,
                    onValueChange = { content = it.take(10_000) },
                    label = "想法内容",
                    singleLine = false,
                )
                IdeaDurationSelector(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    onMore = {
                        customDurationText = durationMinutes
                            ?.takeUnless { value -> IDEA_DURATION_OPTIONS.any { it.minutes == value } }
                            ?.toString()
                            .orEmpty()
                        showCustomDuration = true
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickerTarget = "start" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(startAt?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "选择开始时间")
                    }
                    OutlinedButton(
                        onClick = { pickerTarget = "due" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(dueAt?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "选择截止时间")
                    }
                }
                if (startAt != null || dueAt != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { startAt = null }) { Text("清除开始") }
                        TextButton(onClick = { dueAt = null }) { Text("清除截止") }
                    }
                }
                if (windowInvalid) {
                    Text("开始时间不能晚于截止时间", color = MaterialTheme.colorScheme.error)
                }
                Text("想法子项（可选）", style = MaterialTheme.typography.titleSmall)
                itemTexts.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FloatingUnderlineTextField(
                            value = value,
                            onValueChange = { next ->
                                itemTexts = itemTexts.toMutableList().also { it[index] = next.take(500) }
                            },
                            label = "细节 ${index + 1}",
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                itemTexts = itemTexts.toMutableList().also { it.removeAt(index) }
                            },
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除子项")
                        }
                    }
                }
                if (itemTexts.size < 30) {
                    TextButton(onClick = { itemTexts = itemTexts + "" }) { Text("＋ 添加一个细节") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        content.trim(),
                        durationMinutes,
                        startAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                        dueAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                        itemTexts.mapNotNull { text ->
                            text.trim().takeIf(String::isNotBlank)?.let(::IdeaDetail)
                        },
                    )
                },
                enabled = !loading && content.isNotBlank() && !windowInvalid,
            ) { Text("保存到收件匣") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )

    pickerTarget?.let { target ->
        TaskDateTimePickerDialog(
            title = if (target == "start") "设置开始时间" else "设置截止时间",
            initialDateTime = if (target == "start") startAt else dueAt,
            onDismiss = { pickerTarget = null },
            onConfirm = { selected ->
                if (target == "start") startAt = selected else dueAt = selected
                pickerTarget = null
            },
        )
    }

    if (showCustomDuration) {
        AlertDialog(
            onDismissRequest = { showCustomDuration = false },
            title = { Text("自定义持续时间") },
            text = {
                FloatingUnderlineTextField(
                    value = customDurationText,
                    onValueChange = { customDurationText = it.filter(Char::isDigit).take(6) },
                    label = "分钟数",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = "最长可设置 365 天",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        durationMinutes = customDurationText.toIntOrNull()?.coerceIn(1, 525_600)
                        showCustomDuration = false
                    },
                    enabled = customDurationText.toIntOrNull()?.let { it in 1..525_600 } == true,
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDuration = false }) { Text("取消") }
            },
        )
    }
}

private data class IdeaDurationOption(val minutes: Int?, val label: String)

private val IDEA_DURATION_OPTIONS = listOf(
    IdeaDurationOption(null, "∞"),
    IdeaDurationOption(15, "15"),
    IdeaDurationOption(30, "30"),
    IdeaDurationOption(45, "45"),
    IdeaDurationOption(60, "1h"),
    IdeaDurationOption(90, "1.5h"),
)

@Composable
private fun IdeaDurationSelector(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    onMore: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = IDEA_DURATION_OPTIONS.indexOfFirst { it.minutes == value }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentHaptic by rememberUpdatedState(haptic)
    val isCustom = value != null && selectedIndex < 0
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastSelectedIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) lastSelectedIndex = selectedIndex
    }

    fun select(index: Int) {
        val next = IDEA_DURATION_OPTIONS[index].minutes
        if (index != lastSelectedIndex) {
            lastSelectedIndex = index
            currentHaptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        if (next != currentValue) {
            currentOnValueChange(next)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预计持续时间", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onMore) {
                Text(if (isCustom) "当前 ${formatDuration(requireNotNull(value))}" else "更多…")
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val selectorWidth = maxWidth
            val slotWidth = selectorWidth / IDEA_DURATION_OPTIONS.size
            val indicatorSize = minOf(slotWidth - 10.dp, 44.dp)
            val slotWidthPx = with(LocalDensity.current) { slotWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotWidth)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        fun updateFromPointer(x: Float) {
                            val pointerSlotWidthPx = size.width.toFloat() / IDEA_DURATION_OPTIONS.size
                            dragOffsetPx = (x - pointerSlotWidthPx / 2f)
                                .coerceIn(0f, size.width - pointerSlotWidthPx)
                            val index = ((x / size.width) * IDEA_DURATION_OPTIONS.size)
                                .toInt()
                                .coerceIn(0, IDEA_DURATION_OPTIONS.lastIndex)
                            select(index)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            updateFromPointer(down.position.x)
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes
                                        .firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break
                                    updateFromPointer(change.position.x)
                                    change.consume()
                                }
                            } finally {
                                isDragging = false
                            }
                        }
                    },
            ) {
                val indicatorOffsetPx by animateFloatAsState(
                targetValue = when {
                    selectedIndex < 0 -> 0f
                    isDragging -> dragOffsetPx
                    else -> slotWidthPx * selectedIndex
                },
                animationSpec = if (isDragging) {
                    snap()
                } else {
                    spring(dampingRatio = 0.72f, stiffness = 500f)
                },
                label = "idea-duration-indicator",
            )
            val animatedIndicatorSize by animateDpAsState(
                targetValue = if (isDragging) slotWidth else indicatorSize,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "idea-duration-size",
            )
            val animatedIndicatorCorner by animateDpAsState(
                targetValue = if (isDragging) 16.dp else 9.dp,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
                label = "idea-duration-corner",
            )
            if (selectedIndex >= 0) {
                val horizontalInsetPx = with(LocalDensity.current) { 4.dp.toPx() }
                val innerTrackWidthPx = with(LocalDensity.current) { selectorWidth.toPx() } - horizontalInsetPx * 2f
                val progressWidthPx = if (indicatorOffsetPx <= 0f) {
                    0f
                } else {
                    indicatorOffsetPx + slotWidthPx / 2f - horizontalInsetPx
                }
                val progressFraction = (progressWidthPx / innerTrackWidthPx)
                    .coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(indicatorSize)
                            .clip(RoundedCornerShape(9.dp))
                            .graphicsLayer {
                                scaleX = progressFraction
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    )
                }
            }
            Row(Modifier.fillMaxSize()) {
                IDEA_DURATION_OPTIONS.forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { select(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            option.label,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (selectedIndex >= 0) {
                Box(
                    Modifier
                        .graphicsLayer { translationX = indicatorOffsetPx }
                        .width(slotWidth)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(animatedIndicatorSize)
                            .clip(RoundedCornerShape(animatedIndicatorCorner))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            IDEA_DURATION_OPTIONS[selectedIndex].label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        }
        Text(
            if (value == null) "没有时间限制" else "预计 ${formatDuration(value)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun parseIdeaDateTime(value: String): LocalDateTime? = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
}.getOrNull()

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
    title: String = "设置截止时间",
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
                        title,
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
private fun CalendarScreen(
    paddingValues: PaddingValues,
    tasks: List<TaskItem>,
    requestedDate: String? = null,
) {
    var visibleMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val visibleMonth = remember(visibleMonthText) { YearMonth.parse(visibleMonthText) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val tasksByDate = visibleCalendarTasks(tasks).mapNotNull { task ->
        task.dueAt?.let { dueAt -> parseDueDate(dueAt)?.let { date -> date to task } }
    }.groupBy({ it.first }, { it.second })
    val pendingTaskCountsByDate = tasksByDate.mapValues { (_, datedTasks) ->
        datedTasks.count { !it.completed }
    }
    val firstDayOffset = visibleMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = ((firstDayOffset + visibleMonth.lengthOfMonth() + 6) / 7) * 7
    val selectedTasks = tasksByDate[selectedDate].orEmpty().sortedBy { it.dueAt }

    LaunchedEffect(requestedDate) {
        val date = requestedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date != null) {
            visibleMonthText = YearMonth.from(date).toString()
            selectedDateText = date.toString()
        }
    }

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
                "颜色越深表示当天未完成任务越多，格子下方数字是未完成任务数；今天有未完成任务时使用红色。",
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
                                val taskCount = pendingTaskCountsByDate[date] ?: 0
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
                                        if (taskCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .widthIn(min = 22.dp)
                                                    .height(12.dp)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (isToday) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        },
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    taskCount.toString(),
                                                    color = if (isToday) {
                                                        MaterialTheme.colorScheme.onError
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    },
                                                    fontSize = 8.sp,
                                                    lineHeight = 8.sp,
                                                )
                                            }
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
                    Icons.Outlined.AccountCircle,
                    contentDescription = "账号",
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
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Logout,
                        contentDescription = "退出登录",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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

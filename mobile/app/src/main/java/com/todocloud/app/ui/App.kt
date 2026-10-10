package com.todocloud.app.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todocloud.app.branding.MascotStateManager
import com.todocloud.app.data.ApiException
import com.todocloud.app.data.AiPlan
import com.todocloud.app.data.AiParseResult
import com.todocloud.app.data.InboxIdea
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TaskStep
import com.todocloud.app.data.TodoCloudRepository
import com.todocloud.app.notification.ReminderScheduler
import com.todocloud.app.widget.TaskCalendarWidget
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    var aiResult by remember { mutableStateOf<AiParseResult?>(null) }
    var planLoading by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    // Question/answer turns shown before a plan exists; not stored on the server.
    var planConversation by remember { mutableStateOf(emptyList<com.todocloud.app.data.PlanMessage>()) }
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

    suspend fun removeIdeasConvertedToTasks(
        token: String,
        currentIdeas: List<InboxIdea>,
        acceptedPlans: List<AiPlan>,
    ): List<InboxIdea> {
        val currentIdeaIds = currentIdeas.mapTo(mutableSetOf()) { it.id }
        val convertedIdeaIds = acceptedPlans
            .asSequence()
            .filter { it.status == "accepted" }
            .flatMap { it.items.asSequence() }
            .filter { it.sourceType == "idea" && it.sourceId in currentIdeaIds }
            .map { it.sourceId }
            .toSet()
        convertedIdeaIds.forEach { ideaId ->
            try {
                repository.deleteIdea(token, ideaId)
            } catch (exception: ApiException) {
                // Newer servers already hide converted ideas atomically.
                // Older servers still need the explicit delete request.
                if (exception.statusCode != 404) throw exception
            }
        }
        return currentIdeas.filterNot { it.id in convertedIdeaIds }
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
                val loadedIdeas = repository.listIdeas(currentSession.token)
                val loadedPlans = repository.listPlans(currentSession.token)
                plans = loadedPlans
                ideas = removeIdeasConvertedToTasks(
                    token = currentSession.token,
                    currentIdeas = loadedIdeas,
                    acceptedPlans = loadedPlans,
                )
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
    val visiblePlan = plans.firstOrNull()?.takeIf { plan ->
        fun sourceIsActive(sourceType: String, sourceId: Int): Boolean {
            return when (sourceType) {
                "task" -> sourceId in planningTaskIds
                "idea" -> sourceId in planningIdeaIds
                "prompt" -> true
                else -> false
            }
        }

        plan.status == "draft" && plan.items.isNotEmpty() &&
            plan.items.all { item ->
                sourceIsActive(item.sourceType, item.sourceId)
            }
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
                    onIdea = {
                        showAddMenu = false
                        editingIdea = null
                        showIdeaComposer = true
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
                    onToggle = { task, prepareForReorder ->
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
                        prepareForReorder()
                        tasks = sortTasks(tasks)
                    }
                },
                    onStepToggle = { task, stepIndex, prepareForReorder ->
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
                        prepareForReorder()
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
                conversation = planConversation,
                onOpenInbox = {
                    selectedTab = 0
                    taskSection = "inbox"
                },
                onGenerate = { message, selectedIdeaIds, forcePlan, images ->
                    scope.launch {
                        planLoading = true
                        planError = null
                        try {
                            val result = repository.chatPlan(
                                currentSession.token,
                                message,
                                selectedIdeaIds,
                                planConversation,
                                forcePlan,
                                images,
                            )
                            val shownMessage = if (images.isEmpty()) message else "$message\n（附图 ${images.size} 张）"
                            when (result) {
                                is com.todocloud.app.data.PlanChatResult.Answer -> {
                                    planConversation = (
                                        planConversation +
                                            com.todocloud.app.data.PlanMessage("user", shownMessage) +
                                            com.todocloud.app.data.PlanMessage("assistant", result.reply)
                                        ).takeLast(12)
                                }
                                is com.todocloud.app.data.PlanChatResult.Created -> {
                                    planConversation = emptyList()
                                    plans = listOf(result.plan) + plans.filterNot { it.id == result.plan.id }
                                }
                            }
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
                onRevise = { planId, message, images ->
                    scope.launch {
                        planLoading = true
                        planError = null
                        try {
                            val revised = repository.revisePlan(currentSession.token, planId, message, images)
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
                            ideas = removeIdeasConvertedToTasks(
                                token = currentSession.token,
                                currentIdeas = ideas,
                                acceptedPlans = listOf(accepted.plan),
                            )
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

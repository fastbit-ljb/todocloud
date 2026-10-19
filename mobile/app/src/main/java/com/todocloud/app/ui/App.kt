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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.todocloud.app.data.isLocal
import com.todocloud.app.notification.ReminderScheduler
import com.todocloud.app.widget.TaskCalendarWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
@androidx.compose.material3.ExperimentalMaterial3Api
fun TodoCloudApp(
    requestedTab: Int = 0,
    requestedCalendarDate: String? = null,
    sharedContent: SharedContent? = null,
    onSharedContentHandled: () -> Unit = {},
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
    // Task open in the editor; null with showComposer = a new task.
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    // A delete requested by the user, including the short card animation and the
    // following undo window. Kept at screen level so switching tabs cannot cancel it.
    var pendingDeleteIds by remember { mutableStateOf(emptySet<Int>()) }
    var refreshing by remember { mutableStateOf(false) }
    // Set while the lists come from the on-device copy because the server was unreachable.
    var offlineSyncedAt by remember { mutableStateOf<Long?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var shareLoading by remember { mutableStateOf(false) }
    // Local mode's own model key (Settings → AI 模型).
    var aiChoice by remember { mutableStateOf(repository.aiKeys.choice()) }
    var showAiKeyDialog by remember { mutableStateOf(false) }
    var aiKeyChecking by remember { mutableStateOf(false) }
    var aiKeyError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showIdeaComposer by rememberSaveable { mutableStateOf(false) }
    var editingIdea by remember { mutableStateOf<InboxIdea?>(null) }
    var aiResult by remember { mutableStateOf<AiParseResult?>(null) }
    var planLoading by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var defaultReminderOffsetMinutes by remember(context) {
        mutableStateOf(loadDefaultReminderOffset(context))
    }
    var reminderMode by remember(context) {
        mutableStateOf(ReminderScheduler.loadReminderMode(context))
    }
    var mascotSet by remember(context) { mutableStateOf(MascotStateManager.selectedSet(context)) }
    // The state the desktop icon switches to when the user leaves the app. The account avatar
    // and the 吉祥物 page show it, so they follow every task change (added, completed, deleted,
    // rescheduled, synced) at once; opening a tab or settings page re-reads it as well, so
    // states that come with time passing (overdue, frozen) are current there too.
    val mascotState = remember(context, tasksLoaded, tasks, selectedTab, settingsPage) {
        MascotStateManager.displayState(context, tasks, tasksLoaded)
    }
    // Streak counter in the top bar; follows every task change the same way.
    val flameStreakCount = remember(tasks, pendingDeleteIds, selectedTab) {
        flameStreak(tasks.filter { it.id !in pendingDeleteIds }, java.time.LocalDate.now())
    }
    // Last count the counter showed, so its +1 / -1 animation also plays for a change made
    // while it was off screen. Cleared on account switch: a different account's count is
    // not a change.
    val flameStreakShown = rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(session?.token) { flameStreakShown.value = null }

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

    BackHandler(enabled = selectedTab == 0 && searching && !showAddMenu) {
        searching = false
        searchQuery = ""
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
                error = NETWORK_ERROR_MESSAGE
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

    suspend fun reloadAll(token: String) {
        // Tasks still inside their undo window stay hidden (and unscheduled).
        tasks = sortTasks(withoutPendingDeletes(repository.listTasks(token), pendingDeleteIds))
            .also(::scheduleTasks)
        val loadedIdeas = repository.listIdeas(token)
        val loadedPlans = repository.listPlans(token)
        plans = loadedPlans
        ideas = removeIdeasConvertedToTasks(
            token = token,
            currentIdeas = loadedIdeas,
            acceptedPlans = loadedPlans,
        )
        tasksLoaded = true
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
            pendingDeleteIds = emptySet()
            MascotStateManager.reset(context)
        } else {
            tasksLoaded = false
            loading = true
            taskHeaderLoading = true
            error = null
            // Show the encrypted on-device copy at once; the server load replaces it.
            val snapshot = repository.loadOfflineSnapshot(currentSession.email)
            if (snapshot != null && tasks.isEmpty() && ideas.isEmpty()) {
                tasks = sortTasks(snapshot.tasks)
                ideas = snapshot.ideas
            }
            try {
                reloadAll(currentSession.token)
                offlineSyncedAt = null
            } catch (exception: ApiException) {
                handleApiError(exception)
            } catch (_: Exception) {
                if (snapshot != null) {
                    offlineSyncedAt = snapshot.syncedAtMillis
                    error = offlineNotice(snapshot.syncedAtMillis)
                } else {
                    error = NETWORK_ERROR_MESSAGE
                }
            } finally {
                loading = false
                taskHeaderLoading = false
            }
        }
    }

    // Pull-to-refresh: same reload as the first load, but with the pull indicator
    // instead of the header animation.
    fun refresh() {
        val token = session?.token ?: return
        if (refreshing) return
        scope.launch {
            refreshing = true
            error = null
            try {
                reloadAll(token)
                offlineSyncedAt = null
            } catch (exception: ApiException) {
                handleApiError(exception)
            } catch (_: Exception) {
                error = offlineSyncedAt?.let(::offlineNotice) ?: NETWORK_ERROR_MESSAGE
            } finally {
                refreshing = false
            }
        }
    }

    // Keep the offline copy in step with what the server last confirmed. Only
    // after a successful load: the offline view itself is never written back.
    LaunchedEffect(session?.email, tasks, ideas, tasksLoaded) {
        val email = session?.email ?: return@LaunchedEffect
        if (!tasksLoaded) return@LaunchedEffect
        delay(500) // coalesce bursts of edits
        withContext(Dispatchers.IO) { repository.saveOfflineSnapshot(email, tasks, ideas) }
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

    var updatePrompt by remember { mutableStateOf<com.todocloud.app.update.AppVersionInfo?>(null) }
    var updateAvailableVersion by remember(context) {
        mutableStateOf(
            com.todocloud.app.update.UpdateStore(context).latestKnownVersion?.takeIf {
                com.todocloud.app.update.UpdatePolicy.isNewer(it, com.todocloud.app.update.UpdateChecker.currentVersion)
            },
        )
    }
    var checkingUpdate by remember { mutableStateOf(false) }

    // Launch-time update check: silent on failure, dialog at most once a day per version.
    LaunchedEffect(Unit) {
        val info = com.todocloud.app.update.UpdateChecker.fetch() ?: return@LaunchedEffect
        val store = com.todocloud.app.update.UpdateStore(context)
        val current = com.todocloud.app.update.UpdateChecker.currentVersion
        store.latestKnownVersion = info.latestVersion
        updateAvailableVersion = info.latestVersion.takeIf {
            com.todocloud.app.update.UpdatePolicy.isNewer(it, current)
        }
        if (com.todocloud.app.update.UpdatePolicy.shouldShowDialog(
                info, current, store.skippedVersion, store.lastPromptDay, com.todocloud.app.update.UpdateStore.today(),
            )
        ) {
            updatePrompt = info
        }
    }

    updatePrompt?.let { info ->
        UpdateDialog(
            info = info,
            onDownload = {
                rememberUpdateDecision(context, info, skip = false)
                openUpdateDownload(context, info)
                updatePrompt = null
            },
            onLater = {
                rememberUpdateDecision(context, info, skip = false)
                updatePrompt = null
            },
            onSkip = {
                rememberUpdateDecision(context, info, skip = true)
                updatePrompt = null
            },
        )
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
            onUseLocally = {
                error = null
                session = repository.startLocalSession()
            },
        )
        return
    }

    val currentSession = session ?: return
    val planState = remember(currentSession.email, currentSession.isLocal) {
        PlanConversationState(repository, currentSession.token)
    }
    LaunchedEffect(planState) { planState.load() }

    fun toggleTask(task: TaskItem, prepareForReorder: () -> Unit) {
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
            // Completing a recurring task makes the server create the next one.
            if (nextCompleted && task.recurrence != null) {
                tasks = sortTasks(withoutPendingDeletes(repository.listTasks(currentSession.token), pendingDeleteIds))
                    .also(::scheduleTasks)
            }
        }
    }

    fun toggleStep(task: TaskItem, stepIndex: Int, prepareForReorder: () -> Unit) {
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
            if (nextCompleted && !task.completed && task.recurrence != null) {
                tasks = sortTasks(withoutPendingDeletes(repository.listTasks(currentSession.token), pendingDeleteIds))
                    .also(::scheduleTasks)
            }
        }
    }

    // Start the delete at screen level immediately. The card still gets its full
    // animation, but switching tabs no longer disposes the coroutine before it can
    // hide the task and offer undo. The server delete only happens once the snackbar
    // goes away without the action (timeout, swipe, or the next delete).
    fun deleteWithUndo(task: TaskItem) {
        if (task.id in pendingDeleteIds) return
        pendingDeleteIds = pendingDeleteIds + task.id
        scope.launch {
            delay(TRASH_ANIMATION_BEAT_MILLIS + CARD_FLY_AWAY_DURATION_MILLIS)
            // Logging out or switching accounts clears pending deletes.
            if (task.id !in pendingDeleteIds) return@launch
            tasks = tasks.filterNot { it.id == task.id }
            ReminderScheduler.cancel(context, task.id)
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${task.title.take(20)}」",
                actionLabel = "撤销",
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteIds = pendingDeleteIds - task.id
                tasks = restoreTask(tasks, task)
                ReminderScheduler.schedule(context, task)
                return@launch
            }
            try {
                repository.deleteTask(currentSession.token, task.id)
            } catch (exception: ApiException) {
                if (exception.statusCode != 404) {
                    // Could not delete: put it back so the screen matches the server.
                    tasks = restoreTask(tasks, task)
                    ReminderScheduler.schedule(context, task)
                    handleApiError(exception)
                }
            } catch (_: Exception) {
                tasks = restoreTask(tasks, task)
                ReminderScheduler.schedule(context, task)
                error = "删除失败：$NETWORK_ERROR_MESSAGE"
            } finally {
                pendingDeleteIds = pendingDeleteIds - task.id
            }
        }
    }

    fun generatePlan(message: String, ideaIds: Set<Int>, forcePlan: Boolean, images: List<android.net.Uri>, references: List<com.todocloud.app.data.ConversationReference> = emptyList()) {
        scope.launch {
            planError = null
            planState.send(message, ideaIds, forcePlan, images, references) { plan ->
                if (session?.email == currentSession.email && session?.isLocal == currentSession.isLocal) {
                    plans = listOf(plan) + plans.filterNot { it.id == plan.id }
                }
            }
        }
    }

    // Shortcuts on the task list ("帮我排今天", "让 AI 重新排"): open the AI tab and plan right away.
    fun planShortcut(message: String) {
        selectedTab = 2
        showAddMenu = false
        planState.newConversation()
        generatePlan(message, ideas.mapTo(linkedSetOf()) { it.id }, forcePlan = true, images = emptyList())
    }

    suspend fun undoAcceptedPlan(planId: Int) {
        val undone = repository.undoPlan(currentSession.token, planId)
        undone.deletedTaskIds.forEach { ReminderScheduler.cancel(context, it) }
        val restoredById = undone.restoredTasks.associateBy { it.id }
        tasks = sortTasks(
            tasks.filterNot { it.id in undone.deletedTaskIds }.map { restoredById[it.id] ?: it },
        )
        scheduleTasks(undone.restoredTasks)
        plans = listOf(undone.plan) + plans.filterNot { it.id == undone.plan.id }
        planState.updatePlan(undone.plan)
        ideas = repository.listIdeas(currentSession.token)
    }

    fun acceptPlanWithUndo(planId: Int, items: List<com.todocloud.app.data.PlanItem>?) {
        scope.launch {
            planLoading = true
            planError = null
            val accepted = try {
                repository.acceptPlan(currentSession.token, planId, items).also { accepted ->
                    plans = listOf(accepted.plan) + plans.filterNot { it.id == accepted.plan.id }
                    planState.updatePlan(accepted.plan)
                    ideas = removeIdeasConvertedToTasks(
                        token = currentSession.token,
                        currentIdeas = ideas,
                        acceptedPlans = listOf(accepted.plan),
                    )
                    tasks = sortTasks(repository.listTasks(currentSession.token)).also(::scheduleTasks)
                    ideas = repository.listIdeas(currentSession.token)
                    MascotStateManager.recordMeaningfulActivity(context)
                }
            } catch (exception: ApiException) {
                if (exception.statusCode == 401) handleApiError(exception)
                else planError = exception.message
                null
            } catch (_: Exception) {
                planError = "网络连接失败，请稍后重试"
                null
            } finally {
                planLoading = false
            }
            if (accepted == null) return@launch
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "已采用 ${accepted.plan.items.size} 项安排",
                actionLabel = "撤回",
                duration = SnackbarDuration.Long,
            )
            if (result != SnackbarResult.ActionPerformed) return@launch
            planLoading = true
            try {
                undoAcceptedPlan(planId)
            } catch (exception: ApiException) {
                if (exception.statusCode == 401) handleApiError(exception)
                else planError = exception.message
            } catch (_: Exception) {
                planError = "撤回失败：网络连接失败，请稍后重试"
            } finally {
                planLoading = false
            }
        }
    }
    val planningTasks = tasks.filterNot { it.completed }
    val planningTaskIds = planningTasks.mapTo(mutableSetOf()) { it.id }
    val planningIdeaIds = ideas.mapTo(mutableSetOf()) { it.id }
    val selectedPlan = planState.active?.plan?.let { saved -> plans.firstOrNull { it.id == saved.id } ?: saved }
    val visiblePlan = selectedPlan?.takeIf { plan ->
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            when {
                                selectedTab == 0 && taskSection == "inbox" -> "收件匣"
                                selectedTab == 0 -> "我的任务"
                                selectedTab == 3 && settingsPage == "permissions" -> "权限"
                                selectedTab == 3 && settingsPage == "sponsor" -> "赞助开发"
                                selectedTab == 3 && settingsPage == "mascot" -> "吉祥物"
                                else -> tabs[selectedTab].label
                            },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        if (selectedTab == 0 && taskSection == "tasks") {
                            Text(
                                taskHeaderSubtitle(tasks),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (selectedTab == 3 && settingsPage != "main") {
                        IconButton(onClick = { settingsPage = "main" }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回设置")
                        }
                    }
                },
                actions = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        FlameStreakBadge(
                            count = flameStreakCount,
                            animateChanges = tasksLoaded,
                            lastShown = flameStreakShown,
                            // From the task page it opens the calendar, where the flames are.
                            onClick = if (selectedTab == 0) {
                                {
                                    showAddMenu = false
                                    selectedTab = 1
                                }
                            } else {
                                null
                            },
                        )
                    }
                    if (selectedTab == 0 && !searching) {
                        IconButton(onClick = {
                            showAddMenu = false
                            searching = true
                        }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            if (selectedTab != 3 || settingsPage == "main") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                                indicatorColor = com.todocloud.app.ui.theme.selectionIndicatorColor(),
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
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
                        editingTask = null
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
    ) { scaffoldPadding ->
        // Task list, inbox and calendar can be pulled down to reload from the server.
        val pullable = selectedTab == 0 || selectedTab == 1
        val paddingValues = if (pullable) PaddingValues(0.dp) else scaffoldPadding
        val screen: @Composable () -> Unit = {
        when (selectedTab) {
            0 -> TaskHomeScreen(
                    paddingValues = paddingValues,
                    section = taskSection,
                    onSectionChange = {
                        taskSection = it
                        showAddMenu = false
                    },
                    tasks = tasks,
                    deletingTaskIds = pendingDeleteIds,
                    ideas = ideas,
                    loading = loading,
                    showLoadingAnimation = taskHeaderLoading,
                    error = error,
                    onToggle = { task, prepareForReorder -> toggleTask(task, prepareForReorder) },
                    onStepToggle = { task, stepIndex, prepareForReorder -> toggleStep(task, stepIndex, prepareForReorder) },
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
                    onDelete = { task -> deleteWithUndo(task) },
                    onEditTask = { task ->
                        editingTask = task
                        showComposer = true
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
                    onPlanToday = { planShortcut(todayPlanMessage()) },
                    onReplanOverdue = { overdue -> planShortcut(overduePlanMessage(overdue)) },
                    searching = searching,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        searching = false
                        searchQuery = ""
                    },
                )

            1 -> CalendarScreen(
                paddingValues = paddingValues,
                tasks = tasks,
                requestedDate = requestedCalendarDate,
                onToggleTask = { task -> toggleTask(task) {} },
                onToggleStep = { task, index -> toggleStep(task, index) {} },
                onEditTask = { task ->
                    editingTask = task
                    showComposer = true
                },
            )
            2 -> AiPlanScreen(
                paddingValues = paddingValues,
                ideas = ideas,
                tasks = planningTasks,
                greetingScene = if (tasksLoaded || offlineSyncedAt != null || error != null) {
                    planGreetingScene(tasks = tasks, tasksKnown = tasksLoaded)
                } else {
                    null
                },
                plan = visiblePlan,
                historyPlan = selectedPlan,
                loading = planLoading || planState.loading,
                conversationKey = planState.key,
                conversationTitle = planState.active?.title ?: "新会话",
                conversations = planState.conversations,
                syncPending = planState.active?.syncPending == true,
                onRetrySync = { scope.launch { planState.retrySync() } },
                onNewConversation = { planState.newConversation(); planError = null },
                onOpenConversation = { id -> scope.launch { planState.open(id); planError = null } },
                onRefreshHistory = { scope.launch { planState.refreshHistory() } },
                cloudHistory = !currentSession.isLocal,
                deletedConversationIds = planState.deletedIds,
                onDeleteConversations = { ids -> scope.launch {
                    planError = null
                    val deleted = planState.delete(ids)
                    if (deleted.isNotEmpty() && planState.error == null) android.widget.Toast.makeText(context, "已删除 ${deleted.size} 个会话", android.widget.Toast.LENGTH_SHORT).show()
                } },
                error = planError ?: planState.error,
                conversation = planState.active?.messages.orEmpty(),
                onOpenInbox = {
                    selectedTab = 0
                    taskSection = "inbox"
                },
                onGenerate = { message, selectedIdeaIds, forcePlan, images, references ->
                    generatePlan(message, selectedIdeaIds, forcePlan, images, references)
                },
                onRevise = { planId, message, images, references ->
                    scope.launch {
                        planError = null
                        planState.revise(planId, message, images, references) { revised ->
                            if (session?.email == currentSession.email && session?.isLocal == currentSession.isLocal) {
                                plans = listOf(revised) + plans.filterNot { it.id == revised.id }
                            }
                        }
                    }
                },
                onAccept = { planId, items -> acceptPlanWithUndo(planId, items) },
            )
            else -> when (settingsPage) {
                "permissions" -> PermissionSettingsScreen(
                    context = context,
                    paddingValues = paddingValues,
                    reminderMode = reminderMode,
                )
                "sponsor" -> SponsorSettingsScreen(paddingValues)
                "mascot" -> MascotSettingsScreen(
                    paddingValues = paddingValues,
                    selected = mascotSet,
                    shownState = mascotState,
                    onSelect = { set ->
                        MascotStateManager.selectSet(context, set)
                        mascotSet = set
                    },
                )
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
                    mascot = mascotSet,
                    mascotState = mascotState,
                    onOpenMascot = { settingsPage = "mascot" },
                    updateAvailableVersion = updateAvailableVersion,
                    checkingUpdate = checkingUpdate,
                    onCheckUpdate = {
                        scope.launch {
                            checkingUpdate = true
                            val info = com.todocloud.app.update.UpdateChecker.fetch()
                            checkingUpdate = false
                            val current = com.todocloud.app.update.UpdateChecker.currentVersion
                            when {
                                info == null -> android.widget.Toast
                                    .makeText(context, "暂时无法检查更新，请稍后再试", android.widget.Toast.LENGTH_SHORT).show()
                                com.todocloud.app.update.UpdatePolicy.isNewer(info.latestVersion, current) -> {
                                    com.todocloud.app.update.UpdateStore(context).latestKnownVersion = info.latestVersion
                                    updateAvailableVersion = info.latestVersion
                                    updatePrompt = info // a manual check always shows the dialog
                                }
                                else -> {
                                    updateAvailableVersion = null
                                    android.widget.Toast
                                        .makeText(context, "已是最新版本 $current", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onLogout = {
                        scope.launch {
                            repository.logout(currentSession)
                            MascotStateManager.reset(context)
                            session = null
                        }
                    },
                    aiChoice = aiChoice,
                    onEditAiKey = {
                        aiKeyError = null
                        showAiKeyDialog = true
                    },
                )
            }
        }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (pullable) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = ::refresh,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            ) { screen() }
        } else {
            screen()
        }
        // Own snackbar slot, pinned to the bottom; on the task tab it sits above the
        // add button (66dp + 16dp margin) instead of covering it.
        val fabClearance = if (selectedTab == 0) 66.dp + 16.dp else 0.dp
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = scaffoldPadding.calculateBottomPadding() + fabClearance + 8.dp),
        ) { data ->
            // Light snackbar in the theme's own tint instead of the default dark one.
            androidx.compose.material3.Snackbar(
                snackbarData = data,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionColor = MaterialTheme.colorScheme.primary,
            )
        }
        }
        }

        if (showComposer) {
        TaskEditorDialog(
            task = editingTask,
            loading = loading,
            onDismiss = {
                if (!loading) {
                    showComposer = false
                    editingTask = null
                }
            },
            onSave = { draft ->
                runRequest {
                    val existing = editingTask
                    val saved = if (existing == null) {
                        repository.createTask(
                            currentSession.token,
                            draft.title,
                            draft.description,
                            draft.dueAt,
                            draft.reminderOffsetMinutes,
                            steps = draft.steps,
                            startAt = draft.startAt,
                            durationMinutes = draft.durationMinutes,
                            recurrence = draft.recurrence,
                        ).also { MascotStateManager.recordMeaningfulActivity(context) }
                    } else {
                        repository.editTask(currentSession.token, existing.id, draft)
                    }
                    ReminderScheduler.schedule(context, saved)
                    tasks = sortTasks(listOf(saved) + tasks.filterNot { it.id == saved.id })
                    showComposer = false
                    editingTask = null
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

    if (showAiKeyDialog) {
        AiKeyDialog(
            current = aiChoice,
            signedIn = session?.isLocal == false,
            savedKeyFor = { repository.aiKeys.apiKey(it.id) != null },
            savedModelFor = { repository.aiKeys.model(it.id) },
            checking = aiKeyChecking,
            error = aiKeyError,
            onSave = { request ->
                scope.launch {
                    aiKeyChecking = true
                    aiKeyError = null
                    val signedIn = session?.isLocal == false
                    try {
                        if (aiSaveNeedsCheck(request, signedIn)) {
                            val key = request.key ?: repository.aiKeys.apiKey(request.provider.id)
                                ?: throw ApiException("请填写 API 密钥")
                            repository.checkAiKey(request.provider.id, key, request.model)
                            // Local mode never changes what a signed-in session uses.
                            val own = if (signedIn) request.ownWhenSignedIn else repository.aiKeys.ownWhenSignedIn
                            repository.aiKeys.save(request.provider.id, request.key, request.model, own)
                        } else {
                            repository.aiKeys.setOwnWhenSignedIn(false)
                        }
                        aiChoice = repository.aiKeys.choice()
                        showAiKeyDialog = false
                        val done = if (aiSaveNeedsCheck(request, signedIn)) "${request.provider.label} 可用，已保存" else "已改回服务器的 AI"
                        android.widget.Toast.makeText(context, done, android.widget.Toast.LENGTH_SHORT).show()
                    } catch (exception: ApiException) {
                        aiKeyError = exception.message
                    } catch (_: Exception) {
                        aiKeyError = "验证失败，请检查网络后重试"
                    } finally {
                        aiKeyChecking = false
                    }
                }
            },
            onClear = { provider ->
                repository.aiKeys.clearKey(provider.id)
                aiChoice = repository.aiKeys.choice()
                showAiKeyDialog = false
            },
            onDismiss = { showAiKeyDialog = false },
        )
    }

    sharedContent?.let { content ->
        // Content from the system share sheet. Waits behind the login screen when
        // signed out, since both actions need the account.
        fun handleShare(action: suspend () -> Unit) {
            scope.launch {
                shareLoading = true
                error = null
                try {
                    action()
                } catch (exception: ApiException) {
                    handleApiError(exception)
                } catch (_: Exception) {
                    error = NETWORK_ERROR_MESSAGE
                } finally {
                    shareLoading = false
                    selectedTab = 0
                    onSharedContentHandled()
                }
            }
        }
        ShareIntakeDialog(
            content = content,
            loading = shareLoading,
            onRecognize = {
                handleShare {
                    taskSection = "tasks"
                    aiResult = when (content) {
                        is SharedContent.Text -> repository.parseText(currentSession.token, content.text)
                        is SharedContent.Image -> repository.parseScreenshot(currentSession.token, content.uri)
                    }
                }
            },
            onSaveIdea = {
                val text = (content as? SharedContent.Text)?.text ?: return@ShareIntakeDialog
                handleShare {
                    val saved = repository.createIdea(currentSession.token, text, null, null, null, emptyList())
                    ideas = listOf(saved) + ideas.filterNot { it.id == saved.id }
                    taskSection = "inbox"
                }
            },
            onDismiss = onSharedContentHandled,
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

package com.todocloud.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.todocloud.app.MainActivity
import com.todocloud.app.R
import com.todocloud.app.data.TaskItem
import com.todocloud.app.data.TodoCloudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskCalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        TaskCalendarWidget.renderAll(context)
        refreshFromServer(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        TaskCalendarWidget.render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TaskCalendarWidget.ACTION_REFRESH) {
            TaskCalendarWidget.renderAll(context)
            refreshFromServer(context)
        }
    }

    private fun refreshFromServer(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = TodoCloudRepository(context.applicationContext)
                val session = repository.savedSession()
                if (session == null) {
                    TaskCalendarWidget.clear(context)
                } else {
                    runCatching { repository.listTasks(session.token) }
                        .onSuccess { TaskCalendarWidget.updateCachedTasks(context, it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object TaskCalendarWidget {
    const val ACTION_REFRESH = "com.todocloud.app.widget.REFRESH"

    private const val PREFERENCES_NAME = "calendar_widget"
    private const val KEY_TASKS = "tasks"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val DAYS_TO_SHOW = 7L
    private const val TASKS_TO_SHOW = 5
    private const val COMPACT_TASKS_TO_SHOW = 1
    private const val FULL_LAYOUT_MIN_HEIGHT_DP = 220

    private val dayContainerIds = intArrayOf(
        R.id.widget_day_container_0,
        R.id.widget_day_container_1,
        R.id.widget_day_container_2,
        R.id.widget_day_container_3,
        R.id.widget_day_container_4,
        R.id.widget_day_container_5,
        R.id.widget_day_container_6,
    )
    private val weekdayIds = intArrayOf(
        R.id.widget_weekday_0,
        R.id.widget_weekday_1,
        R.id.widget_weekday_2,
        R.id.widget_weekday_3,
        R.id.widget_weekday_4,
        R.id.widget_weekday_5,
        R.id.widget_weekday_6,
    )
    private val dayNumberIds = intArrayOf(
        R.id.widget_day_number_0,
        R.id.widget_day_number_1,
        R.id.widget_day_number_2,
        R.id.widget_day_number_3,
        R.id.widget_day_number_4,
        R.id.widget_day_number_5,
        R.id.widget_day_number_6,
    )
    private val dayCountIds = intArrayOf(
        R.id.widget_day_count_0,
        R.id.widget_day_count_1,
        R.id.widget_day_count_2,
        R.id.widget_day_count_3,
        R.id.widget_day_count_4,
        R.id.widget_day_count_5,
        R.id.widget_day_count_6,
    )
    private val taskRowIds = intArrayOf(
        R.id.widget_task_row_0,
        R.id.widget_task_row_1,
        R.id.widget_task_row_2,
        R.id.widget_task_row_3,
        R.id.widget_task_row_4,
    )
    private val taskTitleIds = intArrayOf(
        R.id.widget_task_title_0,
        R.id.widget_task_title_1,
        R.id.widget_task_title_2,
        R.id.widget_task_title_3,
        R.id.widget_task_title_4,
    )
    private val taskDueIds = intArrayOf(
        R.id.widget_task_due_0,
        R.id.widget_task_due_1,
        R.id.widget_task_due_2,
        R.id.widget_task_due_3,
        R.id.widget_task_due_4,
    )

    fun updateCachedTasks(context: Context, tasks: List<TaskItem>) {
        val payload = JSONArray().apply {
            tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("due_at", task.dueAt ?: JSONObject.NULL)
                    put("completed", task.completed)
                })
            }
        }
        preferences(context).edit()
            .putString(KEY_TASKS, payload.toString())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
        renderAll(context, tasks = tasks, loggedIn = true)
    }

    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
        renderAll(context, tasks = emptyList(), loggedIn = false)
    }

    fun renderAll(context: Context) {
        val repository = TodoCloudRepository(context.applicationContext)
        renderAll(
            context = context,
            tasks = loadCachedTasks(context),
            loggedIn = repository.savedSession() != null,
        )
    }

    fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val repository = TodoCloudRepository(context.applicationContext)
        manager.updateAppWidget(
            appWidgetId,
            buildViews(
                context = context,
                appWidgetId = appWidgetId,
                tasks = loadCachedTasks(context),
                loggedIn = repository.savedSession() != null,
                compact = isCompact(manager, appWidgetId),
            ),
        )
    }

    private fun renderAll(context: Context, tasks: List<TaskItem>, loggedIn: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TaskCalendarWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        ids.forEach { id ->
            manager.updateAppWidget(
                id,
                buildViews(
                    context = context,
                    appWidgetId = id,
                    tasks = tasks,
                    loggedIn = loggedIn,
                    compact = isCompact(manager, id),
                ),
            )
        }
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        tasks: List<TaskItem>,
        loggedIn: Boolean,
        compact: Boolean,
    ): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (compact) R.layout.widget_task_calendar_compact else R.layout.widget_task_calendar,
        )
        val today = LocalDate.now()
        val endDate = today.plusDays(DAYS_TO_SHOW - 1)
        val datedTasks = tasks.asSequence()
            .filterNot { it.completed }
            .mapNotNull { task -> parseDue(task.dueAt)?.let { due -> task to due } }
            .filter { (_, due) -> !due.toLocalDate().isAfter(endDate) }
            .sortedBy { (_, due) -> due.toInstant() }
            .toList()

        views.setTextViewText(
            R.id.widget_date_range,
            "${today.monthValue}月${today.dayOfMonth}日 — ${endDate.monthValue}月${endDate.dayOfMonth}日",
        )
        views.setOnClickPendingIntent(
            R.id.widget_root,
            openCalendarIntent(context, appWidgetId * 100, today),
        )
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TaskCalendarWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    flags = Intent.FLAG_RECEIVER_FOREGROUND
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        repeat(7) { index ->
            val date = today.plusDays(index.toLong())
            val count = datedTasks.count { (_, due) -> due.toLocalDate() == date }
            views.setTextViewText(weekdayIds[index], weekdayLabel(date))
            views.setTextViewText(dayNumberIds[index], date.dayOfMonth.toString())
            views.setTextViewText(dayCountIds[index], if (count == 0) "" else count.toString())
            views.setViewVisibility(dayCountIds[index], if (count == 0) View.INVISIBLE else View.VISIBLE)
            views.setInt(
                dayContainerIds[index],
                "setBackgroundResource",
                if (index == 0) R.drawable.widget_day_today else R.drawable.widget_day_normal,
            )
            views.setOnClickPendingIntent(
                dayContainerIds[index],
                openCalendarIntent(context, appWidgetId * 100 + index + 1, date),
            )
        }

        val visibleTasks = datedTasks.take(if (compact) COMPACT_TASKS_TO_SHOW else TASKS_TO_SHOW)
        taskRowIds.indices.forEach { index ->
            val entry = visibleTasks.getOrNull(index)
            if (entry == null) {
                views.setViewVisibility(taskRowIds[index], View.GONE)
            } else {
                val (task, due) = entry
                views.setViewVisibility(taskRowIds[index], View.VISIBLE)
                views.setTextViewText(taskTitleIds[index], task.title)
                views.setTextViewText(taskDueIds[index], formatDue(due, today))
                views.setOnClickPendingIntent(
                    taskRowIds[index],
                    openCalendarIntent(
                        context,
                        appWidgetId * 100 + 20 + index,
                        due.toLocalDate(),
                    ),
                )
            }
        }

        val emptyMessage = when {
            !loggedIn -> "打开 TodoCloud 登录后显示日程"
            datedTasks.isEmpty() -> "未来 7 天没有待办任务"
            else -> ""
        }
        views.setTextViewText(R.id.widget_empty, emptyMessage)
        views.setViewVisibility(
            R.id.widget_empty,
            if (emptyMessage.isEmpty()) View.GONE else View.VISIBLE,
        )
        return views
    }

    private fun isCompact(manager: AppWidgetManager, appWidgetId: Int): Boolean {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return height == 0 || height < FULL_LAYOUT_MIN_HEIGHT_DP
    }

    private fun openCalendarIntent(
        context: Context,
        requestCode: Int,
        date: LocalDate,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_INITIAL_TAB, 1)
            putExtra(MainActivity.EXTRA_CALENDAR_DATE, date.toString())
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun loadCachedTasks(context: Context): List<TaskItem> {
        val raw = preferences(context).getString(KEY_TASKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    add(
                        TaskItem(
                            id = item.getInt("id"),
                            title = item.getString("title"),
                            description = null,
                            dueAt = if (item.isNull("due_at")) null else item.getString("due_at"),
                            reminderOffsetMinutes = null,
                            completed = item.optBoolean("completed", false),
                            completedAt = null,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseDue(value: String?): java.time.ZonedDateTime? {
        if (value.isNullOrBlank()) return null
        val zone = ZoneId.systemDefault()
        val instant = runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant() }.getOrNull()
            ?: return null
        return instant.atZone(zone)
    }

    private fun weekdayLabel(date: LocalDate): String =
        listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]

    private fun formatDue(due: java.time.ZonedDateTime, today: LocalDate): String {
        val dateLabel = when (due.toLocalDate()) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> if (due.toLocalDate().isBefore(today)) "逾期" else "周${weekdayLabel(due.toLocalDate())}"
        }
        return "$dateLabel ${due.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

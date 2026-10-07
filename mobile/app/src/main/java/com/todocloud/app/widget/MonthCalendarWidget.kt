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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthCalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        MonthCalendarWidget.renderAll(context)
        refreshFromServer(context)
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

object MonthCalendarWidget {
    private val weekRowIds = intArrayOf(
        R.id.month_widget_week_0,
        R.id.month_widget_week_1,
        R.id.month_widget_week_2,
        R.id.month_widget_week_3,
        R.id.month_widget_week_4,
        R.id.month_widget_week_5,
    )

    fun renderAll(context: Context) {
        val repository = TodoCloudRepository(context.applicationContext)
        renderAll(
            context = context,
            tasks = TaskCalendarWidget.loadCachedTasks(context),
            loggedIn = repository.savedSession() != null,
        )
    }

    internal fun renderAll(context: Context, tasks: List<TaskItem>, loggedIn: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, MonthCalendarWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, tasks, loggedIn))
        }
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        tasks: List<TaskItem>,
        loggedIn: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_task_month)
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val tasksByDate = tasks.asSequence()
            .filterNot { it.completed }
            .mapNotNull { task ->
                parseDueDate(task.dueAt)?.let { date -> date to task }
            }
            .groupBy({ it.first }, { it.second })
        val monthTaskCount = tasksByDate
            .filterKeys { date -> YearMonth.from(date) == month }
            .values
            .sumOf { it.size }

        views.setTextViewText(R.id.month_widget_title, "${month.year}年${month.monthValue}月")
        views.setTextViewText(
            R.id.month_widget_total,
            when {
                !loggedIn -> "登录后显示"
                monthTaskCount == 0 -> "本月已清空"
                else -> "未完成 $monthTaskCount 项"
            },
        )
        views.setOnClickPendingIntent(
            R.id.month_widget_root,
            openCalendarIntent(context, 1_000_000 + appWidgetId * 100, today),
        )

        weekRowIds.forEach(views::removeAllViews)
        val firstDayOffset = month.atDay(1).dayOfWeek.value - 1
        repeat(42) { index ->
            val rowIndex = index / 7
            val dayNumber = index - firstDayOffset + 1
            val cell = RemoteViews(context.packageName, R.layout.widget_month_day_cell)
            if (dayNumber !in 1..month.lengthOfMonth()) {
                cell.setViewVisibility(R.id.month_widget_cell_root, View.INVISIBLE)
            } else {
                val date = month.atDay(dayNumber)
                val count = tasksByDate[date].orEmpty().size
                val isToday = date == today
                cell.setViewVisibility(R.id.month_widget_cell_root, View.VISIBLE)
                cell.setTextViewText(R.id.month_widget_day_number, dayNumber.toString())
                cell.setTextViewText(
                    R.id.month_widget_day_count,
                    if (count == 0) "" else count.toString(),
                )
                cell.setViewVisibility(
                    R.id.month_widget_day_count,
                    if (count == 0) View.INVISIBLE else View.VISIBLE,
                )
                cell.setInt(
                    R.id.month_widget_cell_root,
                    "setBackgroundResource",
                    when {
                        isToday -> R.drawable.widget_day_today
                        count > 0 -> R.drawable.widget_month_day_tasks
                        else -> R.drawable.widget_day_normal
                    },
                )
                cell.setInt(
                    R.id.month_widget_day_count,
                    "setBackgroundResource",
                    if (isToday) {
                        R.drawable.widget_count_today_background
                    } else {
                        R.drawable.widget_count_background
                    },
                )
                cell.setOnClickPendingIntent(
                    R.id.month_widget_cell_root,
                    openCalendarIntent(
                        context,
                        1_000_000 + appWidgetId * 100 + index + 1,
                        date,
                    ),
                )
            }
            views.addView(weekRowIds[rowIndex], cell)
        }
        return views
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

    private fun parseDueDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val zone = ZoneId.systemDefault()
        val instant = runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant() }.getOrNull()
            ?: return null
        return instant.atZone(zone).toLocalDate()
    }
}

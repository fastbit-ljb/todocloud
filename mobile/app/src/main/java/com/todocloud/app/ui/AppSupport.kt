package com.todocloud.app.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal data class TabItem(
    val label: String,
    val icon: @Composable () -> Unit,
)

internal val UiverseEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
internal const val TASK_REORDER_DELAY_MILLIS = 550
internal const val TRASH_ANIMATION_BEAT_MILLIS = 220L
internal const val CARD_FLY_AWAY_DURATION_MILLIS = 900
internal const val TASK_ENTRY_STAGGER_MILLIS = 100L
internal const val TASK_ENTRY_DURATION_MILLIS = 420
internal const val INPUT_LABEL_FLOAT_OFFSET_DP = 24f
internal const val INPUT_TEXT_BOTTOM_PADDING_DP = 10f
internal const val DEFAULT_AI_REMINDER_OFFSET_MINUTES = 10
internal const val REMINDER_PREFERENCES_NAME = "reminder_settings"
internal const val DEFAULT_REMINDER_KEY = "default_offset_minutes"

internal data class CardShatterRequest(
    val task: TaskItem,
    val cardLayer: GraphicsLayer,
    val boundsInRoot: Rect,
)

internal fun sortTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.sortedWith { left, right ->
    if (left.completed != right.completed) {
        return@sortedWith left.completed.compareTo(right.completed)
    }
    if (!left.completed) {
        return@sortedWith compareNullableStrings(left.dueAt, right.dueAt)
    }
    compareNullableStrings(right.completedAt, left.completedAt)
}

internal fun compareNullableStrings(left: String?, right: String?): Int {
    if (left == null && right == null) return 0
    if (left == null) return 1
    if (right == null) return -1
    return left.compareTo(right)
}

internal fun isVisibleWithinCompletedDays(task: TaskItem, days: Long): Boolean {
    if (!task.completed) return true
    val completedAt = task.completedAt?.let(::parseInstant) ?: return true
    return completedAt.isAfter(Instant.now().minus(days, ChronoUnit.DAYS))
}

// The state list is kept in its current order while the checkbox animation
// plays. The caller applies sortTasks after that animation has completed.
internal fun visibleHomeTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter {
    isVisibleWithinCompletedDays(it, 3)
}

internal fun visibleCalendarTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter {
    isVisibleWithinCompletedDays(it, 365)
}

internal fun formatReminderOffset(offsetMinutes: Int?): String = when {
    offsetMinutes == null -> "不提醒"
    offsetMinutes % (24 * 60) == 0 -> "提前 ${offsetMinutes / (24 * 60)} 天"
    offsetMinutes % 60 == 0 -> "提前 ${offsetMinutes / 60} 小时"
    else -> "提前 $offsetMinutes 分钟"
}

internal fun loadDefaultReminderOffset(context: Context): Int? {
    val storedValue = context
        .getSharedPreferences(REMINDER_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getInt(DEFAULT_REMINDER_KEY, DEFAULT_AI_REMINDER_OFFSET_MINUTES)
    return if (storedValue < 0) null else storedValue.coerceIn(0, 7 * 24 * 60)
}

internal fun saveDefaultReminderOffset(context: Context, offsetMinutes: Int?) {
    context
        .getSharedPreferences(REMINDER_PREFERENCES_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(DEFAULT_REMINDER_KEY, offsetMinutes ?: -1)
        .apply()
}

internal fun requestAlarmPermissions(context: Context) {
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
            return
        }
    }

    if (requiresOemAlarmDisplayGuide()) {
        android.app.AlertDialog.Builder(context)
            .setTitle("允许闹钟从后台显示")
            .setMessage(oemAlarmDisplayGuidance())
            .setNegativeButton("稍后", null)
            .setPositiveButton("打开设置") { _, _ -> openAppDetailsSettings(context) }
            .show()
    }
}

internal fun requestBackgroundRunPermission(context: Context) {
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

internal fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
    )
}

internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val packageUri = Uri.parse("package:${context.packageName}")
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
}

internal fun openAppDetailsSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

internal fun isChineseOem(): Boolean {
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

internal fun requiresOemAlarmDisplayGuide(): Boolean = isChineseOem()

internal fun oemAlarmDisplayGuidance(): String {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val instruction = when {
        manufacturer.contains("oppo") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("realme") ->
            "特殊应用权限 → 后台弹出界面"
        manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("blackshark") ->
            "应用权限管理 → 其他权限 → 后台弹出界面"
        manufacturer.contains("vivo") ->
            "应用与权限管理 → 权限管理 → 后台弹出界面（或悬浮窗）"
        else ->
            "应用权限设置中的后台弹出、后台启动界面或类似选项"
    }
    return "为了让闹钟在你使用其他应用时自动显示停止界面，请允许：$instruction。" +
        "不同系统名称可能略有差异；如果没有这个选项，只需保留标准的通知和全屏通知权限。"
}

internal fun oemBackgroundGuidance(): String {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "小米/红米：应用信息 → 自启动 → 允许；电池 → 无限制。"
        manufacturer.contains("huawei") ->
            "华为：应用启动 → TodoCloud → 关闭自动管理，并允许自启动、后台活动和关联启动。"
        manufacturer.contains("honor") ->
            "荣耀：应用启动管理 → TodoCloud → 允许自启动和后台活动；电池 → 不限制。"
        manufacturer.contains("oppo") ->
            "OPPO：特殊应用权限 → 后台弹出界面 → 允许；同时允许自启动，并将耗电管理设为允许后台运行/不限制。"
        manufacturer.contains("vivo") ->
            "vivo：应用与权限管理 → 自启动 → 允许；电量管理 → 不限制。"
        manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
            "一加/realme：特殊应用权限 → 后台弹出界面 → 允许；电池设为不限制，并允许自启动和后台活动。"
        manufacturer.contains("meizu") ->
            "魅族：应用管理 → 权限管理 → 自启动，并将电池策略设为不限制。"
        else ->
            "系统设置 → 应用 → TodoCloud → 电池/自启动，允许后台活动并选择不限制。"
    }
}

internal fun formatDueAt(value: String): String = runCatching {
    val local = OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
    // Show the year when it is not this year, so a date a year off is visible at a glance.
    val pattern = if (local.year == LocalDate.now().year) "M月d日 HH:mm" else "yyyy年M月d日 HH:mm"
    local.format(DateTimeFormatter.ofPattern(pattern))
}.getOrElse { value.replace("T", " ").removeSuffix("Z") }

internal fun parseDueDate(value: String): LocalDate? = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .toLocalDate()
}.getOrNull()

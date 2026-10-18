package com.todocloud.app.ui

import android.Manifest
import android.app.NotificationManager
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.todocloud.app.notification.ReminderScheduler
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

/**
 * Opens the first missing system permission needed by alarm mode.
 *
 * @return true when the standard Android permissions are already available.
 * Overlay access is optional for ringing; request it separately so Android
 * can also display the alarm page while another app is in the foreground.
 */
internal fun requestAlarmPermissions(context: Context): Boolean {
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (!areNotificationsAvailable(context)) {
        openNotificationSettings(context)
        return false
    }

    if (!ReminderScheduler.canScheduleExactAlarms(context)) {
        openExactAlarmSettings(context)
        return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (!notificationManager.canUseFullScreenIntent()) {
            openFullScreenAlarmSettings(context)
            return false
        }
    }

    if (!Settings.canDrawOverlays(context)) {
        // Optional for ringing. This is the standard Android exemption that
        // also permits the alarm page to appear over another foreground app.
        openAlarmBackgroundDisplaySettings(context)
    }
    return true
}

internal fun openAlarmBackgroundDisplaySettings(context: Context) {
    val permissionIntent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    if (startSettingsActivity(context, permissionIntent)) {
        // Android 11+ may display the app list. Its per-app activity can be
        // protected by INTERNAL_SYSTEM_WINDOW, which third-party apps cannot use.
        android.widget.Toast.makeText(
            context,
            "若显示应用列表，请选择 TodoCloud，再允许显示在其他应用上层。",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    } else {
        openAppDetailsSettings(context)
    }
}

internal fun areNotificationsAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return false
    return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
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

internal fun openFullScreenAlarmSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        startSettingsActivity(
            context,
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri),
        )
    ) return
    openOemAlarmDisplaySettings(context)
}

/**
 * ColorOS exposes background-popup access on a vendor-owned per-app permission
 * page. There is no public Android intent for that switch, so try the known
 * ColorOS package generations and keep the standard app-details page as a safe
 * fallback for other systems.
 */
internal fun openOemAlarmDisplaySettings(context: Context) {
    val packageName = context.packageName
    val packageUri = Uri.parse("package:$packageName")
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val candidates = buildList {
        if (manufacturer.contains("oppo") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("realme")
        ) {
            add(
                Intent("oppo.intent.action.APP_PERMISSION").apply {
                    data = packageUri
                    putExtra("packageName", packageName)
                    putExtra("pkgName", packageName)
                },
            )
            add(
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.securitypermission",
                        "com.coloros.securitypermission.permission.PermissionAppAllPermissionActivity",
                    )
                    data = packageUri
                    putExtra("packageName", packageName)
                    putExtra("pkgName", packageName)
                },
            )
            listOf("com.oplus.safecenter", "com.coloros.safecenter", "com.oppo.safe")
                .forEach { settingsPackage ->
                    add(
                        Intent().apply {
                            component = ComponentName(
                                settingsPackage,
                                "$settingsPackage.permission.PermissionAppAllPermissionActivity",
                            )
                            data = packageUri
                            putExtra("packageName", packageName)
                            putExtra("pkgName", packageName)
                        },
                    )
                }
        }
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
    candidates.firstOrNull { startSettingsActivity(context, it) }
}

internal fun startSettingsActivity(context: Context, intent: Intent): Boolean = runCatching {
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)

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
    return "若只响铃不弹页，请在「$instruction」中允许后台显示；没有此项可跳过。"
}

internal fun oemBackgroundGuidance(): String {
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "应用信息 → 自启动 → 允许。"
        manufacturer.contains("huawei") ->
            "应用启动 → TodoCloud → 手动管理，允许自启动和后台活动。"
        manufacturer.contains("honor") ->
            "应用启动管理 → TodoCloud → 允许自启动和后台活动。"
        manufacturer.contains("oppo") ->
            "应用详情 → 耗电管理：允许自启动和后台活动（如有）。"
        manufacturer.contains("vivo") ->
            "应用与权限 → 自启动 → 允许。"
        manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
            "应用详情 → 耗电管理：允许自启动和后台活动（如有）。"
        manufacturer.contains("meizu") ->
            "应用管理 → 权限管理 → 自启动 → 允许。"
        else ->
            "应用详情 → 自启动／后台活动 → 允许（如有）。"
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

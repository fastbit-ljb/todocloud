package com.todocloud.app.ui

import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.todocloud.app.BuildConfig
import com.todocloud.app.R
import com.todocloud.app.branding.MascotSet
import com.todocloud.app.branding.MascotState
import com.todocloud.app.data.Session
import com.todocloud.app.data.isLocal
import com.todocloud.app.notification.ReminderScheduler.ReminderMode

@Composable
internal fun SettingsHomeScreen(
    context: Context,
    paddingValues: PaddingValues,
    session: Session,
    reminderMode: ReminderMode,
    defaultReminderOffsetMinutes: Int?,
    onDefaultReminderChange: (Int?) -> Unit,
    onReminderModeChange: (ReminderMode) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSponsor: () -> Unit,
    mascot: MascotSet,
    mascotState: MascotState,
    onOpenMascot: () -> Unit,
    updateAvailableVersion: String?,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit,
    aiChoice: com.todocloud.app.data.AiChoice? = null,
    onEditAiKey: () -> Unit = {},
) {
    var showDefaultReminderPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsGroup(title = "账号") {
            val local = session.isLocal
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MascotAvatar(mascot, mascotState)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(if (local) "本机模式" else session.email, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (local) "数据只加密保存在这台手机上" else "已登录 · 任务会继续云端同步",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (local) {
                    androidx.compose.material3.TextButton(onClick = onLogout) { Text("登录账号") }
                } else {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = "退出登录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (aiChoice != null) {
            val local = session.isLocal
            SettingsGroup(
                title = "AI 模型",
                caption = if (local) "本机模式用你自己的密钥在手机上直接调用模型，任务内容不经过 TodoCloud 服务器"
                else "默认用 TodoCloud 服务器的 AI；也可以换成你自己的密钥，在手机上直接调用",
            ) {
                SettingsRow(
                    icon = androidx.compose.material.icons.Icons.Outlined.AutoAwesome,
                    title = if (local || aiChoice.ownWhenSignedIn) "${aiChoice.provider.label} API 密钥" else "AI 模型来源",
                    description = aiChoiceSummary(aiChoice, signedIn = !local),
                    onClick = onEditAiKey,
                    badge = local && !aiChoice.keySet,
                )
            }
        }

        SettingsGroup(title = "提醒", caption = "选择任务到点后的提醒方式") {
            SettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "通知栏提醒",
                description = "沿用原来的通知栏提醒，可点击通知进入应用",
                onClick = { onReminderModeChange(ReminderMode.NOTIFICATION) },
                trailing = {
                    RadioButton(
                        selected = reminderMode == ReminderMode.NOTIFICATION,
                        onClick = { onReminderModeChange(ReminderMode.NOTIFICATION) },
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Outlined.Alarm,
                title = "闹钟提醒",
                description = "到点响铃并显示停止界面，不发送任务通知",
                onClick = {
                    onReminderModeChange(ReminderMode.ALARM)
                    requestAlarmPermissions(context)
                },
                trailing = {
                    RadioButton(
                        selected = reminderMode == ReminderMode.ALARM,
                        onClick = {
                            onReminderModeChange(ReminderMode.ALARM)
                            requestAlarmPermissions(context)
                        },
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Outlined.AccessTime,
                title = "AI 识别任务默认提醒",
                description = formatReminderOffset(defaultReminderOffsetMinutes),
                onClick = { showDefaultReminderPicker = true },
            )
            SettingsDivider()
            var digestEnabled by remember(context) {
                mutableStateOf(com.todocloud.app.notification.DailyDigestNotifier.isEnabled(context))
            }
            fun setDigest(enabled: Boolean) {
                digestEnabled = enabled
                com.todocloud.app.notification.DailyDigestNotifier.setEnabled(context, enabled)
            }
            SettingsRow(
                icon = androidx.compose.material.icons.Icons.Outlined.WbSunny,
                title = "每日早晨摘要",
                description = "每天 8 点通知今天要做的事和过期任务；没有安排的日子不打扰",
                onClick = { setDigest(!digestEnabled) },
                trailing = {
                    androidx.compose.material3.Switch(
                        checked = digestEnabled,
                        onCheckedChange = ::setDigest,
                    )
                },
            )
        }

        SettingsGroup(title = "更多") {
            SettingsRow(
                icon = Icons.Outlined.Pets,
                title = "吉祥物",
                description = "${mascot.label} · 桌面图标跟着任务状态换表情",
                onClick = onOpenMascot,
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Outlined.Settings,
                title = "权限",
                description = "通知、闹钟、后台运行和无障碍辅助",
                onClick = onOpenPermissions,
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Outlined.FavoriteBorder,
                title = "赞助开发",
                description = "查看收款码，支持 TodoCloud 持续开发",
                onClick = onOpenSponsor,
            )
        }

        SettingsGroup(title = "关于 TodoCloud") {
            SettingsRow(
                icon = Icons.Outlined.SystemUpdate,
                title = if (updateAvailableVersion != null) "检查更新 · 新版本 $updateAvailableVersion" else "检查更新",
                description = when {
                    checkingUpdate -> "正在检查…"
                    updateAvailableVersion != null -> "点击查看更新内容并下载"
                    else -> "当前版本 ${BuildConfig.VERSION_NAME}"
                },
                badge = updateAvailableVersion != null,
                onClick = { if (!checkingUpdate) onCheckUpdate() },
            )
        }
        Text(
            "TodoCloud ${BuildConfig.VERSION_NAME} · 任务、提醒与云端同步",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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

/** A titled block of settings rows on one rounded surface. */
@Composable
internal fun SettingsGroup(
    title: String,
    caption: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 4.dp, bottom = if (caption == null) 8.dp else 2.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        caption?.let {
            Text(
                it,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

/** One settings row: tinted icon badge, title + description, optional trailing control. */
@Composable
internal fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    badge: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgedBox(badge = { if (badge) Badge() }) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "打开$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
internal fun SettingsEntry(
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
internal fun PermissionSettingsScreen(
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
internal fun PermissionStateRow(
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
internal fun SponsorSettingsScreen(paddingValues: PaddingValues) {
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
internal fun SettingsScreen(
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
internal fun SettingsSection(
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
internal fun ReminderModeCard(
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

package com.todocloud.app.ui

import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
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
import com.todocloud.app.notification.ReminderScheduler

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
    var showAlarmSoundPicker by rememberSaveable { mutableStateOf(false) }
    var alarmSoundTitle by remember(context) {
        mutableStateOf(ReminderScheduler.alarmSoundTitle(context))
    }
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val pickedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        ReminderScheduler.saveAlarmSoundUri(context, pickedUri)
        alarmSoundTitle = ReminderScheduler.alarmSoundTitle(context)
    }
    val audioFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onSuccess {
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "自选音频"
            ReminderScheduler.saveAlarmSoundUri(context, uri, displayName)
            alarmSoundTitle = ReminderScheduler.alarmSoundTitle(context)
        }.onFailure {
            android.widget.Toast.makeText(context, "无法保存这个音频的读取权限，请换一个文件", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) alarmSoundTitle = ReminderScheduler.alarmSoundTitle(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

        SettingsGroup(title = "提醒方式", caption = "选择任务到点后如何提醒") {
            Column(Modifier.selectableGroup()) {
                ReminderModeOption(
                    icon = Icons.Outlined.Notifications,
                    title = "通知栏提醒",
                    description = "发送通知，点击后查看任务",
                    selected = reminderMode == ReminderMode.NOTIFICATION,
                    onClick = { onReminderModeChange(ReminderMode.NOTIFICATION) },
                )
                SettingsDivider()
                ReminderModeOption(
                    icon = Icons.Outlined.Alarm,
                    title = "闹钟提醒",
                    description = "持续响铃，可延后 5 分钟",
                    selected = reminderMode == ReminderMode.ALARM,
                    onClick = {
                        if (requestAlarmPermissions(context)) {
                            onReminderModeChange(ReminderMode.ALARM)
                        }
                    },
                )
            }
        }
        SettingsGroup(title = "闹钟铃声", caption = "仅在闹钟提醒模式下生效") {
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = alarmSoundTitle,
                description = "选择系统铃声或本地音频",
                onClick = { showAlarmSoundPicker = true },
            )
        }
        SettingsGroup(title = "日常提醒") {
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
                description = "通知、闹钟和后台运行",
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

    if (showAlarmSoundPicker) {
        AlarmSoundPickerSheet(
            currentTitle = alarmSoundTitle,
            onDismiss = { showAlarmSoundPicker = false },
            onSystemSound = {
                showAlarmSoundPicker = false
                val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ReminderScheduler.selectedAlarmSoundUri(context))
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                }
                runCatching { ringtonePicker.launch(pickerIntent) }.onFailure {
                    android.widget.Toast.makeText(context, "无法打开系统铃声，请选择本地音频", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onAudioFile = {
                showAlarmSoundPicker = false
                runCatching { audioFilePicker.launch(arrayOf("audio/*")) }.onFailure {
                    android.widget.Toast.makeText(context, "无法打开音频文件选择器", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDefaultSound = {
                ReminderScheduler.saveAlarmSoundUri(context, null)
                alarmSoundTitle = ReminderScheduler.alarmSoundTitle(context)
                showAlarmSoundPicker = false
            },
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
private fun ReminderModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.small,
            color = if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSoundPickerSheet(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSystemSound: () -> Unit,
    onAudioFile: () -> Unit,
    onDefaultSound: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Text("闹钟铃声", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
            Text(
                "当前：$currentTitle",
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsRow(Icons.Outlined.Alarm, "系统铃声", "从手机的铃声库选择", onSystemSound)
            SettingsRow(Icons.Outlined.AudioFile, "本地音频", "选择一个音频文件作为铃声", onAudioFile)
            SettingsRow(Icons.Outlined.Restore, "恢复系统默认", "跟随手机的默认闹钟铃声", onDefaultSound)
            Text(
                "所选音频失效时，会使用系统默认铃声。",
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        areNotificationsAvailable(context)
    }
    val exactAlarmsEnabled = remember(permissionRefreshToken) {
        com.todocloud.app.notification.ReminderScheduler.canScheduleExactAlarms(context)
    }
    val fullScreenIntentEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
    }
    val alarmBackgroundDisplayEnabled = remember(permissionRefreshToken) {
        Settings.canDrawOverlays(context)
    }
    val chineseOem = remember { isChineseOem() }
    val needsOemAlarmDisplayGuide = remember { requiresOemAlarmDisplayGuide() }
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
            "只需开启你使用的提醒功能所需设置。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        PermissionStateRow("通知权限", notificationsEnabled, "关闭后无法显示通知栏提醒")
        if (!notificationsEnabled) {
            OutlinedButton(
                onClick = { openNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开通知设置") }
        }
        PermissionStateRow("精确闹钟", exactAlarmsEnabled, "准时触发提醒；未开可能延迟或漏提醒")
        if (!exactAlarmsEnabled) {
            OutlinedButton(
                onClick = { openExactAlarmSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开启精确闹钟权限") }
        }
        if (reminderMode == ReminderMode.ALARM) {
            AlarmBackgroundDisplayPermission(context, alarmBackgroundDisplayEnabled)
            PermissionStateRow("全屏闹钟", fullScreenIntentEnabled, "锁屏时显示闹钟页；未开可能只显示通知")
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
            if (needsOemAlarmDisplayGuide) {
                Text(
                    oemAlarmDisplayGuidance(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { openOemAlarmDisplaySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("打开后台弹出界面设置") }
            }
        }

        Text("后台运行", style = MaterialTheme.typography.titleMedium)
        BackgroundBatterySettings(context)
        if (chineseOem) {
            TextButton(
                onClick = { showBackgroundLockGuide = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("仍收不到提醒？查看自启动和后台锁定") }
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
                    Text(if (notificationsEnabled) "通知已开启：用于通知栏提醒" else "通知未开启：无法显示通知栏提醒")
                    BackgroundBatterySettings(context)
                    Text(if (exactAlarmsEnabled) "精确闹钟已开启：用于准时提醒" else "精确闹钟未开启：可能延迟或漏提醒")
                    if (chineseOem) {
                        Text("若被清理后漏提醒，请允许自启动和后台活动。")
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
                    Text("若被清理后漏提醒，请允许自启动和后台活动。")
                    Text(oemBackgroundGuidance())
                    Text("若仍被清理，请在最近任务中打开 TodoCloud 卡片菜单并锁定；部分系统需下拉卡片。")
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
private fun AlarmBackgroundDisplayPermission(context: Context, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionStateRow(
            "后台显示闹钟页面",
            enabled,
            "使用其他应用时弹出闹钟页；未开可能只响铃不弹页",
        )
        OutlinedButton(
            onClick = { openAlarmBackgroundDisplaySettings(context) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (enabled) "管理后台显示权限" else "开启后台显示权限（悬浮窗）")
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
        areNotificationsAvailable(context)
    }
    val exactAlarmsEnabled = remember(permissionRefreshToken) {
        com.todocloud.app.notification.ReminderScheduler.canScheduleExactAlarms(context)
    }
    val fullScreenIntentEnabled = remember(permissionRefreshToken) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
    }
    val alarmBackgroundDisplayEnabled = remember(permissionRefreshToken) {
        Settings.canDrawOverlays(context)
    }
    val chineseOem = remember { isChineseOem() }
    val needsOemAlarmDisplayGuide = remember { requiresOemAlarmDisplayGuide() }
    val needsReminderPermissionGuide = !notificationsEnabled ||
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
                description = "到点响铃；允许后台显示后自动打开停止界面",
                icon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                selected = reminderMode == ReminderMode.ALARM,
                onClick = {
                    if (requestAlarmPermissions(context)) {
                        onReminderModeChange(ReminderMode.ALARM)
                    }
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
                        if (!exactAlarmsEnabled) {
                            Text("• 精确闹钟：未开启", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (chineseOem) {
                            Text("• 后台耗电管理：需手动确认", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
            Text(
                if (notificationsEnabled) "通知已开启：用于通知栏提醒" else "通知未开启：无法显示通知栏提醒",
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
                if (exactAlarmsEnabled) "精确闹钟已开启：用于准时提醒" else "精确闹钟未开启：可能延迟或漏提醒",
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
            if (reminderMode == ReminderMode.ALARM) {
                AlarmBackgroundDisplayPermission(context, alarmBackgroundDisplayEnabled)
            }
            if (reminderMode == ReminderMode.ALARM && !fullScreenIntentEnabled) {
                Text("全屏闹钟未开启：锁屏时可能只显示通知，不弹出闹钟页。", color = MaterialTheme.colorScheme.error)
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
            if (reminderMode == ReminderMode.ALARM && needsOemAlarmDisplayGuide) {
                Text(
                    oemAlarmDisplayGuidance(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { openOemAlarmDisplaySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开后台弹出界面设置")
                }
            }
            OutlinedButton(
                onClick = { showReminderPermissionGuide = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看完整权限指引")
            }
            BackgroundBatterySettings(context)
            if (chineseOem) {
                TextButton(
                    onClick = { showBackgroundLockGuide = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("仍收不到提醒？查看自启动和后台锁定") }
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
                        if (notificationsEnabled) "通知已开启：用于通知栏提醒" else "通知未开启：无法显示通知栏提醒",
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
                    BackgroundBatterySettings(context)
                    Text(
                        if (exactAlarmsEnabled) "精确闹钟已开启：用于准时提醒" else "精确闹钟未开启：可能延迟或漏提醒",
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
                        Text("若被清理后漏提醒，请允许自启动和后台活动。")
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
                    Text("若被清理后漏提醒，请允许自启动和后台活动。")
                    Text(oemBackgroundGuidance())
                    Text("若仍被清理，请在最近任务中打开 TodoCloud 卡片菜单并锁定；部分系统需下拉卡片。")
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

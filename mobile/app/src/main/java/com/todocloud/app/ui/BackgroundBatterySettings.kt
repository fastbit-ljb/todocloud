package com.todocloud.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

internal enum class BackgroundBatteryDestination {
    APP_BATTERY,
    APP_DETAILS,
    OPTIMIZATION_LIST,
    UNAVAILABLE,
}

internal fun launchBackgroundBatterySettings(
    launch: (BackgroundBatteryDestination) -> Boolean,
): BackgroundBatteryDestination = listOf(
    BackgroundBatteryDestination.APP_BATTERY,
    BackgroundBatteryDestination.APP_DETAILS,
    BackgroundBatteryDestination.OPTIMIZATION_LIST,
).firstOrNull { destination -> runCatching { launch(destination) }.getOrDefault(false) }
    ?: BackgroundBatteryDestination.UNAVAILABLE

internal data class BackgroundBatteryGuide(val choice: String, val path: String)

internal fun backgroundBatteryGuide(manufacturer: String): BackgroundBatteryGuide {
    val maker = manufacturer.lowercase(Locale.ROOT)
    return when {
        listOf("oppo", "oneplus", "realme").any(maker::contains) -> BackgroundBatteryGuide(
            "请选择「完全允许后台行为」或「无限制」。",
            "应用详情 → 耗电管理（或电池）→ 完全允许后台行为／不限制／无限制。",
        )
        listOf("xiaomi", "redmi", "poco", "blackshark").any(maker::contains) -> BackgroundBatteryGuide(
            "将省电策略设为「无限制」。",
            "应用详情 → 省电策略（或电池）→ 无限制。",
        )
        listOf("huawei", "honor").any(maker::contains) -> BackgroundBatteryGuide(
            "将应用启动设为手动管理，允许后台活动和自启动。",
            "应用详情 → 应用启动管理 → 关闭自动管理，允许后台活动和自启动。",
        )
        else -> BackgroundBatteryGuide(
            "请选择「不限制」或「允许后台活动」。",
            "应用详情 → 耗电管理／电池／电池用量 → 不限制／无限制／允许后台活动。",
        )
    }
}

private fun openBackgroundBatterySettings(context: Context): BackgroundBatteryDestination {
    val packageUri = Uri.parse("package:${context.packageName}")
    return launchBackgroundBatterySettings { destination ->
        val intent = when (destination) {
            // AOSP's per-app battery action is not part of the public SDK.
            // OEMs may omit or protect it: only try it, never assume support.
            BackgroundBatteryDestination.APP_BATTERY -> Intent(
                "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL",
                packageUri,
            ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            BackgroundBatteryDestination.APP_DETAILS ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            BackgroundBatteryDestination.OPTIMIZATION_LIST ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            BackgroundBatteryDestination.UNAVAILABLE -> return@launchBackgroundBatterySettings false
        }
        startSettingsActivity(context, intent)
    }
}

@Composable
internal fun BackgroundBatterySettings(context: Context, systemOptimizationExempt: Boolean) {
    val guide = backgroundBatteryGuide(Build.MANUFACTURER)
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("后台耗电管理", style = MaterialTheme.typography.titleMedium)
            Text(
                "后台受限时，提醒可能延迟或收不到。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guide.choice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Always available: Android's Doze exemption does not tell us
            // whether the OEM's separate background policy is unrestricted.
            OutlinedButton(
                onClick = {
                    val destination = openBackgroundBatterySettings(context)
                    val hint = when (destination) {
                        BackgroundBatteryDestination.APP_BATTERY -> guide.choice
                        BackgroundBatteryDestination.APP_DETAILS -> guide.path
                        BackgroundBatteryDestination.OPTIMIZATION_LIST -> "选择 TodoCloud，允许不优化；厂商耗电设置仍需确认。"
                        BackgroundBatteryDestination.UNAVAILABLE -> null
                    }
                    if (hint == null) {
                        showHelp = true
                    } else {
                        Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("打开后台耗电设置") }
            TextButton(
                onClick = { showHelp = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("找不到设置？") }
            if (!systemOptimizationExempt) {
                TextButton(
                    onClick = { requestBackgroundRunPermission(context) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("放行系统省电优化") }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("设置后台不受限") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(guide.path)
                    Text(
                        "系统省电白名单不等于厂商后台已放行，此项需要你手动确认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    )
                    if (startSettingsActivity(context, intent)) {
                        showHelp = false
                        Toast.makeText(context, guide.path, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "请长按 TodoCloud 图标 → 应用信息，再按上方路径设置。", Toast.LENGTH_LONG).show()
                    }
                }) { Text("打开应用详情") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            },
        )
    }
}

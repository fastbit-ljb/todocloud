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

internal data class BackgroundBatteryGuide(
    val choice: String,
    val path: String,
    val note: String = "需在手机设置中手动确认，软件无法读取是否已放行。",
)

internal fun backgroundBatteryGuide(manufacturer: String): BackgroundBatteryGuide {
    val maker = manufacturer.lowercase(Locale.ROOT)
    return when {
        listOf("oppo", "oneplus", "realme").any(maker::contains) -> BackgroundBatteryGuide(
            "请在「耗电管理」中选择「完全允许后台行为」。",
            "长按 TodoCloud 图标 → 应用信息 → 耗电管理 → 完全允许后台行为。",
            "只开「允许后台使用」仍可能被智能优化限制；请以「耗电管理」为准。",
        )
        listOf("xiaomi", "redmi", "poco", "blackshark").any(maker::contains) -> BackgroundBatteryGuide(
            "将省电策略设为「无限制」。",
            "长按 TodoCloud 图标 → 应用信息 → 省电策略（或电池）→ 无限制。",
        )
        listOf("huawei", "honor").any(maker::contains) -> BackgroundBatteryGuide(
            "将应用启动设为手动管理，允许后台活动和自启动。",
            "长按 TodoCloud 图标 → 应用信息 → 应用启动管理 → 关闭自动管理，允许后台活动和自启动。",
        )
        else -> BackgroundBatteryGuide(
            "请选择「不限制」或「允许后台活动」。",
            "长按 TodoCloud 图标 → 应用信息 → 耗电管理／电池 → 不限制／无限制／允许后台活动。",
        )
    }
}

@Composable
internal fun BackgroundBatterySettings(context: Context) {
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
            // Neither opening a system page nor Android's Doze allowlist
            // proves that the separate OEM background policy is unrestricted.
            // Do not expose a switch or save a synthetic "enabled" state.
            Text(
                "需手动确认，软件无法检测此项",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "后台受限时，提醒可能延迟或收不到。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guide.choice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { showHelp = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("查看手动设置方法") }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("手动设置后台耗电") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(guide.path)
                    Text(
                        guide.note,
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
                        Toast.makeText(context, guide.choice, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "请长按 TodoCloud 图标 → 应用信息，再按上方路径设置。", Toast.LENGTH_LONG).show()
                    }
                }) { Text("打开应用信息") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false }) { Text("知道了") }
            },
        )
    }
}

package com.todocloud.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.todocloud.app.update.AppVersionInfo
import com.todocloud.app.update.UpdateChecker
import com.todocloud.app.update.UpdateNotifier
import com.todocloud.app.update.UpdatePolicy
import com.todocloud.app.update.UpdateStore

/** "发现新版本" dialog. A forced update has no "跳过此版本". */
@Composable
internal fun UpdateDialog(
    info: AppVersionInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val forced = UpdatePolicy.isForced(info, UpdateChecker.currentVersion)
    val notes = UpdatePolicy.summarizeNotes(info.releaseNotes)
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${info.latestVersion}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    buildString {
                        append("当前版本 ${UpdateChecker.currentVersion}。")
                        if (forced) append("这个版本已不再支持，请更新后继续使用。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (notes.isNotBlank()) {
                    Text(
                        notes,
                        modifier = Modifier.heightIn(min = 0.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("去下载") } },
        dismissButton = {
            Column {
                TextButton(onClick = onLater) { Text("稍后") }
                if (!forced) TextButton(onClick = onSkip) { Text("跳过此版本") }
            }
        },
    )
}

internal fun openUpdateDownload(context: Context, info: AppVersionInfo) {
    runCatching { context.startActivity(UpdateNotifier.openUrlIntent(info.openUrl)) }
}

/** Records the outcome so the dialog is not shown again today (or ever, for a skipped version). */
internal fun rememberUpdateDecision(context: Context, info: AppVersionInfo, skip: Boolean) {
    val store = UpdateStore(context)
    store.lastPromptDay = UpdateStore.today()
    if (skip) store.skippedVersion = info.latestVersion
}

package com.todocloud.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.todocloud.app.update.AppVersionInfo
import com.todocloud.app.update.ReleaseNoteBlock
import com.todocloud.app.update.UpdateChecker
import com.todocloud.app.update.UpdateNotifier
import com.todocloud.app.update.UpdatePolicy
import com.todocloud.app.update.UpdateStore

/**
 * "发现新版本" dialog. Behaviour is unchanged from the plain AlertDialog it replaces:
 * 去下载 / 稍后 / 跳过此版本, tapping outside counts as 稍后, and a forced update has
 * no "跳过此版本".
 */
@Composable
internal fun UpdateDialog(
    info: AppVersionInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val current = UpdateChecker.currentVersion
    val forced = UpdatePolicy.isForced(info, current)
    val blocks = remember(info.releaseNotes) { UpdatePolicy.releaseNoteBlocks(info.releaseNotes) }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onLater) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                // Header: soft brand-green wash, icon badge, title and version change.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.primaryContainer, colors.primaryContainer.copy(alpha = 0f)),
                            ),
                        )
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).background(colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text(
                        "发现新版本",
                        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VersionPill("当前 $current", emphasized = false)
                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = "升级到",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                        )
                        VersionPill("新版 ${info.latestVersion}", emphasized = true)
                    }
                }

                if (forced) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .background(colors.errorContainer, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "当前版本已不再支持，请更新后继续使用。",
                            modifier = Modifier.padding(start = 10.dp),
                            color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (blocks.isNotEmpty()) {
                    Text(
                        "更新内容",
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blocks.forEachIndexed { index, block -> ReleaseNoteRow(block, first = index == 0) }
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                ) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("去下载", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = if (forced) Arrangement.Center else Arrangement.SpaceBetween,
                    ) {
                        if (!forced) {
                            TextButton(onClick = onSkip) {
                                Text("跳过此版本", color = colors.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = onLater) { Text("稍后") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionPill(text: String, emphasized: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        modifier = Modifier
            .background(
                if (emphasized) colors.primary else colors.surfaceVariant,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (emphasized) colors.onPrimary else colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun ReleaseNoteRow(block: ReleaseNoteBlock, first: Boolean) {
    val colors = MaterialTheme.colorScheme
    when (block) {
        is ReleaseNoteBlock.Heading -> Text(
            block.text,
            modifier = Modifier.padding(top = if (first) 0.dp else 6.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )
        is ReleaseNoteBlock.Bullet -> Row {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, end = 10.dp)
                    .size(6.dp)
                    .background(colors.primary.copy(alpha = 0.7f), CircleShape),
            )
            Text(
                buildAnnotatedString {
                    block.lead?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.onSurface)) {
                            append(it)
                        }
                        if (block.text.isNotEmpty()) append("：")
                    }
                    append(block.text)
                },
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp,
                color = colors.onSurfaceVariant,
            )
        }
        is ReleaseNoteBlock.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 21.sp,
            color = colors.onSurfaceVariant,
        )
    }
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

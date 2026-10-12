package com.todocloud.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Something another app sent to TodoCloud through the system share sheet. */
sealed interface SharedContent {
    data class Text(val text: String) : SharedContent
    data class Image(val uri: Uri) : SharedContent
}

/** Server limit for an idea's content; also plenty for the AI text parser. */
internal const val SHARED_TEXT_MAX_CHARS = 10_000

/**
 * Joins the optional subject (e.g. a shared link's page title) with the body,
 * dropping the subject when the body already contains it, and caps the length.
 * Returns null when nothing usable was shared.
 */
internal fun sharedText(subject: String?, body: String?): String? {
    val cleanBody = body?.trim().orEmpty()
    val cleanSubject = subject?.trim().orEmpty()
    val combined = when {
        cleanSubject.isEmpty() -> cleanBody
        cleanBody.isEmpty() -> cleanSubject
        cleanSubject in cleanBody -> cleanBody
        else -> "$cleanSubject\n$cleanBody"
    }
    return combined.take(SHARED_TEXT_MAX_CHARS).trim().ifEmpty { null }
}

/** Reads an ACTION_SEND intent; anything else (or an empty share) gives null. */
internal fun sharedContentFrom(intent: Intent?): SharedContent? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val type = intent.type.orEmpty()
    if (type.startsWith("image/")) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        return uri?.let { SharedContent.Image(it) }
    }
    val text = sharedText(
        intent.getStringExtra(Intent.EXTRA_SUBJECT),
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
    )
    return text?.let { SharedContent.Text(it) }
}

@Composable
internal fun ShareIntakeDialog(
    content: SharedContent,
    loading: Boolean,
    onRecognize: () -> Unit,
    onSaveIdea: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(if (content is SharedContent.Image) "收到一张图片" else "收到一段文字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (content) {
                    is SharedContent.Text -> Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            content.text,
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is SharedContent.Image -> Text(
                        "让 AI 从图片里找出要做的事，确认后再加入任务。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onRecognize,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("识别成任务") }
                if (content is SharedContent.Text) {
                    FilledTonalButton(
                        onClick = onSaveIdea,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("存进收件匣") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
        },
    )
}

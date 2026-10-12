package com.todocloud.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.LocalAiConfig

/** Light check before a request is made; the real check is a test call to the model. */
internal fun aiKeyFormatError(key: String): String? {
    val clean = key.trim()
    return when {
        clean.isEmpty() -> "请填写 API 密钥"
        clean.any { it.isWhitespace() } -> "密钥里不能有空格或换行"
        clean.length < 20 -> "密钥太短，请确认复制完整"
        else -> null
    }
}

/**
 * Enter, replace or remove the Gemini key for local mode. Only the key is asked for;
 * the endpoint and model are fixed in [LocalAiConfig].
 */
@Composable
internal fun AiKeyDialog(
    keySet: Boolean,
    checking: Boolean,
    error: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    val formatError = if (key.isEmpty()) null else aiKeyFormatError(key)
    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Gemini API 密钥") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "密钥加密保存在这台手机上，只发给 Google 的 Gemini 接口。使用模型 ${LocalAiConfig.MODEL}，" +
                        "忙时自动换用 ${LocalAiConfig.FALLBACK_MODEL}。手机需要能访问 Google。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !checking,
                    label = { Text(if (keySet) "新的密钥（替换已保存的）" else "API 密钥") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = formatError != null || error != null,
                    supportingText = (formatError ?: error)?.let { message -> { Text(message) } },
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(LocalAiConfig.KEY_HELP_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("没有密钥？去 Google AI Studio 免费申请") }
                if (keySet) {
                    TextButton(onClick = onClear, enabled = !checking) {
                        Text("删除已保存的密钥", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key) },
                enabled = !checking && key.isNotEmpty() && aiKeyFormatError(key) == null,
            ) { Text(if (checking) "验证中…" else "验证并保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("取消") } },
    )
}

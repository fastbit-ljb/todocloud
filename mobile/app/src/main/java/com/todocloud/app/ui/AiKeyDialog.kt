package com.todocloud.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.todocloud.app.data.AiChoice
import com.todocloud.app.data.AiProvider
import com.todocloud.app.data.AiProviders

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

/** What the user asked to save. [key] null = keep the key already saved for that service. */
internal data class AiSaveRequest(
    val provider: AiProvider,
    val key: String?,
    val model: String,
    val ownWhenSignedIn: Boolean,
)

/** Whether saving [request] needs a test call first (something new to verify). */
internal fun aiSaveNeedsCheck(request: AiSaveRequest, signedIn: Boolean): Boolean =
    !signedIn || request.ownWhenSignedIn

/** Settings summary line for the AI row. */
internal fun aiChoiceSummary(choice: AiChoice, signedIn: Boolean): String = when {
    signedIn && !choice.ownWhenSignedIn -> "使用 TodoCloud 服务器的 AI"
    !choice.keySet -> "未填写密钥，AI 计划和识别任务需要它"
    else -> "${choice.provider.label} · ${choice.model}" + if (signedIn) " · 在手机上直接调用" else ""
}

/**
 * Pick a model service and enter its key. Endpoint and model are prefilled per service;
 * the user normally only pastes the key. Signed-in users can also keep the server's AI.
 */
@Composable
internal fun AiKeyDialog(
    current: AiChoice,
    signedIn: Boolean,
    savedKeyFor: (AiProvider) -> Boolean,
    savedModelFor: (AiProvider) -> String,
    checking: Boolean,
    error: String?,
    onSave: (AiSaveRequest) -> Unit,
    onClear: (AiProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var useOwn by remember { mutableStateOf(!signedIn || current.ownWhenSignedIn) }
    var provider by remember { mutableStateOf(current.provider) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(savedModelFor(current.provider)) }
    var showAdvanced by remember { mutableStateOf(false) }
    val hasSavedKey = savedKeyFor(provider)
    val formatError = if (key.isEmpty()) null else aiKeyFormatError(key)
    val canSave = !checking && when {
        !useOwn -> true
        key.isNotEmpty() -> formatError == null && model.isNotBlank()
        else -> hasSavedKey && model.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("AI 模型") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("用我自己的密钥", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (useOwn) "AI 在手机上直接调用你选的服务，不经过也不消耗 TodoCloud 服务器"
                                else "当前使用 TodoCloud 服务器的 AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = useOwn, onCheckedChange = { useOwn = it }, enabled = !checking)
                    }
                }
                if (useOwn) {
                    Text("选择服务", style = MaterialTheme.typography.labelLarge)
                    AiProviders.all.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == provider,
                                    enabled = !checking,
                                    role = Role.RadioButton,
                                    onClick = {
                                        provider = option
                                        key = ""
                                        model = savedModelFor(option)
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == provider, onClick = null, enabled = !checking)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(AiProviders.describe(option), style = MaterialTheme.typography.bodyMedium)
                                if (savedKeyFor(option)) {
                                    Text(
                                        "已保存密钥",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !checking,
                        label = { Text(if (hasSavedKey) "新的密钥（留空沿用已保存的）" else "${provider.label} API 密钥") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = formatError != null || error != null,
                        supportingText = (formatError ?: error)?.let { message -> { Text(message) } },
                    )
                    Text(
                        "密钥加密保存在这台手机上，只发给 ${provider.label}（${provider.host}）。" +
                            if (provider.supportsImages) "" else "这个服务不能识别图片，截图识别和带图计划需要换别的服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyHelpUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("没有密钥？去${provider.keyHelpLabel}申请") }
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(if (showAdvanced) "收起高级设置" else "高级：模型 ${model.ifBlank { provider.model }}")
                    }
                    if (showAdvanced) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !checking,
                            label = { Text("模型名") },
                            supportingText = { Text("默认 ${provider.model}。服务商更新模型后可以在这里改") },
                        )
                        if (model != provider.model) {
                            TextButton(onClick = { model = provider.model }) { Text("恢复默认模型") }
                        }
                    }
                    if (hasSavedKey) {
                        TextButton(onClick = { onClear(provider) }, enabled = !checking) {
                            Text("删除 ${provider.label} 的密钥", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(AiSaveRequest(provider, key.ifEmpty { null }, model, useOwn)) },
                enabled = canSave,
            ) { Text(if (checking) "验证中…" else if (useOwn) "验证并保存" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("取消") } },
    )
}

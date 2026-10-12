package com.todocloud.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn

@Composable
internal fun LoginScreen(
    loading: Boolean,
    error: String?,
    onSubmit: (String, String, Boolean) -> Unit,
    onUseLocally: () -> Unit = {},
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            TodoCloudBrandMark()
            Spacer(Modifier.height(14.dp))
            Text(
                "TodoCloud",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "把想法变成下一步行动",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (registerMode) "创建账号" else "欢迎回来",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (registerMode) "创建账号，开始管理你的任务"
                        else "登录后在云端同步你的任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    FloatingUnderlineTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "邮箱",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    FloatingUnderlineTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "密码",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Text(
                        "密码至少 8 位",
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    error?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                            ),
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onSubmit(email.trim(), password, registerMode) },
                        enabled = !loading && email.isNotBlank() && password.length >= 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(21.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (registerMode) "注册并登录" else "登录")
                        }
                    }
                    TextButton(
                        onClick = { registerMode = !registerMode },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (registerMode) "已有账号？返回登录" else "没有账号？立即注册")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onUseLocally,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("先不登录，在本机使用")
            }
            Text(
                "数据只加密保存在这台手机上，不会上传。AI 功能需要登录。",
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "任务、提醒与步骤，随时云端同步",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
internal fun TodoCloudBrandMark() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// Adapted from liyaxu123's Uiverse input (MIT License):
// https://uiverse.io/liyaxu123/warm-eel-62
@Composable
internal fun FloatingUnderlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotEmpty()
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor by animateColorAsState(
        targetValue = if (active) activeColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "input label color",
    )
    val underlineColor by animateColorAsState(
        targetValue = if (active) activeColor else idleColor,
        animationSpec = tween(300),
        label = "input underline color",
    )
    val labelEasing = remember { CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f) }

    if (!singleLine) {
        val fieldShape = RoundedCornerShape(16.dp)
        Column(modifier = modifier) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(fieldShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f))
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = underlineColor,
                        shape = fieldShape,
                    )
                    .padding(horizontal = 16.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(activeColor),
                singleLine = true,
                maxLines = 1,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
                                fontSize = 18.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    Column(modifier = modifier) {
        Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 40.dp)
                    // Keep the caret close to the shared underline on every form.
                    .padding(
                        top = 8.dp,
                        bottom = INPUT_TEXT_BOTTOM_PADDING_DP.dp,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = label },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 18.sp,
                ),
                cursorBrush = SolidColor(activeColor),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                label.forEachIndexed { index, character ->
                    val characterOffset by animateDpAsState(
                        // Restore the Uiverse-style float animation while keeping
                        // the focused label close to the underline.
                        targetValue = if (active) (-INPUT_LABEL_FLOAT_OFFSET_DP).dp else 0.dp,
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50,
                            easing = labelEasing,
                        ),
                        label = "input label position $index",
                    )
                    Text(
                        text = character.toString(),
                        color = labelColor,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .widthIn(min = 5.dp)
                            .offset(y = characterOffset),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .background(underlineColor),
            )
        }
        supportingText?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

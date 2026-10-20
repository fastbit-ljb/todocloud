package com.todocloud.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp

/** Lay out the complete sentence; reveal ink without reflowing words or the caret. */
@Composable
internal fun StableTypewriterLine(
    text: String,
    visibleCharacters: Int,
    showCursor: Boolean,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val visible = visibleCharacters.coerceIn(0, text.length)
    val revealedText = remember(text, visible) {
        buildAnnotatedString {
            append(text)
            if (visible < text.length) addStyle(SpanStyle(color = Color.Transparent), visible, text.length)
        }
    }
    var layout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier) {
        Text(
            text = revealedText,
            color = color,
            style = style,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                this.text = AnnotatedString(text.take(visible))
            },
            onTextLayout = { layout = it; onTextLayout(it) },
        )
        if (showCursor) {
            Canvas(Modifier.matchParentSize()) {
                val caret = layout?.getCursorRect(visible) ?: return@Canvas
                val inset = (caret.bottom - caret.top) * 0.12f
                val stroke = 1.5.dp.toPx()
                val x = caret.left.coerceIn(stroke / 2, (size.width - stroke / 2).coerceAtLeast(stroke / 2))
                drawLine(color, Offset(x, caret.top + inset), Offset(x, caret.bottom - inset), strokeWidth = stroke)
            }
        }
    }
}

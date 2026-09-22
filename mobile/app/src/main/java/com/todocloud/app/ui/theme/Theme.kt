package com.todocloud.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TodoLightColors = lightColorScheme(
    primary = Color(0xFF27833A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBEBCB),
    onPrimaryContainer = Color(0xFF0A3814),
    secondary = Color(0xFF557A55),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EBD4),
    onSecondaryContainer = Color(0xFF132616),
    background = Color(0xFFF5FAEE),
    onBackground = Color(0xFF182018),
    surface = Color(0xFFF5FAEE),
    onSurface = Color(0xFF182018),
    surfaceVariant = Color(0xFFE4EFDF),
    onSurfaceVariant = Color(0xFF4E5A4C),
    outline = Color(0xFF788477),
)

private val TodoDarkColors = darkColorScheme(
    primary = Color(0xFF8FD694),
    onPrimary = Color(0xFF0A3814),
    primaryContainer = Color(0xFF205F2A),
    onPrimaryContainer = Color(0xFFB0F2B1),
    secondary = Color(0xFFB8D5B5),
    onSecondary = Color(0xFF203621),
    secondaryContainer = Color(0xFF3A513A),
    onSecondaryContainer = Color(0xFFD4EBD0),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE1E9DD),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE1E9DD),
    surfaceVariant = Color(0xFF3F4A3D),
    onSurfaceVariant = Color(0xFFC0CCBC),
    outline = Color(0xFF8B9888),
)

@Composable
fun TodoCloudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

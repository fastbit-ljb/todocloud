package com.todocloud.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand green, used as the seed where the system has no theme color (Android 11 and older).
 * It is the same green the app has always shipped with.
 */
internal val FallbackSeed = Color(0xFF27833A)

/**
 * The theme color the user picked for the system (Material You), or [FallbackSeed].
 * Only this one hue is taken from the system: Android's own secondary/tertiary/neutral
 * palettes are ignored because some vendors return unrelated hues for them (a pink
 * secondary next to a green primary), which is what made the app look mismatched.
 */
internal fun systemSeedColor(context: Context): Color =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_500))
    } else {
        FallbackSeed
    }

/**
 * Builds the whole color scheme from one seed by changing only lightness (and, for
 * surfaces, how much of the seed tint is kept). Error colors stay red on purpose:
 * they carry meaning ("overdue", "failed") and must not turn green or blue.
 */
internal fun seededColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val (hue, rawSaturation) = hueAndSaturation(seed)
    // A grey/black wallpaper gives an almost colorless seed: keep it calm and neutral.
    val chroma = rawSaturation.coerceIn(0.04f, 0.80f)
    // `tone` is perceptual lightness (CIELAB L*, 0 = black, 100 = white), like Material's
    // tonal palettes, so contrast between two tones is the same whatever the seed hue.
    fun tone(t: Int, saturationScale: Float = 1f) =
        colorAtTone(hue, (chroma * saturationScale).coerceIn(0f, 1f), t)
    fun neutral(t: Int, tint: Float = 0.12f) =
        colorAtTone(hue, (chroma * tint).coerceAtMost(0.10f), t)

    return if (!dark) {
        lightColorScheme(
            primary = tone(40),
            onPrimary = Color.White,
            primaryContainer = tone(90, 0.75f),
            onPrimaryContainer = tone(10),
            inversePrimary = tone(80),
            secondary = tone(40, 0.35f),
            onSecondary = Color.White,
            secondaryContainer = tone(90, 0.45f),
            onSecondaryContainer = tone(10, 0.5f),
            tertiary = tone(35, 0.6f),
            onTertiary = Color.White,
            tertiaryContainer = tone(92, 0.55f),
            onTertiaryContainer = tone(10, 0.6f),
            background = neutral(98),
            onBackground = neutral(10),
            surface = neutral(98),
            onSurface = neutral(10),
            surfaceVariant = neutral(90, 0.3f),
            onSurfaceVariant = neutral(30, 0.3f),
            surfaceTint = tone(40),
            inverseSurface = neutral(20),
            inverseOnSurface = neutral(95),
            outline = neutral(50, 0.3f),
            outlineVariant = neutral(80, 0.3f),
            surfaceBright = neutral(98),
            surfaceDim = neutral(87),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral(96, 0.25f),
            surfaceContainer = neutral(94, 0.25f),
            surfaceContainerHigh = neutral(92, 0.28f),
            surfaceContainerHighest = neutral(90, 0.3f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        )
    } else {
        darkColorScheme(
            primary = tone(80, 0.85f),
            onPrimary = tone(20),
            primaryContainer = tone(30, 0.8f),
            onPrimaryContainer = tone(90, 0.7f),
            inversePrimary = tone(40),
            secondary = tone(80, 0.35f),
            onSecondary = tone(20, 0.4f),
            secondaryContainer = tone(30, 0.35f),
            onSecondaryContainer = tone(90, 0.4f),
            tertiary = tone(82, 0.55f),
            onTertiary = tone(20, 0.6f),
            tertiaryContainer = tone(30, 0.55f),
            onTertiaryContainer = tone(92, 0.5f),
            background = neutral(6),
            onBackground = neutral(90),
            surface = neutral(6),
            onSurface = neutral(90),
            surfaceVariant = neutral(30, 0.3f),
            onSurfaceVariant = neutral(80, 0.3f),
            surfaceTint = tone(80, 0.85f),
            inverseSurface = neutral(90),
            inverseOnSurface = neutral(20),
            outline = neutral(60, 0.3f),
            outlineVariant = neutral(30, 0.3f),
            surfaceBright = neutral(24),
            surfaceDim = neutral(6),
            surfaceContainerLowest = neutral(4),
            surfaceContainerLow = neutral(10, 0.25f),
            surfaceContainer = neutral(12, 0.25f),
            surfaceContainerHigh = neutral(17, 0.28f),
            surfaceContainerHighest = neutral(22, 0.3f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        )
    }
}

/** The color of [hue] and HSL [saturation] whose perceptual lightness (L*) is [tone]. */
internal fun colorAtTone(hue: Float, saturation: Float, tone: Int): Color {
    val target = tone.coerceIn(0, 100).toDouble()
    if (target >= 100.0) return Color.White
    if (target <= 0.0) return Color.Black
    var low = 0f
    var high = 1f
    repeat(24) {
        val mid = (low + high) / 2f
        if (lStar(Color.hsl(hue, saturation, mid)) < target) low = mid else high = mid
    }
    return Color.hsl(hue, saturation, (low + high) / 2f)
}

/** CIELAB L* (0..100) of a color. */
internal fun lStar(color: Color): Double {
    val y = relativeLuminance(color)
    return if (y > 216.0 / 24389.0) 116.0 * Math.cbrt(y) - 16.0 else y * 24389.0 / 27.0
}

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** HSL hue (0-360) and saturation (0-1) of a color. */
internal fun hueAndSaturation(color: Color): Pair<Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f
    if (delta < 1e-4f) return 0f to 0f
    val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return hue to saturation.coerceIn(0f, 1f)
}

/** WCAG contrast ratio between two opaque colors (1..21). */
internal fun contrastRatio(a: Color, b: Color): Double {
    fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
    val (light, darkL) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
    return (light + 0.05) / (darkL + 0.05)
}

/** One type scale for the whole app: bolder titles, roomier body text. */
internal val TodoTypography: Typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** Three corner sizes used everywhere: small 12, medium 16, large 24 (dialogs 28). */
internal val TodoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing steps; screens use these instead of ad-hoc values. */
internal object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val screen = 20.dp // horizontal page margin
}

@Composable
fun TodoCloudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val seed = systemSeedColor(context)
    val colorScheme = remember(seed.toArgb(), darkTheme) { seededColorScheme(seed, darkTheme) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TodoTypography,
        shapes = TodoShapes,
        content = content,
    )
}

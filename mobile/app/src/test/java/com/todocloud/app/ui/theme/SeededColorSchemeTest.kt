package com.todocloud.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class SeededColorSchemeTest {
    // Green (brand), wallpaper-ish blue, red, yellow, pink, and a near-grey seed.
    private val seeds = listOf(
        FallbackSeed,
        Color(0xFF3F6FD8),
        Color(0xFFD23C3C),
        Color(0xFFE6C229),
        Color(0xFFE07FB0),
        Color(0xFF7A7A7C),
    )

    @Test
    fun textStaysReadableForEverySeedInBothModes() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val s = seededColorScheme(seed, dark)
            val label = "seed=${seed.value.toString(16)} dark=$dark"
            assertTrue(contrastRatio(s.onPrimary, s.primary) >= 4.5, "onPrimary $label")
            assertTrue(contrastRatio(s.onPrimaryContainer, s.primaryContainer) >= 4.5, "onPrimaryContainer $label")
            assertTrue(contrastRatio(s.onSurface, s.surface) >= 7.0, "onSurface $label")
            assertTrue(contrastRatio(s.onSurfaceVariant, s.surface) >= 4.5, "onSurfaceVariant $label")
            assertTrue(contrastRatio(s.primary, s.surface) >= 3.0, "primary on surface $label")
            assertTrue(contrastRatio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5, "secondary $label")
        }
    }

    @Test
    fun everyAccentRoleKeepsTheSeedHue() {
        for (seed in seeds.dropLast(1)) {
            val seedHue = hueAndSaturation(seed).first
            val s = seededColorScheme(seed, dark = false)
            for (role in listOf(s.primary, s.primaryContainer, s.secondaryContainer, s.tertiary)) {
                val hue = hueAndSaturation(role).first
                val diff = abs(hue - seedHue).let { minOf(it, 360f - it) }
                assertTrue(diff < 4f, "hue drifted to $hue from $seedHue")
            }
        }
    }

    @Test
    fun hueAndSaturationOfKnownColors() {
        assertEquals(0f, hueAndSaturation(Color.Red).first, 0.5f)
        assertEquals(120f, hueAndSaturation(Color.Green).first, 0.5f)
        assertEquals(240f, hueAndSaturation(Color.Blue).first, 0.5f)
        assertEquals(0f, hueAndSaturation(Color.Gray).second, 0.01f)
    }
}

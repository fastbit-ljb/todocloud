package com.todocloud.app.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todocloud.app.ui.theme.TodoCloudTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypewriterLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun everyPrefixAndBlinkKeepsTheFinalGlyphPositions() = verifyPositions(1f)

    @Test fun largeTextKeepsWrappingStableWhileTyping() = verifyPositions(2f)

    private fun verifyPositions(fontScale: Float) {
        val phrase = "先做出来，再慢慢把它做对。"
        val count = mutableStateOf(phrase.length)
        val cursor = mutableStateOf(false)
        var layout: TextLayoutResult? = null
        ActivityScenario.launch(ConversationTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    TodoCloudTheme { Surface {
                        Column(Modifier.width(180.dp)) {
                            StableTypewriterLine(
                                text = phrase, visibleCharacters = count.value, showCursor = cursor.value,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading,
                                ),
                                modifier = Modifier.testTag("stable-opening"),
                                onTextLayout = { layout = it },
                            )
                        }
                    } }
                }
            } }
            compose.waitForIdle()
            val finalLayout = checkNotNull(layout)
            assertTrue("Fixture must wrap to exercise line balancing", finalLayout.lineCount > 1)
            val finalGlyphs = phrase.indices.map(finalLayout::getBoundingBox)
            for (visible in 0..phrase.length) {
                compose.runOnIdle { count.value = visible; cursor.value = true }
                compose.onNodeWithTag("stable-opening").onChild().assertTextEquals(phrase.take(visible))
                compose.runOnIdle {
                    val current = checkNotNull(layout)
                    assertEquals(finalLayout.lineCount, current.lineCount)
                    assertEquals(finalLayout.size, current.size)
                    phrase.indices.forEach { index ->
                        assertEquals("Glyph $index moved at prefix $visible, scale $fontScale", finalGlyphs[index], current.getBoundingBox(index))
                    }
                }
            }
            compose.runOnIdle { cursor.value = false }
            compose.runOnIdle { assertEquals(finalLayout.size, checkNotNull(layout).size) }
        }
    }
}

package com.todocloud.app.ui

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PlanRequestTest {
    @Test fun emptyAndWhitespaceAreNotRequests() {
        for (message in listOf("", " ", "\n\t ", "　")) {
            assertFalse(hasPlanRequestContent(message))
        }
    }

    @Test fun textOrExplicitlySelectedMaterialCanBeSent() {
        assertTrue(hasPlanRequestContent("安排下一步"))
        assertTrue(hasPlanRequestContent("", selectedIdeaCount = 1))
        assertTrue(hasPlanRequestContent("", imageCount = 1))
        assertTrue(hasPlanRequestContent("", referenceCount = 1))
    }

    @Test fun removingTheLastSelectedItemDisablesAnEmptyRequest() {
        assertTrue(hasPlanRequestContent("", selectedIdeaCount = 2))
        assertFalse(hasPlanRequestContent(" ", selectedIdeaCount = 0, imageCount = 0, referenceCount = 0))
    }
}

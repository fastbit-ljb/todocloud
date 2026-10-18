package com.todocloud.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundBatterySettingsTest {
    @Test
    fun supportedSystemOpensPerAppBatteryPageWithoutOpeningAnotherScreen() {
        val attempted = mutableListOf<BackgroundBatteryDestination>()
        val result = launchBackgroundBatterySettings {
            attempted += it
            true
        }
        assertEquals(BackgroundBatteryDestination.APP_BATTERY, result)
        assertEquals(listOf(BackgroundBatteryDestination.APP_BATTERY), attempted)
    }

    @Test
    fun protectedBatteryPageFallsBackToAppDetails() {
        val attempted = mutableListOf<BackgroundBatteryDestination>()
        val result = launchBackgroundBatterySettings {
            attempted += it
            if (it == BackgroundBatteryDestination.APP_BATTERY) throw SecurityException("Not exported")
            true
        }
        assertEquals(BackgroundBatteryDestination.APP_DETAILS, result)
        assertEquals(listOf(BackgroundBatteryDestination.APP_BATTERY, BackgroundBatteryDestination.APP_DETAILS), attempted)
    }

    @Test
    fun unavailableAppPagesFallBackToBatteryOptimizationList() {
        assertEquals(
            BackgroundBatteryDestination.OPTIMIZATION_LIST,
            launchBackgroundBatterySettings { it == BackgroundBatteryDestination.OPTIMIZATION_LIST },
        )
    }

    @Test
    fun noAvailableSettingsReportsFailureInsteadOfPretendingItOpened() {
        assertEquals(BackgroundBatteryDestination.UNAVAILABLE, launchBackgroundBatterySettings { false })
    }

    @Test
    fun colorOsFamilyUsesTheChoiceShownInTheUserScreenshot() {
        listOf("OPPO", "OnePlus", "realme").forEach { maker ->
            val guide = backgroundBatteryGuide(maker)
            assertTrue(guide.choice.contains("完全允许后台行为"))
            assertTrue(guide.path.contains("耗电管理"))
            assertTrue(guide.path.contains("不限制"))
            assertTrue(guide.choice.contains("无限制"))
        }
    }

    @Test
    fun otherManufacturersHaveShortInstructionsForTheirDifferentLabels() {
        assertTrue(backgroundBatteryGuide("Xiaomi").path.contains("无限制"))
        assertTrue(backgroundBatteryGuide("HONOR").path.contains("关闭自动管理"))
        val unknown = backgroundBatteryGuide("unknown")
        assertTrue(unknown.path.contains("电池用量"))
        assertTrue(unknown.path.contains("允许后台活动"))
    }
}

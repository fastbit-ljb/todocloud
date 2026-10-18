package com.todocloud.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundBatterySettingsTest {
    @Test
    fun colorOsFamilyNamesTheActualOemPolicyInsteadOfTheGenericBatterySwitch() {
        listOf("OPPO", "OnePlus", "realme").forEach { maker ->
            val guide = backgroundBatteryGuide(maker)
            assertTrue(guide.choice.contains("耗电管理"))
            assertTrue(guide.choice.contains("完全允许后台行为"))
            assertFalse(guide.choice.contains("无限制"))
        }
    }

    @Test
    fun colorOsGuideExplainsThatAllowBackgroundUseDoesNotOverrideSmartOptimization() {
        val guide = backgroundBatteryGuide("OPPO")
        assertTrue(guide.note.contains("允许后台使用"))
        assertTrue(guide.note.contains("智能优化"))
        assertTrue(guide.note.contains("以「耗电管理」为准"))
    }

    @Test
    fun manualPathStartsAtTheLauncherAndNamesTheSettingToFind() {
        val guide = backgroundBatteryGuide("realme")
        assertTrue(guide.path.startsWith("长按 TodoCloud 图标"))
        assertTrue(guide.path.contains("应用信息 → 耗电管理 → 完全允许后台行为"))
    }

    @Test
    fun xiaomiGuideUsesItsOwnUnrestrictedPolicyName() {
        val guide = backgroundBatteryGuide("Xiaomi")
        assertTrue(guide.path.contains("省电策略"))
        assertTrue(guide.path.contains("无限制"))
        assertTrue(guide.note.contains("无法读取"))
    }

    @Test
    fun huaweiAndHonorExplainManualStartupManagement() {
        listOf("HUAWEI", "HONOR").forEach { maker ->
            val guide = backgroundBatteryGuide(maker)
            assertTrue(guide.path.contains("关闭自动管理"))
            assertTrue(guide.path.contains("后台活动"))
        }
    }

    @Test
    fun unknownDevicesHaveManualGuidanceInsteadOfAnEnabledClaim() {
        val unknown = backgroundBatteryGuide("unknown")
        assertTrue(unknown.path.contains("耗电管理／电池"))
        assertTrue(unknown.path.contains("允许后台活动"))
        assertTrue(unknown.note.contains("手动确认"))
        assertTrue(unknown.note.contains("无法读取是否已放行"))
        assertFalse(unknown.choice.contains("已开启"))
    }
}

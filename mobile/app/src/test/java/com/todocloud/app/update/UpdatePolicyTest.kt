package com.todocloud.app.update

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private fun info(latest: String, minSupported: String? = null) = AppVersionInfo(
        latestVersion = latest,
        minSupportedVersion = minSupported,
        releaseNotes = "",
        releaseUrl = "https://example.invalid/release",
        downloadUrl = null,
    )

    @Test
    fun compareVersions_isNumericPerPart() {
        assertTrue(UpdatePolicy.compareVersions("1.3.10", "1.3.9") > 0)
        assertTrue(UpdatePolicy.compareVersions("1.4", "1.3.99") > 0)
        assertTrue(UpdatePolicy.compareVersions("2.0.0", "10.0.0") < 0)
        assertEquals(0, UpdatePolicy.compareVersions("v1.4", "1.4.0"))
        assertEquals(0, UpdatePolicy.compareVersions("1.3.2-debug", "1.3.2"))
    }

    @Test
    fun isNewer_onlyForStrictlyHigherVersions() {
        assertTrue(UpdatePolicy.isNewer("1.3.3", "1.3.2"))
        assertFalse(UpdatePolicy.isNewer("1.3.2", "1.3.2"))
        assertFalse(UpdatePolicy.isNewer("1.3.1", "1.3.2"))
    }

    @Test
    fun dialog_showsOncePerDayAndRespectsSkip() {
        val update = info("1.3.3")
        assertTrue(UpdatePolicy.shouldShowDialog(update, "1.3.2", null, null, "2026-10-11"))
        assertFalse(UpdatePolicy.shouldShowDialog(update, "1.3.2", null, "2026-10-11", "2026-10-11"))
        assertTrue(UpdatePolicy.shouldShowDialog(update, "1.3.2", null, "2026-10-10", "2026-10-11"))
        assertFalse(UpdatePolicy.shouldShowDialog(update, "1.3.2", "1.3.3", null, "2026-10-11"))
        // A skipped older version does not hide a newer one.
        assertTrue(UpdatePolicy.shouldShowDialog(info("1.3.4"), "1.3.2", "1.3.3", null, "2026-10-11"))
        assertFalse(UpdatePolicy.shouldShowDialog(update, "1.3.3", null, null, "2026-10-11"))
    }

    @Test
    fun forcedUpdate_ignoresSkipAndDailyLimit() {
        val forced = info("1.4.0", minSupported = "1.3.3")
        assertTrue(UpdatePolicy.isForced(forced, "1.3.2"))
        assertFalse(UpdatePolicy.isForced(forced, "1.3.3"))
        assertTrue(UpdatePolicy.shouldShowDialog(forced, "1.3.2", "1.4.0", "2026-10-11", "2026-10-11"))
        assertFalse(UpdatePolicy.isForced(info("1.4.0", minSupported = ""), "1.0.0"))
    }

    @Test
    fun notification_firesOncePerVersion() {
        assertTrue(UpdatePolicy.shouldNotify(info("1.3.3"), "1.3.2", null))
        assertFalse(UpdatePolicy.shouldNotify(info("1.3.3"), "1.3.2", "1.3.3"))
        assertTrue(UpdatePolicy.shouldNotify(info("1.3.4"), "1.3.2", "1.3.3"))
        assertFalse(UpdatePolicy.shouldNotify(info("1.3.2"), "1.3.2", null))
    }

    @Test
    fun openUrl_prefersApkThenReleasePage() {
        assertEquals("https://example.invalid/release", info("1.3.3").openUrl)
        assertEquals("https://example.invalid/a.apk", info("1.3.3").copy(downloadUrl = "https://example.invalid/a.apk").openUrl)
    }

    @Test
    fun summarizeNotes_stripsMarkdownAndCaps() {
        val notes = "## TodoCloud 1.3.3\n\n### AI 计划\n- **提问**时只回答\n完整变更：[v1](x)"
        assertEquals("TodoCloud 1.3.3\nAI 计划\n提问时只回答", UpdatePolicy.summarizeNotes(notes))
        assertEquals(10, UpdatePolicy.summarizeNotes("a".repeat(50), maxChars = 10).length)
    }
}

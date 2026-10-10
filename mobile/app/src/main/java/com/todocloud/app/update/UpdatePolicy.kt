package com.todocloud.app.update

/** What the server says about the newest published Android release. */
data class AppVersionInfo(
    val latestVersion: String,
    val minSupportedVersion: String?,
    val releaseNotes: String,
    val releaseUrl: String,
    val downloadUrl: String?,
) {
    /** Where "去下载" should go: the APK itself when known, else the release page. */
    val openUrl: String get() = downloadUrl?.takeIf { it.isNotBlank() } ?: releaseUrl
}

/** Pure decisions for the update prompt; no Android dependencies, so it is unit tested. */
object UpdatePolicy {
    /**
     * Compares dotted versions numerically: "1.3.10" > "1.3.9", "v1.4" == "1.4.0".
     * A non-numeric suffix on a part ("2-debug") only uses its leading digits.
     */
    fun compareVersions(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

    /** The installed version is below the server's minimum: prompt every launch, no "skip". */
    fun isForced(info: AppVersionInfo, current: String): Boolean {
        val minimum = info.minSupportedVersion?.takeIf { it.isNotBlank() } ?: return false
        return compareVersions(current, minimum) < 0
    }

    /** In-app dialog: once a day per new version, never for a skipped one unless forced. */
    fun shouldShowDialog(
        info: AppVersionInfo,
        current: String,
        skippedVersion: String?,
        lastPromptDay: String?,
        today: String,
    ): Boolean {
        if (!isNewer(info.latestVersion, current)) return false
        if (isForced(info, current)) return true
        if (skippedVersion != null && compareVersions(skippedVersion, info.latestVersion) == 0) return false
        return lastPromptDay != today
    }

    /** Background notification: at most once per released version. */
    fun shouldNotify(info: AppVersionInfo, current: String, notifiedVersion: String?): Boolean {
        if (!isNewer(info.latestVersion, current)) return false
        return notifiedVersion == null || compareVersions(notifiedVersion, info.latestVersion) != 0
    }

    /** Release notes trimmed for a dialog or notification: Markdown markers dropped, length capped. */
    fun summarizeNotes(notes: String, maxChars: Int = 600): String {
        val text = notes.lines()
            .map { it.trim().removePrefix("#").trimStart('#', ' ').removePrefix("- ").removePrefix("* ") }
            .map { it.replace("**", "").replace("`", "") }
            .filter { it.isNotBlank() && !it.startsWith("完整变更") }
            .joinToString("\n")
        return if (text.length <= maxChars) text else text.take(maxChars - 1).trimEnd() + "…"
    }

    private fun parts(version: String): List<Int> = version.trim()
        .removePrefix("v").removePrefix("V")
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

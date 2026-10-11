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

/** One piece of release notes as the update dialog lays it out. */
sealed interface ReleaseNoteBlock {
    data class Heading(val text: String) : ReleaseNoteBlock
    data class Bullet(val lead: String?, val text: String) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
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

    /**
     * Release notes as display blocks for the in-app dialog. Drops what only matters on
     * GitHub (the "## TodoCloud x" title, the 安装 section, the compare link, build numbers)
     * and splits "**要点**：说明" bullets into a bold lead and its text.
     */
    fun releaseNoteBlocks(notes: String, maxBlocks: Int = 14): List<ReleaseNoteBlock> {
        val blocks = mutableListOf<ReleaseNoteBlock>()
        var skippingSection = false
        for (raw in notes.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("###") -> {
                    val title = clean(line.trimStart('#', ' '))
                    skippingSection = title in SKIPPED_SECTIONS
                    if (!skippingSection && title.isNotEmpty()) blocks += ReleaseNoteBlock.Heading(title)
                }
                line.startsWith("#") -> skippingSection = false // release title, not shown
                skippingSection -> Unit
                line.startsWith("完整变更") -> Unit
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val body = line.drop(2).trim().replace("`", "")
                    val match = BOLD_LEAD.find(body)
                    blocks += if (match != null) {
                        ReleaseNoteBlock.Bullet(
                            lead = match.groupValues[1].trim(),
                            text = clean(match.groupValues[2]),
                        )
                    } else {
                        ReleaseNoteBlock.Bullet(lead = null, text = clean(body))
                    }
                }
                "versionCode" in line -> Unit
                else -> blocks += ReleaseNoteBlock.Paragraph(clean(line))
            }
        }
        // A heading whose section ended up empty is noise.
        val withoutEmptyHeadings = blocks.filterIndexed { index, block ->
            block !is ReleaseNoteBlock.Heading ||
                blocks.getOrNull(index + 1).let { it != null && it !is ReleaseNoteBlock.Heading }
        }
        return withoutEmptyHeadings.take(maxBlocks).dropLastWhile { it is ReleaseNoteBlock.Heading }
    }

    private val SKIPPED_SECTIONS = setOf("安装", "下载")
    private val BOLD_LEAD = Regex("""^\*\*(.+?)\*\*\s*[：:]?\s*(.*)$""")

    private fun clean(text: String) = text.replace("**", "").replace("`", "").trim()

    private fun parts(version: String): List<Int> = version.trim()
        .removePrefix("v").removePrefix("V")
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

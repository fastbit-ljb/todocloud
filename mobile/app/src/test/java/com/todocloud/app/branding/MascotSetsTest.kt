package com.todocloud.app.branding

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class MascotSetsTest {
    private val packageName = "com.todocloud.app"

    @Test
    fun setIdsAreStable() {
        // Stored in preferences and part of resource and alias names.
        assertEquals(listOf("classic", "mooncat"), MascotSets.all.map { it.id })
    }

    @Test
    fun classicIsTheDefaultAndUnknownIdsFallBackToIt() {
        assertSame(MascotSets.CLASSIC, MascotSets.DEFAULT)
        assertSame(MascotSets.CLASSIC, MascotSets.fromId(null))
        assertSame(MascotSets.CLASSIC, MascotSets.fromId("a-set-that-was-removed"))
        assertSame(MascotSets.MOON_CAT, MascotSets.fromId("mooncat"))
    }

    @Test
    fun classicKeepsTheLauncherAliasesOlderVersionsEnabled() {
        assertEquals(
            listOf("Daily", "Progress", "DueSoon", "Overdue", "Complete", "NoTasks", "HalfFrozen", "Frozen")
                .map { "$packageName.launcher.$it" },
            MascotState.entries.map { MascotSets.CLASSIC.launcherAlias(packageName, it) },
        )
    }

    @Test
    fun everySetHasItsOwnPictureAndIconForEveryState() {
        val pictures = MascotSets.all.flatMap { set -> MascotState.entries.map { set.pictures[it] } }
        val icons = MascotSets.all.flatMap { set -> MascotState.entries.map { set.launcherIcons[it] } }
        val aliases = MascotSets.all.flatMap { set -> MascotState.entries.map { set.launcherAlias(packageName, it) } }
        val expected = MascotSets.all.size * MascotState.entries.size
        assertEquals(expected, pictures.toSet().size)
        assertEquals(expected, icons.toSet().size)
        assertEquals(expected, aliases.toSet().size)
    }

    @Test
    fun previewsFollowTheArtworkNumbering() {
        assertEquals(
            listOf("日常", "即将到期", "已逾期", "进行中", "已完成", "暂无任务", "半冻结", "完全冻结"),
            MascotSets.previewOrder.map { it.label },
        )
        assertEquals(MascotState.entries.toSet(), MascotSets.previewOrder.toSet())
    }

    @Test
    fun manifestHasOneLauncherAliasPerSetAndStateWithOnlyClassicDailyEnabled() {
        val declared = launcherAliases()
        val expected = MascotSets.all.flatMap { set -> MascotState.entries.map { set to it } }
        assertEquals(expected.size, declared.size, "launcher aliases in AndroidManifest.xml: ${declared.keys}")
        for ((set, state) in expected) {
            val name = set.launcherAlias(packageName, state).removePrefix(packageName)
            val alias = assertNotNull(declared[name], "AndroidManifest.xml has no activity-alias $name")
            val icon = "@mipmap/ic_launcher_${set.id}_${snakeCase(state.launcherAlias)}"
            assertEquals(icon, alias.getAttributeNS(ANDROID, "icon"), name)
            assertEquals(icon, alias.getAttributeNS(ANDROID, "roundIcon"), name)
            assertEquals(".MainActivity", alias.getAttributeNS(ANDROID, "targetActivity"), name)
            val enabledByDefault = set === MascotSets.DEFAULT && state == MascotState.DAILY
            assertEquals(enabledByDefault.toString(), alias.getAttributeNS(ANDROID, "enabled"), name)
        }
    }

    @Test
    fun everyLauncherIconUsesTheSamePlainWhiteBackground() {
        val res = resDirectory()
        // Exactly one definition: an override under drawable-night or drawable-v31 would bring a
        // theme colour back on some phones.
        val definitions = subdirectories(res, "drawable")
            .map { File(it, "ic_launcher_background.xml") }
            .filter { it.isFile }
        assertEquals(listOf("drawable"), definitions.map { it.parentFile.name })
        val fills = parse(definitions.single()).getElementsByTagName("solid")
        assertEquals(1, fills.length)
        assertEquals("@android:color/white", (fills.item(0) as Element).getAttributeNS(ANDROID, "color"))

        val icons = subdirectories(res, "mipmap").flatMap { dir ->
            dir.listFiles().orEmpty().filter { it.extension == "xml" }
        }
        assertTrue(icons.size >= MascotSets.all.size * MascotState.entries.size * 2, "launcher icons: $icons")
        for (icon in icons) {
            val background = parse(icon).getElementsByTagName("background").item(0) as? Element
            assertEquals(
                "@drawable/ic_launcher_background",
                background?.getAttributeNS(ANDROID, "drawable"),
                icon.path,
            )
        }
    }

    /** Every activity-alias that has a MAIN/LAUNCHER intent filter, by android:name. */
    private fun launcherAliases(): Map<String, Element> {
        val document = parse(manifestFile())
        val aliases = document.getElementsByTagName("activity-alias")
        return (0 until aliases.length)
            .map { aliases.item(it) as Element }
            .filter { alias ->
                val categories = alias.getElementsByTagName("category")
                (0 until categories.length).any {
                    (categories.item(it) as Element).getAttributeNS(ANDROID, "name") ==
                        "android.intent.category.LAUNCHER"
                }
            }
            .associateBy { it.getAttributeNS(ANDROID, "name") }
    }

    private fun manifestFile(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")

    private fun resDirectory(): File =
        listOf("src/main/res", "app/src/main/res")
            .map(::File)
            .firstOrNull { it.isDirectory }
            ?: error("res directory not found from ${File(".").absolutePath}")

    /** `drawable`, `drawable-night`, `drawable-v31`, ... for prefix "drawable". */
    private fun subdirectories(res: File, prefix: String): List<File> =
        res.listFiles().orEmpty().filter { it.isDirectory && (it.name == prefix || it.name.startsWith("$prefix-")) }

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun snakeCase(name: String) = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}

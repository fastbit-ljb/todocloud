package com.todocloud.app.branding

import com.todocloud.app.R
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
        assertEquals(
            listOf(
                "classic", "mooncat", "silverguard", "blondeacademy", "bluewitch",
                "silverwitchsimple", "silverwitchdetail", "silverstudentsimple", "silverstudentdetail",
            ),
            MascotSets.all.map { it.id },
        )
    }

    @Test
    fun classicIsTheDefaultAndUnknownIdsFallBackToIt() {
        assertSame(MascotSets.CLASSIC, MascotSets.DEFAULT)
        assertSame(MascotSets.CLASSIC, MascotSets.fromId(null))
        assertSame(MascotSets.CLASSIC, MascotSets.fromId("a-set-that-was-removed"))
        assertSame(MascotSets.MOON_CAT, MascotSets.fromId("mooncat"))
        assertSame(MascotSets.SILVER_GUARD, MascotSets.fromId("silverguard"))
        assertSame(MascotSets.BLONDE_ACADEMY, MascotSets.fromId("blondeacademy"))
        assertSame(MascotSets.BLUE_WITCH, MascotSets.fromId("bluewitch"))
        assertSame(MascotSets.SILVER_WITCH_SIMPLE, MascotSets.fromId("silverwitchsimple"))
        assertSame(MascotSets.SILVER_WITCH_DETAIL, MascotSets.fromId("silverwitchdetail"))
        assertSame(MascotSets.SILVER_STUDENT_SIMPLE, MascotSets.fromId("silverstudentsimple"))
        assertSame(MascotSets.SILVER_STUDENT_DETAIL, MascotSets.fromId("silverstudentdetail"))
    }

    @Test
    fun avatarAndLauncherIconShowTheSameArtworkForAllEightStatesOfEverySet() {
        // Artwork file stems, in the source folders' 01_daily … 08_frozen order.
        val keys = listOf("daily", "due_soon", "overdue", "progress", "complete", "no_tasks", "half_frozen", "frozen")
        assertEquals(keys, MascotSets.previewOrder.map { snakeCase(it.launcherAlias) })
        val res = resDirectory()
        var checked = 0
        for (set in MascotSets.all) {
            for (state in MascotSets.previewOrder) {
                val key = snakeCase(state.launcherAlias)
                val where = "${set.id} ${state.name}"
                val picture = "mascot_${set.id}_$key"
                assertEquals(resourceId(R.drawable::class.java, picture), set.avatar(state), where)
                assertEquals(
                    resourceId(R.mipmap::class.java, "ic_launcher_${set.id}_$key"),
                    set.launcherIcons[state],
                    where,
                )
                // That launcher icon's foreground is the very same picture, in both icon variants.
                for (variant in listOf("mipmap-anydpi-v26", "mipmap-anydpi-v33")) {
                    val icon = parse(File(res, "$variant/ic_launcher_${set.id}_$key.xml"))
                    val foreground = icon.getElementsByTagName("foreground").item(0) as Element
                    assertEquals(
                        "@drawable/ic_mascot_foreground_${set.id}_$key",
                        foreground.getAttributeNS(ANDROID, "drawable"),
                        "$where $variant",
                    )
                }
                val layers = parse(File(res, "drawable/ic_mascot_foreground_${set.id}_$key.xml"))
                    .getElementsByTagName("item")
                assertEquals(1, layers.length, where)
                assertEquals("@drawable/$picture", (layers.item(0) as Element).getAttributeNS(ANDROID, "drawable"), where)
                checked++
            }
        }
        assertEquals(MascotSets.all.size * 8, checked)
    }

    @Test
    fun unknownOrDamagedStoredIdsShowTheClassicAvatar() {
        // What SharedPreferences.getAll() can hand back for the key: missing, unknown or
        // mistyped strings, and other value types from a damaged file.
        val stored = listOf<Any?>(null, "", "cloud", "CLASSIC", " mooncat", 42, 7L, true, 1.5f, setOf("mooncat"))
        for (value in stored) {
            val set = MascotSets.fromStored(value)
            assertSame(MascotSets.CLASSIC, set, "stored value: $value")
            assertEquals(R.drawable.mascot_classic_overdue, set.avatar(MascotState.OVERDUE), "stored value: $value")
        }
        assertSame(MascotSets.MOON_CAT, MascotSets.fromStored("mooncat"))
        assertEquals(R.drawable.mascot_mooncat_overdue, MascotSets.fromStored("mooncat").avatar(MascotState.OVERDUE))
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
        val definedIn = subdirectories(res, "drawable").filter { File(it, "ic_launcher_background.xml").isFile }
        assertEquals(listOf("drawable"), definedIn.map { it.name })
        val fills = parse(File(definedIn.single(), "ic_launcher_background.xml")).getElementsByTagName("solid")
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

    /** A generated R.drawable / R.mipmap id by resource name; fails if there is no such resource. */
    private fun resourceId(type: Class<*>, name: String): Int = type.getField(name).getInt(null)

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}

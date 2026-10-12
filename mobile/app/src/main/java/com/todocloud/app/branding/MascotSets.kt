package com.todocloud.app.branding

import com.todocloud.app.R

/**
 * One resource per [MascotState]. Every state is a required argument, so a set cannot leave
 * one out, and [get] is the only place that maps a state to a resource.
 */
class StateResources(
    private val daily: Int,
    private val dueSoon: Int,
    private val overdue: Int,
    private val inProgress: Int,
    private val complete: Int,
    private val noTasks: Int,
    private val halfFrozen: Int,
    private val frozen: Int,
) {
    operator fun get(state: MascotState): Int = when (state) {
        MascotState.DAILY -> daily
        MascotState.DUE_SOON -> dueSoon
        MascotState.OVERDUE -> overdue
        MascotState.IN_PROGRESS -> inProgress
        MascotState.COMPLETE -> complete
        MascotState.NO_TASKS -> noTasks
        MascotState.HALF_FROZEN -> halfFrozen
        MascotState.FROZEN -> frozen
    }
}

/**
 * A selectable mascot look. It only decides how each state looks; which state applies is
 * still decided by [MascotStateManager.resolve].
 */
class MascotSet(
    /** Stored in preferences and used in resource and alias names. Never rename or reuse one. */
    val id: String,
    val label: String,
    /** Transparent picture per state: drawable-nodpi/mascot_<id>_<state>.webp. */
    val pictures: StateResources,
    /** Adaptive launcher icon per state: mipmap/ic_launcher_<id>_<state>. */
    val launcherIcons: StateResources,
    /**
     * Prefix of this set's activity-alias names under `<package>.launcher.`. The classic set
     * uses "" so its aliases keep the names that versions before mascot sets enabled.
     */
    private val aliasPrefix: String,
) {
    fun launcherAlias(packageName: String, state: MascotState): String =
        "$packageName.launcher.$aliasPrefix${state.launcherAlias}"
}

/**
 * Every mascot set the app ships. To add one: run `mobile/tools/mascot_assets.py <id> <folder>`
 * on its eight PNGs, paste the eight disabled activity-alias entries it prints into
 * AndroidManifest.xml, then add a [MascotSet] here and append it to [all]. MascotSetsTest checks
 * the manifest against this list.
 */
object MascotSets {
    val CLASSIC = MascotSet(
        id = "classic",
        label = "经典猫娘",
        pictures = StateResources(
            daily = R.drawable.mascot_classic_daily,
            dueSoon = R.drawable.mascot_classic_due_soon,
            overdue = R.drawable.mascot_classic_overdue,
            inProgress = R.drawable.mascot_classic_progress,
            complete = R.drawable.mascot_classic_complete,
            noTasks = R.drawable.mascot_classic_no_tasks,
            halfFrozen = R.drawable.mascot_classic_half_frozen,
            frozen = R.drawable.mascot_classic_frozen,
        ),
        launcherIcons = StateResources(
            daily = R.mipmap.ic_launcher_classic_daily,
            dueSoon = R.mipmap.ic_launcher_classic_due_soon,
            overdue = R.mipmap.ic_launcher_classic_overdue,
            inProgress = R.mipmap.ic_launcher_classic_progress,
            complete = R.mipmap.ic_launcher_classic_complete,
            noTasks = R.mipmap.ic_launcher_classic_no_tasks,
            halfFrozen = R.mipmap.ic_launcher_classic_half_frozen,
            frozen = R.mipmap.ic_launcher_classic_frozen,
        ),
        aliasPrefix = "",
    )

    val MOON_CAT = MascotSet(
        id = "mooncat",
        label = "月光猫",
        pictures = StateResources(
            daily = R.drawable.mascot_mooncat_daily,
            dueSoon = R.drawable.mascot_mooncat_due_soon,
            overdue = R.drawable.mascot_mooncat_overdue,
            inProgress = R.drawable.mascot_mooncat_progress,
            complete = R.drawable.mascot_mooncat_complete,
            noTasks = R.drawable.mascot_mooncat_no_tasks,
            halfFrozen = R.drawable.mascot_mooncat_half_frozen,
            frozen = R.drawable.mascot_mooncat_frozen,
        ),
        launcherIcons = StateResources(
            daily = R.mipmap.ic_launcher_mooncat_daily,
            dueSoon = R.mipmap.ic_launcher_mooncat_due_soon,
            overdue = R.mipmap.ic_launcher_mooncat_overdue,
            inProgress = R.mipmap.ic_launcher_mooncat_progress,
            complete = R.mipmap.ic_launcher_mooncat_complete,
            noTasks = R.mipmap.ic_launcher_mooncat_no_tasks,
            halfFrozen = R.mipmap.ic_launcher_mooncat_half_frozen,
            frozen = R.mipmap.ic_launcher_mooncat_frozen,
        ),
        aliasPrefix = "mooncat.",
    )

    /** In the order the settings page lists them. */
    val all: List<MascotSet> = listOf(CLASSIC, MOON_CAT)

    /** Used by fresh installs and by upgrades from versions without mascot sets. */
    val DEFAULT: MascotSet = CLASSIC

    /** The stored choice; a missing or unknown id falls back to [DEFAULT]. */
    fun fromId(id: String?): MascotSet = all.firstOrNull { it.id == id } ?: DEFAULT

    /** The artwork's own numbering (01_daily … 08_frozen), used to lay out previews. */
    val previewOrder: List<MascotState> = listOf(
        MascotState.DAILY,
        MascotState.DUE_SOON,
        MascotState.OVERDUE,
        MascotState.IN_PROGRESS,
        MascotState.COMPLETE,
        MascotState.NO_TASKS,
        MascotState.HALF_FROZEN,
        MascotState.FROZEN,
    )
}

package com.todocloud.app.ui

import com.todocloud.app.branding.MascotState
import com.todocloud.app.branding.MascotStateManager
import com.todocloud.app.data.TaskItem
import java.time.Instant
import java.time.ZoneId

internal enum class PlanGreetingScene {
    GENERAL,
    EMPTY,
    MANY_TASKS,
    RESTART,
    IN_PROGRESS,
    COMPLETE,
    LATE_NIGHT,
}

internal data class PlanOpening(val greeting: String, val question: String)

/** Use the device's local time and confirmed task data; never infer a user's mood. */
internal fun planGreetingScene(
    tasks: List<TaskItem>,
    tasksKnown: Boolean = true,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): PlanGreetingScene {
    val hour = now.atZone(zone).hour
    if (hour >= 23 || hour < 5) return PlanGreetingScene.LATE_NIGHT
    if (!tasksKnown) return PlanGreetingScene.GENERAL

    // Keep daily/weekly/monthly completion semantics identical to the mascot.
    when (MascotStateManager.resolve(tasks, lastActivity = now, now = now, zoneId = zone)) {
        MascotState.COMPLETE -> return PlanGreetingScene.COMPLETE
        MascotState.OVERDUE -> return PlanGreetingScene.RESTART
        else -> Unit
    }
    val pending = tasks.filterNot { it.completed }
    return when {
        pending.isEmpty() -> PlanGreetingScene.EMPTY
        pending.size >= 8 -> PlanGreetingScene.MANY_TASKS
        pending.any { task -> task.steps.any { it.completed } } -> PlanGreetingScene.IN_PROGRESS
        else -> PlanGreetingScene.GENERAL
    }
}

private enum class PlanGreetingLine(val text: String) {
    SMALL_RESULT("先做成一件小事，让想法落地。"),
    SMALL_START("大目标，也可以从很小的一步开始。"),
    BUILD_CONFIDENCE("有些底气，是做成一件件小事之后才有的。"),
    SAVE_ENERGY("把力气留给重要的人和事，也留一点给自己。"),
    PREPARE_AS_YOU_GO("不必等一切就绪，边走边准备。"),
    RESTART("重新开始的这一步，什么时候迈出都值得。"),
    ITERATE("先做出来，再慢慢把它做对。"),
    TRY_ONE_STEP("不用等准备好，先做一步看看。"),
    FIND_THE_WAY("有些路，是走着走着才看清的。"),
    PRIORITIZE("先看清哪件事最值得花力气。"),
    BREAK_IT_DOWN("把难的那件事，拆成今天能做的一小块。"),
    FILL_THE_GAPS("先做起来，缺什么再补什么。"),
    FIRST_ATTEMPT("第一遍不用做好，能往下走就行。"),
    NEXT_STEP("做着做着，就知道下一步了。"),
    REVIEW_LATER("做出来再挑毛病，来得及。"),
    MAKE_IT_CONCRETE("做起来，问题才会变得具体。"),
}

/** Pick once per landing visit/conversation; the UI retains both lines while input changes. */
internal fun choosePlanOpening(scene: PlanGreetingScene): PlanOpening {
    val greetings = when (scene) {
        PlanGreetingScene.COMPLETE, PlanGreetingScene.LATE_NIGHT -> listOf(
            PlanGreetingLine.SAVE_ENERGY,
        )
        PlanGreetingScene.MANY_TASKS -> listOf(
            PlanGreetingLine.PRIORITIZE,
            PlanGreetingLine.BREAK_IT_DOWN,
            PlanGreetingLine.SAVE_ENERGY,
        )
        PlanGreetingScene.RESTART -> listOf(
            PlanGreetingLine.RESTART,
            PlanGreetingLine.SMALL_RESULT,
            PlanGreetingLine.BREAK_IT_DOWN,
            PlanGreetingLine.TRY_ONE_STEP,
        )
        PlanGreetingScene.EMPTY -> listOf(
            PlanGreetingLine.SMALL_RESULT,
            PlanGreetingLine.SMALL_START,
            PlanGreetingLine.PREPARE_AS_YOU_GO,
            PlanGreetingLine.TRY_ONE_STEP,
            PlanGreetingLine.FIND_THE_WAY,
        )
        PlanGreetingScene.IN_PROGRESS -> listOf(
            PlanGreetingLine.BUILD_CONFIDENCE,
            PlanGreetingLine.ITERATE,
            PlanGreetingLine.FIND_THE_WAY,
            PlanGreetingLine.FILL_THE_GAPS,
            PlanGreetingLine.NEXT_STEP,
            PlanGreetingLine.MAKE_IT_CONCRETE,
        )
        PlanGreetingScene.GENERAL -> listOf(
            PlanGreetingLine.BUILD_CONFIDENCE,
            PlanGreetingLine.SMALL_RESULT,
            PlanGreetingLine.SMALL_START,
            PlanGreetingLine.PREPARE_AS_YOU_GO,
            PlanGreetingLine.ITERATE,
            PlanGreetingLine.TRY_ONE_STEP,
            PlanGreetingLine.FIND_THE_WAY,
            PlanGreetingLine.FILL_THE_GAPS,
            PlanGreetingLine.FIRST_ATTEMPT,
            PlanGreetingLine.NEXT_STEP,
            PlanGreetingLine.REVIEW_LATER,
            PlanGreetingLine.MAKE_IT_CONCRETE,
        )
    }
    val questions = when (scene) {
        PlanGreetingScene.COMPLETE, PlanGreetingScene.LATE_NIGHT, PlanGreetingScene.EMPTY -> listOf(
            "你有什么计划吗？",
            "有什么想法想变成计划？",
        )
        PlanGreetingScene.MANY_TASKS, PlanGreetingScene.RESTART -> listOf(
            "哪些事情需要我帮你安排？",
            "接下来想做些什么？",
        )
        PlanGreetingScene.IN_PROGRESS, PlanGreetingScene.GENERAL -> listOf(
            "你有什么计划吗？",
            "今天想完成什么？",
            "有什么想法想变成计划？",
            "哪些事情需要我帮你安排？",
            "接下来想做些什么？",
        )
    }
    return PlanOpening(greetings.random().text, questions.random())
}

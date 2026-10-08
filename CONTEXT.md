# TodoCloud Planning

TodoCloud captures uncertain thoughts without turning them into obligations, then helps a user shape selected thoughts into an executable plan.

## Language

**Idea（想法）**:
A thought captured without a commitment to execute it. It may have an estimated duration, optional time boundaries, and optional detail items, but it is never completed like a task.
_Avoid_: Task, draft task

**Idea Item（想法子项）**:
An optional detail or component belonging to an Idea. It does not have an independent schedule or completion state.
_Avoid_: Task step, subtask

**Inbox（收件匣）**:
The collection of Ideas that have been captured but not necessarily selected for execution.
_Avoid_: Task list, backlog

**Task（任务）**:
An executable commitment that can be scheduled, reminded, stepped through, and completed.
_Avoid_: Idea, note

**Plan（计划）**:
An AI-authored, user-editable proposal that arranges Ideas and existing Tasks into scheduled work. A Plan remains a proposal until the user accepts it.
_Avoid_: Calendar, generated task list

**Plan Conversation（计划对话）**:
The sequence of user requests and AI revisions attached to one Plan. Each revision replaces the current proposal while preserving the conversation that produced it.

**Protected Time（保护时段）**:
A recurring meal or sleep interval that ordinary work must not occupy. Meals, travel or transport, and genuinely urgent time-fixed commitments may use protected time when required.
_Avoid_: Free time, unavailable date

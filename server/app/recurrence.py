"""Repeat rules for recurring tasks.

Completing a recurring task creates its next occurrence. Dates are stepped in
the user's own time zone, so "every day at 08:00" stays at 08:00 local time.
"""
import calendar
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

RECURRENCE_RULES = ("daily", "weekdays", "weekly", "monthly")

# Safety cap: a daily task left overdue for years still terminates quickly.
_MAX_STEPS = 5000


def user_zone(name: str | None) -> ZoneInfo:
    try:
        return ZoneInfo(name or "Asia/Shanghai")
    except (ZoneInfoNotFoundError, ValueError):
        return ZoneInfo("Asia/Shanghai")


def _add_months(value: datetime, months: int, anchor_day: int) -> datetime:
    month_index = value.month - 1 + months
    year = value.year + month_index // 12
    month = month_index % 12 + 1
    # The 31st becomes the last day of shorter months, and returns to the 31st after.
    day = min(anchor_day, calendar.monthrange(year, month)[1])
    return value.replace(year=year, month=month, day=day)


def _step(local: datetime, rule: str, anchor_day: int) -> datetime:
    if rule == "daily":
        return local + timedelta(days=1)
    if rule == "weekdays":
        nxt = local + timedelta(days=1)
        while nxt.weekday() >= 5:  # Saturday, Sunday
            nxt += timedelta(days=1)
        return nxt
    if rule == "weekly":
        return local + timedelta(days=7)
    if rule == "monthly":
        return _add_months(local, 1, anchor_day)
    raise ValueError(f"unknown recurrence rule: {rule}")


def next_occurrence(due_at: datetime, rule: str, zone: ZoneInfo, now: datetime) -> datetime:
    """The first occurrence after both ``due_at`` and ``now``.

    Skipping to the future means a daily task finished three days late gets one
    new occurrence (tomorrow-ish), not three already-overdue copies.
    """
    local = due_at.astimezone(zone)
    anchor_day = local.day
    wall = local.replace(tzinfo=None)
    for _ in range(_MAX_STEPS):
        wall = _step(wall, rule, anchor_day)
        candidate = wall.replace(tzinfo=zone)
        if candidate > now:
            return candidate
    return candidate

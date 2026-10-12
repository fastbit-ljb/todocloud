import unittest
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from app.recurrence import next_occurrence, user_zone

SH = ZoneInfo("Asia/Shanghai")


def sh(*args: int) -> datetime:
    return datetime(*args, tzinfo=SH)


class NextOccurrenceTests(unittest.TestCase):
    def test_daily_keeps_local_time(self) -> None:
        due = sh(2026, 10, 12, 8, 0)
        self.assertEqual(next_occurrence(due, "daily", SH, due), sh(2026, 10, 13, 8, 0))

    def test_weekly(self) -> None:
        due = sh(2026, 10, 14, 19, 30)  # Wednesday
        self.assertEqual(next_occurrence(due, "weekly", SH, due), sh(2026, 10, 21, 19, 30))

    def test_weekdays_skip_the_weekend(self) -> None:
        friday = sh(2026, 10, 16, 9, 0)
        self.assertEqual(next_occurrence(friday, "weekdays", SH, friday), sh(2026, 10, 19, 9, 0))
        monday = sh(2026, 10, 19, 9, 0)
        self.assertEqual(next_occurrence(monday, "weekdays", SH, monday), sh(2026, 10, 20, 9, 0))

    def test_monthly_clamps_short_months_and_returns_to_the_anchor_day(self) -> None:
        jan31 = sh(2027, 1, 31, 10, 0)
        feb = next_occurrence(jan31, "monthly", SH, jan31)
        self.assertEqual(feb, sh(2027, 2, 28, 10, 0))
        dec = sh(2026, 12, 15, 10, 0)
        self.assertEqual(next_occurrence(dec, "monthly", SH, dec), sh(2027, 1, 15, 10, 0))

    def test_overdue_task_jumps_to_the_first_future_occurrence(self) -> None:
        due = sh(2026, 10, 1, 8, 0)
        now = sh(2026, 10, 12, 12, 0)
        self.assertEqual(next_occurrence(due, "daily", SH, now), sh(2026, 10, 13, 8, 0))

    def test_steps_are_computed_in_the_user_zone(self) -> None:
        # 2026-10-12 23:30 in Shanghai is still the 12th there, 15:30 UTC.
        due = datetime(2026, 10, 12, 15, 30, tzinfo=timezone.utc)
        result = next_occurrence(due, "daily", SH, due)
        self.assertEqual(result.astimezone(timezone.utc), datetime(2026, 10, 13, 15, 30, tzinfo=timezone.utc))

    def test_unknown_zone_falls_back(self) -> None:
        self.assertEqual(user_zone("Not/AZone"), SH)
        self.assertEqual(user_zone(None), SH)

    def test_unknown_rule_raises(self) -> None:
        due = sh(2026, 10, 12, 8, 0)
        with self.assertRaises(ValueError):
            next_occurrence(due, "hourly", SH, due)


if __name__ == "__main__":
    unittest.main()

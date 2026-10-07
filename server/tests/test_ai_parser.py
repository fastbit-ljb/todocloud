import unittest

from app.ai_parser import (
    AiParseError,
    _build_prompt,
    _localize_reference,
    _normalize_tasks,
)


class AiTimezoneTests(unittest.TestCase):
    def test_utc_reference_is_converted_to_shanghai_local_date(self) -> None:
        reference = _localize_reference(
            "2026-10-07T22:03:00Z",
            "Asia/Shanghai",
        )

        self.assertEqual(reference.isoformat(), "2026-10-08T06:03:00+08:00")

    def test_prompt_spells_out_relative_dates_in_user_timezone(self) -> None:
        reference = _localize_reference(
            "2026-10-07T22:03:00Z",
            "Asia/Shanghai",
        )

        prompt = _build_prompt(reference, "Asia/Shanghai", "text")

        self.assertIn("当地日期是 2026-10-08", prompt)
        self.assertIn("明天=2026-10-09", prompt)
        self.assertIn("2026-10-08T06:03:00+08:00", prompt)

    def test_text_relative_date_is_repaired_when_model_uses_utc_date(self) -> None:
        reference = _localize_reference(
            "2026-10-07T22:03:00Z",
            "Asia/Shanghai",
        )
        result = {
            "tasks": [
                {
                    "title": "赶火车",
                    "due_at": "2026-10-08T10:00:00+08:00",
                    "source_text": "明天10点的火车",
                    "steps": [],
                }
            ]
        }

        normalized = _normalize_tasks(result, reference, repair_relative_dates=True)

        self.assertEqual(
            normalized["tasks"][0]["due_at"],
            "2026-10-09T10:00:00+08:00",
        )

    def test_new_york_reference_and_dst_use_local_calendar(self) -> None:
        reference = _localize_reference(
            "2026-03-08T04:30:00Z",
            "America/New_York",
        )
        result = {
            "tasks": [
                {
                    "title": "开会",
                    "due_at": "2026-03-08T10:00:00-04:00",
                    "source_text": "明天上午10点开会",
                    "steps": [],
                }
            ]
        }

        normalized = _normalize_tasks(result, reference, repair_relative_dates=True)

        self.assertEqual(reference.isoformat(), "2026-03-07T23:30:00-05:00")
        self.assertEqual(
            normalized["tasks"][0]["due_at"],
            "2026-03-08T10:00:00-04:00",
        )

    def test_java_fixed_offset_zone_is_supported(self) -> None:
        reference = _localize_reference(
            "2026-10-07T22:03:00Z",
            "GMT+08:00",
        )

        self.assertEqual(reference.isoformat(), "2026-10-08T06:03:00+08:00")

    def test_invalid_timezone_is_rejected_instead_of_guessing(self) -> None:
        with self.assertRaises(AiParseError):
            _localize_reference("2026-10-07T22:03:00Z", "Mars/Olympus")


if __name__ == "__main__":
    unittest.main()

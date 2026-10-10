import asyncio
import unittest
from unittest import mock

from app import app_version
from app.app_version import (
    CACHE_SECONDS,
    VersionUnavailableError,
    latest_version_info,
    normalize_version,
    parse_release,
)
from app.core.config import settings

RELEASE = {
    "tag_name": "v1.3.2",
    "draft": False,
    "prerelease": False,
    "html_url": "https://github.com/fastbit-ljb/todocloud/releases/tag/v1.3.2",
    "published_at": "2026-10-10T22:19:25Z",
    "body": "## TodoCloud 1.3.2\n- AI 计划",
    "assets": [
        {"name": "notes.txt", "browser_download_url": "https://example.invalid/notes.txt"},
        {"name": "TodoCloud-v1.3.2.apk", "browser_download_url": "https://example.invalid/app.apk"},
    ],
}


class ParseTests(unittest.TestCase):
    def test_normalize_version(self) -> None:
        self.assertEqual(normalize_version("v1.3.2"), "1.3.2")
        self.assertEqual(normalize_version(" 1.10 "), "1.10")
        for bad in ("", "latest", "v1.3.2-beta", "1..2", None):
            self.assertIsNone(normalize_version(bad))

    def test_parse_release_picks_apk_asset(self) -> None:
        info = parse_release(RELEASE)
        self.assertEqual(info["latest_version"], "1.3.2")
        self.assertEqual(info["download_url"], "https://example.invalid/app.apk")
        self.assertEqual(info["release_url"], RELEASE["html_url"])
        self.assertIn("AI 计划", info["release_notes"])

    def test_parse_release_without_apk_keeps_release_page(self) -> None:
        info = parse_release({**RELEASE, "assets": []})
        self.assertIsNone(info["download_url"])
        self.assertTrue(info["release_url"])

    def test_parse_release_rejects_prerelease_and_bad_tags(self) -> None:
        with self.assertRaises(ValueError):
            parse_release({**RELEASE, "prerelease": True})
        with self.assertRaises(ValueError):
            parse_release({**RELEASE, "tag_name": "nightly"})


class LatestVersionTests(unittest.TestCase):
    def setUp(self) -> None:
        app_version.reset_cache()
        patcher = mock.patch.multiple(
            settings,
            app_min_supported_version="",
            app_latest_version_override="",
            app_download_url_override="",
            app_release_notes_override="",
        )
        patcher.start()
        self.addCleanup(patcher.stop)
        self.addCleanup(app_version.reset_cache)

    def _run(self, now: float) -> dict:
        return asyncio.run(latest_version_info(now=now))

    def test_caches_between_calls(self) -> None:
        with mock.patch.object(app_version, "_fetch_latest_release", return_value=RELEASE) as fetch:
            self.assertEqual(self._run(1000)["latest_version"], "1.3.2")
            self._run(1000 + CACHE_SECONDS - 1)
            self.assertEqual(fetch.call_count, 1)
            self._run(1000 + CACHE_SECONDS + 1)
            self.assertEqual(fetch.call_count, 2)

    def test_serves_stale_value_when_github_fails(self) -> None:
        with mock.patch.object(app_version, "_fetch_latest_release", return_value=RELEASE):
            self._run(1000)
        with mock.patch.object(app_version, "_fetch_latest_release", side_effect=OSError) as fetch:
            self.assertEqual(self._run(1000 + CACHE_SECONDS + 5)["latest_version"], "1.3.2")
            self._run(1000 + CACHE_SECONDS + 30)  # within the 60 s retry backoff
            self.assertEqual(fetch.call_count, 1)

    def test_unavailable_without_cache(self) -> None:
        with mock.patch.object(app_version, "_fetch_latest_release", side_effect=OSError):
            with self.assertRaises(VersionUnavailableError):
                self._run(1000)

    def test_override_skips_github_and_min_supported_is_reported(self) -> None:
        with mock.patch.multiple(
            settings,
            app_latest_version_override="v1.4.0",
            app_download_url_override="https://mirror.invalid/app.apk",
            app_min_supported_version="1.3.0",
        ), mock.patch.object(app_version, "_fetch_latest_release") as fetch:
            info = self._run(1000)
        fetch.assert_not_called()
        self.assertEqual(info["latest_version"], "1.4.0")
        self.assertEqual(info["download_url"], "https://mirror.invalid/app.apk")
        self.assertEqual(info["min_supported_version"], "1.3.0")


if __name__ == "__main__":
    unittest.main()

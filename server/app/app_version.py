"""Latest Android release information, read from GitHub Releases.

The result is cached in-process so app launches do not hit GitHub; if GitHub
is unreachable the last good answer is served. Settings can override the
version/URL by hand (e.g. if GitHub is unusable) or set a minimum supported
version to force an update.
"""
import asyncio
import json
import logging
import re
import time
from urllib import request

from app.core.config import settings

logger = logging.getLogger(__name__)

CACHE_SECONDS = 600
GITHUB_TIMEOUT_SECONDS = 10
MAX_NOTES_CHARS = 2000
_VERSION_PATTERN = re.compile(r"^v?(\d+(?:\.\d+){0,3})$")


class VersionUnavailableError(Exception):
    """No release information could be obtained and nothing is cached."""


def normalize_version(tag: str) -> str | None:
    """'v1.3.2' -> '1.3.2'; anything that is not a plain dotted version -> None."""
    match = _VERSION_PATTERN.match(str(tag or "").strip())
    return match.group(1) if match else None


def parse_release(release: dict) -> dict:
    """Turn a GitHub 'latest release' payload into the app's version info."""
    if release.get("draft") or release.get("prerelease"):
        raise ValueError("latest release is a draft or prerelease")
    version = normalize_version(release.get("tag_name", ""))
    if version is None:
        raise ValueError(f"unexpected release tag {release.get('tag_name')!r}")
    apk_url = next(
        (
            asset.get("browser_download_url")
            for asset in release.get("assets") or []
            if str(asset.get("name", "")).lower().endswith(".apk")
            and asset.get("browser_download_url")
        ),
        None,
    )
    return {
        "latest_version": version,
        "release_notes": str(release.get("body") or "").strip()[:MAX_NOTES_CHARS],
        "release_url": release.get("html_url") or "",
        "download_url": apk_url,
        "published_at": release.get("published_at"),
    }


def _fetch_latest_release() -> dict:
    http_request = request.Request(
        f"https://api.github.com/repos/{settings.app_release_repo}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "todocloud-server"},
    )
    with request.urlopen(http_request, timeout=GITHUB_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


_cache: dict = {"value": None, "fetched_at": 0.0}
_lock = asyncio.Lock()


def _with_policy(info: dict) -> dict:
    return {**info, "min_supported_version": normalize_version(settings.app_min_supported_version)}


def _override() -> dict | None:
    version = normalize_version(settings.app_latest_version_override)
    if version is None:
        return None
    url = settings.app_download_url_override.strip() or None
    return {
        "latest_version": version,
        "release_notes": settings.app_release_notes_override.strip()[:MAX_NOTES_CHARS],
        "release_url": url or "",
        "download_url": url,
        "published_at": None,
    }


async def latest_version_info(now: float | None = None) -> dict:
    override = _override()
    if override is not None:
        return _with_policy(override)
    now = time.monotonic() if now is None else now
    async with _lock:
        cached = _cache["value"]
        if cached is not None and now - _cache["fetched_at"] < CACHE_SECONDS:
            return _with_policy(cached)
        try:
            info = parse_release(await asyncio.to_thread(_fetch_latest_release))
        except Exception as exc:  # network, HTTP, JSON or unexpected payload
            logger.warning("Could not read latest release from GitHub (%s)", type(exc).__name__)
            if cached is None:
                raise VersionUnavailableError("暂时无法获取最新版本信息") from exc
            # Serve the stale answer, and retry no sooner than a minute from now.
            _cache["fetched_at"] = now - CACHE_SECONDS + 60
            return _with_policy(cached)
        _cache["value"], _cache["fetched_at"] = info, now
        return _with_policy(info)


def reset_cache() -> None:
    _cache["value"], _cache["fetched_at"] = None, 0.0

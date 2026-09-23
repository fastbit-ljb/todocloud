from fastapi import HTTPException, status
from redis.asyncio import Redis

from app.core.config import settings


_redis = Redis.from_url(settings.redis_url, decode_responses=True)


async def enforce_rate_limit(scope: str, identifier: str, limit: int, window_seconds: int = 60) -> None:
    key = f"todocloud:rate:{scope}:{identifier}"
    count = await _redis.incr(key)
    if count == 1:
        await _redis.expire(key, window_seconds)
    if count > limit:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="请求过于频繁，请稍后再试",
            headers={"Retry-After": str(window_seconds)},
        )


async def close_rate_limit() -> None:
    await _redis.aclose()

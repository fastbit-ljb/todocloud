from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


DEFAULT_JWT_SECRET = "replace-this-before-deployment"


class Settings(BaseSettings):
    app_name: str = "TodoCloud API"
    environment: str = "development"
    database_url: str = "postgresql+asyncpg://todocloud:change-me-in-development@localhost:5432/todocloud"
    redis_url: str = "redis://localhost:6379/0"
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "todocloud"
    minio_secret_key: str = "change-me-in-development"
    minio_bucket: str = "attachments"
    minio_secure: bool = False
    ai_api_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai/"
    ai_api_key: str = ""
    ai_model: str = "gemini-3-flash-preview"
    ai_fallback_model: str = "gemini-3.6-flash"
    # Gemini thinking level sent as reasoning_effort. Newer models (3.7/3.8 flash)
    # reject "minimal"; use "low" for them. Empty = do not send the field.
    ai_reasoning_effort: str = "minimal"
    jwt_secret: str = DEFAULT_JWT_SECRET
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30
    login_rate_limit_per_minute: int = 10
    ai_rate_limit_per_minute: int = 5
    # Android update check (GET /app/version)
    app_release_repo: str = "fastbit-ljb/todocloud"
    app_min_supported_version: str = ""  # e.g. "1.3.0" forces older apps to update
    app_latest_version_override: str = ""  # set to bypass GitHub, e.g. "1.3.3"
    app_download_url_override: str = ""
    app_release_notes_override: str = ""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


def validate_settings(settings: Settings) -> Settings:
    """Refuse to run production with a forgeable JWT secret.

    Raised as a plain RuntimeError rather than a pydantic validation error,
    because pydantic's message echoes every input field (database password,
    AI key) into the logs.
    """
    if settings.environment.strip().lower() == "production":
        secret = settings.jwt_secret.strip()
        if not secret or secret == DEFAULT_JWT_SECRET:
            raise RuntimeError(
                "JWT_SECRET must be set to a random value when ENVIRONMENT=production"
            )
    return settings


@lru_cache
def get_settings() -> Settings:
    return validate_settings(Settings())


settings = get_settings()

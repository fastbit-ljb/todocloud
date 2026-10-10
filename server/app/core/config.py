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
    jwt_secret: str = DEFAULT_JWT_SECRET
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30
    login_rate_limit_per_minute: int = 10
    ai_rate_limit_per_minute: int = 5

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

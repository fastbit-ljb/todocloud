from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


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
    ai_model: str = "gemini-3.5-flash-lite"
    jwt_secret: str = "replace-this-before-deployment"
    access_token_expire_minutes: int = 60 * 24 * 30

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()

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
    jwt_secret: str = "replace-this-before-deployment"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()


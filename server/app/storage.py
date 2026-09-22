from io import BytesIO
from uuid import uuid4

from minio import Minio

from app.core.config import settings


def _client() -> Minio:
    return Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def store_image(user_id: int, filename: str, content_type: str, data: bytes) -> str:
    client = _client()
    if not client.bucket_exists(settings.minio_bucket):
        client.make_bucket(settings.minio_bucket)
    suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else "bin"
    object_key = f"{user_id}/{uuid4().hex}.{suffix}"
    client.put_object(
        settings.minio_bucket,
        object_key,
        BytesIO(data),
        length=len(data),
        content_type=content_type,
    )
    return object_key

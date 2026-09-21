import base64
import hashlib
import hmac
import json
import secrets
import time

from app.core.config import settings


_PASSWORD_ITERATIONS = 310_000


def _encode_bytes(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def _decode_bytes(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, _PASSWORD_ITERATIONS
    )
    return f"pbkdf2_sha256${_PASSWORD_ITERATIONS}${_encode_bytes(salt)}${_encode_bytes(digest)}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, iterations, salt, expected = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        actual = hashlib.pbkdf2_hmac(
            "sha256", password.encode("utf-8"), _decode_bytes(salt), int(iterations)
        )
        return hmac.compare_digest(actual, _decode_bytes(expected))
    except (TypeError, ValueError):
        return False


def create_access_token(subject: int) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": str(subject),
        "exp": int(time.time()) + settings.access_token_expire_minutes * 60,
    }
    encoded_header = _encode_bytes(json.dumps(header, separators=(",", ":")).encode())
    encoded_payload = _encode_bytes(json.dumps(payload, separators=(",", ":")).encode())
    unsigned = f"{encoded_header}.{encoded_payload}"
    signature = hmac.new(
        settings.jwt_secret.encode("utf-8"), unsigned.encode("ascii"), hashlib.sha256
    ).digest()
    return f"{unsigned}.{_encode_bytes(signature)}"


def decode_access_token(token: str) -> int:
    header, payload, signature = token.split(".", 2)
    unsigned = f"{header}.{payload}"
    expected = hmac.new(
        settings.jwt_secret.encode("utf-8"), unsigned.encode("ascii"), hashlib.sha256
    ).digest()
    if not hmac.compare_digest(expected, _decode_bytes(signature)):
        raise ValueError("invalid token signature")

    data = json.loads(_decode_bytes(payload))
    if int(data["exp"]) < int(time.time()):
        raise ValueError("expired token")
    return int(data["sub"])

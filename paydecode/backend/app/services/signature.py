import hashlib
import hmac

from app.config import settings


def verify_signature(payload: bytes, signature_header: str | None) -> bool:
    if not signature_header:
        return False
    expected = hmac.new(
        settings.webhook_secret.encode(),
        payload,
        hashlib.sha256,
    ).hexdigest()
    received = signature_header.removeprefix("sha256=")
    return hmac.compare_digest(expected, received)


def compute_signature(payload: bytes) -> str:
    sig = hmac.new(
        settings.webhook_secret.encode(),
        payload,
        hashlib.sha256,
    ).hexdigest()
    return f"sha256={sig}"

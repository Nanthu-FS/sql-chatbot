import random
import time
import uuid
from datetime import datetime, timedelta

from app.services.signature import compute_signature

# Weighted distribution matching the spec (sums to 1.0)
WEIGHTED_CODES = [
    ("insufficient_funds", 0.35),
    ("expired_card", 0.15),
    ("processing_error", 0.08),
    ("gateway_timeout", 0.04),
    ("do_not_honor", 0.08),
    ("fraudulent", 0.06),
    ("stolen_card", 0.04),
    ("authentication_required", 0.07),
    ("card_not_supported", 0.03),
    ("3d_secure_failed", 0.04),
    ("card_velocity_exceeded", 0.03),
    ("api_error", 0.02),
    ("network_error", 0.01),
]

CUSTOMER_POOL = [f"cust_{i:04d}" for i in range(1, 201)]
CURRENCY = "INR"


def _random_code() -> str:
    codes, weights = zip(*WEIGHTED_CODES)
    return random.choices(codes, weights=weights, k=1)[0]


def _random_amount() -> int:
    return random.choice([49900, 99900, 199900, 299900, 499900, 999900, 1499900])


def build_mock_payload(
    gateway_code: str | None = None,
    occurred_at: datetime | None = None,
) -> dict:
    code = gateway_code or _random_code()
    ts = occurred_at or datetime.utcnow()
    customer_id = random.choice(CUSTOMER_POOL)

    return {
        "event_id": str(uuid.uuid4()),
        "id": f"pay_{uuid.uuid4().hex[:12]}",
        "payment_id": f"pay_{uuid.uuid4().hex[:12]}",
        "amount": _random_amount(),
        "currency": CURRENCY,
        "error_code": code,
        "decline_code": code,
        "customer_id": customer_id,
        "customer_email": f"{customer_id}@example.com",
        "created": int(ts.timestamp()),
        "status": "failed",
    }


def generate_bulk_payloads(count: int, days: int) -> list[dict]:
    payloads = []
    now = datetime.utcnow()
    start = now - timedelta(days=days)
    span_seconds = int(timedelta(days=days).total_seconds())

    for _ in range(count):
        offset_seconds = random.randint(0, span_seconds)
        occurred_at = start + timedelta(seconds=offset_seconds)
        payload = build_mock_payload(occurred_at=occurred_at)
        payloads.append(payload)

    return payloads

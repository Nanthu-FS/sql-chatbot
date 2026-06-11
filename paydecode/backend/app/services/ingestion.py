from datetime import datetime, timedelta, timezone
from typing import Any

from sqlalchemy.orm import Session

from app.models.core import (
    Customer,
    Payment,
    PaymentEvent,
    PaymentFailure,
    RetryAttempt,
    RetryStrategyEnum,
)
from app.services.categorization import get_retry_strategy
from app.services.normalization import normalize_gateway_code


def _get_or_create_customer(db: Session, external_customer_id: str, email: str) -> Customer:
    customer = db.query(Customer).filter_by(external_customer_id=external_customer_id).first()
    if not customer:
        customer = Customer(external_customer_id=external_customer_id, email=email)
        db.add(customer)
        db.flush()
    return customer


def _schedule_retry(
    db: Session,
    payment_id: int,
    strategy: RetryStrategyEnum,
    occurred_at: datetime,
) -> None:
    if strategy == RetryStrategyEnum.none:
        return

    now = occurred_at
    if strategy == RetryStrategyEnum.immediate:
        scheduled_for = now + timedelta(minutes=5)
    elif strategy == RetryStrategyEnum.scheduled:
        scheduled_for = now + timedelta(hours=24)
    elif strategy == RetryStrategyEnum.payday:
        # Next 1st or last day of month
        if now.day < 15:
            scheduled_for = now.replace(day=1, hour=9, minute=0, second=0, microsecond=0) + timedelta(days=32)
            scheduled_for = scheduled_for.replace(day=1)
        else:
            import calendar
            last_day = calendar.monthrange(now.year, now.month)[1]
            scheduled_for = now.replace(day=last_day, hour=9, minute=0, second=0, microsecond=0)
            if scheduled_for <= now:
                next_month = now.replace(day=1) + timedelta(days=32)
                scheduled_for = next_month.replace(day=1, hour=9, minute=0, second=0, microsecond=0)
    elif strategy == RetryStrategyEnum.prompt_customer:
        scheduled_for = now + timedelta(hours=1)
    else:
        return

    attempt = RetryAttempt(
        payment_id=payment_id,
        scheduled_for=scheduled_for,
        strategy_used=strategy.value,
    )
    db.add(attempt)


def process_webhook(
    db: Session,
    gateway: str,
    event_id: str,
    raw_payload: dict[str, Any],
) -> tuple[bool, str]:
    """Returns (is_duplicate, unified_code)."""
    existing = db.query(PaymentEvent).filter_by(event_id=event_id).first()
    if existing:
        return True, ""

    event = PaymentEvent(event_id=event_id, gateway=gateway, raw_payload=raw_payload)
    db.add(event)
    db.flush()

    data = raw_payload
    external_payment_id = data.get("payment_id", data.get("id", event_id))
    amount = int(data.get("amount", 0))
    currency = data.get("currency", "INR").upper()
    gateway_code = data.get("error_code", data.get("decline_code", "unknown"))
    external_customer_id = data.get("customer_id", "cust_unknown")
    customer_email = data.get("customer_email", f"{external_customer_id}@example.com")
    occurred_at_raw = data.get("created", data.get("occurred_at"))
    if occurred_at_raw:
        if isinstance(occurred_at_raw, (int, float)):
            occurred_at = datetime.fromtimestamp(occurred_at_raw, tz=timezone.utc).replace(tzinfo=None)
        else:
            try:
                occurred_at = datetime.fromisoformat(str(occurred_at_raw).replace("Z", "+00:00")).replace(tzinfo=None)
            except ValueError:
                occurred_at = datetime.utcnow()
    else:
        occurred_at = datetime.utcnow()

    customer = _get_or_create_customer(db, external_customer_id, customer_email)

    payment = db.query(Payment).filter_by(external_payment_id=external_payment_id).first()
    if not payment:
        payment = Payment(
            external_payment_id=external_payment_id,
            gateway=gateway,
            amount=amount,
            currency=currency,
            customer_id=customer.id,
        )
        db.add(payment)
        db.flush()

    unified_code = normalize_gateway_code(db, gateway, gateway_code)

    attempt_number = (
        db.query(PaymentFailure).filter_by(payment_id=payment.id).count() + 1
    )

    failure = PaymentFailure(
        payment_id=payment.id,
        event_id=event.id,
        gateway_code=gateway_code,
        unified_code=unified_code,
        occurred_at=occurred_at,
        attempt_number=attempt_number,
    )
    db.add(failure)
    db.flush()

    strategy = get_retry_strategy(db, unified_code)
    _schedule_retry(db, payment.id, strategy, occurred_at)

    db.commit()
    return False, unified_code

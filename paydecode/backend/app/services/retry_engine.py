import random
from datetime import datetime

from apscheduler.schedulers.background import BackgroundScheduler
from sqlalchemy.orm import Session

from app.db import SessionLocal
from app.models.core import Payment, PaymentFailure, PaymentStatusEnum, RetryAttempt, RetryOutcomeEnum

_SUCCESS_RATES = {
    "immediate": 0.70,
    "scheduled": 0.55,
    "payday": 0.60,
    "prompt_customer": 0.50,
    "none": 0.0,
}

scheduler = BackgroundScheduler()


def _execute_retry(db: Session, attempt: RetryAttempt) -> None:
    rate = _SUCCESS_RATES.get(attempt.strategy_used, 0.5)
    success = random.random() < rate

    attempt.executed_at = datetime.utcnow()
    attempt.outcome = RetryOutcomeEnum.succeeded if success else RetryOutcomeEnum.failed

    if success:
        payment = db.get(Payment, attempt.payment_id)
        if payment:
            payment.status = PaymentStatusEnum.recovered

    db.commit()


def run_due_retries() -> None:
    db = SessionLocal()
    try:
        now = datetime.utcnow()
        due = (
            db.query(RetryAttempt)
            .filter(
                RetryAttempt.outcome == RetryOutcomeEnum.pending,
                RetryAttempt.scheduled_for <= now,
            )
            .all()
        )
        for attempt in due:
            _execute_retry(db, attempt)
    finally:
        db.close()


def start_scheduler() -> None:
    if not scheduler.running:
        scheduler.add_job(run_due_retries, "interval", seconds=60, id="retry_job", replace_existing=True)
        scheduler.start()


def stop_scheduler() -> None:
    if scheduler.running:
        scheduler.shutdown(wait=False)

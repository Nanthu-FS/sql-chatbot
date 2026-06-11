from datetime import datetime, date
from typing import Literal

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, case
from sqlalchemy.orm import Session

from app.db import get_db
from app.models.core import (
    Customer,
    DeclineCode,
    Payment,
    PaymentFailure,
    PaymentStatusEnum,
    RetryAttempt,
    RetryOutcomeEnum,
)
from app.schemas.analytics import (
    CategoryBreakdown,
    CodeBreakdown,
    RecoveryFunnel,
    SummaryResponse,
    TimeseriesPoint,
)

router = APIRouter(prefix="/api/analytics", tags=["analytics"])


def _parse_dt(val: str | None) -> datetime | None:
    if not val:
        return None
    try:
        return datetime.fromisoformat(val)
    except ValueError:
        return datetime.strptime(val, "%Y-%m-%d")


def _date_filter(query, from_: str | None, to_: str | None):
    if from_:
        dt = _parse_dt(from_)
        if dt:
            query = query.filter(PaymentFailure.occurred_at >= dt)
    if to_:
        dt = _parse_dt(to_)
        if dt:
            query = query.filter(PaymentFailure.occurred_at <= dt)
    return query


@router.get("/summary", response_model=SummaryResponse)
def get_summary(
    from_: str | None = Query(default=None, alias="from"),
    to_: str | None = Query(default=None, alias="to"),
    db: Session = Depends(get_db),
):
    q = db.query(
        func.count(PaymentFailure.id).label("failure_count"),
        func.sum(Payment.amount).label("total_failed_amount"),
    ).join(Payment, PaymentFailure.payment_id == Payment.id)
    q = _date_filter(q, from_, to_)
    row = q.one()

    failure_count = row.failure_count or 0
    total_failed_amount = row.total_failed_amount or 0

    recovered_amount_row = (
        db.query(func.sum(Payment.amount))
        .filter(Payment.status == PaymentStatusEnum.recovered)
        .scalar()
    ) or 0

    recovery_rate = 0.0
    if failure_count > 0:
        recovered_count = (
            db.query(func.count(Payment.id))
            .filter(Payment.status == PaymentStatusEnum.recovered)
            .scalar()
        ) or 0
        recovery_rate = round((recovered_count / failure_count) * 100, 1)

    top_cat_row = (
        _date_filter(
            db.query(
                DeclineCode.category.label("category"),
                func.count(PaymentFailure.id).label("cnt"),
            )
            .join(DeclineCode, PaymentFailure.unified_code == DeclineCode.code),
            from_,
            to_,
        )
        .group_by(DeclineCode.category)
        .order_by(func.count(PaymentFailure.id).desc())
        .first()
    )

    return SummaryResponse(
        total_failed_amount=total_failed_amount,
        failure_count=failure_count,
        recovery_rate_pct=recovery_rate,
        top_failure_category=top_cat_row.category.value if top_cat_row else None,
        recovered_amount=recovered_amount_row,
    )


@router.get("/by-category", response_model=list[CategoryBreakdown])
def get_by_category(
    from_: str | None = Query(default=None, alias="from"),
    to_: str | None = Query(default=None, alias="to"),
    db: Session = Depends(get_db),
):
    q = (
        db.query(
            DeclineCode.category.label("category"),
            func.count(PaymentFailure.id).label("failure_count"),
            func.sum(Payment.amount).label("lost_amount"),
        )
        .join(DeclineCode, PaymentFailure.unified_code == DeclineCode.code)
        .join(Payment, PaymentFailure.payment_id == Payment.id)
    )
    q = _date_filter(q, from_, to_)
    rows = q.group_by(DeclineCode.category).order_by(func.count(PaymentFailure.id).desc()).all()
    return [
        CategoryBreakdown(
            category=r.category.value,
            failure_count=r.failure_count,
            lost_amount=r.lost_amount or 0,
        )
        for r in rows
    ]


@router.get("/by-code", response_model=list[CodeBreakdown])
def get_by_code(
    from_: str | None = Query(default=None, alias="from"),
    to_: str | None = Query(default=None, alias="to"),
    db: Session = Depends(get_db),
):
    q = (
        db.query(
            PaymentFailure.unified_code.label("unified_code"),
            DeclineCode.category.label("category"),
            func.count(PaymentFailure.id).label("failure_count"),
            func.sum(Payment.amount).label("lost_amount"),
        )
        .join(DeclineCode, PaymentFailure.unified_code == DeclineCode.code)
        .join(Payment, PaymentFailure.payment_id == Payment.id)
    )
    q = _date_filter(q, from_, to_)
    rows = q.group_by(PaymentFailure.unified_code, DeclineCode.category).order_by(func.count(PaymentFailure.id).desc()).all()
    return [
        CodeBreakdown(
            unified_code=r.unified_code,
            category=r.category.value,
            failure_count=r.failure_count,
            lost_amount=r.lost_amount or 0,
        )
        for r in rows
    ]


@router.get("/timeseries", response_model=list[TimeseriesPoint])
def get_timeseries(
    bucket: Literal["day", "week"] = Query(default="day"),
    from_: str | None = Query(default=None, alias="from"),
    to_: str | None = Query(default=None, alias="to"),
    db: Session = Depends(get_db),
):
    from sqlalchemy import text

    if bucket == "day":
        trunc_expr = func.strftime("%Y-%m-%d", PaymentFailure.occurred_at)
    else:
        trunc_expr = func.strftime("%Y-W%W", PaymentFailure.occurred_at)

    q = (
        db.query(
            trunc_expr.label("bucket"),
            DeclineCode.category.label("category"),
            func.count(PaymentFailure.id).label("failure_count"),
            func.sum(Payment.amount).label("lost_amount"),
        )
        .join(DeclineCode, PaymentFailure.unified_code == DeclineCode.code)
        .join(Payment, PaymentFailure.payment_id == Payment.id)
    )
    q = _date_filter(q, from_, to_)
    rows = q.group_by("bucket", DeclineCode.category).order_by("bucket").all()
    return [
        TimeseriesPoint(
            bucket=r.bucket,
            category=r.category.value,
            failure_count=r.failure_count,
            lost_amount=r.lost_amount or 0,
        )
        for r in rows
    ]


@router.get("/recovery", response_model=RecoveryFunnel)
def get_recovery(db: Session = Depends(get_db)):
    scheduled = db.query(func.count(RetryAttempt.id)).scalar() or 0
    executed = (
        db.query(func.count(RetryAttempt.id))
        .filter(RetryAttempt.outcome != RetryOutcomeEnum.pending)
        .scalar()
    ) or 0
    recovered_count = (
        db.query(func.count(RetryAttempt.id))
        .filter(RetryAttempt.outcome == RetryOutcomeEnum.succeeded)
        .scalar()
    ) or 0
    recovered_amount = (
        db.query(func.sum(Payment.amount))
        .join(RetryAttempt, RetryAttempt.payment_id == Payment.id)
        .filter(RetryAttempt.outcome == RetryOutcomeEnum.succeeded)
        .scalar()
    ) or 0

    rate = round((recovered_count / executed * 100), 1) if executed > 0 else 0.0

    return RecoveryFunnel(
        scheduled=scheduled,
        executed=executed,
        recovered=recovered_count,
        recovered_amount=recovered_amount,
        recovery_rate_pct=rate,
    )

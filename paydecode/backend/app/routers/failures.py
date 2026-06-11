from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.db import get_db
from app.models.core import (
    Customer,
    DeclineCode,
    GatewayCodeMapping,
    Payment,
    PaymentFailure,
)
from app.schemas.analytics import (
    FailureItem,
    FailureListResponse,
    MappingCreate,
    UnmappedCode,
)

router = APIRouter(prefix="/api/failures", tags=["failures"])


@router.get("", response_model=FailureListResponse)
def list_failures(
    category: str | None = Query(default=None),
    code: str | None = Query(default=None),
    from_: str | None = Query(default=None, alias="from"),
    to_: str | None = Query(default=None, alias="to"),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    from datetime import datetime

    q = (
        db.query(PaymentFailure, Payment, Customer, DeclineCode)
        .join(Payment, PaymentFailure.payment_id == Payment.id)
        .outerjoin(Customer, Payment.customer_id == Customer.id)
        .join(DeclineCode, PaymentFailure.unified_code == DeclineCode.code)
    )

    if category:
        q = q.filter(DeclineCode.category == category)
    if code:
        q = q.filter(PaymentFailure.unified_code == code)
    if from_:
        try:
            dt = datetime.fromisoformat(from_)
        except ValueError:
            dt = datetime.strptime(from_, "%Y-%m-%d")
        q = q.filter(PaymentFailure.occurred_at >= dt)
    if to_:
        try:
            dt = datetime.fromisoformat(to_)
        except ValueError:
            dt = datetime.strptime(to_, "%Y-%m-%d")
        q = q.filter(PaymentFailure.occurred_at <= dt)

    total = q.count()
    rows = q.order_by(PaymentFailure.occurred_at.desc()).offset((page - 1) * page_size).limit(page_size).all()

    items = [
        FailureItem(
            id=f.id,
            payment_id=f.payment_id,
            external_payment_id=p.external_payment_id,
            gateway=p.gateway,
            amount=p.amount,
            currency=p.currency,
            customer_email=c.email if c else None,
            gateway_code=f.gateway_code,
            unified_code=f.unified_code,
            category=dc.category.value,
            occurred_at=f.occurred_at.isoformat(),
            attempt_number=f.attempt_number,
        )
        for f, p, c, dc in rows
    ]

    return FailureListResponse(items=items, total=total, page=page, page_size=page_size)


@router.get("/unmapped", response_model=list[UnmappedCode])
def list_unmapped(db: Session = Depends(get_db)):
    rows = (
        db.query(
            PaymentFailure.unified_code,
            PaymentFailure.gateway_code,
            Payment.gateway,
            func.count(PaymentFailure.id).label("occurrence_count"),
        )
        .join(Payment, PaymentFailure.payment_id == Payment.id)
        .filter(PaymentFailure.unified_code == "UNKNOWN")
        .group_by(PaymentFailure.gateway_code, Payment.gateway)
        .order_by(func.count(PaymentFailure.id).desc())
        .all()
    )
    return [
        UnmappedCode(
            gateway=r.gateway,
            gateway_code=r.gateway_code,
            occurrence_count=r.occurrence_count,
        )
        for r in rows
    ]


@router.post("/mappings")
def create_mapping(body: MappingCreate, db: Session = Depends(get_db)):
    existing = (
        db.query(GatewayCodeMapping)
        .filter_by(gateway=body.gateway, gateway_code=body.gateway_code)
        .first()
    )
    if existing:
        existing.unified_code = body.unified_code
    else:
        db.add(GatewayCodeMapping(**body.model_dump()))
    db.commit()

    # Re-normalize existing UNKNOWN failures for this mapping
    failures = (
        db.query(PaymentFailure)
        .join(Payment, PaymentFailure.payment_id == Payment.id)
        .filter(
            PaymentFailure.unified_code == "UNKNOWN",
            PaymentFailure.gateway_code == body.gateway_code,
            Payment.gateway == body.gateway,
        )
        .all()
    )
    for f in failures:
        f.unified_code = body.unified_code
    db.commit()

    return {"status": "ok", "updated_failures": len(failures)}

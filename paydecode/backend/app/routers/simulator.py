import json

import httpx
from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db import get_db
from app.services.ingestion import process_webhook
from app.services.signature import compute_signature
from app.simulator.generator import build_mock_payload, generate_bulk_payloads

router = APIRouter(prefix="/api/simulator", tags=["simulator"])


def _fire_payload(db: Session, payload: dict) -> dict:
    event_id = payload["event_id"]
    is_dup, unified_code = process_webhook(db, "mock", event_id, payload)
    return {"event_id": event_id, "duplicate": is_dup, "unified_code": unified_code}


@router.post("/generate")
def generate_traffic(
    count: int = Query(default=200, ge=1, le=5000),
    days: int = Query(default=30, ge=1, le=365),
    db: Session = Depends(get_db),
):
    payloads = generate_bulk_payloads(count, days)
    results = {"processed": 0, "duplicates": 0, "errors": 0}

    for payload in payloads:
        try:
            is_dup, _ = process_webhook(db, "mock", payload["event_id"], payload)
            if is_dup:
                results["duplicates"] += 1
            else:
                results["processed"] += 1
        except Exception:
            results["errors"] += 1

    return results


@router.post("/fire-one")
def fire_one(
    code: str = Query(default="insufficient_funds"),
    db: Session = Depends(get_db),
):
    payload = build_mock_payload(gateway_code=code)
    result = _fire_payload(db, payload)
    return result

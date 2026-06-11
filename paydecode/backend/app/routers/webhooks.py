import json

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from sqlalchemy.orm import Session

from app.db import get_db
from app.services.ingestion import process_webhook
from app.services.signature import verify_signature

router = APIRouter(prefix="/webhooks", tags=["webhooks"])


@router.post("/{gateway}")
async def receive_webhook(
    gateway: str,
    request: Request,
    x_webhook_signature: str | None = Header(default=None),
    db: Session = Depends(get_db),
):
    body = await request.body()

    if not verify_signature(body, x_webhook_signature):
        raise HTTPException(status_code=401, detail="Invalid signature")

    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid JSON payload")

    event_id = payload.get("event_id", payload.get("id", ""))
    if not event_id:
        raise HTTPException(status_code=400, detail="Missing event_id")

    is_dup, unified_code = process_webhook(db, gateway, event_id, payload)
    return {"status": "ok", "duplicate": is_dup, "unified_code": unified_code if not is_dup else None}

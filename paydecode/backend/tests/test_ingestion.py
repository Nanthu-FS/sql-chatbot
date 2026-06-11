import json
from app.services.signature import compute_signature


def _make_payload(code="insufficient_funds"):
    import uuid
    return {
        "event_id": str(uuid.uuid4()),
        "payment_id": f"pay_{uuid.uuid4().hex[:8]}",
        "amount": 99900,
        "currency": "INR",
        "error_code": code,
        "customer_id": "cust_test_001",
        "customer_email": "test@example.com",
        "created": 1700000000,
    }


def test_webhook_accepts_valid_payload(client):
    payload = _make_payload()
    body = json.dumps(payload).encode()
    sig = compute_signature(body)
    resp = client.post("/webhooks/mock", content=body, headers={"x-webhook-signature": sig})
    assert resp.status_code == 200
    data = resp.json()
    assert data["duplicate"] is False
    assert data["unified_code"] == "INSUFFICIENT_FUNDS"


def test_webhook_deduplicates(client):
    payload = _make_payload()
    body = json.dumps(payload).encode()
    sig = compute_signature(body)
    client.post("/webhooks/mock", content=body, headers={"x-webhook-signature": sig})
    resp2 = client.post("/webhooks/mock", content=body, headers={"x-webhook-signature": sig})
    assert resp2.status_code == 200
    assert resp2.json()["duplicate"] is True


def test_webhook_rejects_invalid_signature(client):
    payload = _make_payload()
    body = json.dumps(payload).encode()
    resp = client.post("/webhooks/mock", content=body, headers={"x-webhook-signature": "sha256=invalid"})
    assert resp.status_code == 401


def test_unknown_code_mapped_to_unknown(client):
    payload = _make_payload(code="totally_fake_code_xyz")
    body = json.dumps(payload).encode()
    sig = compute_signature(body)
    resp = client.post("/webhooks/mock", content=body, headers={"x-webhook-signature": sig})
    assert resp.status_code == 200
    assert resp.json()["unified_code"] == "UNKNOWN"

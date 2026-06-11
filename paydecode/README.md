# PayDecode — Payment Failure Analytics Platform

> "WHY did payments fail?" — not just "how many?"

A local-first portfolio project that ingests payment failure webhooks, normalizes gateway-specific decline codes into a unified taxonomy, categorizes them into actionable buckets, schedules smart retries, and visualizes lost-revenue-by-reason on a React dashboard.

## Architecture

```
Mock Gateway / Simulator
        │  POST /webhooks/{gateway}
        ▼
  Ingestion Layer  (HMAC verify → dedupe → persist raw event)
        │
        ▼
  Normalization   (gateway code → unified decline code)
        │
        ▼
  Categorization  (unified code → category + retry strategy)
        │          │
        ▼          ▼
  APScheduler   Analytics API (/api/analytics/*)
  Retry Engine        │
                      ▼
               React Dashboard (Vite + Tailwind + Recharts)
```

## Quick Start

### Backend

```bash
cd paydecode/backend
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

The database is seeded automatically on first start (decline taxonomy + gateway mappings).

### Frontend

```bash
cd paydecode/frontend
npm install
npm run dev        # http://localhost:5173
```

### Demo Script

1. Open http://localhost:5173 — Overview page shows empty state.
2. Click **Simulate Traffic** in the navbar → generates 200 realistic failure events spread over 30 days.
3. Dashboard auto-refreshes every 5s — charts and KPI cards populate.
4. Click **Failure Explorer** → filter by category, code, date range; click any row for raw event detail.
5. Click **Recovery** → retry funnel fills as APScheduler processes due retries (runs every 60s).
6. Click **Fire Event** to inject a single live event and watch the dashboard tick.
7. In **Settings / Mappings** → any `UNKNOWN` codes appear here; select one and map it to a unified code.

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/webhooks/{gateway}` | Ingest failure webhook (HMAC-signed) |
| GET | `/api/analytics/summary` | KPI totals |
| GET | `/api/analytics/by-category` | Failures + revenue by category |
| GET | `/api/analytics/by-code` | Failures + revenue by unified code |
| GET | `/api/analytics/timeseries?bucket=day\|week` | Time series stacked by category |
| GET | `/api/analytics/recovery` | Retry funnel stats |
| GET | `/api/failures` | Paginated failure list with filters |
| GET | `/api/failures/unmapped` | Gateway codes that resolved to UNKNOWN |
| POST | `/api/failures/mappings` | Add/update a gateway code mapping |
| POST | `/api/simulator/generate?count=200&days=30` | Bulk backfill via webhook pipeline |
| POST | `/api/simulator/fire-one?code=insufficient_funds` | Single live event |

All analytics endpoints support `?from=&to=` date filters (ISO 8601).

## Decline Code Taxonomy

| Category | Codes | Recoverable | Default Action |
|---|---|---|---|
| RECOVERABLE | INSUFFICIENT_FUNDS, EXPIRED_CARD, CARD_LIMIT_EXCEEDED | Yes | payday / prompt_customer / scheduled+24h |
| HARD_DECLINE | STOLEN_CARD, ACCOUNT_CLOSED, INVALID_CARD, DO_NOT_HONOR | No | Never retry |
| TECHNICAL | GATEWAY_TIMEOUT, API_ERROR, NETWORK_ERROR, PROCESSING_ERROR | Yes | Immediate (exp. backoff) |
| FRAUD_RISK | BANK_FRAUD_BLOCK, 3DS_FAILED, RISK_THRESHOLD | Partial | prompt_customer / none |
| CUSTOMER_ACTION | AUTHENTICATION_REQUIRED, CARD_NOT_SUPPORTED | Yes | prompt_customer |
| UNKNOWN | UNKNOWN | No | None — flag for manual mapping |

## Tech Stack

- **Backend:** Python 3.11, FastAPI, SQLAlchemy 2.0, Pydantic v2, APScheduler, SQLite
- **Frontend:** React 18, Vite, TypeScript, Tailwind CSS, Recharts, React Router

## Running Tests

```bash
cd paydecode/backend
pytest tests/ -v
```

19 tests covering normalization, categorization, webhook ingestion, deduplication, and signature verification.

## Environment Variables

Copy `.env.example` to `.env`:

```
DATABASE_URL=sqlite:///./paydecode.db
WEBHOOK_SECRET=dev-secret-key-change-in-prod
CORS_ORIGINS=http://localhost:5173
```

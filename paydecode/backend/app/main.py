from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.db import engine
from app.models.core import Base
from app.routers import analytics, failures, simulator, webhooks
from app.seed.seed import seed
from app.services.retry_engine import start_scheduler, stop_scheduler


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    seed()
    start_scheduler()
    yield
    stop_scheduler()


app = FastAPI(title="PayDecode", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins.split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(webhooks.router)
app.include_router(analytics.router)
app.include_router(failures.router)
app.include_router(simulator.router)


@app.get("/health")
def health():
    return {"status": "ok"}

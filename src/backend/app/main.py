import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.db import SessionLocal
from app.routes_auth import router as auth_router
from app.routes_reports import router as reports_router
from app.routes_trades import router as trades_router
from app.seed import seed_daily_trade_entries, seed_development_users, seed_sample_trades
from app.services import ConsoleEmailProvider, ScheduledJobs, configure_scheduler

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()
    if settings.seed_dev_users:
        with SessionLocal() as db:
            seed_development_users(db)
            seed_sample_trades(db)
            seed_daily_trade_entries(db, settings.development_business_date)
    scheduler = None
    if settings.enable_scheduler:
        scheduler = configure_scheduler(ScheduledJobs(ConsoleEmailProvider()))
        scheduler.start()
    yield
    if scheduler is not None:
        scheduler.shutdown(wait=False)


app = FastAPI(
    title="Trading Reporting API",
    version="0.1.0",
    description="MVP trade entry and reporting service.",
    lifespan=lifespan,
)
settings = get_settings()
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(auth_router)
app.include_router(trades_router)
app.include_router(reports_router)


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok"}

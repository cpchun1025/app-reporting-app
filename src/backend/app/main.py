import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

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
            seed_development_users(
                db,
                admin_password=settings.dev_admin_password,
                trader_password=settings.dev_trader_password,
            )
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


@app.get("/health/live", tags=["health"])
def health_live() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready", tags=["health"])
def health_ready() -> dict[str, str]:
    try:
        with SessionLocal() as db:
            db.execute(text("SELECT 1"))
            revision = db.execute(text("SELECT version_num FROM alembic_version")).scalar_one_or_none()
    except SQLAlchemyError as error:
        logging.getLogger(__name__).warning("Database readiness check failed: %s", type(error).__name__)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database is not ready.",
        ) from error
    if revision is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Database migrations are not ready.",
        )
    return {"status": "ok"}

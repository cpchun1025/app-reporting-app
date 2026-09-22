from datetime import date
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DailyTradeEntry, Trade, TradingBusiness, User
from app.security import hash_password

DEV_USERS = (
    ("dev_admin", "DevAdmin123!", True),
    ("dev_trader", "DevTrader123!", False),
)
DEV_BUSINESSES = (
    ("Rates", "RATES"), ("Credit", "CREDIT"), ("Equities", "EQUITY"),
    ("Commodities", "CMDTY"), ("FX", "FX"), ("Macro", "MACRO"),
    ("Volatility", "VOL"), ("Prime Services", "PRIME"), ("Energy", "ENERGY"),
    ("Metals", "METALS"), ("EMEA Credit", "EMEA"), ("APAC Rates", "APAC"),
)


def seed_development_users(db: Session) -> None:
    for username, password, is_admin in DEV_USERS:
        if db.scalar(select(User).where(User.username == username)) is None:
            db.add(
                User(
                    username=username,
                    password_hash=hash_password(password),
                    is_admin=is_admin,
                )
            )
    db.commit()


def seed_sample_trades(db: Session) -> None:
    if db.scalar(select(Trade.id).limit(1)) is None:
        trader = db.scalar(select(User).where(User.username == "dev_trader"))
        for index, (name, code) in enumerate(DEV_BUSINESSES):
            db.add(
                Trade(
                    trade_date=date(2026, 9, 22),
                    account=name,
                    instrument=code,
                    side="BUY",
                    quantity=Decimal(str(20 + index * 3.5)),
                    price=Decimal(str(100 + index * 2.25)),
                    currency="USD",
                    status="BOOKED",
                    created_by_id=trader.id,
                    locked=index in (0, 4),
                    locked_by_id=trader.id if index in (0, 4) else None,
                    locked_by_display_name="dev_trader" if index in (0, 4) else None,
                )
            )
        db.commit()


def seed_development_businesses(db: Session) -> None:
    existing_codes = set(db.scalars(select(TradingBusiness.code)))
    for name, code in DEV_BUSINESSES:
        if code not in existing_codes:
            db.add(TradingBusiness(name=name, code=code))
    db.commit()


def seed_daily_trade_entries(db: Session, business_date: date) -> None:
    """Create only missing seeded rows, so rerunning development setup never overwrites data."""
    seed_development_businesses(db)
    trader = db.scalar(select(User).where(User.username == "dev_trader"))
    if trader is None:
        raise RuntimeError("Development trader must exist before daily entries are seeded.")
    businesses = list(
        db.scalars(
            select(TradingBusiness)
            .where(TradingBusiness.is_active.is_(True))
            .order_by(TradingBusiness.code)
        )
    )
    existing_business_ids = set(
        db.scalars(
            select(DailyTradeEntry.business_id).where(
                DailyTradeEntry.business_date == business_date
            )
        )
    )
    for index, business in enumerate(businesses):
        if business.id not in existing_business_ids:
            db.add(
                DailyTradeEntry(
                    business_id=business.id,
                    business_date=business_date,
                    delta=Decimal(str(20 + index * 3.5)),
                    gamma=Decimal(str(index * 0.25)),
                    theta=Decimal(str(-(index + 1) * 0.2)),
                    vega=Decimal(str((index + 1) * 0.1)),
                    pnl=Decimal(str((20 + index * 3.5) * (100 + index * 2.25))),
                    created_by_id=trader.id,
                    updated_by_id=trader.id,
                )
            )
    db.commit()

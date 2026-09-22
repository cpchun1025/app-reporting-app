from datetime import date
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Trade, User
from app.security import hash_password

DEV_USERS = (
    ("dev_admin", "DevAdmin123!", True),
    ("dev_trader", "DevTrader123!", False),
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
        businesses = [
            ("Rates", "RATES"), ("Credit", "CREDIT"), ("Equities", "EQUITY"),
            ("Commodities", "CMDTY"), ("FX", "FX"), ("Macro", "MACRO"),
            ("Volatility", "VOL"), ("Prime Services", "PRIME"), ("Energy", "ENERGY"),
            ("Metals", "METALS"), ("EMEA Credit", "EMEA"), ("APAC Rates", "APAC"),
        ]
        trader = db.scalar(select(User).where(User.username == "dev_trader"))
        for index, (name, code) in enumerate(businesses):
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

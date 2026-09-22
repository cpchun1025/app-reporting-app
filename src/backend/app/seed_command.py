from app.config import get_settings
from app.db import SessionLocal
from app.seed import seed_daily_trade_entries, seed_development_users, seed_sample_trades


def main() -> None:
    settings = get_settings()
    if not settings.seed_dev_users:
        raise RuntimeError("Development seeding is disabled. Set SEED_DEV_USERS=true to run it.")

    with SessionLocal() as db:
        seed_development_users(
            db,
            admin_password=settings.dev_admin_password,
            trader_password=settings.dev_trader_password,
        )
        seed_sample_trades(db)
        seed_daily_trade_entries(db, settings.development_business_date)
    print("Development seed completed.")


if __name__ == "__main__":
    main()

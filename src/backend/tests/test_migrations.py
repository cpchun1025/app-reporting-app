import os
import sqlite3
import subprocess
import sys
from pathlib import Path


def run_alembic(repository_root: Path, database_url: str, *args: str) -> None:
    environment = os.environ | {"DATABASE_URL": database_url}
    subprocess.run(
        [sys.executable, "-m", "alembic", *args],
        cwd=repository_root,
        env=environment,
        check=True,
        capture_output=True,
        text=True,
    )


def test_daily_trade_migration_preserves_existing_trades(tmp_path: Path) -> None:
    repository_root = Path(__file__).resolve().parents[3]
    database_path = tmp_path / "migration.db"
    database_url = f"sqlite:///{database_path.as_posix()}"
    run_alembic(repository_root, database_url, "upgrade", "0001_initial")

    with sqlite3.connect(database_path) as connection:
        connection.execute(
            "INSERT INTO users (id, username, password_hash, is_active, is_admin) "
            "VALUES (?, ?, ?, ?, ?)",
            ("user-1", "legacy-user", "hash", 1, 0),
        )
        connection.execute(
            "INSERT INTO trades "
            "(id, trade_date, account, instrument, side, quantity, price, currency, status, version, created_by_id) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                "trade-1",
                "2026-09-22",
                "Legacy Account",
                "LEGACY",
                "BUY",
                "1",
                "100",
                "USD",
                "BOOKED",
                1,
                "user-1",
            ),
        )

    run_alembic(repository_root, database_url, "upgrade", "head")
    with sqlite3.connect(database_path) as connection:
        assert connection.execute("SELECT count(*) FROM trades").fetchone()[0] == 1
        assert connection.execute(
            "SELECT account, instrument FROM trades WHERE id = ?", ("trade-1",)
        ).fetchone() == ("Legacy Account", "LEGACY")
        assert connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'daily_trade_entries'"
        ).fetchone() == ("daily_trade_entries",)

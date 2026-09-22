"""Optional destructive integration coverage for an empty SQL Server database."""

import os
import subprocess
import sys
from pathlib import Path
from uuid import uuid4

import pytest
from sqlalchemy import URL, create_engine, text


pytestmark = pytest.mark.skipif(
    os.getenv("RUN_SQLSERVER_INTEGRATION") != "true",
    reason="Set RUN_SQLSERVER_INTEGRATION=true with SQL Server credentials to run destructive integration coverage.",
)


def test_empty_sql_server_database_can_migrate_and_seed() -> None:
    password = os.environ["MSSQL_SA_PASSWORD"]
    host = os.getenv("MSSQL_HOST", "localhost")
    port = int(os.getenv("MSSQL_PORT", "1433"))
    username = os.getenv("MSSQL_SA_USER", "sa")
    database_name = f"trading_reporting_test_{uuid4().hex[:12]}"
    master_url = URL.create(
        "mssql+pyodbc",
        username=username,
        password=password,
        host=host,
        port=port,
        database="master",
        query={
            "driver": "ODBC Driver 18 for SQL Server",
            "Encrypt": "yes",
            "TrustServerCertificate": os.getenv("MSSQL_TRUST_SERVER_CERTIFICATE", "true"),
        },
    )
    database_url = master_url.set(database=database_name).render_as_string(hide_password=False)
    engine = create_engine(master_url, isolation_level="AUTOCOMMIT")
    repository_root = Path(__file__).resolve().parents[3]
    environment = os.environ | {
        "DATABASE_URL": database_url,
        "SEED_DEV_USERS": "true",
    }

    try:
        with engine.connect() as connection:
            connection.execute(text(f"CREATE DATABASE [{database_name}]"))

        subprocess.run(
            [sys.executable, "-m", "alembic", "upgrade", "head"],
            cwd=repository_root,
            env=environment,
            check=True,
        )
        subprocess.run(
            [sys.executable, "-m", "app.seed_command"],
            cwd=repository_root,
            env=environment | {"PYTHONPATH": str(repository_root / "src" / "backend")},
            check=True,
        )
        subprocess.run(
            [sys.executable, "-m", "app.seed_command"],
            cwd=repository_root,
            env=environment | {"PYTHONPATH": str(repository_root / "src" / "backend")},
            check=True,
        )

        application_engine = create_engine(database_url)
        with application_engine.connect() as connection:
            tables = set(
                connection.execute(
                    text(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                        "WHERE TABLE_TYPE = 'BASE TABLE'"
                    )
                ).scalars()
            )
            assert {"users", "trading_businesses", "daily_trade_entries"} <= tables
            assert connection.execute(text("SELECT count(*) FROM users")).scalar_one() == 2
            assert connection.execute(text("SELECT count(*) FROM trading_businesses")).scalar_one() == 12
            assert connection.execute(text("SELECT count(*) FROM alembic_version")).scalar_one() == 1
    finally:
        with engine.connect() as connection:
            connection.execute(
                text(
                    "IF DB_ID(:database_name) IS NOT NULL "
                    "BEGIN "
                    "ALTER DATABASE " + f"[{database_name}]" + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                    "DROP DATABASE " + f"[{database_name}]; "
                    "END"
                ),
                {"database_name": database_name},
            )

from datetime import date
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import URL


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str | None = None
    mssql_host: str | None = None
    mssql_port: int = 1433
    mssql_sa_user: str = "sa"
    mssql_sa_password: str | None = None
    app_database_name: str = "trading_reporting"
    mssql_encrypt: bool = True
    mssql_trust_server_certificate: bool = False
    jwt_secret: str = "local-development-secret-change-me"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 60
    seed_dev_users: bool = True
    dev_admin_password: str | None = None
    dev_trader_password: str | None = None
    enable_scheduler: bool = False
    cors_origins: str = "http://localhost:5173"
    trade_save_copy_path: str = "./trade-entry-copies"
    development_business_date: date = date(2026, 9, 22)

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def sqlalchemy_database_url(self) -> str:
        if self.database_url:
            return self.database_url
        if self.mssql_host and self.mssql_sa_password:
            return URL.create(
                "mssql+pyodbc",
                username=self.mssql_sa_user,
                password=self.mssql_sa_password,
                host=self.mssql_host,
                port=self.mssql_port,
                database=self.app_database_name,
                query={
                    "driver": "ODBC Driver 18 for SQL Server",
                    "Encrypt": "yes" if self.mssql_encrypt else "no",
                    "TrustServerCertificate": "yes"
                    if self.mssql_trust_server_certificate
                    else "no",
                },
            ).render_as_string(hide_password=False)
        return "sqlite:///./trading.db"


@lru_cache
def get_settings() -> Settings:
    return Settings()

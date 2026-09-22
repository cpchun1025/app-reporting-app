from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class TokenRequest(BaseModel):
    username: str = Field(min_length=1, max_length=100)
    password: str = Field(min_length=1, max_length=256)


class MockCallbackRequest(BaseModel):
    username: str = Field(min_length=1, max_length=100)


class TokenResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"


class CurrentUserResponse(BaseModel):
    username: str
    role: Literal["admin", "trader"]


class TradeCreate(BaseModel):
    trade_date: date
    account: str = Field(min_length=1, max_length=100)
    instrument: str = Field(min_length=1, max_length=100)
    side: Literal["BUY", "SELL"]
    quantity: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    price: Decimal = Field(gt=0, max_digits=18, decimal_places=4)
    currency: str = Field(default="USD", min_length=3, max_length=3)
    status: str = Field(default="BOOKED", min_length=1, max_length=20)
    notes: str | None = Field(default=None, max_length=4000)

    @field_validator("currency")
    @classmethod
    def normalize_currency(cls, value: str) -> str:
        return value.upper()


class TradeUpdate(TradeCreate):
    expected_version: int = Field(ge=1)


class TradeResponse(TradeCreate):
    model_config = ConfigDict(from_attributes=True)

    id: str
    version: int
    created_by_id: str
    created_at: datetime
    updated_at: datetime | None
    locked: bool
    locked_by_id: str | None
    locked_by_display_name: str | None
    locked_at: datetime | None
    lock_expires_at: datetime | None


class TradeEntryRow(BaseModel):
    id: str | None = None
    name: str = Field(min_length=1, max_length=100)
    code: str = Field(min_length=1, max_length=30)
    values: list[Decimal] = Field(min_length=1, max_length=50)
    locked: bool = False
    version: int = Field(default=1, ge=1)


class TradeEntrySaveRequest(BaseModel):
    business_date: date
    rows: list[TradeEntryRow] = Field(min_length=1, max_length=500)


class TradeEntrySaveResponse(BaseModel):
    saved_at: datetime
    snapshot_filename: str
    row_count: int


class LockResponse(BaseModel):
    id: str
    locked: bool
    locked_by_id: str | None
    locked_by_display_name: str | None
    locked_at: datetime | None
    lock_expires_at: datetime | None
    version: int


class ReportRow(BaseModel):
    period: str
    trade_count: int
    buy_quantity: Decimal
    sell_quantity: Decimal
    net_quantity: Decimal
    gross_notional: Decimal


class ConsolidatedReport(BaseModel):
    start_date: date
    end_date: date
    rows: list[ReportRow]

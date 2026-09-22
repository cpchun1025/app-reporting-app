from collections import defaultdict
from datetime import date
from decimal import Decimal
from typing import Literal

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import select

from app.dependencies import CurrentUser, DbSession
from app.models import Trade
from app.schemas import ConsolidatedReport, ReportRow

router = APIRouter(prefix="/reports", tags=["reports"])


def validate_date_range(start_date: date, end_date: date) -> None:
    if end_date < start_date:
        raise HTTPException(status_code=422, detail="end_date must be on or after start_date.")


def build_report(
    db: DbSession, start_date: date, end_date: date, period: Literal["day", "month", "year", "account"]
) -> list[ReportRow]:
    validate_date_range(start_date, end_date)
    trades = db.scalars(
        select(Trade).where(Trade.trade_date >= start_date, Trade.trade_date <= end_date)
    ).all()
    totals: dict[str, dict[str, Decimal | int]] = defaultdict(
        lambda: {
            "trade_count": 0,
            "buy_quantity": Decimal("0"),
            "sell_quantity": Decimal("0"),
            "gross_notional": Decimal("0"),
        }
    )
    for trade in trades:
        if period == "day":
            key = trade.trade_date.isoformat()
        elif period == "month":
            key = trade.trade_date.strftime("%Y-%m")
        elif period == "year":
            key = str(trade.trade_date.year)
        else:
            key = trade.account
        total = totals[key]
        total["trade_count"] += 1
        total["gross_notional"] += trade.quantity * trade.price
        quantity_key = "buy_quantity" if trade.side == "BUY" else "sell_quantity"
        total[quantity_key] += trade.quantity
    return [
        ReportRow(
            period=key,
            trade_count=total["trade_count"],
            buy_quantity=total["buy_quantity"],
            sell_quantity=total["sell_quantity"],
            net_quantity=total["buy_quantity"] - total["sell_quantity"],
            gross_notional=total["gross_notional"],
        )
        for key, total in sorted(totals.items())
    ]


@router.get("/daily", response_model=list[ReportRow])
def daily_report(
    start_date: date, end_date: date, _: CurrentUser, db: DbSession
) -> list[ReportRow]:
    return build_report(db, start_date, end_date, "day")


@router.get("/monthly", response_model=list[ReportRow])
def monthly_report(
    start_date: date, end_date: date, _: CurrentUser, db: DbSession
) -> list[ReportRow]:
    return build_report(db, start_date, end_date, "month")


@router.get("/annual", response_model=list[ReportRow])
def annual_report(
    start_date: date, end_date: date, _: CurrentUser, db: DbSession
) -> list[ReportRow]:
    return build_report(db, start_date, end_date, "year")


@router.get("/consolidated", response_model=ConsolidatedReport)
def consolidated_report(
    start_date: date,
    end_date: date,
    _: CurrentUser,
    db: DbSession,
    group_by: Literal["account"] = Query(default="account"),
) -> ConsolidatedReport:
    return ConsolidatedReport(
        start_date=start_date,
        end_date=end_date,
        rows=build_report(db, start_date, end_date, group_by),
    )

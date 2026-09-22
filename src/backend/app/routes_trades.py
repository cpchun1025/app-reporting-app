import json
import os
import tempfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload
from sqlalchemy.orm.exc import StaleDataError

from app.dependencies import CurrentUser, DbSession
from app.config import get_settings
from app.models import DailyTradeEntry, Trade, TradeEntrySnapshot, TradingBusiness
from app.schemas import (
    DailyTradeEntryResponse,
    DailyTradeEntryListResponse,
    DailyTradeEntrySaveRequest,
    DailyTradeEntrySaveResponse,
    LockResponse,
    TradeCreate,
    TradeEntrySaveRequest,
    TradeEntrySaveResponse,
    TradeResponse,
    TradeUpdate,
)

router = APIRouter(prefix="/trades", tags=["trades"])


def locked_trade_or_404(db: Session, trade_id: str) -> Trade:
    trade = db.scalar(select(Trade).where(Trade.id == trade_id).with_for_update())
    if trade is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trade was not found.")
    return trade


def locked_daily_trade_entry_or_404(db: Session, entry_id: str) -> DailyTradeEntry:
    entry = db.scalar(
        select(DailyTradeEntry)
        .where(DailyTradeEntry.id == entry_id)
        .options(joinedload(DailyTradeEntry.business))
        .with_for_update()
    )
    if entry is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Daily trade entry was not found.")
    return entry


def require_expected_version(trade: Trade | DailyTradeEntry, expected_version: int) -> None:
    if trade.version != expected_version:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Trade version conflict: expected {expected_version}, current version is {trade.version}.",
        )


def ensure_unlocked(trade: Trade | DailyTradeEntry, current_user: CurrentUser) -> None:
    now = datetime.now(timezone.utc)
    expires_at = trade.lock_expires_at
    if expires_at is not None and expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if trade.locked and (expires_at is None or expires_at > now) and trade.locked_by_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Trade is locked by another user. Refresh and try again.",
        )
    if trade.locked and expires_at is not None and expires_at <= now:
        trade.locked = False
        trade.locked_by_id = None
        trade.locked_by_display_name = None
        trade.locked_at = None
        trade.lock_expires_at = None


def daily_trade_entry_response(entry: DailyTradeEntry) -> DailyTradeEntryResponse:
    return DailyTradeEntryResponse(
        id=entry.id,
        business_id=entry.business_id,
        business_date=entry.business_date,
        name=entry.business.name,
        code=entry.business.code,
        delta=entry.delta,
        gamma=entry.gamma,
        theta=entry.theta,
        vega=entry.vega,
        pnl=entry.pnl,
        version=entry.version,
        locked=entry.locked,
        locked_by_id=entry.locked_by_id,
        locked_by_display_name=entry.locked_by_display_name,
        locked_at=entry.locked_at,
        lock_expires_at=entry.lock_expires_at,
        updated_at=entry.updated_at,
    )


def write_backup_copy(business_date: date, data: dict) -> str:
    output_dir = Path(get_settings().trade_save_copy_path).expanduser()
    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    filename = f"trade-entry-{stamp}.json"
    target = output_dir / filename
    fd, temporary = tempfile.mkstemp(prefix=".trade-entry-", suffix=".tmp", dir=output_dir)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump({"business_date": business_date.isoformat(), "saved_data": data}, handle, indent=2)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return filename


def list_daily_trade_entries(
    db: Session, business_date: date, current_user: CurrentUser
) -> list[DailyTradeEntry]:
    businesses = list(
        db.scalars(
            select(TradingBusiness)
            .where(TradingBusiness.is_active.is_(True))
            .order_by(TradingBusiness.code)
        )
    )
    existing_ids = set(
        db.scalars(
            select(DailyTradeEntry.business_id).where(DailyTradeEntry.business_date == business_date)
        )
    )
    if businesses and any(business.id not in existing_ids for business in businesses):
        for business in businesses:
            if business.id not in existing_ids:
                db.add(
                    DailyTradeEntry(
                        business_id=business.id,
                        business_date=business_date,
                        created_by_id=current_user.id,
                        updated_by_id=current_user.id,
                    )
                )
        try:
            db.commit()
        except IntegrityError:
            # Another request initialized the same date first; reload its complete result.
            db.rollback()
    return list(
        db.scalars(
            select(DailyTradeEntry)
            .where(DailyTradeEntry.business_date == business_date)
            .options(joinedload(DailyTradeEntry.business))
            .join(DailyTradeEntry.business)
            .order_by(TradingBusiness.code)
        )
    )


@router.post("", response_model=TradeResponse, status_code=status.HTTP_201_CREATED)
def create_trade(payload: TradeCreate, current_user: CurrentUser, db: DbSession) -> Trade:
    trade = Trade(**payload.model_dump(), created_by_id=current_user.id)
    db.add(trade)
    db.commit()
    db.refresh(trade)
    return trade


@router.get("/entry", response_model=DailyTradeEntryListResponse)
def get_daily_trade_entries(
    business_date: date, current_user: CurrentUser, db: DbSession
) -> DailyTradeEntryListResponse:
    rows = list_daily_trade_entries(db, business_date, current_user)
    return DailyTradeEntryListResponse(
        business_date=business_date,
        rows=[daily_trade_entry_response(entry) for entry in rows],
    )


@router.post("/entry/{entry_id}/lock", response_model=LockResponse)
def lock_daily_trade_entry(
    entry_id: str, current_user: CurrentUser, db: DbSession
) -> DailyTradeEntry:
    entry = locked_daily_trade_entry_or_404(db, entry_id)
    ensure_unlocked(entry, current_user)
    entry.locked = True
    entry.locked_by_id = current_user.id
    entry.locked_by_display_name = current_user.username
    entry.locked_at = datetime.now(timezone.utc)
    entry.lock_expires_at = datetime.now(timezone.utc) + timedelta(hours=8)
    entry.version += 1
    db.commit()
    db.refresh(entry)
    return entry


@router.post("/entry/{entry_id}/unlock", response_model=LockResponse)
def unlock_daily_trade_entry(
    entry_id: str, current_user: CurrentUser, db: DbSession
) -> DailyTradeEntry:
    entry = locked_daily_trade_entry_or_404(db, entry_id)
    if entry.locked and entry.locked_by_id != current_user.id and not current_user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the lock owner or an administrator can unlock this daily trade entry.",
        )
    entry.locked = False
    entry.locked_by_id = None
    entry.locked_by_display_name = None
    entry.locked_at = None
    entry.lock_expires_at = None
    entry.version += 1
    db.commit()
    db.refresh(entry)
    return entry


@router.get("/{trade_id}", response_model=TradeResponse)
def get_trade(trade_id: str, _: CurrentUser, db: DbSession) -> Trade:
    trade = db.get(Trade, trade_id)
    if trade is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Trade was not found.")
    return trade


@router.put("/{trade_id}", response_model=TradeResponse)
def update_trade(trade_id: str, payload: TradeUpdate, current_user: CurrentUser, db: DbSession) -> Trade:
    trade = locked_trade_or_404(db, trade_id)
    ensure_unlocked(trade, current_user)
    require_expected_version(trade, payload.expected_version)
    for field, value in payload.model_dump(exclude={"expected_version"}).items():
        setattr(trade, field, value)
    try:
        db.commit()
    except StaleDataError as error:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Trade was updated by another request. Reload and try again.",
        ) from error
    db.refresh(trade)
    return trade


@router.get("", response_model=list[TradeResponse] | list[DailyTradeEntryResponse])
def list_trades(
    current_user: CurrentUser, db: DbSession, business_date: date | None = None
) -> list[Trade] | list[DailyTradeEntryResponse]:
    if business_date is not None:
        return [
            daily_trade_entry_response(entry)
            for entry in list_daily_trade_entries(db, business_date, current_user)
        ]
    return list(db.scalars(select(Trade).order_by(Trade.trade_date, Trade.account, Trade.id)))


@router.post("/{trade_id}/lock", response_model=LockResponse)
def lock_trade(
    trade_id: str, current_user: CurrentUser, db: DbSession
) -> Trade | DailyTradeEntry:
    trade = db.scalar(
        select(DailyTradeEntry).where(DailyTradeEntry.id == trade_id).with_for_update()
    )
    if trade is None:
        trade = locked_trade_or_404(db, trade_id)
    ensure_unlocked(trade, current_user)
    trade.locked = True
    trade.locked_by_id = current_user.id
    trade.locked_by_display_name = current_user.username
    trade.locked_at = datetime.now(timezone.utc)
    trade.lock_expires_at = datetime.now(timezone.utc) + timedelta(hours=8)
    trade.version += 1
    db.commit()
    db.refresh(trade)
    return trade


@router.post("/{trade_id}/unlock", response_model=LockResponse)
def unlock_trade(
    trade_id: str, current_user: CurrentUser, db: DbSession
) -> Trade | DailyTradeEntry:
    trade = db.scalar(
        select(DailyTradeEntry).where(DailyTradeEntry.id == trade_id).with_for_update()
    )
    if trade is None:
        trade = locked_trade_or_404(db, trade_id)
    if trade.locked and trade.locked_by_id != current_user.id and not current_user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Only the lock owner or an administrator can unlock this trade.")
    trade.locked = False
    trade.locked_by_id = None
    trade.locked_by_display_name = None
    trade.locked_at = None
    trade.lock_expires_at = None
    trade.version += 1
    db.commit()
    db.refresh(trade)
    return trade


@router.post(
    "/entry/save", response_model=TradeEntrySaveResponse | DailyTradeEntrySaveResponse
)
def save_trade_entry(
    payload: TradeEntrySaveRequest | DailyTradeEntrySaveRequest,
    current_user: CurrentUser,
    db: DbSession,
) -> TradeEntrySaveResponse | DailyTradeEntrySaveResponse:
    if isinstance(payload, DailyTradeEntrySaveRequest):
        return save_daily_trade_entries(payload, current_user, db)
    serialized = json.dumps(payload.model_dump(mode="json"), separators=(",", ":"), sort_keys=True)
    snapshot = TradeEntrySnapshot(
        business_date=payload.business_date,
        data_json=serialized,
        saved_by_id=current_user.id,
    )
    db.add(snapshot)
    try:
        db.flush()
        filename = write_backup_copy(payload.business_date, payload.model_dump(mode="json"))
        db.commit()
    except (OSError, ValueError) as error:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Trade data was not saved because the server backup copy could not be written.") from error
    return TradeEntrySaveResponse(saved_at=datetime.now(timezone.utc), snapshot_filename=filename, row_count=len(payload.rows))


def save_daily_trade_entries(
    payload: DailyTradeEntrySaveRequest, current_user: CurrentUser, db: Session
) -> DailyTradeEntrySaveResponse:
    entry_ids = [row.id for row in payload.rows]
    if len(set(entry_ids)) != len(entry_ids):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Each daily trade entry may be supplied only once.",
        )

    entries: list[DailyTradeEntry] = []
    try:
        for row in payload.rows:
            entry = locked_daily_trade_entry_or_404(db, row.id)
            if entry.business_date != payload.business_date:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Daily trade entry does not belong to the requested business date.",
                )
            ensure_unlocked(entry, current_user)
            require_expected_version(entry, row.expected_version)
            entry.delta = row.delta
            entry.gamma = row.gamma
            entry.theta = row.theta
            entry.vega = row.vega
            entry.pnl = row.pnl
            entry.updated_by_id = current_user.id
            entries.append(entry)
        db.add(
            TradeEntrySnapshot(
                business_date=payload.business_date,
                data_json=json.dumps(payload.model_dump(mode="json"), separators=(",", ":"), sort_keys=True),
                saved_by_id=current_user.id,
            )
        )
        write_backup_copy(payload.business_date, payload.model_dump(mode="json"))
        db.commit()
    except StaleDataError as error:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A daily trade entry was updated by another request. Reload and try again.",
        ) from error
    except HTTPException:
        db.rollback()
        raise
    except OSError as error:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Trade data was not saved because the server backup copy could not be written.",
        ) from error

    for entry in entries:
        db.refresh(entry)
        db.refresh(entry, attribute_names=["business"])
    return DailyTradeEntrySaveResponse(
        saved_at=datetime.now(timezone.utc),
        business_date=payload.business_date,
        rows=[daily_trade_entry_response(entry) for entry in entries],
    )


@router.delete("/{trade_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_trade(
    trade_id: str, expected_version: int, _: CurrentUser, db: DbSession
) -> Response:
    trade = locked_trade_or_404(db, trade_id)
    require_expected_version(trade, expected_version)
    try:
        db.delete(trade)
        db.commit()
    except StaleDataError as error:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Trade was updated by another request. Reload and try again.",
        ) from error
    return Response(status_code=status.HTTP_204_NO_CONTENT)

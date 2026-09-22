from datetime import date

from fastapi.testclient import TestClient
from sqlalchemy import func, select

from app.models import DailyTradeEntry, TradingBusiness
from app.seed import DEV_BUSINESSES, seed_daily_trade_entries, seed_development_businesses


def daily_rows(
    client: TestClient, headers: dict[str, str], business_date: str = "2026-09-22"
) -> list[dict[str, object]]:
    response = client.get(f"/trades?business_date={business_date}", headers=headers)
    assert response.status_code == 200
    return response.json()


def explicit_daily_row(entry: dict[str, object], version: int | None = None) -> dict[str, str | int]:
    return {
        "id": str(entry["id"]),
        "expected_version": entry["version"] if version is None else version,
        "delta": "1.2500",
        "gamma": "-2.5000",
        "theta": "3.7500",
        "vega": "-4.1250",
        "pnl": "500.0000",
    }


def test_daily_entries_initialize_per_date_and_do_not_affect_legacy_trades(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    first_date = daily_rows(client, auth_headers)
    second_date = daily_rows(client, auth_headers, "2026-09-23")

    assert len(first_date) == len(second_date) == 12
    assert {row["id"] for row in first_date}.isdisjoint({row["id"] for row in second_date})
    assert {row["code"] for row in first_date} == {row["code"] for row in second_date}
    assert all(row["delta"] == "0.0000" and row["pnl"] == "0.0000" for row in first_date)
    assert client.get("/trades", headers=auth_headers).json() == []


def test_dedicated_daily_entry_routes_return_and_lock_date_scoped_rows(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    response = client.get("/trades/entry?business_date=2026-09-22", headers=auth_headers)
    assert response.status_code == 200
    payload = response.json()
    assert payload["business_date"] == "2026-09-22"
    assert len(payload["rows"]) == 12

    entry = payload["rows"][0]
    locked = client.post(f"/trades/entry/{entry['id']}/lock", headers=auth_headers)
    assert locked.status_code == 200
    assert locked.json()["locked"] is True
    assert locked.json()["version"] == 2

    unlocked = client.post(f"/trades/entry/{entry['id']}/unlock", headers=auth_headers)
    assert unlocked.status_code == 200
    assert unlocked.json()["locked"] is False
    assert unlocked.json()["version"] == 3


def test_development_seed_is_idempotent_and_preserves_existing_entries(db_session) -> None:
    business_date = date(2026, 9, 22)
    seed_development_businesses(db_session)
    seed_daily_trade_entries(db_session, business_date)
    entry = db_session.scalar(
        select(DailyTradeEntry)
        .where(DailyTradeEntry.business_date == business_date)
        .order_by(DailyTradeEntry.id)
    )
    assert entry is not None
    entry.pnl = 999
    db_session.commit()

    seed_development_businesses(db_session)
    seed_daily_trade_entries(db_session, business_date)

    assert db_session.scalar(select(func.count()).select_from(TradingBusiness)) == len(DEV_BUSINESSES)
    assert (
        db_session.scalar(
            select(func.count())
            .select_from(DailyTradeEntry)
            .where(DailyTradeEntry.business_date == business_date)
        )
        == len(DEV_BUSINESSES)
    )
    assert db_session.get(DailyTradeEntry, entry.id).pnl == 999


def test_daily_save_updates_all_explicit_metrics_and_creates_a_snapshot(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    entry = daily_rows(client, auth_headers)[0]
    response = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [explicit_daily_row(entry)]},
        headers=auth_headers,
    )

    assert response.status_code == 200
    saved = response.json()["rows"][0]
    assert {
        metric: saved[metric] for metric in ("delta", "gamma", "theta", "vega", "pnl")
    } == {
        "delta": "1.2500",
        "gamma": "-2.5000",
        "theta": "3.7500",
        "vega": "-4.1250",
        "pnl": "500.0000",
    }
    assert saved["version"] == 2
    reloaded = {row["id"]: row for row in daily_rows(client, auth_headers)}
    assert reloaded[saved["id"]]["pnl"] == "500.0000"


def test_daily_save_rejects_invalid_or_incomplete_metrics(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    entry = daily_rows(client, auth_headers)[0]
    invalid = explicit_daily_row(entry)
    invalid["delta"] = "NaN"
    response = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [invalid]},
        headers=auth_headers,
    )
    assert response.status_code == 422

    incomplete = explicit_daily_row(entry)
    del incomplete["pnl"]
    response = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [incomplete]},
        headers=auth_headers,
    )
    assert response.status_code == 422


def test_daily_save_is_atomic_for_date_version_and_locks(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    entries = daily_rows(client, auth_headers)
    first, second = entries[:2]
    locked = client.post(f"/trades/{first['id']}/lock", headers=auth_headers)
    assert locked.status_code == 200
    assert locked.json()["locked"] is True

    admin_login = client.post(
        "/auth/login", json={"username": "dev_admin", "password": "DevAdmin123!"}
    )
    admin_headers = {"Authorization": f"Bearer {admin_login.json()['access_token']}"}
    blocked = client.post(
        "/trades/entry/save",
        json={
            "business_date": "2026-09-22",
            "rows": [explicit_daily_row(first), explicit_daily_row(second)],
        },
        headers=admin_headers,
    )
    assert blocked.status_code == 409
    assert daily_rows(client, auth_headers)[1]["pnl"] == "0.0000"

    unlocked = client.post(f"/trades/{first['id']}/unlock", headers=auth_headers)
    assert unlocked.status_code == 200
    fresh = daily_rows(client, auth_headers)
    saved = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [explicit_daily_row(fresh[0])]},
        headers=auth_headers,
    )
    assert saved.status_code == 200
    stale = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [explicit_daily_row(fresh[0])]},
        headers=auth_headers,
    )
    assert stale.status_code == 409


def test_daily_save_rejects_a_row_from_another_business_date(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    other_date_entry = daily_rows(client, auth_headers, "2026-09-23")[0]
    response = client.post(
        "/trades/entry/save",
        json={
            "business_date": "2026-09-22",
            "rows": [explicit_daily_row(other_date_entry)],
        },
        headers=auth_headers,
    )
    assert response.status_code == 409

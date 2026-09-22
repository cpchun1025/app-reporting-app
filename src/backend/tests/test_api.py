from datetime import date

from fastapi.testclient import TestClient


def trade_payload(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "trade_date": "2026-09-22",
        "account": "ALPHA",
        "instrument": "MSFT",
        "side": "BUY",
        "quantity": "10",
        "price": "100.50",
        "currency": "usd",
        "status": "BOOKED",
    }
    payload.update(overrides)
    return payload


def test_login_rejects_bad_credentials(client: TestClient) -> None:
    response = client.post("/auth/login", json={"username": "dev_trader", "password": "wrong"})
    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid username or password."


def test_trade_entry_requires_authentication(client: TestClient) -> None:
    response = client.post("/trades", json=trade_payload())
    assert response.status_code == 401
    assert response.json()["detail"] == "Bearer access token is required."


def test_current_user_and_cors_integration(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    current_user = client.get("/auth/me", headers=auth_headers)
    assert current_user.status_code == 200
    assert current_user.json() == {"username": "dev_trader", "role": "trader"}

    preflight = client.options(
        "/auth/me",
        headers={
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert preflight.status_code == 200
    assert preflight.headers["access-control-allow-origin"] == "http://localhost:5173"


def test_create_update_and_version_conflict(client: TestClient, auth_headers: dict[str, str]) -> None:
    created = client.post("/trades", json=trade_payload(), headers=auth_headers)
    assert created.status_code == 201
    trade = created.json()
    assert trade["currency"] == "USD"
    assert trade["version"] == 1

    updated = client.put(
        f"/trades/{trade['id']}",
        json=trade_payload(price="110.25", expected_version=1),
        headers=auth_headers,
    )
    assert updated.status_code == 200
    assert updated.json()["version"] == 2

    conflict = client.put(
        f"/trades/{trade['id']}",
        json=trade_payload(price="111.00", expected_version=1),
        headers=auth_headers,
    )
    assert conflict.status_code == 409
    assert "current version is 2" in conflict.json()["detail"]


def test_reports_aggregate_by_period(client: TestClient, auth_headers: dict[str, str]) -> None:
    assert client.post("/trades", json=trade_payload(), headers=auth_headers).status_code == 201
    assert (
        client.post(
            "/trades",
            json=trade_payload(trade_date="2026-09-23", side="SELL", quantity="3", price="200"),
            headers=auth_headers,
        ).status_code
        == 201
    )
    response = client.get(
        "/reports/daily?start_date=2026-09-22&end_date=2026-09-23", headers=auth_headers
    )
    assert response.status_code == 200
    rows = response.json()
    assert [row["period"] for row in rows] == ["2026-09-22", "2026-09-23"]
    assert rows[0]["gross_notional"] == "1005.00000000"
    assert rows[1]["net_quantity"] == "-3.0000"


def test_reports_reject_invalid_date_range(client: TestClient, auth_headers: dict[str, str]) -> None:
    response = client.get(
        f"/reports/annual?start_date={date(2026, 12, 31)}&end_date={date(2026, 1, 1)}",
        headers=auth_headers,
    )
    assert response.status_code == 422
    assert response.json()["detail"] == "end_date must be on or after start_date."


def test_trade_entry_save_creates_server_copy(client: TestClient, auth_headers: dict[str, str], tmp_path, monkeypatch) -> None:
    monkeypatch.setenv("TRADE_SAVE_COPY_PATH", str(tmp_path / "copies"))
    from app.config import get_settings

    get_settings.cache_clear()
    response = client.post(
        "/trades/entry/save",
        json={"business_date": "2026-09-22", "rows": [{"name": "Rates", "code": "RATES", "values": ["1.25", "-2"]}]},
        headers=auth_headers,
    )
    assert response.status_code == 200
    copies = list((tmp_path / "copies").glob("trade-entry-*.json"))
    assert len(copies) == 1
    assert copies[0].read_text(encoding="utf-8").find('"business_date": "2026-09-22"') >= 0
    get_settings.cache_clear()


def test_locked_trade_rejects_other_user_and_allows_owner_unlock(
    client: TestClient, auth_headers: dict[str, str]
) -> None:
    created = client.post("/trades", json=trade_payload(), headers=auth_headers).json()
    locked = client.post(f"/trades/{created['id']}/lock", headers=auth_headers)
    assert locked.status_code == 200
    admin_login = client.post("/auth/login", json={"username": "dev_admin", "password": "DevAdmin123!"})
    admin_headers = {"Authorization": f"Bearer {admin_login.json()['access_token']}"}
    update = client.put(
        f"/trades/{created['id']}",
        json=trade_payload(price="110.25", expected_version=locked.json()["version"]),
        headers=admin_headers,
    )
    assert update.status_code == 409
    assert client.post(f"/trades/{created['id']}/unlock", headers=auth_headers).status_code == 200

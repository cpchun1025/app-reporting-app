# Trading Reporting Backend MVP

The backend lives in [`src/backend`](./src/backend) and provides local development
authentication, protected trade entry, concurrency-safe mutations, reporting
summaries, a console email provider, and scheduled-job scaffolding.

## Local setup

1. Create a virtual environment and install test dependencies:
   ```powershell
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   python -m pip install -r src\backend\requirements.txt
   ```
2. Copy `.env.example` to `.env`. For a SQLite-only local smoke test, set
   `DATABASE_URL=sqlite:///./trading.db`.
3. Apply schema migrations:
   ```powershell
   python -m alembic upgrade head
   ```
4. Start the API:
   ```powershell
   python -m uvicorn app.main:app --app-dir src/backend --reload
   ```

Seeded development users are `dev_admin` / `DevAdmin123!` and
`dev_trader` / `DevTrader123!`. They are intentionally only seeded when
`SEED_DEV_USERS=true`; replace or disable them outside local development.

## Docker and SQL Server

Copy `.env.example` to `.env`, optionally set `MSSQL_SA_PASSWORD`, then run:

```powershell
docker compose up --build
```

The backend container runs `alembic upgrade head` before serving on port 8000.
The SQL Server connection uses the Microsoft ODBC Driver 18 and is compatible
with SQL Server row locking (`UPDLOCK` emitted by SQLAlchemy's `with_for_update`).
Docker Compose also starts the frontend on port 5173. Its default API URL is
`http://localhost:8000`; this origin is allowed by the backend's configurable
`CORS_ORIGINS` setting.

The frontend is an independent Vite application under `src/frontend`. Run
`npm install; npm run dev` there for local development, or use the `frontend`
service in Docker Compose. It provides the dark trading-workstation interface
with trade entry, daily, monthly, annual, and consolidated views. Trade Entry
uses the browser's local calendar date and loads date-scoped rows from the API;
it does not write files directly from the browser. Set `VITE_API_URL` when
wiring a deployed backend.

## API summary

- `POST /auth/login` returns a bearer access token.
- `GET /auth/me` returns the authenticated username and `admin` or `trader` role;
  it supports the existing frontend client.
- `POST /auth/mock/callback` is a development callback that exchanges a seeded
  local user name for a bearer token.
- `POST /trades`, `GET /trades/{id}`, `PUT /trades/{id}`, and
  `DELETE /trades/{id}` require a bearer token.
- `GET /trades?business_date=YYYY-MM-DD` returns a date-scoped row for each
  active trading business, initializing missing daily rows safely with zero
  metrics. Each row exposes explicit `delta`, `gamma`, `theta`, `vega`, and
  `pnl` values.
- `GET /trades/entry?business_date=YYYY-MM-DD` returns the same date-scoped
  rows in a response wrapper for clients that need the business date echoed.
- `POST /trades/entry/save` accepts an atomic daily-entry payload containing
  `business_date` and rows with an `id`, `expected_version`, and all five
  explicit metrics. Rows locked by another user or stale versions receive
  `409 Conflict`; `POST /trades/{id}/lock` and `/unlock` work for both legacy
  trades and date-scoped daily rows.
- Updates and deletes require `expected_version`; conflicting versions receive
  `409 Conflict`.
- `GET /reports/daily`, `/reports/monthly`, `/reports/annual`, and
  `/reports/consolidated` return protected aggregate trade reports.

Set `TRADE_SAVE_COPY_PATH` to an absolute server-side directory (for example
`C:/scripts/trade-entry`). Each successful daily save creates the directory if
needed and writes an atomically replaced, timestamped JSON snapshot containing
the business date and saved metrics. Database persistence and the snapshot
write must both succeed before the API reports success; the API returns a
generic file-save error without exposing the physical path.

For SQL Server troubleshooting, check readiness and service logs with
`docker compose ps` and `docker compose logs sqlserver backend`. To recreate
development data only, stop the stack and remove the named `sqlserver-data`
volume before running migrations again; do not do this against shared or
production data.

Run backend tests from the repository root with:

```powershell
python -m pytest
```

The root [pyproject.toml](./pyproject.toml) provides package metadata and test
configuration. [src/backend/requirements.txt](./src/backend/requirements.txt)
is the backend-local, pip-compatible dependency manifest used by Docker and the
commands above.

The frontend production build is verified with:

```powershell
cd src/frontend
npm run build
```

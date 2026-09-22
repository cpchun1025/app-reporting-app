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
2. Copy `.env.example` to `.env` and set a strong `MSSQL_SA_PASSWORD` and
   `JWT_SECRET`. The default SQL Server settings target the host instance; set
   `DATABASE_URL` only for an explicit override such as isolated SQLite tests.
3. Ensure the SQL Server application database exists:
   ```powershell
   .\sql\scripts\Initialize-Database.ps1
   ```
4. Apply schema migrations:
   ```powershell
   python -m alembic upgrade head
   ```
5. Set unique `DEV_ADMIN_PASSWORD` and `DEV_TRADER_PASSWORD` in `.env`, then
   seed local development data (safe to rerun):
   ```powershell
   $env:PYTHONPATH = "$PWD\src\backend"
   python -m app.seed_command
   ```
6. Start the API:
   ```powershell
   python -m uvicorn app.main:app --app-dir src/backend --reload
   ```

For VS Code debugging, select the Python interpreter for the virtual
environment, then use **Run and Debug** with **Backend: Debug FastAPI** or
**Full stack: Backend + Frontend + Edge**. The full-stack configuration starts
the FastAPI debugger, Vite, and a browser debugger together. Copy `.env.example`
to `.env` first so the debugger can load the application settings.

Seeded development users are `dev_admin` / `DevAdmin123!` and
`dev_trader` / `DevTrader123!`. They are intentionally only seeded when
`SEED_DEV_USERS=true`; replace or disable them outside local development.

## SQL Server ownership and Docker startup

The SQL Server bootstrap, Alembic migrations, and development seed data have
separate responsibilities:

- [`sql/bootstrap/00-create-database.sql`](./sql/bootstrap/00-create-database.sql)
  connects to `master` and creates the configured application database only.
- Alembic creates and upgrades all application tables: `users`, `trades`,
  `trade_entry_snapshots`, `trading_businesses`, and `daily_trade_entries`.
- `python -m app.seed_command` adds idempotent development users, businesses,
  legacy sample trades, and date-scoped daily entries.

This order matters: a health check can connect to `master` before
`trading_reporting` exists, while Alembic cannot. Docker Compose therefore
starts `db`, waits for its `master` health check, runs `db-init`, migrations,
and seed data, then starts the backend and frontend.

Copy `.env.example` to `.env`, optionally set `MSSQL_SA_PASSWORD`, then run:

```powershell
docker compose up --build
```

To run each stage explicitly:

```powershell
docker compose up -d db
docker compose ps
docker compose run --rm db-init
docker compose run --rm migrations
docker compose run --rm seed
docker compose up -d backend frontend
docker compose logs db-init migrations seed backend
```

Linux/macOS uses the same Compose lifecycle:

```sh
cp .env.example .env
# Set MSSQL_SA_PASSWORD, JWT_SECRET, DEV_ADMIN_PASSWORD, and DEV_TRADER_PASSWORD in .env.
docker compose up -d db
docker compose run --rm db-init
docker compose run --rm migrations
docker compose run --rm seed
docker compose up -d backend frontend
```

The SQL Server connection uses the Microsoft ODBC Driver 18 and is compatible
with SQL Server row locking (`UPDLOCK` emitted by SQLAlchemy's `with_for_update`).
Docker Compose also starts the frontend on port 5173. Its default API URL is
`http://localhost:8000`; this origin is allowed by the backend's configurable
`CORS_ORIGINS` setting.

The Python backend remains the default `backend` service. The alternative
implementations consume the same SQL Server schema and API contract:

```powershell
# Python (existing workflow)
docker compose up --build

# .NET (select only the .NET backend and frontend)
docker compose --profile dotnet up --build backend-dotnet frontend-dotnet

# Java (select only the Java backend and frontend)
docker compose --profile java up --build backend-java frontend-java
```

The alternative commands reuse the shared `db`, `db-init`, `migrations`, and
`seed` dependency chain. Do not start multiple backend profiles together
because they intentionally share host port 8000 and frontend port 5173.
Confirm the selected implementation with `/health/ready` and its startup log.

The frontend is an independent Vite application under `src/frontend`. Run
`npm install; npm run dev` there for local development, or use the `frontend`
service in Docker Compose. It provides the dark trading-workstation interface
with trade entry, daily, monthly, annual, and consolidated views. Trade Entry
uses the browser's local calendar date and loads date-scoped rows from the API;
it does not write files directly from the browser. Set `VITE_API_URL` when
wiring a deployed backend.

Verify the application database and tables from the SQL Server container:

```powershell
docker compose exec db /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$env:MSSQL_SA_PASSWORD" -d master -Q "SELECT name FROM sys.databases WHERE name = N'$env:APP_DATABASE_NAME';"
docker compose exec db /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$env:MSSQL_SA_PASSWORD" -d "$env:APP_DATABASE_NAME" -Q "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_SCHEMA, TABLE_NAME;"
```

Check the migration state from the backend image:

```powershell
docker compose run --rm migrations alembic current
docker compose run --rm migrations alembic heads
docker compose run --rm migrations alembic history
```

`/health/live` only confirms that the FastAPI process is running.
`/health/ready` confirms it can query the application database and its
Alembic version table without exposing connection details.

### SQL Server diagnostics

- SQL Server error **18456** indicates authentication failure. Confirm the
  configured password, variable names, complexity requirements, and whether
  the named volume was initialized with a different password.
- Error **4060** indicates the requested application database is unavailable.
  Run `db-init` against `master`, then migrations; do not treat it as a
  missing-table error.
- `Invalid object name` normally means migrations have not completed.

Changing `MSSQL_SA_PASSWORD` in `.env` does **not** update an already
initialized SQL Server named volume. Stop and inspect logs first:

```powershell
docker compose logs db db-init migrations
docker compose down
```

**DEVELOPMENT ONLY — permanently deletes the SQL Server Docker volume and all
databases stored in it:**

```powershell
docker compose down -v
```

The tracked [`trading.db`](./trading.db) is a legacy SQLite development
artifact created by the old default Alembic URL. Current SQL Server startup
does not use it unless `DATABASE_URL=sqlite:///./trading.db` is explicitly
set. It is retained untouched for now; assess and migrate any needed data in
a separate, explicit change. New generated SQLite files are ignored.

## API summary

### Compatibility inventory

All three implementations are intended to expose the same externally visible
contract. Authentication uses `Authorization: Bearer <JWT>` for protected
routes; JWTs contain the user ID in `sub` and use the configured HMAC secret.
Invalid credentials/tokens return `401`, validation errors return `422` in the
Python reference behavior, missing resources return `404`, lock/optimistic
concurrency conflicts return `409`, and unauthorized unlocks return `403`.

| Area | Methods and paths | Contract |
| --- | --- | --- |
| Auth | `POST /auth/login`, `POST /auth/mock/callback`, `GET /auth/me` | Login returns `{access_token, token_type}`; `/me` returns `{username, role}`. |
| Health | `GET /health`, `/health/live`, `/health/ready` | Liveness returns `{"status":"ok"}`; readiness returns `503` when SQL Server or `alembic_version` cannot be queried. |
| Trades | `POST /trades`, `GET /trades`, `GET /trades/{id}`, `PUT /trades/{id}`, `DELETE /trades/{id}` | CRUD uses decimal quantity/price values and integer `version`; updates/deletes require `expected_version`. |
| Trade locks | `POST /trades/{id}/lock`, `/unlock` | Database-backed ownership, 8-hour lease, owner/admin unlock, and `409` conflicts. |
| Daily entry | `GET /trades?business_date=YYYY-MM-DD`, `GET /trades/entry?business_date=YYYY-MM-DD`, `POST /trades/entry/save` | Date-scoped rows expose `delta`, `gamma`, `theta`, `vega`, `pnl`, lock metadata, and version. |
| Daily locks | `POST /trades/entry/{id}/lock`, `/unlock` | Same lock and authorization semantics as legacy trades. |
| Reports | `GET /reports/daily`, `/monthly`, `/annual`, `/consolidated` | Require `start_date` and `end_date`; consolidated additionally accepts `group_by=account`. |

The Python backend is the behavioral reference for exact Pydantic validation,
JSON decimal/date/time serialization, error details, and report aggregation.
The implementation-specific READMEs describe any framework metadata
differences in OpenAPI documents.

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

The workspace MCP configuration is in
[.vscode/mcp.json](./.vscode/mcp.json). It provides GitHub, Context7,
Microsoft Learn, Playwright, and a workspace-scoped filesystem server. The
filesystem server is restricted to `${workspaceFolder}`; do not broaden that
path unless the MCP client is running in a trusted environment.

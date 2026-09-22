#!/usr/bin/env sh
set -eu

: "${MSSQL_HOST:?MSSQL_HOST is required}"
: "${MSSQL_PORT:?MSSQL_PORT is required}"
: "${MSSQL_SA_USER:?MSSQL_SA_USER is required}"
: "${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}"
: "${APP_DATABASE_NAME:?APP_DATABASE_NAME is required}"

SQLCMD="${SQLCMD_PATH:-}"
if [ -z "$SQLCMD" ]; then
  for candidate in /opt/mssql-tools18/bin/sqlcmd /opt/mssql-tools/bin/sqlcmd; do
    if [ -x "$candidate" ]; then
      SQLCMD="$candidate"
      break
    fi
  done
fi

if [ -z "$SQLCMD" ] || [ ! -x "$SQLCMD" ]; then
  echo "SQL Server command-line client was not found." >&2
  exit 1
fi

attempt=1
max_attempts="${MSSQL_READY_RETRIES:-30}"
while ! "$SQLCMD" -C -S "${MSSQL_HOST},${MSSQL_PORT}" -U "$MSSQL_SA_USER" -P "$MSSQL_SA_PASSWORD" -d master -Q "SELECT 1" >/dev/null 2>&1; do
  if [ "$attempt" -ge "$max_attempts" ]; then
    echo "SQL Server did not become ready after ${max_attempts} attempts." >&2
    echo "Run SQL Server diagnostics below; credentials are not printed." >&2
    "$SQLCMD" -C -S "${MSSQL_HOST},${MSSQL_PORT}" -U "$MSSQL_SA_USER" -P "$MSSQL_SA_PASSWORD" -d master -Q "SELECT 1" || true
    exit 1
  fi
  echo "Waiting for SQL Server (${attempt}/${max_attempts})..."
  attempt=$((attempt + 1))
  sleep 2
done

echo "SQL Server is ready; ensuring application database exists."
"$SQLCMD" -b -C -S "${MSSQL_HOST},${MSSQL_PORT}" -U "$MSSQL_SA_USER" -P "$MSSQL_SA_PASSWORD" -d master \
  -v "APP_DATABASE_NAME=${APP_DATABASE_NAME}" \
  -i "${DATABASE_BOOTSTRAP_SQL:-/workspace/sql/bootstrap/00-create-database.sql}"
echo "Application database bootstrap completed."

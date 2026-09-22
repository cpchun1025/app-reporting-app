# SQL Server bootstrap

This folder owns **instance and database bootstrap only**. The script in
[`bootstrap/00-create-database.sql`](./bootstrap/00-create-database.sql)
connects to `master`, creates the configured application database if needed,
and never creates tables or drops data.

Alembic is the sole authority for application schema: tables, columns,
constraints, indexes, and upgrades. The backend seed command owns repeatable
development reference data and users.

## Run locally

Set `MSSQL_HOST`, `MSSQL_PORT`, `MSSQL_SA_USER`, `MSSQL_SA_PASSWORD`, and
`APP_DATABASE_NAME` in the environment. Then use:

```powershell
.\sql\scripts\Initialize-Database.ps1
```

On Linux/macOS:

```sh
chmod +x sql/scripts/initialize-database.sh
./sql/scripts/initialize-database.sh
```

Both scripts wait for a bounded period, connect to `master`, and invoke
`sqlcmd` with `-C` for the local Docker SQL Server self-signed certificate.
`TrustServerCertificate=yes` / `-C` is for local Docker development only; use
a trusted certificate in production.

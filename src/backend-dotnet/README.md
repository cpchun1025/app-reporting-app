# .NET compatibility backend

This ASP.NET Core 10 service is an alternative implementation of the Python API. It maps to the existing SQL Server tables only; it does not run migrations, `EnsureCreated`, or any schema mutation.

```powershell
dotnet run --project .\src\backend-dotnet
docker compose --profile dotnet up --build backend-dotnet frontend-dotnet
```

It reads the root `.env` settings, accepts HS256 tokens whose `sub` is the existing user ID, and verifies `$2a$`/`$2b$` BCrypt credentials. A legacy Passlib PBKDF2-SHA256 verification path keeps existing development users usable.

Seed only after the existing schema is deployed:

```powershell
dotnet run --project .\src\backend-dotnet -- --seed
```

The explicit, idempotent command requires `SEED_DEV_USERS=true` plus both development passwords and only inserts missing users/businesses.

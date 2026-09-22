[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

foreach ($name in "MSSQL_HOST", "MSSQL_PORT", "MSSQL_SA_USER", "MSSQL_SA_PASSWORD", "APP_DATABASE_NAME") {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name must be set."
    }
}

$sqlcmd = $env:SQLCMD_PATH
if ([string]::IsNullOrWhiteSpace($sqlcmd)) {
    $sqlcmd = (Get-Command sqlcmd -ErrorAction SilentlyContinue).Source
}
if ([string]::IsNullOrWhiteSpace($sqlcmd)) {
    throw "sqlcmd was not found. Install Microsoft SQL Server command-line tools or set SQLCMD_PATH."
}

$retries = if ($env:MSSQL_READY_RETRIES) { [int]$env:MSSQL_READY_RETRIES } else { 30 }
for ($attempt = 1; $attempt -le $retries; $attempt++) {
    & $sqlcmd -C -S "$($env:MSSQL_HOST),$($env:MSSQL_PORT)" -U $env:MSSQL_SA_USER -P $env:MSSQL_SA_PASSWORD -d master -Q "SELECT 1" *> $null
    if ($LASTEXITCODE -eq 0) { break }
    if ($attempt -eq $retries) {
        Write-Error "SQL Server did not become ready. Running diagnostics; credentials are not printed."
        & $sqlcmd -C -S "$($env:MSSQL_HOST),$($env:MSSQL_PORT)" -U $env:MSSQL_SA_USER -P $env:MSSQL_SA_PASSWORD -d master -Q "SELECT 1"
        throw "SQL Server did not become ready after $retries attempts."
    }
    Write-Host "Waiting for SQL Server ($attempt/$retries)..."
    Start-Sleep -Seconds 2
}

$scriptPath = Join-Path $PSScriptRoot "..\bootstrap\00-create-database.sql"
Write-Host "SQL Server is ready; ensuring application database exists."
& $sqlcmd -b -C -S "$($env:MSSQL_HOST),$($env:MSSQL_PORT)" -U $env:MSSQL_SA_USER -P $env:MSSQL_SA_PASSWORD -d master `
    -v "APP_DATABASE_NAME=$($env:APP_DATABASE_NAME)" -i $scriptPath
if ($LASTEXITCODE -ne 0) { throw "Application database bootstrap failed." }
Write-Host "Application database bootstrap completed."

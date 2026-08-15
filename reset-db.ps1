# reset-db.ps1
# Two reset modes for the expensesplit DB:
#   Full reset  : drops all application tables + Flyway history, so Flyway
#                 rebuilds the schema from scratch on next app start.
#   Keep users  : truncates every table EXCEPT users (data only, schema and
#                 Flyway history left untouched) — good for wiping test
#                 groups/expenses without having to re-register test accounts
#                 in Firebase every time.
#
# Usage:
#   .\reset-db.ps1                    # full reset only
#   .\reset-db.ps1 -Start             # full reset + start the app afterwards
#   .\reset-db.ps1 -KeepUsers         # wipe data except users, keep schema
#   .\reset-db.ps1 -KeepUsers -Start  # same, then start the app afterwards

param(
    [switch]$Start,
    [switch]$KeepUsers
)

# ── Configuration (same env vars as the Spring Boot backend) ─────────────────
$dbUrl = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:postgresql://localhost:5432/expensesplit" }

if ($dbUrl -notmatch '^jdbc:postgresql://([^:/]+):(\d+)/([^?]+)') {
    Write-Error "Invalid DB_URL format: $dbUrl"
    exit 1
}

$PG_HOST = $Matches[1]
$PG_PORT = $Matches[2]
$PG_DB   = $Matches[3]
$PG_USER = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }

if (-not $env:DB_PASSWORD) {
    Write-Error "DB_PASSWORD environment variable is not set."
    exit 1
}

$env:PGPASSWORD = $env:DB_PASSWORD
# ──────────────────────────────────────────────────────────────────────────────

$PSQL = "psql"                    # assumes psql is on PATH

function Run-Sql($sql) {
    & $PSQL -h $PG_HOST -p $PG_PORT -U $PG_USER -d $PG_DB -c $sql
    if ($LASTEXITCODE -ne 0) {
        Write-Error "SQL command failed: $sql"
        exit 1
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Splitty — Database Reset Script        " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Target: $PG_USER@$PG_HOST`:$PG_PORT/$PG_DB" -ForegroundColor Yellow
Write-Host "Mode:   $(if ($KeepUsers) { 'Keep users (truncate data only)' } else { 'Full reset (drop all tables + Flyway history)' })" -ForegroundColor Yellow
Write-Host ""

# Confirm before wiping
$warningText = if ($KeepUsers) {
    "This will DELETE all groups/expenses/settlements data in '$PG_DB' but KEEP the users table."
} else {
    "This will DELETE ALL DATA in '$PG_DB', including users."
}
$confirm = Read-Host "$warningText Type 'yes' to continue"
if ($confirm -ne "yes") {
    Write-Host "Aborted." -ForegroundColor Red
    exit 0
}

Write-Host ""

if ($KeepUsers) {
    Write-Host "[1/1] Truncating data tables (users preserved)..." -ForegroundColor Green

    # TRUNCATE ... CASCADE also clears any tables with FKs pointing at these,
    # but since users isn't in this list, CASCADE has no way to reach it —
    # nothing here can touch the users table.
    # RESTART IDENTITY resets auto-increment ids back to 1 for a clean slate.
    $truncateSql = @"
TRUNCATE TABLE expense_splits, settlements, expenses, group_members, groups
RESTART IDENTITY CASCADE;
"@

    Run-Sql $truncateSql
    Write-Host "      Groups, expenses, splits, settlements, and memberships cleared." -ForegroundColor Gray
    Write-Host "      users table left untouched — no need to re-register test accounts." -ForegroundColor Gray
    Write-Host ""
    Write-Host "Done. Schema and Flyway history untouched, so the app will start normally." -ForegroundColor Cyan
} else {
    Write-Host "[1/3] Dropping application tables (in FK-safe order)..." -ForegroundColor Green

    # Drop in reverse dependency order so FK constraints don't block us
    $dropSql = @"
DROP TABLE IF EXISTS expense_splits  CASCADE;
DROP TABLE IF EXISTS settlements     CASCADE;
DROP TABLE IF EXISTS expenses        CASCADE;
DROP TABLE IF EXISTS group_members   CASCADE;
DROP TABLE IF EXISTS groups          CASCADE;
DROP TABLE IF EXISTS users           CASCADE;
"@

    Run-Sql $dropSql
    Write-Host "      Application tables dropped." -ForegroundColor Gray

    Write-Host "[2/3] Clearing Flyway schema history..." -ForegroundColor Green
    Run-Sql "DROP TABLE IF EXISTS flyway_schema_history CASCADE;"
    Write-Host "      Flyway history cleared." -ForegroundColor Gray

    Write-Host "[3/3] Done — database is empty." -ForegroundColor Green
    Write-Host ""
    Write-Host "On next app start, Flyway will re-run all migrations from scratch." -ForegroundColor Cyan
}

Write-Host ""

# ── Optional: start the app ───────────────────────────────────────────────────
if ($Start) {
    $backendPath = Join-Path $PSScriptRoot "backend"
    if (-not (Test-Path (Join-Path $backendPath "pom.xml"))) {
        # Script might be run from the backend folder itself
        $backendPath = $PSScriptRoot
    }

    Write-Host "Starting Spring Boot application..." -ForegroundColor Cyan
    Write-Host "(Ctrl+C to stop)" -ForegroundColor Gray
    Write-Host ""
    Set-Location $backendPath
    & mvn spring-boot:run
}
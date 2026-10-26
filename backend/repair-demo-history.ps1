<#
.SYNOPSIS
  Brings the canonical Flyway history back in sync after a run with the `demo`
  profile, so the backend starts normally again.

.DESCRIPTION
  The demo seed (db/seed/R__demo_catalog.sql) is only resolved when the `demo`
  profile adds classpath:db/seed to Flyway's locations. Flyway records it in
  flyway_schema_history, and the normal `local` profile then sees an applied
  migration it cannot find locally, which is a hard validation failure:

      Detected applied migration not resolved locally: demo catalog.

  The seeded rows themselves are harmless; only the history row has to go.
  Deleting it is safe because the seed guards every insert on both its id and
  its natural key, so a later `demo` run re-registers without duplicating.

.PARAMETER Database
  Schema to repair. Defaults to `clothing` (the dev database).
#>
param(
  [string]$Database = "clothing"
)

$ErrorActionPreference = "Stop"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $here ".env"
if (-not (Test-Path $envFile)) { throw ".env not found next to this script ($here)" }
$cfg = @{}
Get-Content $envFile | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() }
}

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysql)) { $mysql = "mysql" }

$user = $cfg["DB_USERNAME"]
$pass = $cfg["DB_PASSWORD"]
$hostName = if ($cfg["DB_URL"] -match '//([^:/]+)') { $matches[1] } else { "127.0.0.1" }

Write-Host "Repairing Flyway history in '$Database' on $hostName ..."
& $mysql -u $user "-p$pass" -h $hostName $Database -e "DELETE FROM flyway_schema_history WHERE description = 'demo catalog';"
if ($LASTEXITCODE -ne 0) { throw "mysql repair failed" }

& $mysql -u $user "-p$pass" -h $hostName $Database -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
Write-Host "Done. 'mvnw spring-boot:run' will start normally again."
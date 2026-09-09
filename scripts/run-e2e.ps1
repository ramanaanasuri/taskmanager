# run-e2e.ps1 — browser-layer tests from Windows against a deployed environment.
# One-time setup: install Node LTS (nodejs.org), then this script handles the rest.
# Usage:  .\scripts\run-e2e.ps1          (gcp, default)
#         .\scripts\run-e2e.ps1 aws
# Requires $env:PW_TESTER_PASSWORD to be set in this PowerShell session first.

param([string]$TargetEnv = "gcp")
$ErrorActionPreference = "Stop"

switch ($TargetEnv) {
  "gcp" { $base = "https://taskmanager.gcp.sriinfosoft.com"; $api = "https://api-taskmanager.gcp.sriinfosoft.com" }
  "aws" { $base = "https://taskmanager.sriinfosoft.com";     $api = "https://api-taskmanager.sriinfosoft.com" }
  default { Write-Host "usage: .\run-e2e.ps1 [gcp|aws]"; exit 1 }
}

# Password: session variable wins; else read API_TESTER_PASSWORD from repo-root .env
if (-not $env:PW_TESTER_PASSWORD) {
  $envFile = Join-Path $PSScriptRoot "..\.env"
  if (Test-Path $envFile) {
    $line = Select-String -Path $envFile -Pattern '^API_TESTER_PASSWORD=' | Select-Object -First 1
    if ($line) { $env:PW_TESTER_PASSWORD = ($line.Line -split '=', 2)[1].Trim() }
  }
}
if (-not $env:PW_TESTER_PASSWORD) {
  Write-Host "Tester password not found. Either set it for this session:"
  Write-Host '  $env:PW_TESTER_PASSWORD = "<value>"'
  Write-Host "or create a minimal .env at the repo root containing one line:"
  Write-Host "  API_TESTER_PASSWORD=<value>"
  exit 1
}

# Health gate — do not spend a run on a dead target.
Write-Host "=== waiting for backend health at $api/actuator/health"
try { $code = (Invoke-WebRequest "$api/actuator/health" -UseBasicParsing -TimeoutSec 15).StatusCode } catch { $code = 0 }
if ($code -ne 200) { Write-Host "=== backend NOT healthy ($code) - aborting"; exit 1 }
Write-Host "=== backend healthy - starting tests (env=$TargetEnv)"

$env:PW_BASE_URL = $base
$env:PW_API_URL  = $api

Set-Location (Join-Path $PSScriptRoot "..\apps\frontend\e2e")
npm install --silent
npx playwright install chromium
npx playwright test
Write-Host "=== HTML report:  npx playwright show-report   (opens in your browser)"

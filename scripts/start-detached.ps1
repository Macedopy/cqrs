# ============================================================
#  Starts the application detached from the current terminal.
#
#  Use this when you want the API to survive closing the shell,
#  running CI, or working from another terminal. Logs go to
#  run.out / run.err in the project root; follow with:
#
#     Get-Content run.out -Wait -Tail 20
#
#  To stop:
#     powershell -ExecutionPolicy Bypass -File scripts/stop.ps1
#
#  Usage: powershell -ExecutionPolicy Bypass -File scripts/start-detached.ps1
# ============================================================

$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

if (-not $env:JAVA_HOME) {
    $jdk25 = "C:\Program Files\Java\jdk-25"
    if (Test-Path $jdk25) {
        $env:JAVA_HOME = $jdk25
        Write-Host "JAVA_HOME set to $jdk25"
    } else {
        Write-Host "JAVA_HOME is not set. Install JDK 25 or set it manually." -ForegroundColor Red
        exit 1
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "java not found on PATH. Check JAVA_HOME." -ForegroundColor Red
    exit 1
}

Write-Host "Checking Docker services..." -ForegroundColor Cyan
$containers = docker compose ps --format "{{.Name}}"
if (-not $containers) {
    Write-Host "Docker services are down. Starting them..." -ForegroundColor Yellow
    docker compose up -d | Out-Null
    Start-Sleep -Seconds 5
}
docker compose ps --format "table {{.Name}}\t{{.Status}}"

Write-Host "Cleaning previous build..." -ForegroundColor Cyan
.\mvnw.cmd -q clean 2>&1 | Out-Null

Write-Host "Starting the application in the background..." -ForegroundColor Cyan
Start-Process -FilePath ".\mvnw.cmd" `
    -ArgumentList "spring-boot:run" `
    -RedirectStandardOutput "run.out" `
    -RedirectStandardError "run.err" `
    -WindowStyle Hidden

Write-Host ""
Write-Host "Waiting for the API to answer on :8080 ..." -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/queries/auctions/hot" `
            -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        # not up yet
    }
}

if ($ready) {
    Write-Host ""
    Write-Host "API is up on http://localhost:8080" -ForegroundColor Green
    Get-Content run.out | Select-String "Started AuctionApiApplication|Provisioned"
} else {
    Write-Host ""
    Write-Host "The API did not answer within 120 seconds. Check the log:" -ForegroundColor Red
    Write-Host "  Get-Content run.out -Tail 30"
    Write-Host "  Get-Content run.err -Tail 30"
    exit 1
}
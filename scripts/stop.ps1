# ============================================================
#  Stops the application started by run.ps1 or start-detached.ps1
#
#  Usage: powershell -ExecutionPolicy Bypass -File scripts/stop.ps1
#         powershell -ExecutionPolicy Bypass -File scripts/stop.ps1 -WithDocker
# ============================================================

param(
    [switch]$WithDocker
)

Set-Location (Join-Path $PSScriptRoot "..")

$running = Get-Process java -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Stopping the application..." -ForegroundColor Cyan
    $running | Stop-Process -Force
    Write-Host "Application stopped." -ForegroundColor Green
} else {
    Write-Host "No running Java process found."
}

if ($WithDocker) {
    Write-Host "Stopping Docker services..." -ForegroundColor Cyan
    docker compose down
    Write-Host "Docker services stopped." -ForegroundColor Green
}
else {
    Write-Host "Docker services left running. Use -WithDocker to stop them too."
}
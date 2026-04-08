# ============================================================
# AuraLink - Stop All Microservices
# ============================================================

$PROJECT_ROOT = $PSScriptRoot

function Write-Green { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Red   { param($msg) Write-Host $msg -ForegroundColor Red }
function Write-Cyan  { param($msg) Write-Host $msg -ForegroundColor Cyan }

Clear-Host
Write-Cyan "=============================================="
Write-Cyan "   AuraLink - Stopping All Microservices"
Write-Cyan "=============================================="

$pidsFile = "$PROJECT_ROOT\running-pids.txt"

if (Test-Path $pidsFile) {
    $lines = Get-Content $pidsFile
    foreach ($line in $lines) {
        $parts = $line.Split("=")
        $serviceName = $parts[0]
        $pid = $parts[1]

        try {
            Stop-Process -Id $pid -Force -ErrorAction Stop
            Write-Green "✅ Stopped $serviceName (PID: $pid)"
        } catch {
            Write-Red "❌ Could not stop $serviceName (PID: $pid) - may already be stopped"
        }
    }
    Remove-Item $pidsFile
    Write-Green "`n✅ All services stopped!"
} else {
    Write-Red "No running-pids.txt found."
    Write-Cyan "Trying to kill all java processes on service ports..."

    $ports = @(8080, 8081, 8082, 8083, 8084,
               8085, 8086, 8087, 8088, 8089, 8090, 8091)

    foreach ($port in $ports) {
        $result = netstat -ano |
            Select-String ":$port " |
            Select-Object -First 1

        if ($result) {
            $pid = ($result -split '\s+')[-1]
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Green "✅ Killed process on port $port (PID: $pid)"
            } catch {
                Write-Red "❌ Could not kill port $port"
            }
        }
    }
}

Write-Cyan "`n=============================================="

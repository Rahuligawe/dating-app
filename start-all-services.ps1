# ============================================================
# AuraLink - Start All Microservices in Correct Order
# Run this from your project root:
# E:\DatingApp\DatingAppApplication\
# ============================================================

$PROJECT_ROOT = $PSScriptRoot
$JAVA_HOME_PATH = "C:\jdk"

# Set Java 17
$env:JAVA_HOME = $JAVA_HOME_PATH
$env:PATH = "$JAVA_HOME_PATH\bin;" + $env:PATH

# ─── Colors for output ───────────────────────────────────────
function Write-Green  { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Yellow { param($msg) Write-Host $msg -ForegroundColor Yellow }
function Write-Red    { param($msg) Write-Host $msg -ForegroundColor Red }
function Write-Cyan   { param($msg) Write-Host $msg -ForegroundColor Cyan }

# ─── Wait for a service to be UP ─────────────────────────────
function Wait-ForService {
    param(
        [string]$ServiceName,
        [string]$HealthUrl,
        [int]$TimeoutSeconds = 60
    )

    Write-Yellow "  Waiting for $ServiceName to be ready..."
    $elapsed = 0

    while ($elapsed -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $HealthUrl `
                -Method GET -TimeoutSec 3 -ErrorAction Stop
            if ($response.status -eq "UP") {
                Write-Green "  ✅ $ServiceName is UP!"
                return $true
            }
        } catch {
            # Not ready yet
        }
        Start-Sleep -Seconds 3
        $elapsed += 3
        Write-Host "  ⏳ $elapsed seconds..." -NoNewline
        Write-Host "`r" -NoNewline
    }

    Write-Red "  ❌ $ServiceName did not start within $TimeoutSeconds seconds"
    return $false
}

# ─── Start a single service ───────────────────────────────────
function Start-Service {
    param(
        [string]$ServiceName,
        [string]$ServicePath,
        [string]$MainClass,
        [string]$HealthUrl,
        [int]$WaitSeconds = 60
    )

    Write-Cyan "`n🚀 Starting $ServiceName..."

    $jarPath = Join-Path $PROJECT_ROOT `
        "services\$ServicePath\target\*.jar"

    $jars = Get-ChildItem -Path `
        (Join-Path $PROJECT_ROOT "services\$ServicePath\target") `
        -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*plain*" }

    if ($jars.Count -eq 0) {
        Write-Red "  ❌ JAR not found for $ServiceName"
        Write-Red "  Run 'mvn clean package -DskipTests' first!"
        return $false
    }

    $jar = $jars[0].FullName
    Write-Yellow "  JAR: $($jars[0].Name)"

    # Start in new window so we can see logs
    $process = Start-Process -FilePath "java" `
        -ArgumentList "-jar", "`"$jar`"" `
        -WorkingDirectory (Join-Path $PROJECT_ROOT "services\$ServicePath") `
        -PassThru `
        -WindowStyle Minimized

    Write-Yellow "  PID: $($process.Id)"

    # Save PID for later shutdown
    Add-Content -Path "$PROJECT_ROOT\running-pids.txt" `
        "$ServiceName=$($process.Id)"

    # Wait for health check
    return Wait-ForService -ServiceName $ServiceName `
        -HealthUrl $HealthUrl -TimeoutSeconds $WaitSeconds
}

# ─── Main ─────────────────────────────────────────────────────

Clear-Host
Write-Cyan "=============================================="
Write-Cyan "   AuraLink - Starting All Microservices"
Write-Cyan "=============================================="
Write-Green "Java: $(java -version 2>&1 | Select-Object -First 1)"
Write-Cyan "----------------------------------------------"

# Clear old PIDs
if (Test-Path "$PROJECT_ROOT\running-pids.txt") {
    Remove-Item "$PROJECT_ROOT\running-pids.txt"
}

# ─── Check Docker infrastructure is running ──────────────────
Write-Cyan "`n📦 Checking Docker infrastructure..."

$containers = @("dating-postgres", "dating-mongo",
                "dating-redis", "dating-kafka")

foreach ($container in $containers) {
    $status = docker inspect -f '{{.State.Running}}' `
        $container 2>$null
    if ($status -eq "true") {
        Write-Green "  ✅ $container is running"
    } else {
        Write-Red "  ❌ $container is NOT running!"
        Write-Yellow "  Starting Docker infrastructure..."
        Set-Location $PROJECT_ROOT
        docker-compose up -d postgres mongodb redis zookeeper kafka
        Start-Sleep -Seconds 15
        break
    }
}

# ─── Start services in order ──────────────────────────────────

$services = @(
    @{
        Name    = "Auth Service"
        Path    = "auth-service"
        Health  = "http://localhost:8081/api/auth/health"
        Wait    = 60
    },
    @{
        Name    = "User Service"
        Path    = "user-service"
        Health  = "http://localhost:8082/api/users/health"
        Wait    = 60
    },
    @{
        Name    = "Subscription Service"
        Path    = "subscription-service"
        Health  = "http://localhost:8091/api/subscriptions/health"
        Wait    = 60
    },
    @{
        Name    = "Swipe Service"
        Path    = "swipe-service"
        Health  = "http://localhost:8083/api/swipes/health"
        Wait    = 60
    },
    @{
        Name    = "Match Service"
        Path    = "match-service"
        Health  = "http://localhost:8084/api/matches/health"
        Wait    = 60
    },
    @{
        Name    = "Chat Service"
        Path    = "chat-service"
        Health  = "http://localhost:8085/api/chats/health"
        Wait    = 60
    },
    @{
        Name    = "Notification Service"
        Path    = "notification-service"
        Health  = "http://localhost:8090/api/notifications/health"
        Wait    = 60
    },
    @{
        Name    = "Event Service"
        Path    = "event-service"
        Health  = "http://localhost:8086/api/events/health"
        Wait    = 60
    },
    @{
        Name    = "Location Service"
        Path    = "location-service"
        Health  = "http://localhost:8087/api/locations/health"
        Wait    = 60
    },
    @{
        Name    = "Mood Service"
        Path    = "mood-service"
        Health  = "http://localhost:8088/api/moods/health"
        Wait    = 60
    },
    @{
        Name    = "Radar Service"
        Path    = "radar-service"
        Health  = "http://localhost:8089/api/radar/health"
        Wait    = 60
    },
    @{
        Name    = "API Gateway"
        Path    = "api-gateway"
        Health  = "http://localhost:8080/actuator/health"
        Wait    = 60
    }
)

$started  = 0
$failed   = 0
$failedServices = @()

foreach ($svc in $services) {
    $result = Start-Service `
        -ServiceName $svc.Name `
        -ServicePath $svc.Path `
        -HealthUrl   $svc.Health `
        -WaitSeconds $svc.Wait

    if ($result) {
        $started++
    } else {
        $failed++
        $failedServices += $svc.Name
    }
}

# ─── Final Summary ────────────────────────────────────────────

Write-Cyan "`n=============================================="
Write-Cyan "              STARTUP SUMMARY"
Write-Cyan "=============================================="
Write-Green "✅ Started successfully: $started services"

if ($failed -gt 0) {
    Write-Red "❌ Failed to start:     $failed services"
    foreach ($f in $failedServices) {
        Write-Red "   - $f"
    }
} else {
    Write-Green "`n🎉 ALL SERVICES ARE UP AND RUNNING!"
}

Write-Cyan "`n─── Health Check URLs ──────────────────────"
Write-Host "Auth:         http://localhost:8081/api/auth/health"
Write-Host "User:         http://localhost:8082/api/users/health"
Write-Host "Swipe:        http://localhost:8083/api/swipes/health"
Write-Host "Match:        http://localhost:8084/api/matches/health"
Write-Host "Chat:         http://localhost:8085/api/chats/health"
Write-Host "Event:        http://localhost:8086/api/events/health"
Write-Host "Location:     http://localhost:8087/api/locations/health"
Write-Host "Mood:         http://localhost:8088/api/moods/health"
Write-Host "Radar:        http://localhost:8089/api/radar/health"
Write-Host "Notification: http://localhost:8090/api/notifications/health"
Write-Host "Subscription: http://localhost:8091/api/subscriptions/health"
Write-Host "Gateway:      http://localhost:8080/actuator/health"

Write-Cyan "`n─── PIDs saved to: running-pids.txt ────────"
Write-Cyan "To stop all services run: .\stop-all-services.ps1"
Write-Cyan "=============================================="

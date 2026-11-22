# ==============================================================================
# PulseCare 2.0 - Local Quality Gate & Pre-Push Verification Script (PowerShell)
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " PulseCare Quality Verification Gate (PowerShell)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$FailedSteps = @()
$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")

# 1. Check Docker Availability
Write-Host "`n[1/5] Checking Docker Environment..." -ForegroundColor Yellow
$DockerAvailable = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Docker daemon is active. Full integration tests can execute." -ForegroundColor Green
        $DockerAvailable = $true
    } else {
        Write-Host "  ⚠ Docker daemon is NOT running or accessible." -ForegroundColor DarkYellow
        Write-Host "  ⚠ Testcontainers MongoDB integration tests require live Docker." -ForegroundColor DarkYellow
    }
} catch {
    Write-Host "  ⚠ Docker command not available or daemon inactive." -ForegroundColor DarkYellow
}

# 2. Backend Verification
Write-Host "`n[2/5] Running Backend Compilation & Tests..." -ForegroundColor Yellow
Push-Location (Join-Path $RootDir "backend")
try {
    Write-Host "  Compiling Java sources and test classes..." -ForegroundColor Gray
    & .\mvnw.cmd -B test-compile
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Backend compilation succeeded." -ForegroundColor Green
    } else {
        Write-Host "  ✗ Backend compilation failed!" -ForegroundColor Red
        $FailedSteps += "Backend Compilation"
    }

    $env:MAIL_ENABLED = "false"
    $env:JWT_SECRET = "ci-only-jwt-secret-with-at-least-256-bits-entropy-guaranteed"

    if ($DockerAvailable) {
        Write-Host "  Running full test suite..." -ForegroundColor Gray
        & .\mvnw.cmd -B test
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ All backend unit and integration tests passed." -ForegroundColor Green
        } else {
            Write-Host "  ✗ Backend tests failed!" -ForegroundColor Red
            $FailedSteps += "Backend Tests"
        }
    } else {
        Write-Host "  Running unit and security tests (excluding Docker integration tests)..." -ForegroundColor Gray
        & .\mvnw.cmd -B test "-Dtest=!*MongoIntegrationTest"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ Backend unit and security tests passed." -ForegroundColor Green
            Write-Host "  ⚠ MongoIntegrationTest suites SKIPPED locally (will run on CI runner)." -ForegroundColor DarkYellow
        } else {
            Write-Host "  ✗ Backend unit/security tests failed!" -ForegroundColor Red
            $FailedSteps += "Backend Unit Tests"
        }
    }
} finally {
    Pop-Location
}

# 3. Frontend Verification
Write-Host "`n[3/5] Running Frontend Lint & Build..." -ForegroundColor Yellow
Push-Location (Join-Path $RootDir "frontend")
try {
    Write-Host "  Running ESLint..." -ForegroundColor Gray
    npm run lint
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Frontend linting passed." -ForegroundColor Green
    } else {
        Write-Host "  ✗ Frontend lint failed!" -ForegroundColor Red
        $FailedSteps += "Frontend Lint"
    }

    Write-Host "  Building Next.js production bundle..." -ForegroundColor Gray
    $env:NEXT_PUBLIC_API_BASE = "http://localhost:8080/api"
    $env:NEXT_PUBLIC_WS_URL = "ws://localhost:8080/ws"
    npm run build
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ Next.js build succeeded." -ForegroundColor Green
    } else {
        Write-Host "  ✗ Next.js build failed!" -ForegroundColor Red
        $FailedSteps += "Frontend Build"
    }
} finally {
    Pop-Location
}

# 4. Compose Validation
Write-Host "`n[4/5] Validating Docker Compose Architecture..." -ForegroundColor Yellow
Push-Location $RootDir
try {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        docker compose config --quiet 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ Local docker-compose.yml is valid." -ForegroundColor Green
        } else {
            Write-Host "  ✗ Local docker-compose.yml validation failed!" -ForegroundColor Red
            $FailedSteps += "Compose Local Validation"
        }

        # Fail-closed test
        & docker compose -f docker-compose.prod.yml config 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✗ Production compose should fail closed without secrets!" -ForegroundColor Red
            $FailedSteps += "Production Compose Fail-Closed Check"
        } else {
            Write-Host "  ✓ Production compose correctly rejects missing secrets (Fail-Closed)." -ForegroundColor Green
        }
    }
} finally {
    Pop-Location
}

# Summary
Write-Host "`n=================================================================" -ForegroundColor Cyan
if ($FailedSteps.Count -eq 0) {
    Write-Host " ✓ QUALITY VERIFICATION SUCCEEDED" -ForegroundColor Green
    if (-not $DockerAvailable) {
        Write-Host " ⚠ NOTE: Docker was unavailable locally. Integration tests" -ForegroundColor DarkYellow
        Write-Host "   will execute on the GitHub Actions CI runner." -ForegroundColor DarkYellow
    }
    Write-Host "=================================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " ✗ QUALITY VERIFICATION FAILED" -ForegroundColor Red
    Write-Host " Failed steps:" -ForegroundColor Red
    foreach ($step in $FailedSteps) {
        Write-Host "   - $step" -ForegroundColor Red
    }
    Write-Host "=================================================================" -ForegroundColor Cyan
    exit 1
}

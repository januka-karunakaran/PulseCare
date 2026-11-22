#!/usr/bin/env bash
# ==============================================================================
# PulseCare 2.0 - Local Quality Gate & Pre-Push Verification Script
# ==============================================================================
set -eo pipefail

echo "================================================================="
echo " PulseCare Quality Verification Gate"
echo "================================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

FAILED_STEPS=()

# 1. Check Docker Availability
echo ""
echo "[1/5] Checking Docker Environment..."
if docker info > /dev/null 2>&1; then
    echo "  ✓ Docker daemon is active. Full integration tests can execute."
    DOCKER_AVAILABLE=true
else
    echo "  ⚠ Docker daemon is NOT running or accessible."
    echo "  ⚠ Testcontainers MongoDB integration tests will FAIL or require live Docker."
    DOCKER_AVAILABLE=false
fi

# 2. Backend Verification
echo ""
echo "[2/5] Running Backend Compilation & Tests..."
cd "${ROOT_DIR}/backend"
chmod +x mvnw || true

echo "  Compiling Java sources and test classes..."
if ./mvnw -B test-compile; then
    echo "  ✓ Backend compilation succeeded."
else
    echo "  ✗ Backend compilation failed!"
    FAILED_STEPS+=("Backend Compilation")
fi

echo "  Running backend test suite..."
export MAIL_ENABLED="false"
export JWT_SECRET="ci-only-jwt-secret-with-at-least-256-bits-entropy-guaranteed"

if [ "$DOCKER_AVAILABLE" = true ]; then
    if ./mvnw -B test; then
        echo "  ✓ All backend unit and integration tests passed."
    else
        echo "  ✗ Backend tests failed!"
        FAILED_STEPS+=("Backend Tests")
    fi
else
    echo "  Running unit and security tests (excluding Docker integration tests)..."
    if ./mvnw -B test -Dtest="!*MongoIntegrationTest"; then
        echo "  ✓ Backend unit and security tests passed."
        echo "  ⚠ MongoIntegrationTest suites SKIPPED (Docker unavailable locally)."
    else
        echo "  ✗ Backend unit/security tests failed!"
        FAILED_STEPS+=("Backend Unit Tests")
    fi
fi

# 3. Frontend Verification
echo ""
echo "[3/5] Running Frontend Lint & Build..."
cd "${ROOT_DIR}/frontend"

echo "  Running ESLint..."
if npm run lint; then
    echo "  ✓ Frontend linting passed."
else
    echo "  ✗ Frontend lint failed!"
    FAILED_STEPS+=("Frontend Lint")
fi

echo "  Building Next.js production bundle..."
export NEXT_PUBLIC_API_BASE="http://localhost:8080/api"
export NEXT_PUBLIC_WS_URL="ws://localhost:8080/ws"
if npm run build; then
    echo "  ✓ Next.js build succeeded."
else
    echo "  ✗ Next.js build failed!"
    FAILED_STEPS+=("Frontend Build")
fi

# 4. Compose Validation
echo ""
echo "[4/5] Validating Docker Compose Architecture..."
cd "${ROOT_DIR}"
if command -v docker > /dev/null 2>&1; then
    if docker compose config --quiet; then
        echo "  ✓ Local docker-compose.yml is valid."
    else
        echo "  ✗ Local docker-compose.yml validation failed!"
        FAILED_STEPS+=("Compose Local Validation")
    fi

    # Fail-closed check
    if docker compose -f docker-compose.prod.yml config > /dev/null 2>&1; then
        echo "  ✗ Production compose should fail closed without secrets!"
        FAILED_STEPS+=("Production Compose Fail-Closed Check")
    else
        echo "  ✓ Production compose correctly rejects missing secrets (Fail-Closed)."
    fi
else
    echo "  ⚠ Docker CLI not found. Skipping compose validation."
fi

# 5. Git Status Check
echo ""
echo "[5/5] Checking Git Status..."
cd "${ROOT_DIR}"
git status --short

echo ""
echo "================================================================="
if [ ${#FAILED_STEPS[@]} -eq 0 ]; then
    echo " ✓ QUALITY VERIFICATION SUCCEEDED"
    if [ "$DOCKER_AVAILABLE" = false ]; then
        echo " ⚠ NOTE: Docker was unavailable locally. Integration tests"
        echo "   will be executed by GitHub Actions CI runner."
    fi
    echo "================================================================="
    exit 0
else
    echo " ✗ QUALITY VERIFICATION FAILED"
    echo " Failed steps:"
    for step in "${FAILED_STEPS[@]}"; do
        echo "   - $step"
    done
    echo "================================================================="
    exit 1
fi

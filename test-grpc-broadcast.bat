@echo off
REM gRPC Broadcast Test Script

echo ========================================
echo gRPC Broadcast Test Tool
echo ========================================
echo.

echo [1] Checking Consul status...
curl -s http://localhost:8500/v1/agent/self > nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Consul is not running. Please start Consul first.
    echo Command: C:\consul\consul.exe agent -dev
    pause
    exit /b 1
)
echo [SUCCESS] Consul is running
echo.

echo [2] Checking registered instances...
curl -s http://localhost:8500/v1/health/service/business-logic-editor?passing=true
echo.
echo.

echo [3] Test API List:
echo   - Health Check:         GET  http://localhost:8080/api/grpc/test/health
echo   - List Instances:       GET  http://localhost:8080/api/grpc/test/instances
echo   - Broadcast Test:       POST http://localhost:8080/api/grpc/test/broadcast
echo   - Simple Broadcast:     POST http://localhost:8080/api/grpc/test/broadcast-simple
echo   - Simulate 2PC:         POST http://localhost:8080/api/grpc/test/simulate-2pc
echo   - Expression Test:      POST http://localhost:8080/api/grpc/test/expression-test
echo   - Batch Test:           POST http://localhost:8080/api/grpc/test/batch
echo.
echo [4] Test Examples:
echo.
echo Example 1 - List instances:
echo   curl http://localhost:8080/api/grpc/test/instances
echo.
echo Example 2 - Broadcast test:
echo   curl -X POST "http://localhost:8080/api/grpc/test/broadcast?transactionCode=T001&featureCode=F001&expression=age+^>18&version=1"
echo.
echo Example 3 - Simple broadcast:
echo   curl -X POST "http://localhost:8080/api/grpc/test/broadcast-simple?transactionCode=T001&featureCode=F001&expression=age+^>18&version=1"
echo.
echo Example 4 - Simulate 2PC:
echo   curl -X POST "http://localhost:8080/api/grpc/test/simulate-2pc?transactionCode=T002&featureCode=F002&expression=price+^>100&version=1"
echo.
echo Example 5 - Expression test:
echo   curl -X POST "http://localhost:8080/api/grpc/test/expression-test?expression=age+^>18"
echo.
echo ========================================
echo Tip: Access Consul UI at http://localhost:8500/ui/
echo ========================================
pause
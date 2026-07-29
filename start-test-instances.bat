@echo off
REM Start multiple application instances for gRPC broadcast testing

echo ========================================
echo Starting Instance 1 (HTTP:8080, gRPC:9090)
echo ========================================
start "Instance-1" cmd /k "cd /d ""%~dp0backend"" && mvn spring-boot:run -Dspring-boot.run.profiles=instance1"

timeout /t 15 /nobreak > nul

echo ========================================
echo Starting Instance 2 (HTTP:8081, gRPC:9091)
echo ========================================
start "Instance-2" cmd /k "cd /d ""%~dp0backend"" && mvn spring-boot:run -Dspring-boot.run.profiles=instance2"

timeout /t 15 /nobreak > nul

echo ========================================
echo Starting Instance 3 (HTTP:8082, gRPC:9092)
echo ========================================
start "Instance-3" cmd /k "cd /d ""%~dp0backend"" && mvn spring-boot:run -Dspring-boot.run.profiles=instance3"

echo.
echo All instances started. Please wait 60 seconds for applications to fully start.
echo.
echo Access Consul UI: http://localhost:8500/ui/
echo.
echo Close all windows to stop instances after testing.
pause
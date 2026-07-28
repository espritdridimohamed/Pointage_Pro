@echo off
echo ============================================
echo   PointagePro - Starting all services...
echo ============================================
echo.

echo [1/2] Starting Backend (Spring Boot)...
start "PointagePro-Backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"

timeout /t 5 /nobreak >nul

echo [2/2] Starting Frontend (Angular)...
start "PointagePro-Frontend" cmd /k "cd /d %~dp0frontend && npx ng serve --open"

echo.
echo ============================================
echo   Backend:  http://localhost:8080/api/v1
echo   Frontend: http://localhost:4200
echo ============================================
echo.
echo Press any key to exit this window...
pause >nul

@echo off
echo ============================================
echo   PointagePro - Build Script (Dev PC)
echo ============================================
echo.

set ROOT=%~dp0..
set DEPLOY=%~dp0

echo [1/3] Building Angular frontend (production)...
cd /d "%ROOT%\frontend"
call npx ng build --configuration production
if %ERRORLEVEL% neq 0 (
    echo ERROR: Frontend build failed!
    pause
    exit /b 1
)
echo      Frontend built OK.

echo [2/3] Building Spring Boot backend...
cd /d "%ROOT%\backend"
call mvn package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Backend build failed!
    pause
    exit /b 1
)
echo      Backend built OK.

echo [3/3] Copying artifacts to deploy folder...
copy /Y "%ROOT%\backend\target\pointagepro-backend-1.0.0.jar" "%DEPLOY%\backend\pointagepro.jar" >nul
xcopy /E /Y /Q "%ROOT%\frontend\dist\frontend\browser\*" "%DEPLOY%\frontend\" >nul
echo      Artifacts copied OK.

echo.
echo ============================================
echo   BUILD COMPLETE!
echo   Deploy folder: %DEPLOY%
echo ============================================
echo.
echo To deploy to company PC:
echo   1. Copy the 'deploy' folder to the company PC
echo   2. Right-click install.bat > Run as Administrator
echo.
pause

@echo off
echo ============================================
echo   PointagePro - Service Manager
echo ============================================
echo.
echo  [1] Start backend service
echo  [2] Stop backend service
echo  [3] Restart backend service
echo  [4] Check service status
echo  [5] View backend logs
echo  [6] Open app in browser
echo  [0] Exit
echo.
set /p choice="Choose: "

if "%choice%"=="1" (
    nssm start PointagePro
    echo Service started.
)
if "%choice%"=="2" (
    nssm stop PointagePro
    echo Service stopped.
)
if "%choice%"=="3" (
    nssm restart PointagePro
    echo Service restarted.
)
if "%choice%"=="4" (
    nssm status PointagePro
)
if "%choice%"=="5" (
    if exist "C:\PointagePro\logs\stdout.log" (
        notepad "C:\PointagePro\logs\stdout.log"
    ) else (
        echo No logs found.
    )
)
if "%choice%"=="6" (
    start http://localhost/pointagepro/
)
timeout /t 3 /nobreak >nul

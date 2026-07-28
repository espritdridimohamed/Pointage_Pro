@echo off
echo ============================================
echo   PointagePro - Uninstaller
echo   Run as Administrator!
echo ============================================
echo.
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Right-click and "Run as administrator"
    pause
    exit /b 1
)

echo Stopping PointagePro service...
nssm stop PointagePro >nul 2>&1
nssm remove PointagePro confirm >nul 2>&1
echo      Service removed.

echo Removing files...
if exist "C:\PointagePro" rmdir /S /Q "C:\PointagePro"
if exist "C:\xampp\htdocs\pointagepro" rmdir /S /Q "C:\xampp\htdocs\pointagepro"
echo      Files removed.

echo.
echo Uninstall complete. XAMPP and Java were NOT removed.
echo To remove XAMPP: C:\xampp\uninstall.exe
echo.
pause

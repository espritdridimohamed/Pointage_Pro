@echo off
setlocal enabledelayedexpansion
echo ============================================
echo   PointagePro - Installer (Company PC)
echo   Run as Administrator!
echo ============================================
echo.

:: Check admin
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Right-click this file and select "Run as administrator"
    pause
    exit /b 1
)

set INSTALL_DIR=C:\PointagePro
set DEPLOY_DIR=%~dp0

echo [1/7] Creating install directory...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\backend" mkdir "%INSTALL_DIR%\backend"
if not exist "%INSTALL_DIR%\logs" mkdir "%INSTALL_DIR%\logs"
echo      Done.

echo [2/7] Downloading and installing Java Runtime (JRE 17)...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo      Downloading JRE 17 from Adoptium...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%%2B11/OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.msi' -OutFile '%TEMP%\jre17.msi'"
    if exist "%TEMP%\jre17.msi" (
        msiexec /i "%TEMP%\jre17.msi" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /quiet /norestart
        timeout /t 30 /nobreak >nul
        del "%TEMP%\jre17.msi" >nul 2>&1
        echo      JRE 17 installed.
    ) else (
        echo      WARNING: Could not download JRE. If Java is already installed, continue.
    )
) else (
    echo      Java already installed.
)

echo [3/7] Downloading and installing XAMPP (MySQL + Apache)...
if not exist "C:\xampp\xampp-control.exe" (
    echo      Downloading XAMPP...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://sourceforge.net/projects/xampp/files/XAMPP%%20Windows/8.2.12/xampp-windows-x64-8.2.12-0-VS16-installer.exe/download' -OutFile '%TEMP%\xampp-installer.exe'"
    if exist "%TEMP%\xampp-installer.exe" (
        echo      Installing XAMPP (this may take a few minutes)...
        "%TEMP%\xampp-installer.exe" --unattendedmodeui none --installer-language en --install_dir C:\xampp --components "mysql,apache,php" --mode unattended
        timeout /t 60 /nobreak >nul
        del "%TEMP%\xampp-installer.exe" >nul 2>&1
        echo      XAMPP installed.
    ) else (
        echo      ERROR: Could not download XAMPP. Please install manually from https://www.apachefriends.org/
        echo      Press any key after installing XAMPP...
        pause >nul
    )
) else (
    echo      XAMPP already installed.
)

echo [4/7] Starting MySQL and creating database...
if exist "C:\xampp\mysql\bin\mysql.exe" (
    net start MySQL80 >nul 2>&1
    timeout /t 5 /nobreak >nul
    "C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE IF NOT EXISTS pointagepro CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>nul
    echo      Database 'pointagepro' ready.
) else (
    echo      WARNING: MySQL not found at C:\xampp. Check XAMPP installation.
)

echo [5/7] Copying frontend files to Apache...
if exist "C:\xampp\htdocs" (
    if not exist "C:\xampp\htdocs\pointagepro" mkdir "C:\xampp\htdocs\pointagepro"
    xcopy /E /Y /Q "%DEPLOY_DIR%frontend\*" "C:\xampp\htdocs\pointagepro\" >nul
    echo      Frontend deployed to http://localhost/pointagepro/
) else (
    echo      WARNING: C:\xampp\htdocs not found. Copy frontend files manually.
)

echo [6/7] Copying backend JAR...
copy /Y "%DEPLOY_DIR%backend\pointagepro.jar" "%INSTALL_DIR%\backend\pointagepro.jar" >nul
echo      Backend JAR copied to %INSTALL_DIR%\backend\

echo [7/7] Registering backend as Windows service...
where nssm >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo      Downloading NSSM...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://nssm.cc/release/nssm-2.24.zip' -OutFile '%TEMP%\nssm.zip'"
    if exist "%TEMP%\nssm.zip" (
        powershell -Command "Expand-Archive -Path '%TEMP%\nssm.zip' -DestinationPath '%TEMP%\nssm' -Force"
        copy /Y "%TEMP%\nssm\nssm-2.24\win64\nssm.exe" "%INSTALL_DIR%\nssm.exe" >nul
        copy /Y "%TEMP%\nssm\nssm-2.24\win64\nssm.exe" "C:\Windows\nssm.exe" >nul
        del "%TEMP%\nssm.zip" >nul 2>&1
        rmdir /S /Q "%TEMP%\nssm" >nul 2>&1
        echo      NSSM downloaded.
    ) else (
        echo      ERROR: Could not download NSSM.
        echo      Download manually from https://nssm.cc/download and put nssm.exe in C:\PointagePro\
    )
)

:: Stop existing service if any
nssm stop PointagePro >nul 2>&1
nssm remove PointagePro confirm >nul 2>&1

:: Find java executable
set JAVA_PATH=
for /f "delims=" %%i in ('where java 2^>nul') do (
    if not defined JAVA_PATH set "JAVA_PATH=%%i"
)
if not defined JAVA_PATH (
    for /f "delims=" %%i in ('dir /b /s "C:\Program Files\Eclipse Adoptium\*java.exe" 2^>nul') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)
if not defined JAVA_PATH (
    for /f "delims=" %%i in ('dir /b /s "C:\Program Files\Java\*java.exe" 2^>nul') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)

if defined JAVA_PATH (
    echo      Registering service with java at: !JAVA_PATH!
    nssm install PointagePro "!JAVA_PATH!" "-jar" "%INSTALL_DIR%\backend\pointagepro.jar"
    nssm set PointagePro AppDirectory "%INSTALL_DIR%"
    nssm set PointagePro AppStdout "%INSTALL_DIR%\logs\stdout.log"
    nssm set PointagePro AppStderr "%INSTALL_DIR%\logs\stderr.log"
    nssm set PointagePro AppRotateFiles 1
    nssm set PointagePro AppRotateBytes 10485760
    nssm set PointagePro Description "PointagePro Backend API"
    nssm set PointagePro Start SERVICE_AUTO_START
    nssm start PointagePro
    echo      Service 'PointagePro' registered and started.
) else (
    echo      ERROR: Java not found. Please install JRE 17 manually.
    echo      Then run: nssm install PointagePro "C:\path\to\java.exe" "-jar C:\PointagePro\backend\pointagepro.jar"
)

echo.
echo ============================================
echo   INSTALLATION COMPLETE!
echo ============================================
echo.
echo   Frontend:  http://localhost/pointagepro/
echo   Backend:   http://localhost:8080/api/v1/
echo   Database:  MySQL on localhost:3306
echo   Service:   PointagePro (auto-starts on boot)
echo.
echo   Login: admin / admin000
echo.
echo   The app will start automatically when the PC boots.
echo   To restart manually:
echo     - XAMPP: Open C:\xampp\xampp-control.exe
echo     - Backend: nssm restart PointagePro
echo.
pause

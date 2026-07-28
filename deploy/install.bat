@echo off
title PointagePro Installer
echo ============================================
echo   PointagePro - Installer (Company PC)
echo   Run as Administrator!
echo ============================================
echo.

:: ALWAYS keep window open
:: Check admin
net session >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Right-click this file and select "Run as administrator"
    echo.
    pause
    exit /b 1
)

set INSTALL_DIR=C:\PointagePro
set DEPLOY_DIR=%~dp0
set LOG=%INSTALL_DIR%\install.log

:: Create dirs
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%INSTALL_DIR%\backend" mkdir "%INSTALL_DIR%\backend"
if not exist "%INSTALL_DIR%\logs" mkdir "%INSTALL_DIR%\logs"

echo [%date% %time%] Starting install > "%LOG%"

echo ========================================
echo  STEP 1/7: Java Runtime (JRE 17)
echo ========================================
where java >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK] Java already installed
    echo  Java already installed >> "%LOG%"
) else (
    echo  Checking common Java locations...
    set JAVA_FOUND=0
    if exist "C:\Program Files\Eclipse Adoptium" set JAVA_FOUND=1
    if exist "C:\Program Files\Java" set JAVA_FOUND=1
    if "!JAVA_FOUND!"=="1" (
        echo  [OK] Java found in Program Files
        echo  Java found in Program Files >> "%LOG%"
    ) else (
        echo  Downloading JRE 17 (~170MB)...
        echo  Downloading JRE 17 >> "%LOG%"
        curl.exe -L --progress-bar -o "%TEMP%\jre17.msi" "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%%2B11/OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.msi"
        if %ERRORLEVEL% neq 0 (
            echo  [FAIL] curl failed to download JRE
            echo  curl failed >> "%LOG%"
        ) else if not exist "%TEMP%\jre17.msi" (
            echo  [FAIL] File not created
            echo  File not created >> "%LOG%"
        ) else (
            echo  Installing JRE 17 (silent, ~30 sec)...
            echo  Installing JRE 17 >> "%LOG%"
            msiexec /i "%TEMP%\jre17.msi" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /quiet /norestart
            timeout /t 40 /nobreak >nul
            del "%TEMP%\jre17.msi" >nul 2>&1
            echo  [OK] JRE 17 installed
            echo  JRE 17 installed >> "%LOG%"
        )
    )
)

echo.
echo ========================================
echo  STEP 2/7: XAMPP (MySQL + Apache)
echo ========================================
if exist "C:\xampp\xampp-control.exe" (
    echo  [OK] XAMPP already installed
    echo  XAMPP already installed >> "%LOG%"
) else (
    echo  Downloading XAMPP (~170MB)...
    echo  Downloading XAMPP >> "%LOG%"
    curl.exe -L --progress-bar -o "%TEMP%\xampp-installer.exe" "https://sourceforge.net/projects/xampp/files/XAMPP%%20Windows/8.2.12/xampp-windows-x64-8.2.12-0-VS16-installer.exe/download"
    if %ERRORLEVEL% neq 0 (
        echo  [FAIL] curl failed to download XAMPP
        echo  XAMPP download failed >> "%LOG%"
    ) else if not exist "%TEMP%\xampp-installer.exe" (
        echo  [FAIL] File not created
        echo  XAMPP file not created >> "%LOG%"
    ) else (
        echo  Installing XAMPP (silent, ~2 min)...
        echo  Installing XAMPP >> "%LOG%"
        "%TEMP%\xampp-installer.exe" --unattendedmodeui none --installer-language en --install_dir C:\xampp --components "mysql,apache,php" --mode unattended
        timeout /t 60 /nobreak >nul
        del "%TEMP%\xampp-installer.exe" >nul 2>&1
        echo  [OK] XAMPP installed
        echo  XAMPP installed >> "%LOG%"
    )
)

echo.
echo ========================================
echo  STEP 3/7: Create Database
echo ========================================
:: Start MySQL
if exist "C:\xampp\xampp_start.exe" (
    echo  Starting MySQL...
    "C:\xampp\xampp_start.exe" mysql >nul 2>&1
    timeout /t 8 /nobreak >nul
)
if exist "C:\xampp\mysql\bin\mysql.exe" (
    "C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE IF NOT EXISTS pointagepro CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>>"%LOG%"
    echo  [OK] Database 'pointagepro' ready
    echo  Database created >> "%LOG%"
) else (
    echo  [WARN] MySQL not found at C:\xampp
    echo  MySQL not found >> "%LOG%"
)

echo.
echo ========================================
echo  STEP 4/7: Deploy Frontend
echo ========================================
if exist "C:\xampp\htdocs" (
    if not exist "C:\xampp\htdocs\pointagepro" mkdir "C:\xampp\htdocs\pointagepro"
    xcopy /E /Y /Q /H "%DEPLOY_DIR%frontend\*" "C:\xampp\htdocs\pointagepro\" >nul 2>&1
    echo  [OK] Frontend deployed to http://localhost/pointagepro/
    echo  Frontend deployed >> "%LOG%"
) else (
    echo  [WARN] C:\xampp\htdocs not found
    echo  htdocs not found >> "%LOG%"
)

echo.
echo ========================================
echo  STEP 5/7: Deploy Backend
echo ========================================
copy /Y "%DEPLOY_DIR%backend\pointagepro.jar" "%INSTALL_DIR%\backend\pointagepro.jar" >nul 2>&1
if exist "%INSTALL_DIR%\backend\pointagepro.jar" (
    echo  [OK] Backend JAR copied
    echo  Backend JAR copied >> "%LOG%"
) else (
    echo  [FAIL] Could not copy JAR
    echo  JAR copy failed >> "%LOG%"
)

echo.
echo ========================================
echo  STEP 6/7: Download NSSM
echo ========================================
where nssm >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo  [OK] NSSM already available
    echo  NSSM already available >> "%LOG%"
) else if exist "C:\Windows\nssm.exe" (
    echo  [OK] NSSM already in Windows
    echo  NSSM in Windows >> "%LOG%"
) else (
    echo  Downloading NSSM...
    echo  Downloading NSSM >> "%LOG%"
    curl.exe -L --progress-bar -o "%TEMP%\nssm.zip" "https://nssm.cc/release/nssm-2.24.zip"
    if %ERRORLEVEL% neq 0 (
        echo  [FAIL] Could not download NSSM
        echo  NSSM download failed >> "%LOG%"
    ) else (
        powershell -Command "Expand-Archive -Path '%TEMP%\nssm.zip' -DestinationPath '%TEMP%\nssm' -Force"
        if exist "%TEMP%\nssm\nssm-2.24\win64\nssm.exe" (
            copy /Y "%TEMP%\nssm\nssm-2.24\win64\nssm.exe" "C:\Windows\nssm.exe" >nul
            echo  [OK] NSSM installed
            echo  NSSM installed >> "%LOG%"
        )
        del "%TEMP%\nssm.zip" >nul 2>&1
        rmdir /S /Q "%TEMP%\nssm" >nul 2>&1
    )
)

echo.
echo ========================================
echo  STEP 7/7: Register Windows Service
echo ========================================
:: Remove old service
nssm stop PointagePro >nul 2>&1
nssm remove PointagePro confirm >nul 2>&1

:: Find java.exe
set JAVA_PATH=
:: Try where java first
where java >nul 2>&1
if %ERRORLEVEL% equ 0 (
    for /f "delims=" %%i in ('where java') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)
:: Search Adoptium
if not defined JAVA_PATH (
    for /f "delims=" %%i in ('dir /b /s "C:\Program Files\Eclipse Adoptium\*java.exe" 2^>nul') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)
:: Search Java folder
if not defined JAVA_PATH (
    for /f "delims=" %%i in ('dir /b /s "C:\Program Files\Java\*java.exe" 2^>nul') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)
:: Search x86 Java
if not defined JAVA_PATH (
    for /f "delims=" %%i in ('dir /b /s "C:\Program Files (x86)\Java\*java.exe" 2^>nul') do (
        if not defined JAVA_PATH set "JAVA_PATH=%%i"
    )
)

if defined JAVA_PATH (
    echo  Found java: !JAVA_PATH!
    echo  Registering service... >> "%LOG%"
    nssm install PointagePro "!JAVA_PATH!" "-jar" "%INSTALL_DIR%\backend\pointagepro.jar"
    nssm set PointagePro AppDirectory "%INSTALL_DIR%"
    nssm set PointagePro AppStdout "%INSTALL_DIR%\logs\stdout.log"
    nssm set PointagePro AppStderr "%INSTALL_DIR%\logs\stderr.log"
    nssm set PointagePro AppRotateFiles 1
    nssm set PointagePro AppRotateBytes 10485760
    nssm set PointagePro Description "PointagePro Backend API"
    nssm set PointagePro Start SERVICE_AUTO_START
    echo  Starting service...
    nssm start PointagePro
    echo  [OK] Service 'PointagePro' registered and started
    echo  Service started >> "%LOG%"
) else (
    echo  [FAIL] Java not found after installation
    echo  You may need to RESTART the PC first, then run install.bat again
    echo  Java not found >> "%LOG%"
)

echo.
echo ============================================
echo   INSTALLATION DONE!
echo ============================================
echo.
echo   Frontend:  http://localhost/pointagepro/
echo   Backend:   http://localhost:8080/api/v1/
echo   Database:  MySQL on localhost:3306
echo   Service:   PointagePro (auto-starts on boot)
echo.
echo   Login: admin / admin000
echo.
echo   Log saved to: %LOG%
echo.
echo ============================================
echo   PRESS ANY KEY TO CLOSE...
echo ============================================
pause >nul

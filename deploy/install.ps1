Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  PointagePro - Installer (Company PC)" -ForegroundColor Cyan
Write-Host "  Right-click -> Run as Administrator" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Check admin
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: Run as Administrator!" -ForegroundColor Red
    Write-Host "Right-click this file -> Run as administrator" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

$INSTALL_DIR = "C:\PointagePro"
$DEPLOY_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$LOG = "$INSTALL_DIR\install.log"

New-Item -ItemType Directory -Force -Path "$INSTALL_DIR\backend" | Out-Null
New-Item -ItemType Directory -Force -Path "$INSTALL_DIR\logs" | Out-Null

"[$(Get-Date)] Starting install" | Out-File $LOG

Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 1/7: Java Runtime (JRE 17)"
Write-Host "=======================================" -ForegroundColor Yellow

$javaExe = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaExe) {
    $adoptium = "C:\Program Files\Eclipse Adoptium"
    $javaDir = "C:\Program Files\Java"
    if ((Test-Path $adoptium) -or (Test-Path $javaDir)) {
        Write-Host "  [OK] Java found in Program Files" -ForegroundColor Green
        "Java found" | Out-File $LOG -Append
    } else {
        Write-Host "  Downloading JRE 17 (~170MB)..." -ForegroundColor Cyan
        "Downloading JRE 17" | Out-File $LOG -Append
        curl.exe -L --progress-bar -o "$env:TEMP\jre17.msi" "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.msi"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  [FAIL] Download failed" -ForegroundColor Red
        } else {
            Write-Host "  Installing JRE 17 (~30 sec)..." -ForegroundColor Cyan
            Start-Process msiexec.exe -ArgumentList "/i `"$env:TEMP\jre17.msi`" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /quiet /norestart" -Wait -NoNewWindow
            Start-Sleep -Seconds 5
            Remove-Item "$env:TEMP\jre17.msi" -Force -ErrorAction SilentlyContinue
            Write-Host "  [OK] JRE 17 installed" -ForegroundColor Green
            "JRE 17 installed" | Out-File $LOG -Append
        }
    }
} else {
    Write-Host "  [OK] Java already installed" -ForegroundColor Green
    "Java already installed" | Out-File $LOG -Append
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 2/7: XAMPP (MySQL + Apache)"
Write-Host "=======================================" -ForegroundColor Yellow

if (Test-Path "C:\xampp\xampp-control.exe") {
    Write-Host "  [OK] XAMPP already installed" -ForegroundColor Green
    "XAMPP already installed" | Out-File $LOG -Append
} else {
    Write-Host "  Downloading XAMPP (~170MB)..." -ForegroundColor Cyan
    "Downloading XAMPP" | Out-File $LOG -Append
    curl.exe -L --progress-bar -o "$env:TEMP\xampp-installer.exe" "https://sourceforge.net/projects/xampp/files/XAMPP%20Windows/8.2.12/xampp-windows-x64-8.2.12-0-VS16-installer.exe/download"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Download failed" -ForegroundColor Red
    } else {
        Write-Host "  Installing XAMPP (~2 min)..." -ForegroundColor Cyan
        Start-Process "$env:TEMP\xampp-installer.exe" -ArgumentList "--unattendedmodeui none --installer-language en --install_dir C:\xampp --components `"mysql,apache,php`" --mode unattended" -Wait -NoNewWindow
        Start-Sleep -Seconds 10
        Remove-Item "$env:TEMP\xampp-installer.exe" -Force -ErrorAction SilentlyContinue
        Write-Host "  [OK] XAMPP installed" -ForegroundColor Green
        "XAMPP installed" | Out-File $LOG -Append
    }
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 3/7: Create Database"
Write-Host "=======================================" -ForegroundColor Yellow

if (Test-Path "C:\xampp\xampp_start.exe") {
    Write-Host "  Starting MySQL..."
    Start-Process "C:\xampp\xampp_start.exe" -ArgumentList "mysql" -NoNewWindow -Wait -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 8
}
if (Test-Path "C:\xampp\mysql\bin\mysql.exe") {
    & "C:\xampp\mysql\bin\mysql.exe" -u root -e "CREATE DATABASE IF NOT EXISTS pointagepro CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>>$LOG
    Write-Host "  [OK] Database 'pointagepro' ready" -ForegroundColor Green
    "Database created" | Out-File $LOG -Append
} else {
    Write-Host "  [WARN] MySQL not found" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 4/7: Deploy Frontend"
Write-Host "=======================================" -ForegroundColor Yellow

if (Test-Path "C:\xampp\htdocs") {
    $target = "C:\xampp\htdocs\pointagepro"
    if (-not (Test-Path $target)) { New-Item -ItemType Directory -Force -Path $target | Out-Null }
    Copy-Item -Path "$DEPLOY_DIR\frontend\*" -Destination $target -Recurse -Force
    Write-Host "  [OK] Frontend -> http://localhost/pointagepro/" -ForegroundColor Green
    "Frontend deployed" | Out-File $LOG -Append
} else {
    Write-Host "  [WARN] C:\xampp\htdocs not found" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 5/7: Deploy Backend"
Write-Host "=======================================" -ForegroundColor Yellow

Copy-Item -Path "$DEPLOY_DIR\backend\pointagepro.jar" -Destination "$INSTALL_DIR\backend\pointagepro.jar" -Force
if (Test-Path "$INSTALL_DIR\backend\pointagepro.jar") {
    Write-Host "  [OK] Backend JAR copied" -ForegroundColor Green
    "Backend JAR copied" | Out-File $LOG -Append
} else {
    Write-Host "  [FAIL] Could not copy JAR" -ForegroundColor Red
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 6/7: Download NSSM"
Write-Host "=======================================" -ForegroundColor Yellow

$nssmFound = Get-Command nssm -ErrorAction SilentlyContinue
if ($nssmFound -or (Test-Path "C:\Windows\nssm.exe")) {
    Write-Host "  [OK] NSSM already available" -ForegroundColor Green
    "NSSM already available" | Out-File $LOG -Append
} else {
    Write-Host "  Downloading NSSM..." -ForegroundColor Cyan
    "Downloading NSSM" | Out-File $LOG -Append
    curl.exe -L --progress-bar -o "$env:TEMP\nssm.zip" "https://nssm.cc/release/nssm-2.24.zip"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Download failed" -ForegroundColor Red
    } else {
        Expand-Archive -Path "$env:TEMP\nssm.zip" -DestinationPath "$env:TEMP\nssm" -Force
        if (Test-Path "$env:TEMP\nssm\nssm-2.24\win64\nssm.exe") {
            Copy-Item "$env:TEMP\nssm\nssm-2.24\win64\nssm.exe" "C:\Windows\nssm.exe" -Force
            Write-Host "  [OK] NSSM installed" -ForegroundColor Green
            "NSSM installed" | Out-File $LOG -Append
        }
        Remove-Item "$env:TEMP\nssm.zip" -Force -ErrorAction SilentlyContinue
        Remove-Item "$env:TEMP\nssm" -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Yellow
Write-Host " STEP 7/7: Register Windows Service"
Write-Host "=======================================" -ForegroundColor Yellow

& nssm stop PointagePro 2>$null
& nssm remove PointagePro confirm 2>$null

$javaPath = $null
$cmd = Get-Command java -ErrorAction SilentlyContinue
if ($cmd) { $javaPath = $cmd.Source }

if (-not $javaPath) {
    $searchPaths = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files (x86)\Java"
    )
    foreach ($sp in $searchPaths) {
        if (Test-Path $sp) {
            $found = Get-ChildItem -Path $sp -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($found) { $javaPath = $found.FullName; break }
        }
    }
}

if ($javaPath) {
    Write-Host "  Found java: $javaPath" -ForegroundColor Green
    "Java: $javaPath" | Out-File $LOG -Append
    & nssm install PointagePro "$javaPath" "-jar" "$INSTALL_DIR\backend\pointagepro.jar"
    & nssm set PointagePro AppDirectory "$INSTALL_DIR"
    & nssm set PointagePro AppStdout "$INSTALL_DIR\logs\stdout.log"
    & nssm set PointagePro AppStderr "$INSTALL_DIR\logs\stderr.log"
    & nssm set PointagePro AppRotateFiles 1
    & nssm set PointagePro AppRotateBytes 10485760
    & nssm set PointagePro Description "PointagePro Backend API"
    & nssm set PointagePro Start SERVICE_AUTO_START
    Write-Host "  Starting service..."
    & nssm start PointagePro
    Write-Host "  [OK] Service 'PointagePro' started" -ForegroundColor Green
    "Service started" | Out-File $LOG -Append
} else {
    Write-Host "  [FAIL] Java not found" -ForegroundColor Red
    Write-Host "  Restart PC, then run this script again" -ForegroundColor Yellow
    "Java not found" | Out-File $LOG -Append
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  INSTALLATION DONE!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:  http://localhost/pointagepro/"
Write-Host "  Backend:   http://localhost:8080/api/v1/"
Write-Host "  Database:  MySQL on localhost:3306"
Write-Host "  Service:   PointagePro (auto-starts on boot)"
Write-Host ""
Write-Host "  Login: admin / admin000"
Write-Host ""
Write-Host "  Log: $LOG"
Write-Host ""
Read-Host "Press Enter to close"

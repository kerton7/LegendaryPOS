@echo off
setlocal
cd /d "%~dp0"
if not exist "frontend\target\efsane-kokorec-frontend-1.0.0.jar" (
  echo Once mvn clean package komutunu calistirin.
  exit /b 1
)
set "BUILD_ID=%RANDOM%"
set "INPUT_DIR=dist-input-%BUILD_ID%"
set "OUTPUT_DIR=dist-build-%BUILD_ID%"
mkdir "%INPUT_DIR%"
copy "frontend\target\efsane-kokorec-frontend-1.0.0.jar" "%INPUT_DIR%\" >nul
jpackage --type app-image --name EfsaneKokorec --input "%INPUT_DIR%" --main-jar efsane-kokorec-frontend-1.0.0.jar --main-class app.frontend.Launcher --dest "%OUTPUT_DIR%"
if errorlevel 1 exit /b %errorlevel%
rmdir /s /q "%INPUT_DIR%"
if not exist "%OUTPUT_DIR%\EfsaneKokorec\data" mkdir "%OUTPUT_DIR%\EfsaneKokorec\data"
echo Uygulama hazir: %OUTPUT_DIR%\EfsaneKokorec\EfsaneKokorec.exe
endlocal

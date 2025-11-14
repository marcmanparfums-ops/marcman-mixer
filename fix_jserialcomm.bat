@echo off
REM Script to fix jSerialComm DLL issues after Windows update
REM This script deletes the jSerialComm cache folders to force re-extraction

echo ========================================
echo   Fixing jSerialComm DLL Issues
echo ========================================
echo.

set "TEMP_DIR=%LOCALAPPDATA%\Temp\jSerialComm"
set "HOME_DIR=%USERPROFILE%\.jSerialComm"

echo Checking for jSerialComm cache folders...
echo.

REM Delete Temp folder
if exist "%TEMP_DIR%" (
    echo Found: %TEMP_DIR%
    echo Attempting to delete...
    REM Try to delete with force
    rd /s /q "%TEMP_DIR%" 2>nul
    timeout /t 1 /nobreak >nul
    if exist "%TEMP_DIR%" (
        echo WARNING: Could not delete %TEMP_DIR%
        echo This folder may be locked by another process.
        echo.
        echo Please:
        echo 1. Close ALL Java applications
        echo 2. Run this script as Administrator
        echo 3. Or delete manually: %TEMP_DIR%
    ) else (
        echo Successfully deleted: %TEMP_DIR%
    )
) else (
    echo Not found: %TEMP_DIR%
)

echo.

REM Delete Home folder
if exist "%HOME_DIR%" (
    echo Found: %HOME_DIR%
    echo Attempting to delete...
    REM Try to delete with force
    rd /s /q "%HOME_DIR%" 2>nul
    timeout /t 1 /nobreak >nul
    if exist "%HOME_DIR%" (
        echo WARNING: Could not delete %HOME_DIR%
        echo This folder may be locked by another process.
        echo.
        echo Please:
        echo 1. Close ALL Java applications
        echo 2. Run this script as Administrator
        echo 3. Or delete manually: %HOME_DIR%
    ) else (
        echo Successfully deleted: %HOME_DIR%
    )
) else (
    echo Not found: %HOME_DIR%
)

echo.
echo ========================================
echo   Cleanup Complete
echo ========================================
echo.
echo Next steps:
echo 1. Make sure ALL Java applications are closed
echo 2. If folders still exist, run this script as Administrator
echo 3. Restart the MarcmanMixer application
echo 4. jSerialComm will automatically re-extract the correct DLL
echo.
echo NOTE: The DLL will be re-extracted for your platform (AMD64/x64)
echo       when you first use serial port functionality.
echo.
pause


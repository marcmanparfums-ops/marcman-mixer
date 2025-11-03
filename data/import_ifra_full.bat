@echo off
REM Import script for full IFRA ingredients list

echo ========================================
echo IFRA Full List Import to Database
echo ========================================
echo.

REM Set environment
set JAVA_HOME=C:\Java\jdk-21.0.5+11
set MAVEN_HOME=C:\Maven\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

cd /d "c:\Users\Marcman\Documents\MarcmanMixer"

echo Checking for CSV file...
if not exist "data\ifra_ingredients_full.csv" (
    echo ERROR: ifra_ingredients_full.csv not found!
    echo.
    echo Please run: python data\scrape_ifra.py
    echo.
    pause
    exit /b 1
)

echo CSV file found. Starting import...
echo.

mvn exec:java -Dexec.mainClass="ro.marcman.mixer.sqlite.IfraDataImporter" ^
              -Dexec.args="data/ifra_ingredients_full.csv" ^
              -pl sqlite

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Import failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Import completed successfully!
echo ========================================
echo.
echo Database location: marcman_mixer.db
echo.
pause




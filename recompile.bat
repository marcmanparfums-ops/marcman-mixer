@echo off
echo ========================================
echo Recompilare completă proiect MarcmanMixer
echo ========================================
echo.

set JAVA_HOME=C:\Java\jdk-21.0.5+11
set MAVEN_HOME=C:\Maven\apache-maven-3.9.6
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

cd /d "%~dp0"

echo Curățare build-uri anterioare...
call mvn clean -q

echo.
echo Compilare și instalare toate modulele...
call mvn install -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo [SUCCESS] Recompilare completă reușită!
    echo ========================================
    echo.
    echo JAR-uri create:
    dir /b /s target\*.jar 2>nul | findstr /V "original-"
) else (
    echo.
    echo ========================================
    echo [ERROR] Recompilare eșuată!
    echo ========================================
    exit /b 1
)

pause









@ECHO OFF
REM BFCPEOPTIONSTART
REM Advanced BAT to EXE Converter www.BatToExeConverter.com
REM BFCPEEXE=
REM BFCPEICON=
REM BFCPEICONINDEX=-1
REM BFCPEEMBEDDISPLAY=0
REM BFCPEEMBEDDELETE=1
REM BFCPEADMINEXE=0
REM BFCPEINVISEXE=0
REM BFCPEVERINCLUDE=0
REM BFCPEVERVERSION=1.0.0.0
REM BFCPEVERPRODUCT=Product Name
REM BFCPEVERDESC=Product Description
REM BFCPEVERCOMPANY=Your Company
REM BFCPEVERCOPYRIGHT=Copyright Info
REM BFCPEWINDOWCENTER=1
REM BFCPEDISABLEQE=0
REM BFCPEWINDOWHEIGHT=30
REM BFCPEWINDOWWIDTH=120
REM BFCPEWTITLE=Window Title
REM BFCPEOPTIONEND
@echo off
REM Simple launcher - fixes path issues
setlocal enabledelayedexpansion

cd /d "%~dp0"

REM Set Java and Maven
set "JAVA_HOME=C:\Java\jdk-21.0.5+11"
set "MAVEN_HOME=C:\Maven\apache-maven-3.9.6"

REM Verify Java exists
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java not found at: %JAVA_HOME%
    echo Please check JAVA_HOME path.
    pause
    exit /b 1
)

REM Verify Maven exists
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo ERROR: Maven not found at: %MAVEN_HOME%
    echo Please check MAVEN_HOME path.
    pause
    exit /b 1
)

REM Add to PATH
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo ========================================
echo    MarcmanMixer - Parfum Management
echo ========================================
echo.
echo Working Directory: %CD%
echo Java: %JAVA_HOME%
echo Maven: %MAVEN_HOME%
echo.

REM Build first if needed - build all modules
if not exist "app\target\app-1.0.0-SNAPSHOT.jar" (
    echo Building application first...
    echo This may take a minute...
    echo.
    call "%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests
    if errorlevel 1 (
        echo.
        echo ERROR: Build failed!
        echo Check the error messages above.
        pause
        exit /b 1
    )
    echo Build completed.
    echo.
)

echo Starting application...
echo.

REM Check for shaded JAR (preferred) or regular JAR
set "SHADED_JAR=app\target\app-1.0.0-SNAPSHOT-shaded.jar"
set "REGULAR_JAR=app\target\app-1.0.0-SNAPSHOT.jar"

REM Try Maven javafx:run first (best method)
call "%MAVEN_HOME%\bin\mvn.cmd" javafx:run -pl app 2>&1
set EXIT_CODE=%ERRORLEVEL%

REM If Maven javafx:run fails, try alternative: run shaded JAR directly
if %EXIT_CODE% neq 0 (
    echo.
    echo Maven javafx:run failed ^(exit code: %EXIT_CODE%^)
    echo Trying alternative launch method...
    echo.
    
    if exist "%SHADED_JAR%" (
        echo Using shaded JAR with all dependencies...
        echo.
        REM Shaded JAR should have all dependencies including JavaFX
        "%JAVA_HOME%\bin\java.exe" -jar "%SHADED_JAR%"
        set EXIT_CODE=%ERRORLEVEL%
    ) else if exist "%REGULAR_JAR%" (
        echo Using regular JAR - trying with module path...
        echo.
        REM Try to find JavaFX JARs in Maven repository or use module path
        REM Get Maven local repository path
        for /f "tokens=*" %%i in ('call "%MAVEN_HOME%\bin\mvn.cmd" help:evaluate -Dexpression=settings.localRepository -q -DforceStdout') do set "MAVEN_REPO=%%i"
        
        REM Try with JavaFX from Maven repository
        if defined MAVEN_REPO (
            set "JAVAFX_PATH=!MAVEN_REPO!\org\openjfx\javafx-controls\23.0.2"
            if exist "!JAVAFX_PATH!" (
                for /d %%d in ("!JAVAFX_PATH!\*") do (
                    set "JAVAFX_JAR=%%d\javafx-controls-23.0.2.jar"
                    if exist "!JAVAFX_JAR!" (
                        "%JAVA_HOME%\bin\java.exe" --module-path "%%d" --add-modules javafx.controls,javafx.fxml -cp "%REGULAR_JAR%" ro.marcman.mixer.app.App
                        set EXIT_CODE=%ERRORLEVEL%
                        goto :done_alt
                    )
                )
            )
        )
        
        REM Last resort: try without module path (will fail if JavaFX not bundled)
        echo WARNING: JavaFX modules not found, trying direct execution...
        "%JAVA_HOME%\bin\java.exe" -cp "%REGULAR_JAR%" ro.marcman.mixer.app.App
        set EXIT_CODE=%ERRORLEVEL%
        
        :done_alt
    ) else (
        echo ERROR: No JAR file found!
        echo Expected locations:
        echo   %SHADED_JAR%
        echo   %REGULAR_JAR%
        echo.
        echo Please build the project first:
        echo   call "%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests
        set EXIT_CODE=1
    )
)

if %EXIT_CODE% neq 0 (
    echo.
    echo ========================================
    echo ERROR: Failed to start application!
    echo ========================================
    echo.
    echo Exit code: %EXIT_CODE%
    echo Working directory: %CD%
    echo.
    echo Troubleshooting steps:
    echo 1. Build all modules:
    echo    call "%MAVEN_HOME%\bin\mvn.cmd" clean package -DskipTests
    echo.
    echo 2. Check Java version:
    echo    "%JAVA_HOME%\bin\java.exe" -version
    echo.
    echo 3. Check Maven version:
    echo    "%MAVEN_HOME%\bin\mvn.cmd" -version
    echo.
    echo 4. Verify JAR files exist:
    echo    dir app\target\*.jar
    echo.
    echo 5. Try running manually:
    echo    "%MAVEN_HOME%\bin\mvn.cmd" javafx:run -pl app
    echo.
    pause
    exit /b %EXIT_CODE%
)

pause

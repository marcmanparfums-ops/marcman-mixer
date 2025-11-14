# Script to fix jSerialComm DLL by manually copying the correct AMD64 DLL
# This script extracts the correct DLL from jSerialComm JAR and places it in cache folders

Write-Host "========================================"
Write-Host "  Fixing jSerialComm DLL - Manual Copy"
Write-Host "========================================"
Write-Host ""

$mavenRepo = "$env:USERPROFILE\.m2\repository"
$jSerialCommJar = Join-Path $mavenRepo "com\fazecast\jSerialComm\2.10.4\jSerialComm-2.10.4.jar"

if (-not (Test-Path $jSerialCommJar)) {
    Write-Host "ERROR: jSerialComm JAR not found at: $jSerialCommJar"
    Write-Host "Please build the project first to download dependencies."
    Pause
    exit 1
}

Write-Host "Found jSerialComm JAR: $jSerialCommJar"
Write-Host ""

# Create temp directory for extraction
$tempExtract = Join-Path $env:TEMP "jSerialComm_extract_$(Get-Random)"
New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null

try {
    Write-Host "Extracting JAR contents..."
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jSerialCommJar, $tempExtract)
    
    # Find the AMD64/x64 DLL (x86_64 is the correct one for AMD64 systems)
    $dllPath = Get-ChildItem -Path $tempExtract -Recurse -Filter "jSerialComm.dll" | 
        Where-Object { 
            $_.FullName -like "*windows*x86_64*" -or 
            $_.FullName -like "*windows*amd64*" -or 
            ($_.FullName -like "*windows*x64*" -and $_.FullName -notlike "*x86*")
        } |
        Select-Object -First 1
    
    if (-not $dllPath) {
        Write-Host "WARNING: AMD64/x86_64 DLL not found in JAR. Trying to find any DLL..."
        $dllPath = Get-ChildItem -Path $tempExtract -Recurse -Filter "jSerialComm.dll" | Select-Object -First 1
    }
    
    if (-not $dllPath) {
        Write-Host "ERROR: Could not find jSerialComm.dll in JAR"
        Remove-Item -Path $tempExtract -Recurse -Force
        Pause
        exit 1
    }
    
    Write-Host "Found DLL: $($dllPath.FullName)"
    Write-Host ""
    
    # Target directories
    $tempDir = Join-Path $env:LOCALAPPDATA "Temp\jSerialComm\2.10.4"
    $homeDir = Join-Path $env:USERPROFILE ".jSerialComm\2.10.4"
    
    # Create directories and copy DLL
    Write-Host "Copying DLL to cache directories..."
    
    # Copy to temp directory
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    $tempDll = Join-Path $tempDir "jSerialComm.dll"
    Copy-Item -Path $dllPath.FullName -Destination $tempDll -Force
    Write-Host "Copied to: $tempDll"
    
    # Copy to home directory
    New-Item -ItemType Directory -Path $homeDir -Force | Out-Null
    $homeDll = Join-Path $homeDir "jSerialComm.dll"
    Copy-Item -Path $dllPath.FullName -Destination $homeDll -Force
    Write-Host "Copied to: $homeDll"
    
    Write-Host ""
    Write-Host "========================================"
    Write-Host "  DLL Copy Complete"
    Write-Host "========================================"
    Write-Host ""
    Write-Host "The correct AMD64 DLL has been copied to:"
    Write-Host "  - $tempDll"
    Write-Host "  - $homeDll"
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. Restart the MarcmanMixer application"
    Write-Host "2. jSerialComm should now use the correct DLL"
    Write-Host ""
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    Write-Host $_.ScriptStackTrace
} finally {
    # Cleanup temp extraction
    if (Test-Path $tempExtract) {
        Remove-Item -Path $tempExtract -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Pause


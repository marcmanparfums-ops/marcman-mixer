# Script to check what DLLs are in jSerialComm JAR

$mavenRepo = "$env:USERPROFILE\.m2\repository"
$jSerialCommJar = Join-Path $mavenRepo "com\fazecast\jSerialComm\2.10.4\jSerialComm-2.10.4.jar"

if (-not (Test-Path $jSerialCommJar)) {
    Write-Host "ERROR: jSerialComm JAR not found at: $jSerialCommJar"
    exit 1
}

Write-Host "Extracting JAR to check DLLs..."
$tempExtract = Join-Path $env:TEMP "jserialcomm_check_$(Get-Random)"
New-Item -ItemType Directory -Path $tempExtract -Force | Out-Null

try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jSerialCommJar, $tempExtract)
    
    Write-Host ""
    Write-Host "DLLs found in JAR:"
    Write-Host "=================="
    Get-ChildItem -Path $tempExtract -Recurse -Filter "*.dll" | ForEach-Object {
        $relativePath = $_.FullName.Replace($tempExtract, "").TrimStart('\')
        Write-Host "  $relativePath ($($_.Length) bytes)"
    }
    
    Write-Host ""
    Write-Host "Looking for AMD64/x64 DLL..."
    $amd64Dll = Get-ChildItem -Path $tempExtract -Recurse -Filter "*.dll" | 
        Where-Object { 
            $_.FullName -like "*amd64*" -or 
            $_.FullName -like "*x64*" -or 
            $_.FullName -like "*x86_64*" -or
            ($_.FullName -like "*windows*" -and $_.FullName -notlike "*aarch64*" -and $_.FullName -notlike "*arm*")
        } | Select-Object -First 1
    
    if ($amd64Dll) {
        Write-Host "Found AMD64 DLL: $($amd64Dll.FullName)"
    } else {
        Write-Host "WARNING: No AMD64 DLL found! Only found:"
        Get-ChildItem -Path $tempExtract -Recurse -Filter "*.dll" | ForEach-Object {
            Write-Host "  $($_.FullName)"
        }
    }
    
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
} finally {
    if (Test-Path $tempExtract) {
        Remove-Item -Path $tempExtract -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Pause


# PowerShell script to forcefully delete jSerialComm cache folders
# Run this script as Administrator if folders are locked

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fixing jSerialComm DLL Issues" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$tempDir = "$env:LOCALAPPDATA\Temp\jSerialComm"
$homeDir = "$env:USERPROFILE\.jSerialComm"

Write-Host "Checking for jSerialComm cache folders..." -ForegroundColor Yellow
Write-Host ""

# Delete Temp folder
if (Test-Path $tempDir) {
    Write-Host "Found: $tempDir" -ForegroundColor Yellow
    Write-Host "Attempting to delete..." -ForegroundColor Yellow
    
    try {
        # Stop any processes that might be using the DLL
        Get-Process | Where-Object {$_.Path -like "*java*"} | ForEach-Object {
            Write-Host "Warning: Java process found: $($_.ProcessName) (PID: $($_.Id))" -ForegroundColor Yellow
            Write-Host "  Please close all Java applications before deleting jSerialComm folders" -ForegroundColor Yellow
        }
        
        # Force delete
        Remove-Item -Path $tempDir -Recurse -Force -ErrorAction Stop
        Write-Host "Successfully deleted: $tempDir" -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Could not delete $tempDir" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please:" -ForegroundColor Yellow
        Write-Host "  1. Close ALL Java applications" -ForegroundColor Yellow
        Write-Host "  2. Run this script as Administrator (Right-click -> Run as Administrator)" -ForegroundColor Yellow
        Write-Host "  3. Or delete manually: $tempDir" -ForegroundColor Yellow
    }
} else {
    Write-Host "Not found: $tempDir" -ForegroundColor Gray
}

Write-Host ""

# Delete Home folder
if (Test-Path $homeDir) {
    Write-Host "Found: $homeDir" -ForegroundColor Yellow
    Write-Host "Attempting to delete..." -ForegroundColor Yellow
    
    try {
        # Force delete
        Remove-Item -Path $homeDir -Recurse -Force -ErrorAction Stop
        Write-Host "Successfully deleted: $homeDir" -ForegroundColor Green
    } catch {
        Write-Host "ERROR: Could not delete $homeDir" -ForegroundColor Red
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please:" -ForegroundColor Yellow
        Write-Host "  1. Close ALL Java applications" -ForegroundColor Yellow
        Write-Host "  2. Run this script as Administrator (Right-click -> Run as Administrator)" -ForegroundColor Yellow
        Write-Host "  3. Or delete manually: $homeDir" -ForegroundColor Yellow
    }
} else {
    Write-Host "Not found: $homeDir" -ForegroundColor Gray
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Cleanup Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Make sure ALL Java applications are closed" -ForegroundColor White
Write-Host "2. If folders still exist, run this script as Administrator" -ForegroundColor White
Write-Host "3. Restart the MarcmanMixer application" -ForegroundColor White
Write-Host "4. jSerialComm will automatically re-extract the correct DLL (AMD64/x64)" -ForegroundColor White
Write-Host ""
Write-Host "NOTE: The DLL will be re-extracted for your platform when you first" -ForegroundColor Cyan
Write-Host "      use serial port functionality." -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to continue..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")



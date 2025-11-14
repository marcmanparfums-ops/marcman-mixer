# Set jSerialComm DLL to read-only to prevent overwrite

$tempDll = "$env:TEMP\jSerialComm\2.10.4\jSerialComm.dll"
$homeDll = "$env:USERPROFILE\.jSerialComm\2.10.4\jSerialComm.dll"

if (Test-Path $tempDll) {
    $file = Get-Item $tempDll
    $file.IsReadOnly = $true
    Write-Host "Temp DLL set to read-only: $tempDll (size: $($file.Length) bytes)"
} else {
    Write-Host "Temp DLL not found: $tempDll"
}

if (Test-Path $homeDll) {
    $file = Get-Item $homeDll
    $file.IsReadOnly = $true
    Write-Host "Home DLL set to read-only: $homeDll (size: $($file.Length) bytes)"
} else {
    Write-Host "Home DLL not found: $homeDll"
}

Write-Host "Done!"


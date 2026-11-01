$ErrorActionPreference = "Stop"
Write-Host "Waiting for device to connect and be authorized..."
adb wait-for-device
Write-Host "Device detected! Checking authorization..."
$state = adb get-state
Write-Host "Device state: $state"

Write-Host "1. Freezing Samsung Auto Blocker..."
adb shell pm disable-user --user 0 com.samsung.android.rampart

Write-Host "2. Installing USB Debugging APK..."
$apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
adb install -r $apkPath

Write-Host "3. Granting WRITE_SECURE_SETTINGS permission..."
adb shell pm grant com.antigravity.usbdebugtile android.permission.WRITE_SECURE_SETTINGS

Write-Host "Setup completed successfully!"

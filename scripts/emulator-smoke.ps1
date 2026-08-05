param(
    [string]$AvdName = "zapret_api36_x86_64",
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$adb = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
$emulator = "$env:ANDROID_SDK_ROOT\emulator\emulator.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    throw "Debug APK missing: $apk"
}

$devices = & $adb devices
if (-not ($devices -match $Serial)) {
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $AvdName,
        "-no-window",
        "-no-audio",
        "-no-snapshot",
        "-gpu", "swiftshader_indirect",
        "-netdelay", "none",
        "-netspeed", "full"
    ) -WindowStyle Hidden
}

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 5
    $boot = & $adb -s $Serial shell getprop sys.boot_completed 2>$null
    if ($boot -match "1") {
        break
    }
} while ((Get-Date) -lt $deadline)

if (-not ($boot -match "1")) {
    throw "Emulator did not boot before timeout"
}

& $adb -s $Serial install -r $apk
& $adb -s $Serial shell am start -n dev.zapret.mobile/.MainActivity
Start-Sleep -Seconds 2
& $adb -s $Serial shell input tap 540 543
Start-Sleep -Seconds 2
& $adb -s $Serial shell input tap 894 1516
Start-Sleep -Seconds 5

$services = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
$interfaces = & $adb -s $Serial shell ip addr show
$connectivity = & $adb -s $Serial shell dumpsys connectivity

if (-not ($services -match "ZapretVpnService")) {
    throw "ZapretVpnService is not running"
}
if (-not ($interfaces -match "tun0") -or -not ($interfaces -match "10.71.0.1")) {
    throw "Expected tun0 with 10.71.0.1 was not found"
}
if (-not ($connectivity -match "VPN:dev.zapret.mobile")) {
    throw "Connectivity service does not report Zapret Mobile VPN"
}

& $adb -s $Serial shell input tap 540 695
Start-Sleep -Seconds 3
$servicesAfterStop = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
$interfacesAfterStop = & $adb -s $Serial shell ip addr show

if ($servicesAfterStop -match "ZapretVpnService") {
    throw "ZapretVpnService still running after stop"
}
if ($interfacesAfterStop -match "10.71.0.1") {
    throw "tun0 address still present after stop"
}

Write-Host "Emulator smoke test passed: install, launch, permission, TUN create, foreground service, stop."

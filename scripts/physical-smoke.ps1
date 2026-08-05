param(
    [string]$Serial = "adb-53271JEKB00683-G83QXW._adb-tls-connect._tcp"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:PATH = "$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

$adb = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$artifactRoot = Join-Path $root "build\test-artifacts\physical-device"

if (-not (Test-Path $apk)) {
    throw "Debug APK missing: $apk"
}
if (-not ((& $adb devices) -match [regex]::Escape($Serial))) {
    throw "Physical device is not connected: $Serial"
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null

$servicesBefore = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
$interfacesBefore = & $adb -s $Serial shell ip addr show
if ($servicesBefore -match "ZapretVpnService") {
    throw "ZapretVpnService is already active on the physical device"
}
if ($interfacesBefore -match "(?m)^\d+: tun\d+:.*<[^>]*UP") {
    throw "Another active TUN interface was found on the physical device"
}

$model = (& $adb -s $Serial shell getprop ro.product.model).Trim()
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
$android = (& $adb -s $Serial shell getprop ro.build.version.release).Trim()
$apkHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash

& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "Failed to update Zapret Mobile on physical device"
}

$vpnAppOp = & $adb -s $Serial shell cmd appops get dev.zapret.mobile ACTIVATE_VPN
if (-not ($vpnAppOp -match "ACTIVATE_VPN: allow")) {
    throw "VPN consent is not granted on the physical device"
}

$passed = $false
try {
    & $adb -s $Serial shell cmd deviceidle tempwhitelist -d 45000 dev.zapret.mobile | Out-Null
    & $adb -s $Serial shell run-as dev.zapret.mobile /system/bin/am start-foreground-service `
        --user 0 `
        -n dev.zapret.mobile/.ZapretVpnService `
        -a dev.zapret.mobile.action.START | Out-Null
    Start-Sleep -Seconds 8

    $services = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
    $interfaces = & $adb -s $Serial shell ip addr show
    $logcat = & $adb -s $Serial shell logcat -d -v time -t 2500
    $services | Set-Content -LiteralPath (Join-Path $artifactRoot "activity-services-active.txt")
    $interfaces | Set-Content -LiteralPath (Join-Path $artifactRoot "ip-addr-active.txt")
    $logcat | Set-Content -LiteralPath (Join-Path $artifactRoot "logcat.txt")

    if (-not ($services -match "ZapretVpnService") -or -not ($services -match "isForeground=true")) {
        throw "Physical ZapretVpnService is not active in foreground"
    }
    if (-not ($interfaces -match "tun0") -or -not ($interfaces -match "10.71.0.1")) {
        throw "Physical device has no active Zapret TUN"
    }
    if (-not ($logcat -match "ZapretVpnService.*Protected outbound socket fd=")) {
        throw "Physical device log has no outbound socket protection evidence"
    }
    if ($logcat -match "FATAL EXCEPTION.*dev.zapret.mobile") {
        throw "Physical device log contains a Zapret Mobile crash"
    }

    $passed = $true
} finally {
    try {
        & $adb -s $Serial shell run-as dev.zapret.mobile /system/bin/am start-service `
            --user 0 `
            -n dev.zapret.mobile/.ZapretVpnService `
            -a dev.zapret.mobile.action.STOP | Out-Null
    } catch {
        Write-Warning "Graceful physical VPN stop failed: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 5

    $servicesStopped = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
    $interfacesStopped = & $adb -s $Serial shell ip addr show
    $servicesStopped | Set-Content -LiteralPath (Join-Path $artifactRoot "activity-services-stopped.txt")
    $interfacesStopped | Set-Content -LiteralPath (Join-Path $artifactRoot "ip-addr-stopped.txt")
    if ($servicesStopped -match "ZapretVpnService" -or $interfacesStopped -match "10.71.0.1") {
        & $adb -s $Serial shell am force-stop dev.zapret.mobile | Out-Null
        throw "Physical VPN cleanup failed; the app was force-stopped"
    }
}

$report = [ordered]@{
    serial = $Serial
    model = $model
    abi = $abi
    android = $android
    apk_sha256 = $apkHash
    vpn_appop = ($vpnAppOp -join " ").Trim()
    foreground_service = $true
    tun_address = "10.71.0.1/24"
    socket_protection_observed = $true
    cleanup_passed = $true
    passed = $passed
}
$report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $artifactRoot "physical-report.json")

Write-Host "Physical smoke passed on $model ($abi, Android $android). Artifacts: $artifactRoot"

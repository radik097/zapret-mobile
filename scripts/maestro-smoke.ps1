param(
    [string]$AvdName = "zapret_api36_x86_64",
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:MAESTRO_CLI_NO_ANALYTICS = "1"
$env:MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED = "true"
$env:PATH = "$env:JAVA_HOME\bin;D:\Android\tools\maestro-2.8.0\maestro\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\emulator;$env:PATH"

$adb = "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
$emulator = "$env:ANDROID_SDK_ROOT\emulator\emulator.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$flow = Join-Path $root ".maestro\zapret-smoke.yaml"
$stopFlow = Join-Path $root ".maestro\zapret-stop.yaml"
$artifactRoot = Join-Path $root "build\test-artifacts\maestro-smoke"

if (-not (Test-Path $apk)) {
    throw "Debug APK missing: $apk"
}
if (-not (Test-Path $flow)) {
    throw "Maestro flow missing: $flow"
}
if (-not (Test-Path $stopFlow)) {
    throw "Maestro stop flow missing: $stopFlow"
}

function Wait-DeviceReady {
    param(
        [string]$TargetSerial,
        [int]$TimeoutSeconds = 120,
        [int]$StableChecks = 3
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $stable = 0
    do {
        try {
            $state = & $adb -s $TargetSerial get-state 2>$null
            $boot = & $adb -s $TargetSerial shell getprop sys.boot_completed 2>$null
        } catch {
            $state = ""
            $boot = ""
        }
        if (($state -match "device") -and ($boot -match "1")) {
            $stable += 1
            if ($stable -ge $StableChecks) {
                return
            }
        } else {
            $stable = 0
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Device $TargetSerial did not stay ready for $StableChecks consecutive checks"
}

function Invoke-MaestroFlow {
    param(
        [string]$TargetSerial,
        [string]$FlowPath,
        [string]$JunitPath,
        [string]$OutputDir,
        [string]$Name,
        [int]$Attempts = 3
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt += 1) {
        Wait-DeviceReady -TargetSerial $TargetSerial -TimeoutSeconds 120
        maestro test --udid $TargetSerial --format JUNIT --output $JunitPath --test-output-dir $OutputDir $FlowPath
        if ($LASTEXITCODE -eq 0) {
            return
        }
        if ($attempt -eq $Attempts) {
            throw "$Name failed with exit code $LASTEXITCODE after $Attempts attempt(s)"
        }
        Write-Warning "$Name attempt $attempt failed with exit code $LASTEXITCODE; restarting ADB transport"
        & $adb kill-server 2>$null
        Start-Sleep -Seconds 3
        & $adb start-server | Out-Null
        Wait-DeviceReady -TargetSerial $TargetSerial -TimeoutSeconds 180
    }
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null

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
    try {
        $boot = & $adb -s $Serial shell getprop sys.boot_completed 2>$null
    } catch {
        $boot = ""
    }
    if ($boot -match "1") {
        break
    }
} while ((Get-Date) -lt $deadline)

if (-not ($boot -match "1")) {
    throw "Emulator did not boot before timeout"
}
Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 120

& $adb -s $Serial shell am force-stop dev.zapret.mobile 2>$null
& $adb -s $Serial uninstall dev.zapret.mobile 2>$null | Out-Null
& $adb -s $Serial install -r $apk
Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 180
& $adb -s $Serial logcat -c

Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 120
Invoke-MaestroFlow `
    -TargetSerial $Serial `
    -FlowPath $flow `
    -JunitPath (Join-Path $artifactRoot "maestro-junit.xml") `
    -OutputDir (Join-Path $artifactRoot "maestro-output") `
    -Name "Maestro start flow"

Start-Sleep -Seconds 2
$services = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
$interfaces = & $adb -s $Serial shell ip addr show
$connectivity = & $adb -s $Serial shell dumpsys connectivity

$services | Set-Content -LiteralPath (Join-Path $artifactRoot "activity-services-active.txt")
$interfaces | Set-Content -LiteralPath (Join-Path $artifactRoot "ip-addr-active.txt")
$connectivity | Set-Content -LiteralPath (Join-Path $artifactRoot "connectivity-active.txt")
& $adb -s $Serial logcat -d -v time > (Join-Path $artifactRoot "logcat.txt")
& $adb -s $Serial exec-out screencap -p > (Join-Path $artifactRoot "screen.png")

if (-not ($services -match "ZapretVpnService")) {
    throw "ZapretVpnService is not running after Maestro start action"
}
if (-not ($interfaces -match "tun0") -or -not ($interfaces -match "10.71.0.1")) {
    throw "Expected active tun0 with 10.71.0.1 was not found"
}
if (-not ($connectivity -match "VPN:dev.zapret.mobile")) {
    throw "Connectivity service does not report active Zapret Mobile VPN"
}

Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 120
Invoke-MaestroFlow `
    -TargetSerial $Serial `
    -FlowPath $stopFlow `
    -JunitPath (Join-Path $artifactRoot "maestro-stop-junit.xml") `
    -OutputDir (Join-Path $artifactRoot "maestro-stop-output") `
    -Name "Maestro stop flow"
Start-Sleep -Seconds 3
$servicesAfterStop = & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile
$interfacesAfterStop = & $adb -s $Serial shell ip addr show
$servicesAfterStop | Set-Content -LiteralPath (Join-Path $artifactRoot "activity-services-stopped.txt")
$interfacesAfterStop | Set-Content -LiteralPath (Join-Path $artifactRoot "ip-addr-stopped.txt")

if ($servicesAfterStop -match "ZapretVpnService") {
    throw "ZapretVpnService still running after stop action"
}
if ($interfacesAfterStop -match "10.71.0.1") {
    throw "TUN address still present after stop action"
}

Write-Host "Maestro smoke test passed. Artifacts: $artifactRoot"

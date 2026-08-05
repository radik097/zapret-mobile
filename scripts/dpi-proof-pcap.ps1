param(
    [string]$AvdName = "zapret_api36_x86_64",
    [string]$Serial = "emulator-5554",
    [int]$Port = 18081,
    [switch]$KeepEmulator
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
$artifactRoot = Join-Path $root "build\test-artifacts\dpi-proof-pcap"
$pcapPath = Join-Path $artifactRoot "emulator-network.pcap"
$dpiProof = Join-Path $root "scripts\dpi-proof.ps1"

function Wait-DeviceMissing {
    param(
        [string]$TargetSerial,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $devices = & $adb devices
        if (-not ($devices -match $TargetSerial)) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Device $TargetSerial did not disconnect"
}

function Wait-DeviceReady {
    param(
        [string]$TargetSerial,
        [int]$TimeoutSeconds = 300,
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

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
Remove-Item -LiteralPath $pcapPath -Force -ErrorAction SilentlyContinue

$startedEmulator = $false
try {
    $devices = & $adb devices
    if ($devices -match $Serial) {
        & $adb -s $Serial emu kill | Out-Null
        Wait-DeviceMissing -TargetSerial $Serial -TimeoutSeconds 90
    }

    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", $AvdName,
        "-no-window",
        "-no-audio",
        "-no-snapshot",
        "-gpu", "swiftshader_indirect",
        "-netdelay", "none",
        "-netspeed", "full",
        "-tcpdump", $pcapPath
    ) -WindowStyle Hidden
    $startedEmulator = $true
    Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 300

    powershell -ExecutionPolicy Bypass -File $dpiProof -AvdName $AvdName -Serial $Serial -Port $Port
    if ($LASTEXITCODE -ne 0) {
        throw "Nested DPI proof failed with exit code $LASTEXITCODE"
    }

    if (-not $KeepEmulator) {
        & $adb -s $Serial emu kill | Out-Null
        Wait-DeviceMissing -TargetSerial $Serial -TimeoutSeconds 90
        $startedEmulator = $false
    }

    if (-not (Test-Path $pcapPath)) {
        throw "Emulator PCAP was not written: $pcapPath"
    }
    $pcapSize = (Get-Item $pcapPath).Length
    if ($pcapSize -le 24) {
        throw "Emulator PCAP is empty or header-only: $pcapPath"
    }

    Copy-Item -LiteralPath (Join-Path $root "build\test-artifacts\dpi-proof\dpi-report.json") `
        -Destination (Join-Path $artifactRoot "dpi-report.json") -Force
    Copy-Item -LiteralPath (Join-Path $root "build\test-artifacts\dpi-proof\logcat.txt") `
        -Destination (Join-Path $artifactRoot "logcat.txt") -Force

    Write-Host "DPI proof with emulator PCAP passed. PCAP: $pcapPath ($pcapSize bytes)"
} finally {
    if ($startedEmulator -and -not $KeepEmulator) {
        & $adb -s $Serial emu kill 2>$null | Out-Null
    }
}

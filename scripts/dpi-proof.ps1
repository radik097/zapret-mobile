param(
    [string]$AvdName = "zapret_api36_x86_64",
    [string]$Serial = "emulator-5554",
    [int]$Port = 18081
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
$appApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$clientApk = Join-Path $root "test-client\build\outputs\apk\debug\test-client-debug.apk"
$startFlow = Join-Path $root ".maestro\zapret-smoke.yaml"
$stopFlow = Join-Path $root ".maestro\zapret-stop.yaml"
$artifactRoot = Join-Path $root "build\test-artifacts\dpi-proof"
$simulator = Join-Path $root "tools\dpi_http_simulator.py"
$report = Join-Path $artifactRoot "dpi-report.json"
$ready = Join-Path $artifactRoot "dpi-ready.txt"
$serverLog = Join-Path $artifactRoot "dpi-simulator.log"
$serverErr = Join-Path $artifactRoot "dpi-simulator.err.log"

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

function Stop-ZapretVpn {
    try {
        & $adb -s $Serial shell am start -n dev.zapret.mobile/.MainActivity | Out-Null
        Start-Sleep -Seconds 2
        Invoke-MaestroFlow `
            -TargetSerial $Serial `
            -FlowPath $stopFlow `
            -JunitPath (Join-Path $artifactRoot "maestro-stop-junit.xml") `
            -OutputDir (Join-Path $artifactRoot "maestro-stop-output") `
            -Name "Maestro stop flow"
    } catch {
        Write-Warning "VPN stop flow failed during cleanup: $($_.Exception.Message)"
    }
}

if (-not (Test-Path $appApk)) {
    throw "Zapret debug APK missing: $appApk"
}
if (-not (Test-Path $clientApk)) {
    throw "Test-client debug APK missing: $clientApk"
}
if (-not (Test-Path $simulator)) {
    throw "DPI simulator missing: $simulator"
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
Remove-Item -LiteralPath $ready, $report -Force -ErrorAction SilentlyContinue

$server = $null
try {
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
    Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 300

    & $adb -s $Serial shell am force-stop dev.zapret.mobile 2>$null
    & $adb -s $Serial shell am force-stop dev.zapret.testclient 2>$null
    & $adb -s $Serial uninstall dev.zapret.mobile 2>$null | Out-Null
    & $adb -s $Serial uninstall dev.zapret.testclient 2>$null | Out-Null
    & $adb -s $Serial install -r $appApk
    & $adb -s $Serial install -r $clientApk
    Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 180
    & $adb -s $Serial logcat -c

    Invoke-MaestroFlow `
        -TargetSerial $Serial `
        -FlowPath $startFlow `
        -JunitPath (Join-Path $artifactRoot "maestro-start-junit.xml") `
        -OutputDir (Join-Path $artifactRoot "maestro-start-output") `
        -Name "Maestro start flow"

    Start-Sleep -Seconds 2
    & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile > (Join-Path $artifactRoot "activity-services-active.txt")
    & $adb -s $Serial shell ip addr show > (Join-Path $artifactRoot "ip-addr-active.txt")
    & $adb -s $Serial shell dumpsys connectivity > (Join-Path $artifactRoot "connectivity-active.txt")

    $server = Start-Process `
        -FilePath "python" `
        -ArgumentList @($simulator, "--port", "$Port", "--report", $report, "--ready", $ready, "--timeout", "60") `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $serverLog `
        -RedirectStandardError $serverErr

    $readyDeadline = (Get-Date).AddSeconds(10)
    while (-not (Test-Path $ready)) {
        if ($server.HasExited) {
            throw "DPI simulator exited early. See $serverErr"
        }
        if ((Get-Date) -ge $readyDeadline) {
            throw "DPI simulator did not become ready. See $serverErr"
        }
        Start-Sleep -Milliseconds 200
    }

    & $adb -s $Serial shell am start `
        -n dev.zapret.testclient/.TestClientActivity `
        --es mode raw-http `
        --es host 10.0.2.2 `
        --ei port $Port `
        --es path /probe `
        --es authority blocked.example | Out-Null

    $deadline = (Get-Date).AddSeconds(45)
    $matched = $false
    $logcat = ""
    do {
        Start-Sleep -Seconds 2
        $logcat = & $adb -s $Serial logcat -d -v time
        if ($logcat -match "ZAPRET_TEST_CLIENT.*raw_result=200 body=dpi-split-proof") {
            $matched = $true
            break
        }
    } while ((Get-Date) -lt $deadline)

    $logcat | Set-Content -LiteralPath (Join-Path $artifactRoot "logcat.txt")
    if (-not $matched) {
        throw "DPI proof did not observe expected raw test-client success"
    }

    if ($server -ne $null -and -not $server.HasExited) {
        Wait-Process -Id $server.Id -Timeout 5 -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path $report)) {
        throw "DPI simulator report missing: $report"
    }
    $dpi = Get-Content -Raw -LiteralPath $report | ConvertFrom-Json
    if ($dpi.decision -ne "allowed_split" -or -not $dpi.passed) {
        throw "DPI simulator decision was not allowed_split. See $report"
    }
    if ($dpi.first_chunk_has_complete_blocked_host) {
        throw "DPI simulator observed complete blocked host in first chunk. See $report"
    }

    Stop-ZapretVpn
    Start-Sleep -Seconds 3
    & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile > (Join-Path $artifactRoot "activity-services-stopped.txt")
    & $adb -s $Serial shell ip addr show > (Join-Path $artifactRoot "ip-addr-stopped.txt")
    $servicesAfterStop = Get-Content -Raw -LiteralPath (Join-Path $artifactRoot "activity-services-stopped.txt")
    $interfacesAfterStop = Get-Content -Raw -LiteralPath (Join-Path $artifactRoot "ip-addr-stopped.txt")
    if ($servicesAfterStop -match "ZapretVpnService") {
        throw "ZapretVpnService still running after DPI proof cleanup"
    }
    if ($interfacesAfterStop -match "10.71.0.1") {
        throw "TUN address still present after DPI proof cleanup"
    }

    Write-Host "DPI proof passed on port $Port. Artifacts: $artifactRoot"
} finally {
    if ($server -ne $null -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force
    }
    Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }
}

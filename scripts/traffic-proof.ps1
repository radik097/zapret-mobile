param(
    [string]$AvdName = "zapret_api36_x86_64",
    [string]$Serial = "emulator-5554",
    [int]$Port = 18080,
    [switch]$SelectedAppsOnly
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
$routingFlow = Join-Path $root ".maestro\zapret-app-routing.yaml"
$artifactName = if ($SelectedAppsOnly) { "traffic-proof-selected-apps" } else { "traffic-proof" }
$artifactRoot = Join-Path $root "build\test-artifacts\$artifactName"
$wwwRoot = Join-Path $artifactRoot "www"
$serverLog = Join-Path $artifactRoot "host-http-server.log"
$probeUrl = "http://10.0.2.2:$Port/probe"

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

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
New-Item -ItemType Directory -Force -Path $wwwRoot | Out-Null
Set-Content -LiteralPath (Join-Path $wwwRoot "probe") -Value "zapret-proof" -NoNewline

$server = $null
try {
    $server = Start-Process `
        -FilePath "python" `
        -ArgumentList @("-m", "http.server", "$Port", "--bind", "127.0.0.1", "--directory", $wwwRoot) `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $serverLog `
        -RedirectStandardError (Join-Path $artifactRoot "host-http-server.err.log")
    Start-Sleep -Seconds 2
    if ($server.HasExited) {
        throw "Host HTTP server exited early. See $serverLog"
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
    Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 300

    & $adb -s $Serial shell am force-stop dev.zapret.mobile 2>$null
    & $adb -s $Serial shell am force-stop dev.zapret.testclient 2>$null
    & $adb -s $Serial uninstall dev.zapret.mobile 2>$null | Out-Null
    & $adb -s $Serial uninstall dev.zapret.testclient 2>$null | Out-Null
    & $adb -s $Serial install -r $appApk
    & $adb -s $Serial install -r $clientApk
    Wait-DeviceReady -TargetSerial $Serial -TimeoutSeconds 180
    & $adb -s $Serial logcat -c

    if ($SelectedAppsOnly) {
        Invoke-MaestroFlow `
            -TargetSerial $Serial `
            -FlowPath $routingFlow `
            -JunitPath (Join-Path $artifactRoot "maestro-routing-junit.xml") `
            -OutputDir (Join-Path $artifactRoot "maestro-routing-output") `
            -Name "Maestro selected-app routing flow"

        $routingPreferences = & $adb -s $Serial shell run-as dev.zapret.mobile cat shared_prefs/app_routing.xml
        $routingPreferences | Set-Content -LiteralPath (Join-Path $artifactRoot "app-routing.xml")
        if (-not ($routingPreferences -match 'name="selected_only" value="true"')) {
            throw "Selected-app routing mode was not persisted"
        }
        if (-not ($routingPreferences -match 'dev.zapret.testclient')) {
            throw "Selected test-client package was not persisted"
        }
    }

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

    & $adb -s $Serial shell am start -n dev.zapret.testclient/.TestClientActivity --es url $probeUrl | Out-Null
    $deadline = (Get-Date).AddSeconds(45)
    $matched = $false
    do {
        Start-Sleep -Seconds 2
        $logcat = & $adb -s $Serial logcat -d -v time
        if ($logcat -match "ZAPRET_TEST_CLIENT.*result=200 body=zapret-proof") {
            $matched = $true
            break
        }
    } while ((Get-Date) -lt $deadline)

    $logcat | Set-Content -LiteralPath (Join-Path $artifactRoot "logcat.txt")
    if (-not $matched) {
        throw "Traffic proof did not observe expected test-client success for $probeUrl"
    }
    if ($SelectedAppsOnly -and -not ($logcat -match "ZapretVpnService.*Routing 1 selected app\(s\)")) {
        throw "VPN service did not report the selected-app routing policy"
    }

    Stop-ZapretVpn
    Start-Sleep -Seconds 3
    & $adb -s $Serial shell dumpsys activity services dev.zapret.mobile > (Join-Path $artifactRoot "activity-services-stopped.txt")
    & $adb -s $Serial shell ip addr show > (Join-Path $artifactRoot "ip-addr-stopped.txt")
    $servicesAfterStop = Get-Content -Raw -LiteralPath (Join-Path $artifactRoot "activity-services-stopped.txt")
    $interfacesAfterStop = Get-Content -Raw -LiteralPath (Join-Path $artifactRoot "ip-addr-stopped.txt")
    if ($servicesAfterStop -match "ZapretVpnService") {
        throw "ZapretVpnService still running after traffic proof cleanup"
    }
    if ($interfacesAfterStop -match "10.71.0.1") {
        throw "TUN address still present after traffic proof cleanup"
    }

    $routingMode = if ($SelectedAppsOnly) { "selected app" } else { "all apps" }
    Write-Host "Traffic proof passed for $probeUrl in $routingMode mode. Artifacts: $artifactRoot"
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

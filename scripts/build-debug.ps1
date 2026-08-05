param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin;$env:PATH"

Write-Host "Java:"
java -version

Write-Host "Android SDK: $env:ANDROID_SDK_ROOT"
if (-not (Test-Path "$env:ANDROID_SDK_ROOT\platforms\android-36\android.jar")) {
    throw "Android platform 36 is missing"
}
if (-not (Test-Path "$env:ANDROID_SDK_ROOT\ndk\28.2.13676358")) {
    throw "NDK 28.2.13676358 is missing"
}
if (-not (Test-Path "$env:ANDROID_SDK_ROOT\cmake\3.31.6\bin\cmake.exe")) {
    throw "CMake 3.31.6 is missing"
}

Push-Location $root
try {
    if (Test-Path ".git") {
        git submodule update --init --recursive
    }
    cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml
    if (-not $SkipTests) {
        .\gradlew.bat test
    }
    .\gradlew.bat assembleDebug
    $apk = Resolve-Path "app\build\outputs\apk\debug\app-debug.apk"
    $hash = Get-FileHash -Algorithm SHA256 $apk
    $size = (Get-Item $apk).Length
    Write-Host "APK: $apk"
    Write-Host "Size: $size bytes"
    Write-Host "SHA-256: $($hash.Hash)"
} finally {
    Pop-Location
}


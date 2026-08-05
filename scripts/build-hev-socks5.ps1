param(
    [string]$SourceDir = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($SourceDir)) {
    $SourceDir = Join-Path $root "research\hev-socks5-tunnel"
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $root "app\src\main\jniLibs"
}

$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_NDK_HOME = "D:\Android\Sdk\ndk\28.2.13676358"
$env:ANDROID_NDK_ROOT = $env:ANDROID_NDK_HOME
$env:PATH = "$env:ANDROID_NDK_HOME;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

$ndkBuild = Join-Path $env:ANDROID_NDK_HOME "ndk-build.cmd"
if (-not (Test-Path $ndkBuild)) {
    throw "ndk-build is missing: $ndkBuild"
}

if (-not (Test-Path $SourceDir)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $SourceDir) | Out-Null
    git clone --depth 1 https://github.com/heiher/hev-socks5-tunnel.git $SourceDir
}
if (-not (Test-Path (Join-Path $SourceDir ".git"))) {
    throw "hev-socks5-tunnel source directory exists but is not a git checkout: $SourceDir"
}

Push-Location $SourceDir
try {
    git submodule update --init --recursive

    $sourceRoot = (Resolve-Path ".").Path
    Get-ChildItem -LiteralPath $sourceRoot -Recurse -File |
        Where-Object {
            $_.Length -lt 512 -and
            $_.FullName -notmatch "\\\.git\\" -and
            $_.FullName -notmatch "\\obj\\" -and
            $_.FullName -notmatch "\\libs\\"
        } |
        ForEach-Object {
            $content = (Get-Content -Raw -LiteralPath $_.FullName).Trim()
            if ($content -match "^\.\.[/\\]") {
                $targetPath = Join-Path $_.DirectoryName $content
                $resolvedTarget = (Resolve-Path $targetPath).Path
                if (-not $resolvedTarget.StartsWith($sourceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
                    throw "Refusing to materialize symlink placeholder outside source tree: $($_.FullName) -> $resolvedTarget"
                }
                Copy-Item -LiteralPath $resolvedTarget -Destination $_.FullName -Force
            }
        }

    & $ndkBuild `
        "NDK_PROJECT_PATH=." `
        "APP_BUILD_SCRIPT=Android.mk" `
        "NDK_APPLICATION_MK=Application.mk" `
        "APP_ABI=arm64-v8a x86_64" `
        "APP_CFLAGS=-O3 -DPKGNAME=hev/htproxy -DCLSNAME=TProxyService"
    if ($LASTEXITCODE -ne 0) {
        throw "ndk-build failed with exit code $LASTEXITCODE"
    }

    foreach ($abi in @("arm64-v8a", "x86_64")) {
        $built = Join-Path $sourceRoot "libs\$abi\libhev-socks5-tunnel.so"
        if (-not (Test-Path $built)) {
            throw "Expected hev-socks5-tunnel output missing: $built"
        }
        $abiOutput = Join-Path $OutputDir $abi
        New-Item -ItemType Directory -Force -Path $abiOutput | Out-Null
        Copy-Item -LiteralPath $built -Destination (Join-Path $abiOutput "libhev-socks5-tunnel.so") -Force
    }
} finally {
    Pop-Location
}

Write-Host "hev-socks5-tunnel native libraries copied to $OutputDir"

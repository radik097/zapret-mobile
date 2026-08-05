# Full Work Report - Zapret Mobile

Timestamp: 2026-08-06 01:34:05 +10:00

## Executive Summary

The `D:\Android\VPN_app` folder was turned from a specification/instruction workspace into a buildable, locally testable Android VPN project. The app now builds a debug APK, starts a rootless Android `VpnService`, creates a TUN interface, bridges TUN traffic into a local SOCKS5 engine, and has automated emulator proofs for lifecycle, TCP traffic, deterministic HTTP split behavior, and emulator-level PCAP capture.

No remote push or PR was performed. The repository had no `.git` directory at handoff time, so the requested local commit is created as a new local Git repository commit.

## Implemented Application

- Created a Rust-first Android project with Gradle wrapper and Android app module.
- Kept Android platform glue in Java: `MainActivity`, `ZapretVpnService`, JNI wrapper, and bridge lifecycle code.
- Implemented a native Rust `zapret_engine` shared library loaded by the Android app.
- Implemented a local SOCKS5 listener on `127.0.0.1:1080`.
- Implemented SOCKS5 CONNECT relay for TCP.
- Implemented bounded initial HTTP Host split behavior.
- Implemented bounded TLS ClientHello/SNI parser skeleton and split point calculation.
- Implemented minimal SOCKS5 UDP ASSOCIATE relay for DNS/UDP datagrams.
- Integrated `hev-socks5-tunnel` as the native Android TUN-to-SOCKS bridge.
- Packaged both `libzapret_engine.so` and `libhev-socks5-tunnel.so` for `arm64-v8a` and `x86_64`.

## Local Tooling Installed / Used

- JDK 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`
- Android SDK: `D:\Android\Sdk`
- Android platform 36
- Build Tools 36.1.0
- NDK 28.2.13676358
- CMake 3.31.6
- Gradle wrapper 9.6.1
- Android Gradle Plugin 9.3.1
- Rust Android targets: `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`
- `cargo-ndk` 4.1.2
- Maestro CLI 2.8.0: `D:\Android\tools\maestro-2.8.0\maestro\bin`
- Android Emulator AVD: `zapret_api36_x86_64`

## Automation And Test Harness

- Added `.maestro/zapret-smoke.yaml` for app launch, VPN consent, UI assertions, and start flow.
- Added `.maestro/zapret-stop.yaml` for stop flow without force-stopping the active `VpnService`.
- Added `scripts/emulator-smoke.ps1` for direct emulator lifecycle smoke.
- Added `scripts/maestro-smoke.ps1` for reproducible Maestro start/stop verification with JUnit output.
- Added `test-client` APK (`dev.zapret.testclient`) to generate traffic from a separate UID.
- Added `scripts/traffic-proof.ps1` to prove TCP traffic from `test-client` passes through active VPN to a host HTTP endpoint.
- Added `tools/dpi_http_simulator.py` to simulate a local HTTP DPI rule against `Host: blocked.example`.
- Added `scripts/dpi-proof.ps1` to prove split behavior through the live VPN path.
- Added `scripts/dpi-proof-pcap.ps1` to restart the AVD with Android Emulator `-tcpdump` and save a real `.pcap`.

## Verified Runtime Evidence

- Emulator lifecycle: install, launch, VPN permission, foreground service, `tun0`, `10.71.0.1`, stop cleanup.
- Maestro lifecycle: start and stop JUnit files show zero failures.
- Traffic proof: `ZAPRET_TEST_CLIENT result=200 body=zapret-proof`.
- DPI proof: `ZAPRET_TEST_CLIENT raw_result=200 body=dpi-split-proof`.
- DPI simulator report: `decision=allowed_split`, `passed=true`, `chunk_count=2`.
- DPI first chunk: `GET /probe HTTP/1.1\r\nHost: blocked`.
- DPI full request: `Host: blocked.example`.
- PCAP artifact: `D:\Android\VPN_app\build\test-artifacts\dpi-proof-pcap\emulator-network.pcap`.

## Current APK Artifacts

- Main APK: `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`
- Last recorded main APK SHA-256: `CC6D669930058314612A12685A096790D8E6F164C12E0DEBAAEE3F01C3F5D4C8`
- Test-client APK: `D:\Android\VPN_app\test-client\build\outputs\apk\debug\test-client-debug.apk`
- Last recorded test-client APK SHA-256: `41FB8A451B25CFD52577F701BAFAFBF745B0D5BB5F189371FB89679BBA882EFE`

## Important Rollback

An attempted profile/settings UI and JNI strategy flag change was tested and rejected. It compiled, but `scripts/dpi-proof.ps1` failed at runtime. Per the local repository rule, that app-code change was rolled back to the last working VPN/JNI/Rust path before continuing. The retained fix from that investigation is limited to the test harness: `tools/dpi_http_simulator.py` now ignores empty/no-data TCP connections and keeps waiting for the real raw HTTP request.

## Current Known Limits

- App selection UI is not implemented.
- Persistent strategy profiles are not implemented.
- Production Rust socket protection callback is not implemented; current loop avoidance uses `addDisallowedApplication(getPackageName())`.
- HAR/mitmproxy or HTTP Toolkit export is not implemented.
- QUIC/UDP443 policy is not implemented in the production app.
- Physical device verification has not been performed; one physical/network ADB device was visible, but this work did not install or test on it.
- This is a local debug APK and local proof harness, not a released production package.

## Final Verified Commands Before Commit

```powershell
cargo test --manifest-path native-engine\rust\zapret_engine\Cargo.toml
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug
python -m py_compile tools\dpi_http_simulator.py
powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1
powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1
powershell -ExecutionPolicy Bypass -File scripts\dpi-proof-pcap.ps1
```

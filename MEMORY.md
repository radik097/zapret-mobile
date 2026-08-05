# MEMORY: Project State

Updated: 2026-08-06 01:34:05 +10:00

## Current Project

- Name: Zapret Mobile.
- Workspace: D:\Android\VPN_app.
- Current structure: Rust-first Android project skeleton exists and builds a debug APK.
- Git status: .git is not present in `D:\Android\VPN_app`.
- User direction: create multiple files so an LLM can navigate instructions, memory, tasks, workflow, role, and BLACKBOX without rereading one large file.
- Latest user direction: autonomously develop the Android app from the local files with full control to install needed tooling, without asking clarification.

## Durable Decisions

- INSTRUCTION.md is now a lightweight navigation index.
- PROJECT_BRIEF.md stores the full technical project brief that was previously embedded in INSTRUCTION.md.
- AGENT.md stores role and behavior.
- MEMORY.md stores mutable project state.
- TASKS.md stores active tasks and backlog.
- WORKFLOW.md stores repeatable work rules.
- BLACKBOX.md is reserved for private/provider-specific instructions.
- .vscode/TASKS.md is the chronological task journal required by the repository instructions.
- START_PROMPT.md is the development launch prompt.
- Rust is the current primary implementation language override.
- Kotlin is not allowed as the main implementation language unless the user explicitly approves a narrow Android platform-glue exception.
- Java is currently used as the minimal Android platform glue for `MainActivity` and `VpnService`; Rust owns the native SOCKS/DPI engine.
- Android SDK is installed at `D:\Android\Sdk`.
- JDK 21 is installed at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`.
- Gradle wrapper exists and uses Gradle 9.6.1.
- Android Gradle Plugin is 9.3.1.
- Installed Android build packages used by the project: platform 36, Build Tools 36.1.0, NDK 28.2.13676358, CMake 3.31.6.
- Rust Android targets installed: `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`.
- `cargo-ndk` 4.1.2 is installed.
- Maestro CLI 2.8.0 is installed at `D:\Android\tools\maestro-2.8.0\maestro\bin`; scripts disable Maestro analytics through environment variables.
- `hev-socks5-tunnel` research checkout exists at `D:\Android\VPN_app\research\hev-socks5-tunnel`; Gradle builds it through `scripts\build-hev-socks5.ps1` and copies `libhev-socks5-tunnel.so` into `app/src/main/jniLibs`.

## Known Constraints

- Current debug APK path: `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`.
- Last clean debug APK SHA-256: `CC6D669930058314612A12685A096790D8E6F164C12E0DEBAAEE3F01C3F5D4C8`.
- Last clean debug APK size: 2,432,359 bytes.
- Verified commands: `cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml`, `.\gradlew.bat test`, `.\gradlew.bat lintDebug`, `.\gradlew.bat assembleDebug`, `.\gradlew.bat clean test assembleDebug`, `scripts\build-debug.ps1`, `powershell -ExecutionPolicy Bypass -File scripts\maestro-smoke.ps1`.
- `lintDebug` passed with 0 errors and 2 warnings about API 37 availability; `sdkmanager` could not install `platforms;android-37` at this time.
- Initial `adb devices` check found no connected devices or running emulators before the emulator setup below.
- Android Emulator and `system-images;android-36;google_apis;x86_64` are installed.
- AVD `zapret_api36_x86_64` exists and has been used for smoke testing.
- `scripts\emulator-smoke.ps1` passed on `emulator-5554`: install, launch, VPN permission path, `ZapretVpnService` foreground service, `tun0` with `10.71.0.1`, connectivity `VPN:dev.zapret.mobile`, and stop cleanup.
- `scripts\maestro-smoke.ps1` passed on `emulator-5554`: start flow JUnit and stop flow JUnit both have `failures="0"`; active artifacts show foreground `ZapretVpnService`, `tun0`, `10.71.0.1`, and `VPN:dev.zapret.mobile`; stopped artifacts show no service and no `tun0/10.71.0.1`.
- `scripts\traffic-proof.ps1` passed on `emulator-5554`: it installs separate `dev.zapret.testclient`, starts a host Python HTTP server at `127.0.0.1:18080`, activates VPN, and verifies `ZAPRET_TEST_CLIENT result=200 body=zapret-proof` for `http://10.0.2.2:18080/probe`; host log records `GET /probe HTTP/1.1` 200.
- Test-client debug APK path: `D:\Android\VPN_app\test-client\build\outputs\apk\debug\test-client-debug.apk`.
- Last test-client debug APK SHA-256: `41FB8A451B25CFD52577F701BAFAFBF745B0D5BB5F189371FB89679BBA882EFE`.
- Last test-client debug APK size: 879,886 bytes.
- APK currently packages both `libzapret_engine.so` and `libhev-socks5-tunnel.so` for `arm64-v8a` and `x86_64`.
- ADB also saw a physical/network device id `adb-53271JEKB00683-G83QXW._adb-tls-connect._tcp`, but this run did not install or test on that device.
- `scripts\dpi-proof.ps1` passed on `emulator-5554`: it installs the app and `dev.zapret.testclient`, activates VPN through Maestro, starts a local HTTP DPI simulator at `127.0.0.1:18081`, sends raw HTTP with `Host: blocked.example`, and verifies `ZAPRET_TEST_CLIENT raw_result=200 body=dpi-split-proof`.
- DPI proof artifact path: `D:\Android\VPN_app\build\test-artifacts\dpi-proof\dpi-report.json`; last report records `decision=allowed_split`, `passed=true`, `chunk_count=2`, first chunk `GET /probe HTTP/1.1\r\nHost: blocked`, and full request containing `Host: blocked.example`.
- `scripts\dpi-proof-pcap.ps1` passed: it restarts `zapret_api36_x86_64` with Android Emulator `-tcpdump`, runs `scripts\dpi-proof.ps1`, stops the emulator to flush the capture, and writes `D:\Android\VPN_app\build\test-artifacts\dpi-proof-pcap\emulator-network.pcap` (30,007 bytes, classic PCAP magic `D4 C3 B2 A1`).
- Latest verified commands: `cargo test --manifest-path native-engine/rust/zapret_engine/Cargo.toml`, `.\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug`, `powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1`, `powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1`, and `powershell -ExecutionPolicy Bypass -File scripts\dpi-proof-pcap.ps1`.
- After the PCAP proof, the emulator is intentionally stopped; `adb devices` showed only physical/network device `adb-53271JEKB00683-G83QXW._adb-tls-connect._tcp`, and this run did not install/test on that physical device.
- `tools\dpi_http_simulator.py` was hardened to skip empty/no-data TCP connections before the real raw HTTP request; this fixed a simulator-only race where the first accepted connection could starve `scripts\dpi-proof.ps1`.
- An attempted persisted profile/JNI strategy-flags UI change compiled but failed runtime `scripts\dpi-proof.ps1`; it was rolled back and should not be treated as implemented.
- Full local work report path: `D:\Android\VPN_app\docs\WORK_REPORT_2026-08-06.md`.
- The project is not MVP-complete: deterministic TCP/IPv4 host proof, deterministic HTTP DPI split proof, and emulator PCAP capture are integrated, but app selection, persistent strategy profiles, QUIC policy, HAR/mitmproxy capture, Rust socket protection callback, and physical-device testing are still missing.

## Next Agent Startup

1. Read INSTRUCTION.md.
2. Read this file.
3. Read TASKS.md.
4. Read START_PROMPT.md before development startup.
5. Read WORKFLOW.md if work will modify files.
6. Read PROJECT_BRIEF.md only if the task needs technical project requirements.

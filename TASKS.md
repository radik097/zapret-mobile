# TASKS: Active Queue

Updated: 2026-08-06 02:11:18 +10:00

## Current Task

Status: in progress

Problem: Zapret Mobile had only specification files and no buildable Android application.

Goal: develop a rootless Rust-first Android VpnService MVP that builds into a real APK and can be tested locally/on-device.

Completion criteria:

- Debug APK exists and SHA-256 is recorded.
- Rust native engine builds into APK for `arm64-v8a` and `x86_64`.
- Android app requests VPN permission and creates TUN.
- Local TUN traffic is bridged to Rust SOCKS/DPI engine.
- TCP/IPv4 traffic works without a VPN loop.
- TLS ClientHello SNI split and HTTP Host split are tested.
- App/profile selection and persistence are implemented.
- Runtime behavior is verified on emulator and later on physical device.

## Backlog

- TASK-008: Replace current package-level VPN loop avoidance with explicit socket protection callback/path for outbound Rust sockets.
- TASK-021: Add Automatic fallback and Custom strategy profiles; extend Aggressive with multi-split and TLS record fragmentation.
- TASK-011: Add robust TLS ClientHello positive fixtures, SNI positions, ALPN parsing, and malformed packet coverage.
- TASK-012: Add HTTP parser coverage for methods, case-insensitive Host, header limits, and malformed input.
- TASK-014: Run on physical device and verify permission request, TUN creation, start/stop, and non-crash behavior.
- TASK-004: Research the referenced third-party repositories and licenses before reusing components.
- TASK-006: Do not claim MVP readiness until TUN traffic is really relayed and runtime/device behavior is verified.
- TASK-017: Add HTTP Toolkit/mitmproxy HAR export to the local report artifacts.

## Done

- TASK-000: Created a multi-file LLM agent basis from the previous one-file setup.
- TASK-000A: Added START_PROMPT.md for Windows-first, Rust-first development startup.
- TASK-001: Verified and installed local toolchain: JDK 21, Android SDK at `D:\Android\Sdk`, platform 36, Build Tools 36.1.0, NDK 28.2.13676358, CMake 3.31.6, Gradle wrapper 9.6.1, Rust Android targets, cargo-ndk 4.1.2.
- TASK-002: Resolved stack conflict by using Rust as the native engine and Java only as Android platform glue; no Kotlin source was added.
- TASK-003: Created buildable Android project structure with `app`, `native-engine/rust/zapret_engine`, docs, scripts, Gradle wrapper, and CI workflow.
- TASK-005: Created first-pass `docs/ARCHITECTURE.md`, `docs/LIMITATIONS.md`, `docs/SECURITY.md`, and `docs/THIRD_PARTY_LICENSES.md`.
- TASK-015: Built clean debug APK at `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `A9E2FE840933750017AED4BAFD1FFE335C1D03FA578E151F1B286D4B6F7A9BF4`.
- TASK-016: Installed Android Emulator and API 36 x86_64 Google APIs image; created AVD `zapret_api36_x86_64`; added and ran `scripts\emulator-smoke.ps1`, verifying install, launch, VPN permission path, TUN creation, foreground service, and stop cleanup on `emulator-5554`.
- TASK-007A: Integrated `hev-socks5-tunnel` as the Android TUN-to-SOCKS bridge; Gradle now runs `scripts\build-hev-socks5.ps1`, packages `libhev-socks5-tunnel.so`, starts it with a duplicated TUN fd, and stops it cleanly by closing the detached fd before native shutdown.
- TASK-019: Added Maestro CLI smoke automation with start and stop flows, JUnit output, logcat, screenshot, activity-service dumps, connectivity dumps, and TUN interface snapshots.
- TASK-020: Added minimal SOCKS5 UDP ASSOCIATE support in the Rust engine for UDP/DNS datagrams.
- TASK-007B: Proved user-app TCP traffic with a separate `dev.zapret.testclient` APK and `scripts\traffic-proof.ps1`; the test client reached `http://10.0.2.2:18080/probe` through active VPN and logged `result=200 body=zapret-proof`, while the host server logged `GET /probe HTTP/1.1` 200.
- TASK-018: Added a deterministic local HTTP DPI simulator harness with `tools\dpi_http_simulator.py` and `scripts\dpi-proof.ps1`; the raw test client sends `Host: blocked.example` through the active VPN, the simulator observes split chunks, and `dpi-report.json` records `decision=allowed_split`.
- TASK-017A: Added emulator PCAP capture proof with `scripts\dpi-proof-pcap.ps1`; it restarts the AVD with Android Emulator `-tcpdump`, runs the DPI proof, stops the emulator to flush capture data, and writes `build\test-artifacts\dpi-proof-pcap\emulator-network.pcap`.
- TASK-018A: Hardened `tools\dpi_http_simulator.py` to ignore empty/no-data TCP connections before the real raw HTTP request; an attempted profile/JNI strategy UI change was rolled back because runtime DPI proof failed.
- TASK-010: Added persistent app routing settings and UI. Users can route all apps or select launcher apps; `ZapretVpnService` applies the saved mode with `addDisallowedApplication` or `addAllowedApplication`. `scripts\traffic-proof.ps1 -SelectedAppsOnly` verified persisted selection and TCP traffic through the VPN on the emulator.
- TASK-013: Added a persistent `Block QUIC (UDP/443)` switch, separate JNI engine configuration, and a Rust UDP relay policy that drops destination port 443 when enabled. The policy is enabled by default, covered by Rust unit tests, and the emulator DPI proof confirmed the configured native engine still starts and relays TCP correctly.
- TASK-009: Added persistent Compatible, Balanced, and Aggressive profiles with a Spinner UI and native behavior. Balanced preserves midpoint split plus 12 ms delay, Compatible uses the same single split without delay, and Aggressive splits after the first Host/SNI character plus 35 ms delay. `scripts\dpi-proof.ps1 -AggressiveProfile` verified persistence and a distinct early split on the emulator.

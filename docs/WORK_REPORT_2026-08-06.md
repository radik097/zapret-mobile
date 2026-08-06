# Full Work Report - Zapret Mobile

Timestamp: 2026-08-06 02:32:52 +10:00

## Executive Summary

The `D:\Android\VPN_app` folder was turned from a specification/instruction workspace into a buildable, locally testable Android VPN project. The app now builds a debug APK, starts a rootless Android `VpnService`, creates a TUN interface, bridges TUN traffic into a local SOCKS5 engine, and has automated proofs for lifecycle, TCP traffic, deterministic HTTP split behavior, emulator-level PCAP capture, native socket protection, and physical Android runtime.

No remote push or PR was performed. The verified changes and this report are recorded only in the local Git repository.

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
- Added persistent all-app/selected-app routing, three strategy profiles, and a QUIC/UDP 443 blocking policy.
- Added fail-closed `VpnService.protect(fd)` callbacks for Rust TCP and UDP upstream sockets.
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
- Added `scripts/physical-smoke.ps1` for repeatable physical-device foreground-service, TUN, socket-protection, non-crash, and cleanup verification.

## Verified Runtime Evidence

- Emulator lifecycle: install, launch, VPN permission, foreground service, `tun0`, `10.71.0.1`, stop cleanup.
- Maestro lifecycle: start and stop JUnit files show zero failures.
- Traffic proof: `ZAPRET_TEST_CLIENT result=200 body=zapret-proof`.
- DPI proof: `ZAPRET_TEST_CLIENT raw_result=200 body=dpi-split-proof`.
- DPI simulator report: `decision=allowed_split`, `passed=true`, `chunk_count=2`.
- DPI first chunk: `GET /probe HTTP/1.1\r\nHost: blocked`.
- DPI full request: `Host: blocked.example`.
- PCAP artifact: `D:\Android\VPN_app\build\test-artifacts\dpi-proof-pcap\emulator-network.pcap`.
- Physical device: Pixel 8a, `arm64-v8a`, Android 17; foreground service and `tun0` at `10.71.0.1/24` observed.
- Physical socket protection: multiple `Protected outbound socket fd=` entries with no Zapret Mobile fatal exception.
- Physical cleanup: service and Zapret TUN absent after graceful stop; `physical-report.json` records `passed=true` and `cleanup_passed=true`.

## Current APK Artifacts

- Main APK: `D:\Android\VPN_app\app\build\outputs\apk\debug\app-debug.apk`
- Main APK size: 3,065,295 bytes
- Last recorded main APK SHA-256: `7279D03170BFC3C4491B8FED6EABEA042F6F7190DF6DFDAF94DAA9B2DB9CED42`
- Test-client APK: `D:\Android\VPN_app\test-client\build\outputs\apk\debug\test-client-debug.apk`
- Last recorded test-client APK SHA-256: `41FB8A451B25CFD52577F701BAFAFBF745B0D5BB5F189371FB89679BBA882EFE`

## Important Rollback

An attempted profile/settings UI and JNI strategy flag change was tested and rejected. It compiled, but `scripts/dpi-proof.ps1` failed at runtime. Per the local repository rule, that app-code change was rolled back to the last working VPN/JNI/Rust path before continuing. The retained fix from that investigation is limited to the test harness: `tools/dpi_http_simulator.py` now ignores empty/no-data TCP connections and keeps waiting for the real raw HTTP request.

## Current Known Limits

- App selection UI was implemented after the original report; see the post-report update below.
- Compatible, Balanced, and Aggressive profiles were implemented after the original report; Automatic/Custom profiles and advanced aggressive actions remain incomplete.
- Production Rust socket protection was implemented after the original report; package routing remains a fallback.
- HAR/mitmproxy or HTTP Toolkit export is not implemented.
- QUIC/UDP443 policy was implemented after the original report; see the post-report updates below.
- Physical runtime was verified with previously granted VPN consent. Because the device was locked, a fresh-install consent dialog was not exercised there; that consent flow is covered by emulator automation.
- This is a local debug APK and local proof harness, not a released production package.

## Final Verified Commands Before Commit

```powershell
cargo test --manifest-path native-engine\rust\zapret_engine\Cargo.toml
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat test lintDebug assembleDebug :test-client:assembleDebug
python -m py_compile tools\dpi_http_simulator.py
powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1
powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1
powershell -ExecutionPolicy Bypass -File scripts\dpi-proof-pcap.ps1
powershell -ExecutionPolicy Bypass -File scripts\physical-smoke.ps1
```

## Post-report update: selected-app routing

Persistent application routing was implemented and runtime-verified after this report was first written. The app now supports all-app routing or a saved launcher-app allow-list; `scripts\traffic-proof.ps1 -SelectedAppsOnly` verified selection persistence, `VpnService.Builder.addAllowedApplication`, and `result=200 body=zapret-proof` through the emulator VPN path. The updated debug APK SHA-256 is `A6358B77CB104A99FE64EA27E177269AFAF47B6EBC985D609F8824A1118AA7C5`.

## Post-report update: QUIC/UDP 443 policy

A persistent `Block QUIC (UDP/443)` switch and Rust relay policy were added. The Android service configures the policy through a separate JNI call before startup; the Rust UDP relay drops destination port 443 when enabled. Rust tests pass 7/7, and the emulator DPI proof passed with the configured policy logged as blocked. The updated debug APK SHA-256 is `5DF8DC1CF76EBAF9C204288480F78E4BACC08853719C16CE5A591FC32FDF8767`.

## Post-report update: native strategy profiles

Persistent Compatible, Balanced, and Aggressive profiles were added to the UI and native engine. `scripts\dpi-proof.ps1 -AggressiveProfile` verified UI persistence, JNI configuration, and a distinct early split: the aggressive first chunk ends at `Host: b`, while Balanced ends at `Host: blocked`. Rust tests pass 8/8. The updated debug APK SHA-256 is `4CAA7B59DEB25C80560AFDBDF8699ED27DE6205376939809E260A1F00C85083C`.

## Post-report update: native socket protection

Rust TCP sockets are now created before connect and passed through `VpnService.protect(fd)` via a JNI JavaVM/GlobalRef callback; UDP upstream sockets are protected before send. Protection failure aborts that relay operation, while package-level routing remains a fallback. The emulator traffic proof passed with protected-fd log evidence followed by HTTP 200. The updated debug APK SHA-256 is `7279D03170BFC3C4491B8FED6EABEA042F6F7190DF6DFDAF94DAA9B2DB9CED42`.

## Post-report update: physical Android runtime

`scripts\physical-smoke.ps1` passed on a Pixel 8a (`arm64-v8a`, Android 17) connected through network ADB. It reinstalled the current APK while preserving data, confirmed existing VPN consent, started `ZapretVpnService`, verified foreground state, `tun0` at `10.71.0.1/24`, protected native socket logs, and absence of an app crash, then stopped the service and confirmed complete TUN cleanup. Evidence is stored under `build\test-artifacts\physical-device`.

## Post-report update: notification start/stop, fallback, Zaptret2, redesign, updates, GitHub (2026-08-06, later session)

- **Notification Start/Stop controls**: `ZapretVpnService` now posts a foreground notification with a `Stop` action while running and, after stopping, a dismissible notification with a `Start` action, both wired to `PendingIntent.getService` against `ACTION_START`/`ACTION_STOP` — the VPN can be toggled entirely from the notification shade. `MainActivity` requests the `POST_NOTIFICATIONS` runtime permission on API 33+, since without it the foreground notification (and these actions) would not render at all.
- **Automatic strategy fallback**: the Rust engine now counts SOCKS upstream connect/relay failures (`CONNECTION_FAILURES`, reset on `configure()`), exposed via `pollFailureCount()`. A new `StrategyFallbackController` polls this every 15s while the VPN runs and escalates Compatible → Balanced → Aggressive → Zaptret2 after 3 failures, live-reconfiguring the native engine without tearing down the TUN, and updates the running notification text. A downloaded `CUSTOM` strategy is left alone by the escalation (explicit user choice).
- **Zaptret2 + custom strategies**: added native profile `PROFILE_ZAPTRET2` (splits after the first payload byte, independent of SNI/Host parsing, plus a 40ms delay) and a generic `PROFILE_CUSTOM` driven by a new JNI export `configureCustomStrategy(splitPosition, delayMs)`. `StrategyRepository` downloads a JSON strategy-pack list (default source: `strategy-packs.json` in this repo's `main` branch), caches it locally, and `StrategiesActivity` lets the user fetch and switch to one instantly.
- **Redesign**: added a theme system (`AppTheme`/`ThemeSettings`) with three selectable visual designs — Classic Green (original palette), Midnight (dark), and Aurora (violet/teal gradient header) — plus a shared `UiKit` of theme-aware card/button/switch builders (still plain `android.widget.*`, no AndroidX/Compose, matching the existing `android.useAndroidX=false` setup). The single-screen `MainActivity` was split into a lean status/start-stop screen plus dedicated `SettingsActivity` (theme picker, app routing, QUIC toggle, update check, about/diagnostics) and `StrategiesActivity` (built-in profile picker, downloaded strategy packs).
- **In-app updates**: `UpdateManager` checks the GitHub Releases API (`/repos/radik097/zapret-mobile/releases/latest`) once per 24h on launch and on demand from Settings, compares semantic versions, and on a newer release downloads the `.apk` asset and launches the system installer via a small custom `ApkFileProvider` (`content://dev.zapret.mobile.apkprovider/...`) rather than pulling in `androidx.core.content.FileProvider`. Requires `REQUEST_INSTALL_PACKAGES`; if "install unknown apps" isn't granted, it routes the user to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- **Verification**: `cargo test` 10/10 (added `zaptret2_profile_splits_after_first_byte`, `custom_profile_uses_configured_split_and_delay`), `.\gradlew.bat test lintDebug assembleDebug` all green (lint has 3 pre-existing informational warnings, no errors). No emulator/physical device was attached this session, so the new screens and notification actions were verified by build/lint/unit tests only, not a live UI walkthrough — that remains outstanding.
- **GitHub publication**: repository created and pushed to `https://github.com/radik097/zapret-mobile` (private, since this is a DPI-circumvention tool that has not had a public-release security/legal review). Updated debug APK SHA-256 is `B28B66BA5A6E779D5B8B2DA6659A8D1E438575073F63DCE73AB335E013157C9B`.

### Explicitly declined

A request to add telemetry that would upload phone identifiers, a device fingerprint, and engine logs to GitHub as `id.json` on every proxy connection was declined as covert data collection without user consent, even for a small friends-and-family distribution; see chat history for the reasoning and the consented/minimized alternative offered instead. It was not implemented.

### Still open

- No native/root-level TTL or IP-fragmentation tricks (out of scope for a rootless `VpnService` app).
- No live emulator/physical-device walkthrough of the new notification actions, themes, or Settings/Strategies screens this session.
- No HAR/mitmproxy export.

## Post-report update: CI fix and shared debug keystore

- **`android.yml` was failing on GitHub-hosted runners**: `buildRustAndroid` (in `app/build.gradle.kts`) and `scripts/build-hev-socks5.ps1` both hardcoded the local dev machine's SDK/NDK path (`D:\Android\Sdk`), which doesn't exist on the runner (`C:\Android\android-sdk`). Both now resolve `ANDROID_SDK_ROOT`/`ANDROID_HOME`/`ANDROID_NDK_HOME` from the environment first and only fall back to the local path when unset. Verified green: run `31057191232`, `build` job in 10m49s.
- **Added `.github/workflows/release.yml`**: on a `vX.Y.Z` tag push or manual dispatch, it verifies the tag matches `app/build.gradle.kts`'s `versionName`, builds and tests, then publishes/updates a GitHub Release with the APK attached (or `--clobber`-updates the asset if the release already exists) — this is what `UpdateManager`'s in-app update check looks for.
- **Bug found and fixed: GitHub release APK wouldn't install over the local build.** Every machine (and every ephemeral GitHub Actions runner) that has no explicit debug signing config auto-generates its own `~/.android/debug.keystore`, so each build gets signed with a different certificate. Confirmed with `apksigner verify --print-certs`: the local build's signer was `E8:C5:60:27...61F`, the first CI-built `v0.1.0` release asset was `D0:CD:58:E1...5AD` — Android refuses to install/update an app when the new APK's certificate doesn't match what's already installed, which is exactly the reported symptom ("release from GitHub won't install"). Fix: committed the local machine's existing `debug.keystore` at `keystore/debug.keystore` (the standard, non-secret Android debug credential — password `"android"`, alias `"androiddebugkey"` — safe to check in) and wired `app/build.gradle.kts`'s `debug` `signingConfig` to it explicitly, so local builds, CI builds, and GitHub Releases all share one signing identity going forward. Rebuilt locally (signature unchanged, `E8:C5:60:27...61F`) and re-ran `release.yml` (run `31058902937`); the updated `v0.1.0` release asset now verifies with the same `E8:C5:60:27...61F` certificate.

## Post-report update: multi-ABI builds (arm64-v8a, armeabi-v7a, x86, x86_64)

- Added `armeabi-v7a` and `x86` to `app/build.gradle.kts`'s `ndk.abiFilters`, the `buildRustAndroid` `cargo ndk -t` target list, and `scripts/build-hev-socks5.ps1`'s `APP_ABI` list, so the Rust engine and the `hev-socks5-tunnel` native bridge both build for all four ABIs. Installed the missing `i686-linux-android` Rust target locally (`armv7-linux-androideabi` was already present) and added both new triples to the `dtolnay/rust-toolchain` `targets` in `android.yml` and `release.yml`.
- `armeabi` (ARMv5/v6) is intentionally **not** built: the NDK removed that toolchain entirely years before the current NDK (28.2.13676358), so no supported toolchain can produce it.
- Enabled AGP `splits { abi { ... isUniversalApk = true } }`, so `assembleDebug`/`assembleRelease` now emit `app-arm64-v8a-*.apk`, `app-armeabi-v7a-*.apk`, `app-x86-*.apk`, `app-x86_64-*.apk`, and `app-universal-*.apk`. Verified locally: all five APKs build, and `apksigner verify --print-certs` confirms all five share the same signer (`E8:C5:60:27...61F`, the pinned shared debug keystore).
- `release.yml` now renames and publishes all five APKs per tag/dispatch as `zapret-mobile-<version>-<abi>.apk` / `-universal.apk`, and `android.yml`'s artifact upload globs `*.apk` instead of the single old `app-debug.apk` filename.
- `UpdateManager.findApkAssetUrl` now picks the release asset matching the device's own `Build.SUPPORTED_ABIS` first, then a `-universal.apk` asset, then any `.apk` as a last resort — needed because a release now has 5 APK assets instead of 1, and downloading the wrong ABI would produce an APK that fails to install or crashes.

## Still open

- No native/root-level TTL or IP-fragmentation tricks (out of scope for a rootless `VpnService` app).
- No live emulator/physical-device walkthrough of the new notification actions, themes, Settings/Strategies screens, or the multi-ABI update picker this session.
- No HAR/mitmproxy export.
- Multi-ABI builds were verified for arm64-v8a and x86_64 only via physical/emulator runtime in earlier sessions; armeabi-v7a and x86 builds compile and are signed correctly but have not been runtime-tested on real 32-bit hardware.

## Post-report update: Play Protect block and a proper release signing key

- **Reported symptom**: "Play Protect заблокировала небезопасное приложение" when sideloading the GitHub release APK. Confirmed with the user this is a soft block (an override/"install anyway" path exists), not a hard block.
- **Root cause is behavioral, not the certificate.** Play Protect scores sideloaded apps by on-device/cloud heuristics and reputation, not by which key signed them — switching to a different signing certificate would not have removed the warning. The most likely trigger in this app was the in-app self-update flow: `REQUEST_INSTALL_PACKAGES` plus a custom `content://` provider (`ApkFileProvider`) that the app used to silently download an APK and launch the system installer for itself. A VPN-permission app that also fetches-and-installs its own APKs matches a well-known "dropper" malware heuristic.
- **Fix**: `UpdateManager` no longer downloads or installs anything. It now opens the GitHub release page in the browser (`Intent.ACTION_VIEW` on the release's `html_url`) and tells the user which per-ABI asset to tap; the actual download/install happens through Chrome's normal, trusted download flow instead of the app's own code. Removed `ApkFileProvider.java`, the `REQUEST_INSTALL_PACKAGES` permission, and the `<provider>` manifest entry as a result. This does not guarantee Play Protect will never flag a brand-new/low-reputation sideloaded APK again, but it removes the single strongest heuristic signal this app was carrying.
- **Also generated a real release signing key** (separate from the shared debug key used for local/CI/GitHub-Releases builds so far): `release.keystore` (RSA 4096, ~30-year validity, alias `zapret-mobile`, matching the pre-existing `keystore.properties.example` template) plus a local `keystore.properties` — both git-ignored, neither committed. Wired `app/build.gradle.kts` to sign `release` builds with it when present, falling back to unsigned (not build-breaking) when the properties file is absent, e.g. on CI. Verified locally: `assembleRelease` produces 5 ABI-split + universal APKs all signed with the new certificate (`58:7E:1E:A2...FA:C9:0`), distinct from the shared debug certificate. **This keystore and its password are not stored anywhere but this machine's `keystore.properties`/`release.keystore` — back them up. Losing them means any future release under this identity (e.g. Google Play, if pursued later) can never be updated under the same app.**
- **Play Store / RuStore / F-Droid submission was explicitly not pursued this session** — the user's actual goal turned out to be eliminating the Play Protect warning on direct sideloading, not store distribution. If store distribution is wanted later: Google Play requires removing any self-update-outside-Play mechanism entirely (policy-mandated, already effectively done) and going through VPN-app-specific policy review; RuStore/F-Droid have their own separate review processes not evaluated here.

## Post-report update: Flowseal-inspired "fake + split" strategy is now the default

The user asked to bring in the technique set from `Flowseal/zapret-discord-youtube` (a Windows `winws.exe`/WinDivert packaging of `bol-van/zapret`) and make it the primary strategy. That project runs at the raw-packet level via a kernel driver (WinDivert), which a rootless Android `VpnService` cannot do; what was actually portable and implemented:

- **New default profile `PROFILE_FLOWSEAL`** (native id 5): sends a same-shaped decoy TLS ClientHello with the SNI bytes flipped (so length fields stay valid but the hostname is garbage) using a temporarily-lowered `IP_TTL` (via `TcpStream::set_ttl`, no raw sockets needed) so it expires before the real server but is still seen by a DPI middlebox closer to the client, then restores the original TTL and sends the real ClientHello split near its SNI with a 35ms delay. This mirrors bol-van/zapret's `fake`+`split`/"fakedsplit" combination as packaged by Flowseal's presets. Falls back to a plain early split (no decoy) for non-TLS traffic.
- **`STRATEGY_PROFILE`'s default, `EngineSettings.getStrategyProfile`'s default, `StrategyProfile.fromNativeId`'s fallback, and `StrategyFallbackController.ESCALATION_ORDER`'s first entry were all changed to Flowseal** — it is now genuinely the primary/default strategy, escalating to Zaptret2 → Aggressive → Balanced → Compatible on repeated failures, per the user's "make it primary" request.
- **Fake TTL is configurable** (`NativeZapretEngine.configureFakeTtl`, `EngineSettings.getFakeTtl`/`setFakeTtl`, default 6, editable in the Strategies screen) since the right TTL is provider/network-dependent — exactly the "стратегии нужно периодически обновлять" point from the user's own description of how Flowseal presets are chosen.
- **Hostlist-based targeted mode** (`NativeZapretEngine.configureHostlist`, off by default): mirrors Flowseal's `--hostlist`/`general.bat` vs `discord.bat` targeting — when enabled, the active strategy is only applied to connections whose SNI/HTTP-Host suffix-matches a configured domain list (default: `discord.com, discordapp.com, discord.gg, discordapp.net, youtube.com, youtu.be, googlevideo.com, ytimg.com, ggpht.com`); everything else relays untouched. Editable in the Strategies screen. Defaults to off (apply to all traffic) so existing behavior for other apps/domains isn't silently narrowed.
- **Explicitly not implemented, and why**: (1) `multidisorder`/true out-of-order TCP segment delivery — requires raw sockets/packet crafting with forged sequence numbers, not available to a normal (non-root) Android app; (2) QUIC Initial packet faking — the app already blocks QUIC/UDP-443 outright to force fallback to TCP/TLS, which is simpler and already covers the same goal; (3) GameFilter port exclusion — not built as its own toggle, but achievable today by leaving "Route selected apps only" off for the game and relying on hostlist targeting being off by default so game traffic is never touched unless targeted mode is explicitly turned on.
- **Verification**: `cargo test` 18/18 (added a hand-built TLS ClientHello test fixture, decoy-shape tests, hostlist-matching tests, and a loopback-socket smoke test for the combined fake+split send path). `gradlew test lintDebug assembleDebug` green across all 4 ABIs. No live device/emulator walkthrough of the new Strategies-screen controls (TTL editor, targeting editor) this session.

## Post-report update: web research on current DPI/TSPU state, then two follow-up fixes

Researched the current (August 2026) state of `bol-van/zapret`, `Flowseal/zapret-discord-youtube`, `hufrea/byedpi` (Android, root-free, same architecture class as this app), and `by-sonic/sonicdpi` (cross-platform Rust) to check whether the Flowseal profile added above was using outdated or needlessly fingerprintable techniques. Findings, independently corroborated across sources:

- Real zapret's `disorder`/`multidisorder`/`badseq`/`ts`-fooling genuinely require NFQUEUE (Linux) or WinDivert (Windows) raw-packet access — confirmed not just by zapret's own docs but by ByeDPI (Android, no-root) and SonicDPI (which explicitly requires root/admin/`cap_net_admin` on every platform it supports, and doesn't support Android at all). This corroborates the earlier "not implemented, and why" note rather than changing it.
- TSPU now does entropy analysis on fake packets — older zapret fakes used fixed patterns TSPU learned to fingerprint. This app's own fake decoy had the same class of problem: XORing the real SNI with a constant (0xFF) is a fixed, reversible, high-entropy-looking transform.
- `dpi-desync-fooling` has shifted community preference from `badseq` to `ts` (TCP timestamp) fooling as more DPI implementations strictly validate sequence numbers — but both still require raw sockets, so neither is portable here.
- `--dpi-desync=oob` (one byte sent via TCP's URG/out-of-band mechanism) is the one additional technique confirmed implementable via a plain socket flag (`MSG_OOB`), not raw packets.

Two follow-up changes to `native-engine/rust/zapret_engine/src/lib.rs`:

1. **Less deterministic fake decoy**: `build_fake_tls_hello` no longer XORs the SNI; it now calls `build_decoy_hostname`, which replaces the SNI with a byte-length-matched slice of a rotating pool of five ordinary domains (`www.google.com`, `www.cloudflare.com`, `www.wikipedia.org`, `static.googleusercontent.com`, `www.microsoft.com`), padding with ordinary ASCII filler if the target length exceeds the picked domain. The decoy content is now ordinary-looking hostname text with normal entropy instead of high-entropy garbage, and rotates across connections via `DECOY_ROTATION` (an `AtomicU32` counter) instead of being a single fixed function of the real hostname. This is a lightweight rotation, not cryptographic randomness — deliberately scoped to fix the "always the same reversible transform" problem the research flagged, not to add a new RNG dependency.
2. **MSG_OOB added to the Flowseal split**: new `send_oob_byte` (Android: borrows the `TcpStream`'s fd into a `socket2::Socket` via `Socket::from_raw_fd` + `send_out_of_band`, then `std::mem::forget`s that `Socket` so its `Drop` doesn't close a file descriptor the `TcpStream` still owns; non-Android test builds fall back to a plain `write_all` so no byte is ever lost) and `send_first_segment_with_oob` (sends all but the last byte of a segment normally, then the last byte via `send_oob_byte`). Wired into `send_flowseal_strategy`'s first segment on both the SNI-split and no-split-found fallback paths. No new crate dependency — `socket2` was already a dependency and already exposes `send_out_of_band`.
- **Verification**: `cargo test` 21/21 (added decoy-length/rotation tests and a byte-exact OOB-segment test). `cargo build` for all 4 Android ABIs via `cargo ndk` succeeded (the Android-only `send_oob_byte` path using `FromRawFd`/`mem::forget` is not exercised by host-side `cargo test`, only compile-checked through the real cross-compile). `gradlew test lintDebug assembleDebug` green across all 4 ABIs.

## Post-report update: in-app strategy auto-tester (blockcheck.sh equivalent)

Added a `StrategyAutoTester` that, while the VPN is running, cycles through the five built-in profiles (Flowseal, Zaptret2, Aggressive, Balanced, Compatible), and for each one makes a real SOCKS5 CONNECT + TLS handshake + tiny HTTP GET to `discord.com` and `www.youtube.com` through the app's own local SOCKS5 port (`127.0.0.1:1080`) -- the exact same code path real app traffic uses, so results reflect whether the currently-configured strategy actually gets a ClientHello past this network's DPI right now, not just whether a socket opens. This mirrors `bol-van/zapret`'s `blockcheck.sh`.

- `ZapretVpnService` gained a static `runningInstance` reference (same-process only; the app has no `android:process` split) plus `applyConfiguredStrategy()` (the user's saved profile/QUIC/hostlist settings, extracted from `startVpn()`) and `applyProfileForTesting(StrategyProfile)` (temporarily switches the live engine to a candidate, bypassing hostlist targeting so results reflect the strategy itself). The tester calls the latter for each candidate, then calls the former once at the end to restore the user's real settings.
- The SOCKS5 client in `StrategyAutoTester` is intentionally minimal, not a general client: it reads exactly the 10-byte reply our own Rust server always sends (`write_socks_success` hardcodes `ATYP=0x01`/`127.0.0.1`), so there's no need for generic ATYP branching.
- Added a "Strategies" screen card: "Run auto-test" (disabled with a toast if the VPN isn't running), a result row per profile (`X/2 OK, avg Nms`) with an "Apply" button to select it.
- **Live device attempt**: ran this on the Pixel 8a over Wi-Fi ADB. VPN consent had been revoked since the last session (`ACTIVATE_VPN: ignore; rejectTime=...`) and had to be re-granted through the real system dialog by the user (not something ADB can do non-interactively). The user then ran the auto-test themselves. Result: could not fully confirm pass/fail details after the fact -- the device's system logcat buffer is dominated by audio/Wi-Fi HAL noise and had already rotated past the relevant window by the time it was inspected, and `StrategyAutoTester` only logged on failure (by design, at the time), so a clean run leaves no trace either. Confirmed working from logs: VPN started with Flowseal as the active profile, `tun0` established at `10.71.0.1/24`, and multiple `Protected outbound socket` events during the brief run. This gap in observability is exactly what prompted the next update below.

## Post-report update: daily app log (system logcat kept rotating past what we needed)

The auto-tester's live device run above showed the problem directly: Android's shared system logcat buffer on a busy phone rotates within seconds under audio/Wi-Fi HAL noise, and by design `StrategyAutoTester` only logged failures, so there was no durable record of what actually happened. Added `AppLog`, a small file-backed log separate from logcat:

- Writes timestamped lines to `filesDir/logs/zapret-<yyyy-MM-dd>.log`, mirroring `android.util.Log`'s `i`/`w`/`e` API (and still calling through to `Log` too, so logcat visibility is unchanged). Every write also deletes any log file whose name doesn't match today's date, so the log is self-clearing on a day boundary with no separate cleanup job needed, per the request ("логи которые очищаются каждый день").
- Wired into `ZapretVpnService` (start/stop lifecycle, strategy profile, QUIC policy, socket protection, app routing, network callbacks), `StrategyAutoTester` (now logs every domain result -- success and failure -- not just failures as before), and `StrategyFallbackController` (escalation events).
- Added a "Diagnostics log" card to the Settings screen: "View today's log" opens a dialog with a scrollable monospace view of the current day's entries, plus Copy-to-clipboard and Clear actions -- so this is inspectable from the phone itself, not just via `adb logcat` after the fact.

## Post-report update: dark theme (Midnight) text-on-text bug

Reported: some text was unreadable in the Midnight (dark) theme -- dark text on a dark background. Root cause: `Spinner`'s closed/selected display (used for the built-in strategy picker and the theme picker itself) is built from `android.R.layout.simple_spinner_item`, which renders with the *platform's* default text color (dark, meant for a light background) regardless of the app's own `AppTheme` enum -- since that stock Android layout has no way to know about our custom palette. On Classic/Aurora (light card backgrounds) this was invisible as a bug; on Midnight's dark cards, it meant near-black text on a near-black background.

Fix: added `UiKit.spinnerAdapter(context, theme, labels)`, an `ArrayAdapter` subclass overriding `getView()` (the closed display, always rendered on our own themed card) to force `theme.textPrimary`, while leaving `getDropDownView()` (the transient popup list) on the platform default -- that popup uses the system's own light background with dark text regardless of our theme, which stays legible either way, so only the always-visible closed value needed fixing. Replaced both `Spinner` construction sites (`StrategiesActivity`'s profile picker, `SettingsActivity`'s theme picker) to use it. Also added `UiKit.editText(context, theme)` to set both text and hint colors consistently, since the two hand-built `EditText` fields (fake-TTL, hostlist domains) had text color set ad hoc at each call site.

**Verification**: `gradlew test lintDebug assembleDebug` green across all 4 ABIs (lint: same 3 pre-existing informational warnings, no new ones). No live device re-check of the Midnight theme specifically this pass -- the original report came from the user's own device, but the fix itself wasn't re-verified visually on-device before this report was written.

## Post-report update: v0.1.2 MSG_OOB regression -- Flowseal was actively breaking real connections

The user ran the strategy auto-tester live and shared the resulting `AppLog` output (proof the daily log feature above works: it captured exactly what was needed). Two findings:

- **Flowseal (the default profile) failed 0/2 domains in the auto-test, with `SSLHandshakeException` / BoringSSL `DECRYPTION_FAILED_OR_BAD_RECORD_MAC` and `BAD_DECRYPT` errors.** Worse, this wasn't just a test artifact: the *live* `StrategyFallbackController` had already escalated away from Flowseal to Zaptret2 after 12 real connection failures, minutes before the auto-test even ran, meaning ordinary use of the app (not just the tester) was being broken by this in the v0.1.2 build.
- discord.com failed uniformly across *every* profile with `CertPathValidatorException: Trust anchor for certification path not found`, while `www.youtube.com` succeeded on every profile except Flowseal. Since this is consistent across all strategies, it looks like a genuine network-level TLS interception/substitution specific to discord.com on this network (not something split/fake-style DPI desync addresses) rather than a bug in this app -- noted here rather than "fixed", since there's nothing in our own code to change for it.

**Root cause of the Flowseal regression**: the MSG_OOB technique added in the prior update (`send_first_segment_with_oob`) was applied to the *real* split ClientHello. Without `SO_OOBINLINE` set on the receiving socket -- the default for essentially all TLS server sockets -- a receiver's ordinary `read()` calls silently drop the byte marked urgent; the real server therefore saw a ClientHello with one byte missing from the middle of the message. TLS 1.3 key derivation hashes the entire handshake transcript, so a byte dropped server-side but present client-side desyncs that transcript hash between the two ends, which surfaces exactly as a MAC/decryption failure on the first encrypted record -- matching the observed errors precisely, and deterministically (100% of Flowseal attempts failed, both live and in the auto-test, unlike a probabilistic DPI-block signature).

**Fix**: moved OOB off the real split segments entirely (`send_flowseal_strategy` now writes both real segments with plain `write_all`, unchanged from before OOB was added) and onto the disposable fake decoy only (`send_fake_decoy` now calls `send_first_segment_with_oob` on the decoy bytes). This is safe specifically because the decoy is sent at a deliberately low TTL and is not expected to arrive at the real destination intact anyway -- a dropped byte there costs nothing, while still adding the intended extra DPI-confusion signal at the point the decoy is actually observed (a middlebox a few hops from the client, not the real endpoint). `send_first_segment_with_oob`/`send_oob_byte` themselves are unchanged; only *where* they're called moved.

**Verification**: `cargo test` 21/21 (unchanged pass count; the affected code path isn't independently unit-tested against a real TLS peer, since that requires a live server -- this is exactly why the live-device auto-test mattered). `gradlew test lintDebug assembleDebug` green across all 4 ABIs. Shipped as v0.1.3, installed directly onto the Pixel 8a via `adb install -r` ahead of the GitHub release finishing, since this was actively breaking real VPN use.

## Post-report update: explicit "user pressed this" log entries

Requested: make it possible to tell, from the diagnostics log, what the user actually did versus what happened automatically (fallback escalation, network callbacks, etc.) -- directly motivated by reading real `AppLog` output where this distinction wasn't always obvious.

Added `AppLog.userAction(context, description)`, a thin wrapper that logs under one consistent tag (`UserAction`) so these lines are easy to pick out or filter. Wired into every user-initiated action across the three screens: Start/Stop VPN and screen navigation (`MainActivity`); theme selection, app-routing toggle and app-selection save, QUIC toggle, "Check for updates", and the log dialog's View/Copy actions (`SettingsActivity`); "Run auto-test", auto-test result "Apply", built-in profile selection, fake-TTL save, hostlist targeting save, and downloaded-pack selection/refresh (`StrategiesActivity`). Skipped logging the log dialog's "Clear" action specifically, since logging it would just get deleted along with everything else it's clearing.

`Spinner.OnItemSelectedListener` fires once automatically on initial layout, not just on user interaction -- both spinners already guarded against this in a way that also happens to suppress the log call on that first automatic firing: the theme spinner via its existing `if (selected != currentTheme)` check (the automatic firing always reports the already-current theme), and the strategy spinner via a new `userDriven` flag (the first callback sets it without logging or applying; only the second callback onward does).

**Verification**: `gradlew test lintDebug assembleDebug` green across all 4 ABIs.

## Post-report update: the v0.1.3 OOB fix did NOT fix Flowseal -- fake decoy disabled by default

The user re-ran the auto-test on v0.1.3 (the OOB fix above) and shared the log: **Flowseal still failed 0/2 domains with the identical BoringSSL `BAD_DECRYPT`/`DECRYPTION_FAILED_OR_BAD_RECORD_MAC` errors.** This disproves the "OOB on real data" theory as the sole cause -- moving OOB off the real segments and onto the decoy-only did not help, so the corruption was coming from the fake decoy itself, independent of OOB.

**Revised root cause**: the fake decoy is sent over the *same* TCP connection as the real ClientHello, at a low TTL meant to make it expire before reaching the real server (so only a DPI middlebox a few hops closer to the client sees it). Real zapret computes this TTL automatically from the destination's observed SYN-ACK TTL (`autottl`), which requires raw packet access this app doesn't have. With a static guessed default (`FAKE_TTL = 6`), if the real network path to Discord/YouTube's edge is shorter than 6 hops -- plausible for well-peered CDN infrastructure -- the decoy is not dropped en route: it reaches the real server intact. The server then sees an unexpected second ClientHello-shaped message on the same connection immediately before the real (split) one, which corrupts the handshake -- consistent with every other profile (no decoy) succeeding on youtube.com while only Flowseal fails 100% of the time, and consistent with TLS 1.3's transcript-hash mechanism producing exactly a MAC/decrypt failure once negotiation state is thrown off.

**Fix**: added `FAKE_DECOY_ENABLED` (default `false`) gating the decoy send in `send_flowseal_strategy` entirely -- Flowseal now defaults to split-only (the same reliable mechanism every other profile already uses successfully), with the fake decoy demoted to an explicit opt-in for users willing to tune `FAKE_TTL` for their own network. New `NativeZapretEngine.configureFakeDecoy(boolean)` / `EngineSettings.isFakeDecoyEnabled`/`setFakeDecoyEnabled`, wired into both `applyConfiguredStrategy()` and `applyProfileForTesting()`. Added a "Strategies" screen switch ("Enable fake decoy (advanced)") with an explicit warning about the risk, placed above the existing TTL field. Updated the Flowseal profile label to stop implying "fake + split" unconditionally.

**Verification**: `cargo test` 22/22 (added `flowseal_strategy_skips_decoy_when_disabled_by_default`, asserting the exact real bytes arrive with nothing prepended when the flag is off; updated the existing decoy test to explicitly enable the flag, since it's no longer on by default). `gradlew test lintDebug assembleDebug` green across all 4 ABIs. Installed directly onto the Pixel 8a via `adb install -r` ahead of the GitHub release finishing, same as the previous hotfix, given this was still actively breaking real use.

## Post-report update: the log now identifies its own build (v0.1.5)

The user sent another `AppLog` dump labelled "VER 0.1.4" showing Flowseal still at 0/2 with the same BoringSSL errors, and asked whether blocking QUIC could be the cause, plus: put the version into the log so it doesn't have to be typed manually each time.

**The log was not from v0.1.4.** Its entries run 15:03:35–15:04:00; `dumpsys package dev.zapret.mobile` on the device reports `lastUpdateTime=2026-08-06 15:17:02` for versionCode 5 / 0.1.4. The session was therefore recorded on v0.1.3 — the build where the decoy was still unconditional — roughly 14 minutes before v0.1.4 was installed. The v0.1.4 fix has still not been exercised on-device.

This is independently corroborated by the code itself: with the decoy off, `PROFILE_FLOWSEAL` and `PROFILE_AGGRESSIVE` execute byte-identical logic (both split at `tls_sni_start_split_position` with a 35 ms delay, both via plain `write_all`). A build matching current `main` cannot produce Flowseal 0/2 while Aggressive is 1/2 — so the binary that produced that log did not match current `main`.

**QUIC blocking is not a plausible cause**, and can be ruled out on mechanism: it only drops outbound UDP/443 datagrams (`should_block_udp`), which forces clients to fall back to TCP/TLS. It touches no TCP byte stream, so it cannot desynchronise a TLS record MAC. It is also on for every profile equally, yet only Flowseal failed. `BAD_DECRYPT`/`DECRYPTION_FAILED_OR_BAD_RECORD_MAC` means the two ends derived different handshake transcripts — a TCP-stream-content problem, which on that build was the decoy.

**Change**: `AppLog` now writes a `=== Zapret Mobile <version> (build N) — <date> ===` header as the first line of each day's log file, and `ZapretVpnService.startVpn` logs `Zapret Mobile <version> (build N), engine <native version>` on every start (so a same-day upgrade is still visible). Version comes from `PackageManager` (`getLongVersionCode` on API 28+), not a hardcoded constant, so it can never drift from what is actually installed.

Also fixed a genuine test-isolation flake found while verifying: `flowseal_strategy_sends_decoy_then_splits_real_hello` and `flowseal_strategy_skips_decoy_when_disabled_by_default` both mutate the process-global `FAKE_DECOY_ENABLED` and `cargo test` runs them on parallel threads, so the decoy-off test intermittently observed decoy bytes and failed (observed once locally). Both now take a shared `DECOY_FLAG_GUARD` mutex.

**Verification**: `cargo test` 22/22, run 5× consecutively with no flake. `gradlew testDebugUnitTest lintDebug assembleDebug` green across all 4 ABIs. Installed on the Pixel 8a via `adb install -r` as versionCode 6 / 0.1.5. Flowseal's actual behaviour with the decoy disabled is **still unconfirmed on-device** — that needs a fresh auto-test run on 0.1.5, which will now say `0.1.5 (build 6)` in its own log.

## Post-report update: identifying who signs the discord.com certificate

Asked whether the `CertPathValidatorException: Trust anchor for certification path not found` on discord.com is fixable at all. The earlier note calling it "a network-level interception, nothing in our code to change" was too quick a dismissal: **if** the chain is a DPI-injected substitute, that injection is triggered by the middlebox reading the SNI — which is exactly what a desync strategy is supposed to prevent, so it would be in scope after all. Two hypotheses that the existing log cannot distinguish:

1. A substitute certificate injected in response to the SNI (a block mechanism — in scope for strategy work).
2. A genuine trust-store/chain problem or a blanket MITM proxy (not in scope). Weak on the evidence, since `www.youtube.com` validates fine on the same network through the same code path — a blanket MITM would break both.

Added the diagnostic that separates them: `StrategyAutoTester` now builds its `SSLSocketFactory` from an `SSLContext` wrapping a `ChainRecordingTrustManager`. That manager stores the presented chain and then **delegates to the platform's own `X509TrustManager` unchanged** — it is explicitly not a trust-all manager, validation is identical to before and a bad chain still fails the handshake. The only new behaviour is that a failure can now be logged as `... | peer cert: subject=..., issuer=..., chain_length=N`. A Let's Encrypt/Cloudflare issuer on a rejected chain means hypothesis 2; anything else (a self-signed leaf, an unknown local CA, a mismatched subject) means hypothesis 1 and points back at strategy work.

**Verification**: `gradlew testDebugUnitTest lintDebug assembleDebug` green across all 4 ABIs; installed on the Pixel 8a. The diagnostic has not yet been run — it needs one auto-test on the device.

## Post-report update: multisplit profile (v0.1.6)

Of the three candidate improvements listed above, multisplit was chosen to implement and test first. Added as a **separate profile** (`PROFILE_MULTISPLIT`, native id 6) rather than folded into Flowseal, specifically so the auto-tester compares it against every existing profile in a single run instead of changing the meaning of an existing result.

- `send_multisplit_strategy` cuts the first payload into several TCP segments (`MULTISPLIT_DELAY_MS = 20` between each, enough that they aren't coalesced) instead of the single cut every other profile makes. `multisplit_positions` picks: offset 1 (splitting the TLS record header itself, so the record length can't be read out of segment one), one byte into the SNI hostname, the hostname's midpoint, and its end — so the hostname is spread across three segments rather than merely being preceded by a break. Plain HTTP gets the same treatment on the `Host:` header value. Positions are deduplicated, sorted, and constrained strictly inside the packet; anything too short or unrecognised falls through to a single whole-payload write.
- Rationale: a single split defeats DPI that inspects only the first segment. It does not defeat DPI that reassembles a fixed small number of segments, or that matches the SNI string as long as it survives intact inside any one segment. Multisplit targets both.
- Byte contents are never modified — only how the payload is chopped into `write` calls. This is the significant safety difference from the fake decoy: there is no extra data on the wire that the real server could misinterpret, so multisplit cannot cause the transcript-hash corruption that made the decoy unsafe.
- Added `PROFILE_MAX` (currently `PROFILE_MULTISPLIT`) so `configure`'s id-range validation no longer has to be edited by hand each time a profile is added — the previous hardcoded `..=PROFILE_FLOWSEAL` bound would have silently rejected the new profile.
- Wired into `StrategyProfile` (Java enum), the Strategies-screen picker, `StrategyAutoTester.TESTABLE_PROFILES`, and `StrategyFallbackController.ESCALATION_ORDER` — placed second in both, right after Flowseal.

**Verification**: `cargo test` 25/25 (added three tests: split positions cut the record header and land inside the SNI and are strictly increasing; HTTP and undersized inputs handled; and a loopback test asserting the bytes received equal the original packet exactly, i.e. segmentation changed and nothing else). `gradlew testDebugUnitTest lintDebug assembleDebug` green across all 4 ABIs. Installed on the Pixel 8a as versionCode 7 / 0.1.6 (confirmed via `dumpsys`). Whether multisplit actually gets past this network's DPI is **unverified** — that is the pending on-device auto-test.

## Post-report update: emulator run (x86_64, Android 16)

Ran the full flow on the local emulator: installed 0.1.6, granted the VPN app-op via `appops set dev.zapret.mobile ACTIVATE_VPN allow` (the consent dialog can't be tapped non-interactively, but `appops` sets the same state), started the VPN and drove the real UI via `uiautomator dump` + `input tap`.

Every profile returned **2/2**, including `MULTISPLIT` and including `discord.com`. What that does and does not establish:

- **Establishes**: multisplit works functionally — a real TLS handshake and HTTP request survive four cuts. Flowseal with the decoy off no longer breaks connections (the v0.1.4 fix is correct). Version-stamping and user-action logging behave as designed.
- **Does not establish**: anything about DPI evasion. `discord.com` succeeds here on *every* profile including Compatible, so this network applies no Discord block and no certificate substitution — there is nothing to bypass, and a profile with no splitting at all would also score 2/2. The `peer cert` diagnostic never fired because no handshake failed. Only the user's own network can answer the open question.

## Post-report update: automatic log upload via a Cloudflare Worker (v0.1.7)

Requested: send the log automatically once the auto-test finishes, to GitHub, through Cloudflare.

**Architecture**: app → Cloudflare Worker → GitHub Issues. The relay exists specifically so the GitHub token never ships inside the APK, where anyone downloading a release could extract it. The app carries only the Worker URL and a shared secret whose sole capability is "file a log".

- `LogUploader` posts today's log as JSON on a background thread after `StrategyAutoTester` writes its final line (deliberately after, so the upload contains the results that triggered it). Refuses non-HTTPS endpoints outright. Truncates to the most recent 60,000 characters, below GitHub's 65,536-character issue-body limit. Every outcome — skipped, rejected, failed, succeeded — is written to the log itself, so the log is an honest record of whether it left the device.
- `cloudflare/log-relay/` holds the Worker (`src/index.js`), `wrangler.jsonc`, a README with the full deploy procedure, and `test/worker.test.mjs`. The Worker checks the shared secret with a length-then-accumulate comparison rather than `===`, rejects non-POST, requires a non-empty log, strips newlines/backticks from caller-supplied strings used in the issue title, and fences the log with five backticks while collapsing any run of five or more inside it, so no log line can close the fence early and have the remainder rendered as markdown. Upstream error detail goes to `console.error`, never to the caller.
- **Off by default, and configured in the UI rather than hardcoded.** A new Settings card carries the switch, the endpoint field, the shared-secret field, and text stating plainly what the log contains (version, strategy results, TLS errors for the two test domains) and what it does not (browsing history — the app never logs visited sites). This is diagnostics the user's own device sends to the user's own repo; if builds are handed to other people, it becomes *their* data going to that repo, which is why it is opt-in with the contents disclosed rather than silent. The README also notes that the filed issues are exactly as private as the repository is.

**Verification**: relay tests 16/16 via `node test/worker.test.mjs` against a stubbed `fetch` — happy path, wrong/absent/prefix token all 401, non-POST 405, empty and missing log 400, unset secret 500, GitHub failure 502, fence-escaping and title-injection. `gradlew testDebugUnitTest lintDebug assembleDebug` green across all 4 ABIs. On the emulator, with upload enabled and pointed at a deliberately unresolvable HTTPS endpoint, the log shows the attempt firing immediately after `Auto-test finished` and recording `UnknownHostException` — confirming the trigger point, the background execution and the failure logging. **The 2xx path is unverified on-device**: it needs a real deployed Worker, and no third-party endpoint was used as a stand-in rather than send even a synthetic log somewhere unnecessary. `curl` instructions for verifying the deployed Worker are in the README.

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

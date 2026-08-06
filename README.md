# Zapret Mobile

Zapret Mobile is a rootless Android app for local traffic handling through Android `VpnService`. It does not use a remote VPN server, does not change the external IP address, and does not decrypt HTTPS traffic.

Current state: working Rust-first MVP in progress. The APK requests VPN permission, creates a TUN interface, relays selected or system-wide IPv4 traffic through `hev-socks5-tunnel` and the local Rust SOCKS5/DPI engine, protects native outbound sockets with `VpnService.protect(fd)`, supports persistent Compatible/Balanced/Aggressive/Zaptret2 profiles plus downloadable custom strategy packs, and per-app routing, and can block QUIC/UDP 443. It automatically falls back through strategy profiles on repeated connection failures, can be started/stopped from the notification shade, ships three selectable visual themes (Settings screen), and checks GitHub Releases for in-app updates. Emulator and physical-device runtime are verified for the core VPN path; the newer UI/notification/update surfaces are build/lint/unit-test verified only (no live device walkthrough yet this session). Advanced QUIC analysis, HAR export, and deeper TLS/HTTP parser coverage remain in progress.

## Build

```powershell
.\scripts\build-debug.ps1
```

Manual commands:

```powershell
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Selected-app runtime proof:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\traffic-proof.ps1 -SelectedAppsOnly
```

Aggressive-profile DPI proof:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dpi-proof.ps1 -AggressiveProfile
```

Physical-device smoke (device must already have VPN consent):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\physical-smoke.ps1
```

## Supported CPU architectures

`assembleDebug`/`assembleRelease` produce one APK per ABI plus a universal fallback:

- `arm64-v8a` — current 64-bit ARM (all modern phones/tablets/TVs).
- `armeabi-v7a` — 32-bit ARM, devices roughly up to 2015-2016.
- `x86` / `x86_64` — Intel/AMD tablets and Android emulators.
- `armeabi` (ARMv5/v6) is **not** built: the Android NDK dropped its toolchain entirely years ago (pre-r17), so no currently supported NDK version can produce it.

GitHub Releases (see `.github/workflows/release.yml`) publish all four split APKs plus `zapret-mobile-<version>-universal.apk`; the in-app updater (`UpdateManager`) picks the asset matching the device's own ABI automatically.

## Boundaries

- No root, Magisk, iptables, nftables, NFQUEUE, custom kernel, or hidden remote fallback.
- No TLS MITM and no custom CA installation.
- Local listeners must remain on `127.0.0.1`.

See `docs/ARCHITECTURE.md`, `docs/LIMITATIONS.md`, `docs/SECURITY.md`, and `docs/THIRD_PARTY_LICENSES.md`.

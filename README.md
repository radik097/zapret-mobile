# Zapret Mobile

Zapret Mobile is a rootless Android app for local traffic handling through Android `VpnService`. It does not use a remote VPN server, does not change the external IP address, and does not decrypt HTTPS traffic.

Current state: early Rust-first MVP skeleton. The APK can build, the app can request VPN permission, create a TUN interface, start a foreground service, and load a Rust native engine with a local SOCKS5 listener. The full TUN-to-SOCKS bridge and production DPI feature set are still in progress.

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

## Boundaries

- No root, Magisk, iptables, nftables, NFQUEUE, custom kernel, or hidden remote fallback.
- No TLS MITM and no custom CA installation.
- Local listeners must remain on `127.0.0.1`.

See `docs/ARCHITECTURE.md`, `docs/LIMITATIONS.md`, `docs/SECURITY.md`, and `docs/THIRD_PARTY_LICENSES.md`.


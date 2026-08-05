# Zapret Mobile Architecture

Zapret Mobile is a rootless Android `VpnService` application. The Android layer owns user consent, foreground-service lifetime, TUN creation, notifications, and future application routing. The Rust native engine owns local SOCKS5 handling, protocol inspection, and stream split behavior.

Current implementation status:

- Android shell: Java `MainActivity` and `ZapretVpnService`.
- Native engine: Rust `cdylib` loaded through JNI.
- Local listener: `127.0.0.1:1080`.
- TUN-to-SOCKS bridge: `hev-socks5-tunnel` native library built by `scripts/build-hev-socks5.ps1`, packaged for `arm64-v8a` and `x86_64`, and started from Java with a duplicated TUN fd.
- Implemented parsing: bounded HTTP `Host` split detection and bounded TLS record/ClientHello validation skeleton.
- Implemented transport: SOCKS5 CONNECT relay with initial HTTP/TLS split attempt plus minimal SOCKS5 UDP ASSOCIATE relay for DNS/UDP datagrams.
- Runtime automation: Maestro CLI flow launches the APK, grants VPN consent, validates foreground service/TUN/connectivity evidence, stops the VPN, and writes JUnit plus device artifacts under `build/test-artifacts/maestro-smoke`.
- Traffic proof: `scripts/traffic-proof.ps1` installs a separate `dev.zapret.testclient` APK, runs a host HTTP endpoint, activates the VPN, and verifies `test-client -> VpnService TUN -> hev-socks5-tunnel -> Rust SOCKS/DPI engine -> host server` with `result=200 body=zapret-proof`.
- DPI simulator proof: `scripts/dpi-proof.ps1` runs a local HTTP DPI simulator, drives a raw HTTP request with `Host: blocked.example` through the VPN, and verifies the Rust split behavior. The simulator report records two upstream chunks: the first contains only `Host: blocked`, the full request contains `blocked.example`, and the final decision is `allowed_split`.
- PCAP capture proof: `scripts/dpi-proof-pcap.ps1` restarts the AVD with the Android Emulator `-tcpdump` option, runs `scripts/dpi-proof.ps1`, stops the emulator to flush capture data, and writes `build/test-artifacts/dpi-proof-pcap/emulator-network.pcap`.
- Not implemented yet: app selection UI, persistent profiles, QUIC policy, production socket protection callback from Rust, HAR/mitmproxy capture, and physical-device verification.

Target chain:

```text
Android apps
  -> VpnService TUN
  -> hev-socks5-tunnel TUN-to-SOCKS bridge
  -> Rust local SOCKS5/DPI engine
  -> app-disallowed outbound sockets in current MVP
  -> physical network
```

The project does not use root, NFQUEUE, iptables, a remote VPN server, or HTTPS MITM.

The current loop-avoidance mechanism is `VpnService.Builder.addDisallowedApplication(getPackageName())`, so the app process that owns the Rust SOCKS listener bypasses its own VPN. A future production version should add explicit per-socket protection for outbound Rust sockets instead of relying only on package-level exclusion.

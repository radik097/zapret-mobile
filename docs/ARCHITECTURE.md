# Zapret Mobile Architecture

Zapret Mobile is a rootless Android `VpnService` application. The Android layer owns user consent, foreground-service lifetime, TUN creation, notifications, and application routing. The Rust native engine owns local SOCKS5 handling, protocol inspection, and stream split behavior.

Current implementation status:

- Android shell: Java `MainActivity` and `ZapretVpnService`.
- Native engine: Rust `cdylib` loaded through JNI.
- Local listener: `127.0.0.1:1080`.
- TUN-to-SOCKS bridge: `hev-socks5-tunnel` native library built by `scripts/build-hev-socks5.ps1`, packaged for `arm64-v8a` and `x86_64`, and started from Java with a duplicated TUN fd.
- Implemented parsing: bounded HTTP `Host` split detection and bounded TLS record/ClientHello validation skeleton.
- Implemented transport: SOCKS5 CONNECT relay with initial HTTP/TLS split attempt plus minimal SOCKS5 UDP ASSOCIATE relay for DNS/UDP datagrams.
- App routing: `AppRoutingSettings` persists either all-app routing or a selected launcher-app allow-list. `MainActivity` owns selection UI, while `ZapretVpnService` applies the saved packages through `addAllowedApplication` at the next start.
- QUIC policy: `EngineSettings` persists a `Block QUIC (UDP/443)` toggle, enabled by default. `ZapretVpnService` sends it through a separate JNI `configure` call, and the Rust SOCKS5 UDP relay drops datagrams whose destination port is 443 when the policy is active.
- Strategy profiles: `EngineSettings` persists Compatible, Balanced, or Aggressive. Compatible performs one midpoint split without delay; Balanced is the default midpoint split with 12 ms delay; Aggressive splits after the first Host/SNI character and waits 35 ms. The profile id is applied through JNI before native startup.
- Socket protection: JNI keeps a global reference to `ZapretVpnService`. Android-target TCP sockets are created with `socket2`, passed to `protectSocket(fd)` before connect, and only then connected. UDP upstream sockets are protected before their first send. A failed Android protection call aborts that relay operation.
- Runtime automation: Maestro CLI flow launches the APK, grants VPN consent, validates foreground service/TUN/connectivity evidence, stops the VPN, and writes JUnit plus device artifacts under `build/test-artifacts/maestro-smoke`.
- Traffic proof: `scripts/traffic-proof.ps1` installs a separate `dev.zapret.testclient` APK, runs a host HTTP endpoint, activates the VPN, and verifies `test-client -> VpnService TUN -> hev-socks5-tunnel -> Rust SOCKS/DPI engine -> host server` with `result=200 body=zapret-proof`. Passing `-SelectedAppsOnly` first selects that APK through the real UI, verifies persisted settings after an Activity restart, and runs the same proof under the allow-list policy.
- DPI simulator proof: `scripts/dpi-proof.ps1` runs a local HTTP DPI simulator, drives a raw HTTP request with `Host: blocked.example` through the VPN, and verifies the Rust split behavior. The simulator report records two upstream chunks: the first contains only `Host: blocked`, the full request contains `blocked.example`, and the final decision is `allowed_split`.
- PCAP capture proof: `scripts/dpi-proof-pcap.ps1` restarts the AVD with the Android Emulator `-tcpdump` option, runs `scripts/dpi-proof.ps1`, stops the emulator to flush capture data, and writes `build/test-artifacts/dpi-proof-pcap/emulator-network.pcap`.
- Physical-device proof: `scripts/physical-smoke.ps1` installs the current APK with data preservation, validates existing VPN consent, starts the service on a connected device, verifies foreground state, `tun0` at `10.71.0.1/24`, protected outbound socket evidence, and no app crash, then verifies complete stop cleanup. It passed on a Pixel 8a (`arm64-v8a`, Android 17).
- Not implemented yet: Automatic fallback and Custom profiles, multi-split/TLS record fragmentation, QUIC Initial parsing/manipulation, and HAR/mitmproxy capture.

Target chain:

```text
All apps or persisted selected apps
  -> VpnService TUN
  -> hev-socks5-tunnel TUN-to-SOCKS bridge
  -> Rust local SOCKS5/DPI engine
  -> VpnService.protect(fd) outbound sockets
  -> physical network
```

The project does not use root, NFQUEUE, iptables, a remote VPN server, or HTTPS MITM.

Rust TCP and UDP upstream sockets use explicit `VpnService.protect(fd)` before network I/O. In all-app mode, `addDisallowedApplication(getPackageName())` remains as a defense-in-depth fallback. In selected-app mode, only saved application packages are added through `addAllowedApplication`, so the engine process also remains outside its own VPN route.

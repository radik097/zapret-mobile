# Security

Zapret Mobile must stay local, rootless, and transparent about its limits.

Current rules:

- Local proxy listeners bind only to `127.0.0.1`.
- No telemetry, ads, remote management API, embedded secrets, or closed private APIs.
- No HTTPS MITM, no user CA installation, and no TLS decryption.
- Imported profiles must be treated as untrusted data.
- Native parsers must bound all lengths and reject truncated or malformed data.
- Release builds must not contain debug management interfaces.

Threat model:

- Malicious app on device: can attempt to connect to local ports; listeners must stay loopback-only and protocol parsing must be defensive.
- Malicious profile: must not be able to execute code, shell, Lua, or access paths outside the import target.
- Malformed packet: parsers must validate lengths and avoid panics/out-of-bounds reads.
- Local SOCKS abuse: bind to loopback only and add authentication or per-process controls if Android exposes the port to other apps on a target device.
- Log leakage: do not persist full user URLs, request bodies, TLS payloads, or DNS contents.
- Native hang: service stop must terminate listeners and close TUN resources.


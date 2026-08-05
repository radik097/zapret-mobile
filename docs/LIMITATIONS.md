# Limitations

- The app occupies Android's system VPN slot.
- It cannot run at the same time as a classic `VpnService` VPN such as WireGuard, WARP, or AdGuard VPN.
- It is not a complete clone of Zapret2 and does not implement NFQUEUE.
- Raw packet strategies are unavailable without root or kernel privileges.
- Arbitrary TCP SEQ/ACK manipulation, `badsum`, `md5sig`, and full `seqovl` are unavailable.
- Behavior depends on the network, carrier, and deployed DPI implementation.
- QUIC/UDP handling is not complete in the current code.
- The current build has no TUN-to-SOCKS bridge yet, so TUN traffic is not relayed through the Rust SOCKS engine.
- Always-on VPN can block network access if the service fails.
- Foreground-service behavior can vary on OEM firmware.


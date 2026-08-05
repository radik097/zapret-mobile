# Limitations

- The app occupies Android's system VPN slot.
- It cannot run at the same time as a classic `VpnService` VPN such as WireGuard, WARP, or AdGuard VPN.
- It is not a complete clone of Zapret2 and does not implement NFQUEUE.
- Raw packet strategies are unavailable without root or kernel privileges.
- Arbitrary TCP SEQ/ACK manipulation, `badsum`, `md5sig`, and full `seqovl` are unavailable.
- Behavior depends on the network, carrier, and deployed DPI implementation.
- QUIC support is limited to an optional UDP/443 drop policy that encourages TCP fallback; QUIC Initial parsing and packet manipulation are not implemented.
- App selection lists launcher-visible applications; background-only packages without a launcher activity are not exposed by the current UI.
- Routing changes apply when the VPN is started again, not while an existing TUN session is active.
- Profiles are currently limited to Compatible, Balanced, and Aggressive single-split variants. Automatic fallback, Custom parameters, multi-split, and TLS record fragmentation are not implemented.
- Always-on VPN can block network access if the service fails.
- Foreground-service behavior can vary on OEM firmware.

# Third-party Licenses

This file records components currently used in the local project.

| Component | License | Source | Use |
| --- | --- | --- | --- |
| Android SDK command-line tools, platform tools, build tools, NDK, CMake | Android SDK License / component notices | https://developer.android.com/studio | Local build toolchain only |
| Android Gradle Plugin | Apache-2.0 | https://developer.android.com/build/releases/gradle-plugin | Gradle Android build |
| Gradle | Apache-2.0 | https://gradle.org/releases/ | Build wrapper/distribution |
| Rust toolchain | MIT OR Apache-2.0 | https://www.rust-lang.org/ | Native engine build |
| cargo-ndk | MIT OR Apache-2.0 | https://github.com/bbqsrc/cargo-ndk | Rust Android build helper |
| jni 0.21.1 | MIT OR Apache-2.0 | https://github.com/jni-rs/jni-rs | Safe JavaVM attachment and VpnService global reference from Rust |
| socket2 0.5.10 | MIT OR Apache-2.0 | https://github.com/rust-lang/socket2-rs | Create Android TCP sockets before connect so their fd can be protected |
| Maestro CLI | Apache-2.0 | https://github.com/mobile-dev-inc/maestro | Local Android UI automation and JUnit smoke reports |
| hev-socks5-tunnel | MIT | https://github.com/heiher/hev-socks5-tunnel | Packaged Android TUN-to-SOCKS native bridge |
| hev-socks5-core | MIT | https://github.com/heiher/hev-socks5-core | Vendored submodule used by `hev-socks5-tunnel` |
| hev-task-system | MIT | https://github.com/heiher/hev-task-system | Vendored submodule used by `hev-socks5-tunnel` |
| yaml parser under `third-part/yaml` | MIT | `research/hev-socks5-tunnel/third-part/yaml` | Static dependency of `hev-socks5-tunnel` |
| lwIP | BSD-style 3-clause | https://savannah.nongnu.org/projects/lwip/ | Static dependency of `hev-socks5-tunnel` |

Other referenced research repositories from the project brief are not vendored or copied in the current code. Their licenses still need to be reviewed before any code or binary reuse.

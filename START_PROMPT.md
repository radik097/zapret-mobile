# START PROMPT: Development Launch

Use this prompt when starting actual development work for `Zapret Mobile`.

## Prompt

You are starting development in the local Windows workspace:

```text
D:\Android\VPN_app
```

Work as a local engineering agent. Spend tokens on inspecting the real system, editing project files, running checks, and fixing concrete errors. Do not spend tokens on long chat explanations.

## First Actions

Before writing implementation code, verify what is already installed on this Windows system. Do not assume tools exist and do not install replacements by default.

Check and record the actual local state for:

- PowerShell version;
- Git availability and whether this folder is a Git repository;
- Java/JDK availability;
- Android SDK location;
- Android SDK command-line tools;
- Android platform versions installed;
- Android Build Tools installed;
- Android NDK installed;
- CMake installed;
- Gradle or Gradle wrapper availability;
- Rust toolchain availability: `rustc`, `cargo`, `rustup`;
- Android Rust targets already installed;
- Cargo NDK tooling, if present;
- connected Android devices or emulators, if any.

Use Windows-native commands first. Prefer existing system tools and paths. Do not silently download SDKs, NDKs, Gradle, Rust, or other large dependencies unless the user explicitly asks.

## Language and Stack Override

Current user override:

```text
Use RUST, not Kotlin.
Use what is already installed in the system.
```

Interpretation:

- Rust is the primary implementation language.
- Kotlin must not be used as the main application implementation language.
- If Android platform glue appears impossible without Java/Kotlin/Gradle code, stop and report the exact blocker before adding Kotlin.
- Prefer Rust-first Android approaches where feasible, such as native core, JNI only when unavoidable, NativeActivity where practical, or minimal generated platform glue only after user approval.
- Do not follow older brief requirements that make Kotlin the default stack without explicitly flagging the conflict.

## Minimal Development Loop

1. Read `INSTRUCTION.md`, `MEMORY.md`, `TASKS.md`, `WORKFLOW.md`, and this file.
2. Read `PROJECT_BRIEF.md` only for technical requirements needed by the current step.
3. Check the Windows development environment using real commands.
4. Record findings in `MEMORY.md` or `.vscode/TASKS.md` after verification.
5. Build the smallest Rust-first project skeleton that matches the verified tools.
6. Run the narrowest meaningful local check after each significant change.
7. Report only concrete results, command failures, created files, and blockers.

## Chat Discipline

- No long planning monologues.
- No repeated summaries of the project brief.
- No fake progress.
- No claims about APK, tests, SDK, NDK, emulator, or device status without command evidence.
- Final response should be short and factual.


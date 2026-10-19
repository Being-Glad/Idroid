# Security Policy

## Reporting Security Vulnerabilities

We take the security of Idroid and our users seriously. If you discover a vulnerability or security issue within the Idroid codebase, native bridges, or protocol implementations, please report it responsibly.

**Please DO NOT open a public GitHub issue for sensitive security vulnerabilities.**

Instead, report it via one of the following methods:
1. **GitHub Security Advisory**: Open a private advisory under the repository's **Security > Advisories** tab.
2. **Security Email**: Send an email with reproducible details, proof-of-concept, and environment information to **security@idroid.dev** (or the project maintainers).

You will receive an initial response acknowledging your submission within **48 hours**. We will coordinate a remediation timeline and credit your disclosure in release notes once a fix is published.

---

## Supported Versions

Only the current major release branch receives active security updates and patches.

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

---

## Architectural Security Model & Guarantees

Idroid is designed around a **Zero-Cloud, Private-by-Default** philosophy. When contributing or reviewing code, ensure that all changes adhere to these core architectural security invariants:

### 1. Zero Cloud Interception (Local P2P Architecture)
- All streaming (H.264 camera ingest, scrcpy mirroring), telemetry, clipboard synchronization, and notification payloads flow **exclusively over direct peer-to-peer transports** (USB, Wi-Fi Direct, Local WLAN, or private WireGuard mesh).
- No telemetry, keystrokes, audio, or video streams are ever transmitted to or processed by third-party cloud infrastructure.

### 2. Native CoreGraphics Keystroke Injection (No Shell Injection)
- Native Mac text input is handled directly by `desktop/src-tauri/src/mac_window.c` and `desktop/src-tauri/src/commands/input.rs` using the native macOS CoreGraphics API (`CGEventKeyboardSetUnicodeString`).
- Shell scripts, `osascript`, and formatted string concatenations are strictly forbidden for user-supplied keyboard strings to eliminate command injection (`CWE-78`) vectors.

### 3. Local Optical QR Pairing & Cryptographic Nonce Handshake
- Wireless pairing between the Mac host and the Android companion app uses a dynamic JSON payload with a generated cryptographic session token and network discovery metadata.
- Companion WebSocket endpoints bind to loopback (`127.0.0.1`) and local interfaces, verifying session pairing tokens prior to executing remote actions.

### 4. ADB & scrcpy Process Isolation
- The ADB binary is resolved from hermetic system paths (`$ANDROID_HOME`, Homebrew, or `/opt/homebrew/bin/adb`).
- All process spawning in Rust (`std::process::Command`) uses separated positional argument arrays rather than executing arbitrary shell subshells (`sh -c`).

### 5. Media & Photo Sandbox Access
- Album photos and thumbnails fetched from the Android device are cached strictly in temporary sandbox directories (`/tmp/idroid_thumbs/`) and purged automatically.
- No remote device files are saved to the user's Mac filesystem without explicit user initiation (`handleSaveFilesToMac` / native save dialogs).

---

## Bug Bounty & Credits

Idroid is community-driven open source. We deeply appreciate researchers and developers who take the time to audit our code and responsibly disclose findings. All verified security researchers will be permanently acknowledged in our release notes and contributor hall of fame.

<div align="center">

# ⚡ Idroid

### The Open-Source Flagship Ecosystem Bridge between Android and macOS

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-macOS%20%7C%20Android-lightgrey.svg)](README.md)
[![Backend](https://img.shields.io/badge/backend-Rust%20%2B%20Tauri%20v2-orange.svg)](desktop/src-tauri)
[![Frontend](https://img.shields.io/badge/frontend-TypeScript%20%2B%20Vite-blue.svg)](desktop/src)
[![Companion](https://img.shields.io/badge/companion-Kotlin%20%2B%20Camera2-green.svg)](android)
[![Build Status](https://img.shields.io/badge/tests-23%20passed-brightgreen.svg)](desktop/tests)

**Idroid** brings the seamless ecosystem experience of Apple's Continuity and iPhone Mirroring to Android and macOS. Engineered for sub-millisecond responsiveness, zero cloud reliance, and deep hardware integration.

[Key Features](#-key-features) • [Architecture](#-architecture) • [Getting Started](#-getting-started) • [Pairing Guide](#-pairing-guide) • [Security](#-security--privacy) • [Contributing](#-contributing)

</div>

---

## 🚀 Key Features

### 📱 60–120 FPS Low-Latency Screen Mirroring
- **Hardware-Accelerated Scrcpy Pipeline**: High-fidelity H.264/H.265 video rendering with customizable bitrate, max resolution (1080p, 2K, 4K), and smooth 60–120 FPS refresh rates.
- **Virtual Second Display**: Expand your macOS workspace by turning your Android tablet or phone into a dedicated second monitor.
- **Screen-Off Mode**: Mirror and interact with phone apps with the physical phone display turned off to save battery and prevent burn-in.
- **Audio Forwarding**: Stream phone media, notifications, and game audio directly to your Mac speakers or AirPods.

### 📷 Ultra-Low Latency Wireless Camera Stream
- Turn your phone's multi-lens camera (ultra-wide, telephoto, studio front) into a broadcast-grade Mac webcam.
- Native H.264 ingest stream over Wi-Fi Direct or USB with sub-35ms pipeline latency.

### ⌨️ Native CoreGraphics Remote Input Synthesizer
- **Type Mac from Phone**: Use your phone keyboard or dictation to type directly into any focused macOS application (Safari, Xcode, VS Code, Notes).
- **Type Phone from Mac**: Control Android text fields seamlessly using your Mac Magic Keyboard and Trackpad.
- **Zero Shell Injection**: Keystrokes are synthesized at the OS level via macOS CoreGraphics `CGEventKeyboardSetUnicodeString` in native C (`mac_window.c`), avoiding slow or vulnerable AppleScript execution.

### 📋 Bi-Directional Universal Clipboard Sync
- Instant, seamless copy-paste between macOS and Android with automatic loopback deduplication.
- Clipboard History viewer with search, pinned items, and one-click recall.

### 🔔 Live Notifications & Quick SMS Reply
- Real-time notification mirroring from Android to macOS Notification Center.
- Dismiss notifications remotely and send instant SMS/RCS replies directly from your Mac desktop.

### 📞 Native Phone Call Integration
- Make and receive phone calls from macOS.
- Integrated dialer keypad, favorite contacts, live floating call banner, and speakerphone routing.

### 🗂️ Lightning File Manager & Photo Album Gallery
- Browse Android storage (`/sdcard/`) with directory navigation, fast search, and one-click downloads to macOS Downloads.
- Visual photo gallery with smart album filtering and background asynchronous thumbnail caching.

### 🩺 Idroid Doctor & Diagnostics Hub
- Real-time telemetry monitoring: battery health, voltage, temperature, Wi-Fi link speed, and transport classification.
- Self-healing diagnostics for ADB, wireless TCP/IP pairing, and port collision recovery.

---

## 🏛️ Architecture

Idroid utilizes a high-performance hybrid architecture designed for raw throughput and security:

```
┌─────────────────────────────────────────────────────────────┐
│                    macOS Host (Idroid)                      │
│                                                             │
│  ┌─────────────────────────┐     ┌──────────────────────┐  │
│  │   TypeScript Frontend   │◄────┤  Reactive Data Store │  │
│  │ (Titanium Design Theme) │     │ (desktop/src/state/) │  │
│  └────────────▲────────────┘     └──────────────────────┘  │
│               │ Tauri IPC Bridge                           │
│  ┌────────────▼──────────────────────────────────────────┐  │
│  │                   Rust Core (Tauri v2)                 │  │
│  │  ┌────────────┐ ┌─────────────┐ ┌───────────────────┐  │  │
│  │  │  input.rs  │ │   adb.rs    │ │  companion.rs     │  │  │
│  │  │ (C-Bridge) │ │  (scrcpy)   │ │ (WebSocket:53102) │  │  │
│  │  └────────────┘ └─────────────┘ └───────────────────┘  │  │
│  │  ┌────────────┐ ┌─────────────┐ ┌───────────────────┐  │  │
│  │  │  files.rs  │ │  calls.rs   │ │ notifications.rs  │  │  │
│  │  └────────────┘ └─────────────┘ └───────────────────┘  │  │
│  └─────────────┬─────────────────────────────────────────┘  │
│                │ Native C CoreGraphics FFI                  │
│  ┌─────────────▼────────────┐                               │
│  │ mac_window.c (Keystrokes)│                               │
│  └──────────────────────────┘                               │
└────────────────┬────────────────────────────────────────────┘
                 │
       Peer-to-Peer Transports:
       • USB Direct (Zero Latency)
       • Personal Hotspot (<2ms latency, recommended)
       • Local WLAN (TCP/IP:5555 + WebSocket:53100)
                 │
┌────────────────▼────────────────────────────────────────────┐
│              Android Companion App (Kotlin)                 │
│                                                             │
│  ┌────────────────────────┐     ┌────────────────────────┐  │
│  │   Camera2 Subsystem    │     │ Notification Listener  │  │
│  │ (H.264 Camera Stream)  │     │   (Real-time Ingest)   │  │
│  └────────────────────────┘     └────────────────────────┘  │
│  ┌────────────────────────┐     ┌────────────────────────┐  │
│  │ WebSocket Remote Server│     │  Telephony & Calls     │  │
│  │   (JSON Remote RPC)    │     │   (In-call Audio)      │  │
│  └────────────────────────┘     └────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚡️ Quick Install

### Option 1: 1-Line Installer (Recommended)
Automatically downloads the latest Universal 2 DMG, installs Idroid into `/Applications`, and configures macOS Gatekeeper permissions:

```bash
curl -fsSL https://raw.githubusercontent.com/Being-Glad/Idroid/main/install.sh | bash
```

### Option 2: Download DMG from Releases
1. Download **`Idroid_1.0.0_universal.dmg`** from the [Latest Release](https://github.com/Being-Glad/Idroid/releases/latest).
2. Open the disk image and drag **`Idroid.app`** into your **Applications** folder.
3. *First Launch Note*: Because Idroid is community open-source, modern macOS Gatekeeper may prompt `"Idroid.app" Not Opened`. Run this one command to approve it immediately:
   ```bash
   xattr -cr /Applications/Idroid.app
   ```
   *(Alternatively, go to **System Settings** → **Privacy & Security** and click **"Open Anyway"**).*

---

## 🛠️ Build From Source

### Prerequisites
- **macOS**: Sonoma 14.0+ or Sequoia 15.0+ (Apple Silicon or Intel)
- **Homebrew**: `brew install scrcpy android-platform-tools`
- **Node.js**: 20.0+ (`node -v`)
- **Rust**: 1.80+ (`rustup default stable`)

### Desktop Development

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Being-Glad/Idroid.git
   cd Idroid/desktop
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Run in development mode**:
   ```bash
   npm run tauri dev
   ```

4. **Build standalone macOS `.app` bundle**:
   ```bash
   npm run tauri build
   ```

### Android Companion Installation

- The pre-built companion APK is available in the root of this repository: `Idroid-Companion.apk`.
- Install via ADB directly to your connected device:
  ```bash
  adb install -r Idroid-Companion.apk
  ```
- Or open Idroid desktop, navigate to **Wireless Studio**, and scan the on-screen APK download QR code.

---

## 📶 Pairing Guide

Idroid supports three connection modes:

### 1. Personal Hotspot (Recommended — Best Experience)
- Turn on your Android **Personal Hotspot** and connect your Mac to it.
- **Advantage**: Bypasses corporate and public router isolation, eliminates Wi-Fi jitter, and delivers **<2ms latency** without using any cellular data.

### 2. Local Wi-Fi (QR Optical Pairing)
- Connect both your Mac and Android phone to the same local Wi-Fi router.
- Open **Wireless Studio** on Idroid desktop and tap **Scan to Pair** in the Android Companion app.
- Optical pairing performs an instantaneous handshake over your local subnet.

### 3. USB Cable (Plug & Play)
- Plug your phone into your Mac via USB-C.
- Enable **USB Debugging** in Android Developer Options.
- Idroid detects the device instantly with zero manual configuration.

---

## 🔒 Security & Privacy

Idroid is built strictly on a **Zero-Cloud, Private-by-Default** architecture:

- **No Remote Servers**: 100% of telemetry, video, audio, notifications, and clipboard data stays inside your private local network or USB link.
- **No Keystroke Logging**: Keyboard input is synthesized directly into active application viewports via macOS CoreGraphics C-FFI without intermediate text loggers.
- **Cryptographic Token Handshake**: Wireless companion sessions verify dynamic authentication tokens to prevent unauthorized local network discovery.

For details or vulnerability reporting, see [SECURITY.md](SECURITY.md).

---

## 🧪 Test Suites & Verification

Idroid maintains test coverage across all layers of the stack:

```bash
# Run Desktop Frontend Tests (Icons, QR, Battery, Network, Settings)
cd desktop && npm test

# Run Native Rust Backend Tests (ADB Parsing, Storage, Clipboard, Notifications)
cd desktop/src-tauri && cargo test

# Verify Rust Compilation & Type Safety
cargo check

# Run Android Companion Unit Tests
cd ../../android && ./gradlew testDebugUnitTest
```

---

## 🤝 Contributing

We welcome contributions from developers worldwide! Whether you are improving low-latency video streaming, polishing macOS glassmorphism UI, or adding new Android integrations, please review our [CONTRIBUTING.md](CONTRIBUTING.md) guide before submitting pull requests.

---

## 📄 License

Idroid is licensed under the [Apache License, Version 2.0](LICENSE).
You may freely use, modify, and distribute this software under the terms of the Apache 2.0 license.

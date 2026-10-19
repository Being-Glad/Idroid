# Contributing to Idroid

Thank you for your interest in contributing to Idroid! Idroid is a high-performance, open-source cross-platform studio connecting Android smartphones directly with macOS laptops and desktops with sub-millisecond responsiveness.

As an open-source project, we hold ourselves to rigorous engineering standards: clean architecture, zero bloat, strict security invariants, and modular domain code.

---

## Code of Conduct

Please treat all members of the community with dignity, respect, and professionalism. Collaborative technical discussions should focus on evidence, measurable performance, and code quality.

---

## Architecture Overview

Idroid is split into two primary workspaces:

```
Idroid/
├── desktop/                       # macOS Desktop Client (Tauri v2 + TypeScript)
│   ├── src/                       # Frontend SPA (Vanilla TypeScript, Titanium Theme)
│   │   ├── types/                 # Domain DTOs and Data Models
│   │   ├── services/              # Type-safe IPC and invoke adapters
│   │   ├── state/                 # Reactive store, subscribers, localStorage
│   │   ├── components/            # Views and Modal Dialogs
│   │   ├── icons.ts               # Hand-tuned crisp SVG icons
│   │   └── motionGraphics.ts      # Tactile physics, ripples, and HUD animations
│   ├── src-tauri/                 # Native macOS / Rust Backend
│   │   ├── src/commands/          # Domain-driven Rust submodules
│   │   │   ├── types.rs           # Shared Rust DTOs & synchronized statics
│   │   │   ├── input.rs           # CoreGraphics keyboard & mouse synthesis
│   │   │   ├── system.rs          # Mac battery, volume, storage, Bluetooth
│   │   │   ├── apps.rs            # macOS / Android app scanner and launcher
│   │   │   ├── adb.rs             # ADB transport, TCP/IP, pairing QR, scrcpy
│   │   │   ├── files.rs           # Phone file manager and photo gallery
│   │   │   ├── calls.rs           # Phone call routing, speakerphone, dialer
│   │   │   ├── notifications.rs   # Notification streaming & SMS dispatch
│   │   │   ├── clipboard.rs       # Bidirectional clipboard sync & deduplication
│   │   │   └── companion.rs       # WebSocket server & remote window controls
│   │   ├── src/mac_window.c       # Native CoreGraphics C bridge
│   │   └── src/camera_server.rs   # High-throughput H.264 camera ingest server
│   └── tests/                     # Node.js automated unit and integration tests
└── android/                       # Native Android Companion App (Kotlin)
    └── app/src/main/
        ├── java/com/idroid/app/   # Camera2, WebSocket, NotificationListener, Telephony
        └── res/                   # Layouts, vector drawables, themes
```

---

## Prerequisites

To build and test the full Idroid stack locally on macOS, ensure you have:

1. **macOS Sonoma (14+) or Sequoia (15+)** (Apple Silicon or Intel).
2. **Rust & Cargo** (`rustup default stable`).
3. **Node.js 18+** & **npm** (`node -v`, `npm -v`).
4. **Android SDK & Platform Tools** (with `adb` in your `$PATH`).
5. **scrcpy** (Installed via `brew install scrcpy`).
6. **JDK 17+** (for Android Gradle builds).

---

## Getting Started

### 1. Clone Repository & Install Dependencies
```bash
git clone https://github.com/your-org/idroid.git
cd Idroid

# Install desktop frontend dependencies
cd desktop
npm install
```

### 2. Run the Desktop Application in Development
```bash
# In desktop/ directory:
npm run tauri dev
```
This boots Vite dev server on `http://localhost:1420` and spawns the native macOS Tauri window with live-reload enabled.

### 3. Build & Run Tests

Before submitting any code changes, all test suites must pass without errors:

```bash
# 1. Desktop Frontend Unit Tests (13 tests)
cd desktop
npm test

# 2. Frontend Production Build Check
npm run build

# 3. Native Rust Backend Tests (10 tests)
cd desktop/src-tauri
cargo test
cargo check

# 4. Android Unit Tests
cd ../../android
./gradlew testDebugUnitTest
```

---

## Coding Standards & Architectural Guidelines

### Rust Native Backend
- **Domain Modularization**: Place all new commands in their respective domain file under `desktop/src-tauri/src/commands/`. Do not bloat `commands.rs` or `lib.rs`.
- **Error Handling**: Commands exposed to Tauri IPC must return `Result<T, String>`. Never panic (`unwrap()`) in command handlers; handle errors gracefully with informative error messages.
- **Safety**: Keep `unsafe` blocks minimal and contained within the `mac_window.c` C-FFI boundary. Use `unsafe extern "C"` as required by Rust 2024.
- **No Command Injection**: Never execute user-supplied strings via `sh -c` or `osascript`. Always use `std::process::Command::new(binary).args([...])` or native CoreGraphics events.

### TypeScript Desktop Frontend
- **Type Safety**: Add all new interface types to `desktop/src/types/index.ts`. Avoid `any` types.
- **State Management**: Manage shared state through `desktop/src/state/store.ts`. Components should subscribe to store mutations rather than maintaining fragmented global variables.
- **UI Aesthetics**: Follow our Solar Titanium and Obsidian dark glassmorphism design tokens in `desktop/src/styles/app.css`. Maintain micro-animations, tactile ripples, and 60-120fps fluid transitions.
- **Self-Contained Icons**: All icons must be clean, resolution-independent SVG strings added to `desktop/src/icons.ts`. Do not introduce bloated external icon webfonts.

### Android Companion
- **Performance First**: Camera2 and screen capture pipelines must prioritize low latency and zero heap allocations during live streaming loops.
- **Permissions**: Request runtime permissions only when the corresponding feature (e.g. Camera, Notifications, Storage) is activated by the user.

---

## Pull Request Guidelines

1. **Feature Branch**: Create a descriptive branch from `main`:
   ```bash
   git checkout -b feat/ultra-low-latency-audio
   ```
2. **Commit Messages**: Use conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `perf:`).
3. **Verify Everything**: Run `npm test`, `cargo test`, and `npm run build` locally.
4. **Open a PR**: Fill out the PR template with:
   - Summary of changes and motivation.
   - Proof of verification (terminal test outputs or screenshots).
   - Any hardware or device requirements tested (e.g., Galaxy S23 Ultra, Pixel 8).

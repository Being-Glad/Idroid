# Idroid Ecosystem — Cross-Platform Design System (`DESIGN.md`)

> **Design Language**: CleanShot X Desktop Studio  
> **Aesthetic**: White product canvas with dark ink typography, subtle hardware-lift shadows, layered window depth, and electric capture blue accents.  
> **Philosophy**: Simple and human. Every control and label is practical and clear without jargon.

---

## 1. Core Design Philosophy & SlopMonster Rules

In accordance with our **Anti-Slop (SlopMonster)** guidelines:

1. **Clear, Friendly Naming Over Jargon**:
   - ❌ *Never use*: "OmniSync Protocol", "NeuralDataBroker", "AdvancedAdbMirrorPipeline", "ContinuousBiDirectionalPacketStreamer".
   - ✅ *Always use*: **Screen Mirror**, **Quick Share**, **Shared Clipboard**, **Phone Camera**, **Phone Calls**, **Connected Devices**.
2. **Instant Clarity**:
   - Every button and switch performs the exact action described on its label.
   - Status indicators speak human English: *"Connected to Mac via Wi-Fi"* instead of *"Socket state: BOUND_TCP_53102"*.
3. **Restraint & Native Polish**:
   - Do not overwhelm the user with dials or technical settings.
   - Make the hardware and product content the focus, framed by quiet white space and crisp typography.

---

## 2. Design Tokens

### 2.1 Color Palette

| Token | Hex Value | Role & Usage |
| :--- | :--- | :--- |
| `--color-paper-white` | `#ffffff` | Primary background, cards, popover surfaces, white secondary buttons. |
| `--color-absolute-ink` | `#000000` | Display titles, high-emphasis headlines, primary metrics. |
| `--color-graphite` | `#161618` | Navigation text, secondary headers, dark borders, icon strokes. |
| `--color-soft-charcoal` | `#6a6a6b` | Body copy, secondary descriptions, metadata, device details. |
| `--color-studio-black` | `#0f0f12` | Dark badges, camera viewfinder frames, menu bar active states. |
| `--color-electric-blue` | `#0d44e8` | Primary action buttons, active toggles, connected badges, focus rings. |
| `--gradient-electric-sweep` | `linear-gradient(90deg, #161618 0%, #161618 60%, #174ef2 100%)` | Accent wordmark and feature banner gradient. |

#### Color Rules:
- **Do**: Keep most of the interface in clean white and graphite tones. Use Electric Blue `#0d44e8` as a concentrated highlight.
- **Don't**: Never use Electric Blue as a page or window background. Never use harsh saturated reds or greens for status chips; use soft tinted pills.

---

### 2.2 Typography (Google Sans Flex / Inter / San Francisco)

- **macOS Primary**: `SF Pro` / `Google Sans Flex` / `Inter`
- **Android Primary**: `Inter` / `Roboto`

| Role | Weight | Size | Line Height | Tracking | Token |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Display / Hero** | 600 | 40px – 60px | 1.15 | -1.0px | `--text-display` |
| **Section Heading** | 600 | 24px – 28px | 1.25 | -0.5px | `--text-section-heading` |
| **Card / Feature Title**| 600 | 18px – 20px | 1.30 | 0px | `--text-feature-heading` |
| **Body (Default)** | 450 | 15px – 16px | 1.50 | 0px | `--text-body` |
| **Body Small / Details**| 450 | 13px – 14px | 1.40 | 0px | `--text-body-small` |
| **Button / Control** | 550 | 14px – 15px | 1.00 | 0px | `--text-control` |
| **Caption / Badge** | 550 | 12px – 13px | 1.20 | +0.2px | `--text-badge` |

---

### 2.3 Spacing, Radii & Depth

- **Base Unit**: `4px`
- **Spacing Scale**: `4px`, `8px`, `12px`, `16px`, `20px`, `24px`, `32px`, `40px`

#### Corner Radii:
- **Cards & Media Panels**: `20px`
- **Action Buttons & Inputs**: `14px`
- **Context Badges & Chips**: `14px`
- **Circular Icon Controls**: `40px` (or `9999px` for pill icons)

#### Shadows (Hardware Lift):
- **Card Lift (`--shadow-card`)**:  
  `rgba(22, 22, 24, 0.02) 0px 7px 20px -8px, rgba(22, 22, 24, 0.05) 0px 22px 30px -8px, rgba(22, 22, 24, 0.06) 0px 0px 0px 1px`
- **Primary Button Inset Glow (`--shadow-button-primary`)**:  
  `rgba(4, 24, 85, 0.28) 0px 2px 6px -1px, rgba(66, 174, 247, 0.48) 0px 6px 14px 0px inset`
- **Subtle Surface Outline (`--shadow-subtle`)**:  
  `rgba(22, 22, 24, 0.12) 0px 2px 6px -1px, rgba(22, 22, 24, 0.08) 0px 0px 0px 1px`

---

## 3. macOS Design Blueprint (SwiftUI)

### 3.1 Menu Bar Popover (`360px × 480px`)
- **Header**:
  - Connected Phone status (e.g. *"Galaxy S24 Ultra"* • *Connected over Wi-Fi*).
  - Battery percentage pill and network strength glyph.
- **Quick Actions Row** (40px circular controls):
  - **Screen Mirror**: Opens the floating phone screen window.
  - **Camera**: Activates phone as a Mac CoreMediaIO webcam.
  - **Files**: Opens the drag-and-drop file drop zone.
  - **Audio & Calls**: Call and media route control.
- **Activity & Clipboard Feed**:
  - Last copied item with a single-click "Copy to Mac" button.
  - Recent notifications card with inline quick-reply support.

### 3.2 Floating Screen Mirror Window
- **Container**: `20px` rounded window with a 1px soft graphite border and blurred translucent backdrop (`.ultraThinMaterial`).
- **Aspect Ratio**: Locked to phone screen ratio (e.g., 19.5:9) with smooth hardware-accelerated resizing.
- **Interactions**:
  - Mouse hover displays navigation bar overlay (Home, Back, Recent Apps, Rotate).
  - Drag-and-drop files directly onto the mirror window to transfer to phone.
  - Keyboard inputs route directly into active Android text fields.

### 3.3 File Drop HUD (AirDrop Experience)
- Native drag-over highlight: Dragging a file from Finder onto the Menu Bar icon illuminates the target with an Electric Blue border and tactile haptic feedback.

---

## 4. Android Design Blueprint (Jetpack Compose / Material 3)

### 4.1 Companion Dashboard
- **Top Bar**: Minimalist white canvas with *"Idroid"* wordmark and connection state chip.
- **Device Connection Card**:
  - Displays your paired MacBook with device icon, current IP/Wi-Fi name, and active connection toggle.
- **Feature Cards Grid** (`20px` radius, Paper White with subtle outline):
  - **Shared Clipboard**: Toggle switch plus visual status of last synced text.
  - **Webcam Mode**: Camera preview, lens selector, and orientation lock.
  - **Screen Sharing**: One-tap toggle for low-latency casting.
  - **Notification Sync**: App list to choose which notifications route to Mac.

### 4.2 System-Level Android Integrations (Zero Friction)
- **Quick Settings Tile**:
  - *"Idroid Connect"*: Tap in notification shade to toggle connection without opening the app.
  - *"Send Clipboard"*: One-tap quick copy to Mac.
- **Text Selection Context Menu**:
  - Highlighting text anywhere in Android shows `"Copy to Mac"` directly next to Cut and Copy.
- **Share Sheet**:
  - Appears in standard Android Share dialog: tap to beam photos, links, or files directly to your Mac.

---

## 5. Windows & Linux Blueprint (Unified Expansion)

To ensure the design scales across desktop operating systems:
- **Windows (WinUI 3 / Mica)**:
  - System Tray flyout matching the 360px popover layout.
  - Acrylic and Mica materials applying our CleanShot X tokens.
- **Linux (GTK4 / Libadwaita or Qt)**:
  - AppIndicator icon with quick-action tray menu and floating phone window.

---

## 6. Component Specification & State Behaviors

| Component | Default State | Hover / Focus State | Active / Pressed State |
| :--- | :--- | :--- | :--- |
| **Primary Action Button** | Electric Blue `#0d44e8`, White Text, `14px` radius | Electric Blue + 5% brightness, inset glow | Electric Blue - 5% brightness, scale `0.98` |
| **Secondary Button** | Paper White `#ffffff`, Graphite Text, 1px border | Light gray `#f6f6f8` surface | Scaled `0.98` |
| **Status Chip** | Studio Black `#0f0f12`, White 12px text, `14px` radius | No change | Dismissible if active alert |
| **Feature Card** | Paper White `#ffffff`, `--shadow-card`, `20px` radius | 1px border shifts to Graphite `#161618` | Lifted by 2px |

---

## 7. Next Steps & Implementation Alignment

With our skills installed and `DESIGN.md` codified:
1. **Frontend (Mac)**: Implement SwiftUI views strictly using the typography and spacing tokens from `DESIGN.md`.
2. **Frontend (Android)**: Implement Jetpack Compose theme matching these exact color tokens and corner radii.
3. **Backend & Protocol**: Keep socket payloads clean and decoupled from UI representations.

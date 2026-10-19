// Idroid Flagship Cross-Platform Studio Engine (Solar Titanium & Native Window System)
window.addEventListener('error', (e) => {
  console.error('[Idroid UI Error]', e.message, e.filename, e.lineno, e.error);
});
window.addEventListener('unhandledrejection', (e) => {
  console.error('[Idroid Unhandled Rejection]', e.reason);
});

import QRCode from 'qrcode';
import { Icons } from './icons';
import {
  attachGlobalClickRipples,
  showAppLaunchHUD,
  attachTactileCardPhysics,
} from './motionGraphics';
import { getInvoke } from './services/tauri';

import type {
  DeviceState,
  AppInfo,
  FileItem,
  PhotoItem,
  FavouriteContact,
  CallRecord,
  NotificationItem,
  ClipboardSnippet,
} from './types';

// Active State - Dashboard is primary home overview
let currentSection = 'dashboard';
let isDndActive = false;
let isCameraActive = false;
let cameraStreamStats: {
  active: boolean;
  fps: number;
  bitrate_mbps: number;
  latency_ms: number;
  jitter_ms: number;
  total_frames: number;
  resolution: string;
  codec: string;
} | null = null;
let isAudioActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_mirror_audio');
    return saved !== null ? saved === 'true' : false;
  } catch (_) {
    return false;
  }
})();
let isVirtualDisplayActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_mirror_vdisplay');
    return saved !== null ? saved === 'true' : false;
  } catch (_) {
    return false;
  }
})();
let isScreenOffModeActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_mirror_screenoff');
    return saved !== null ? saved === 'true' : true;
  } catch (_) {
    return true;
  }
})();
let isAlwaysOnTopActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_mirror_always_on_top');
    return saved !== null ? saved === 'true' : false;
  } catch (_) {
    return false;
  }
})();
let isDockNavActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_dock_nav_bar');
    return saved !== null ? saved === 'true' : true;
  } catch (_) {
    return true;
  }
})();
let isPhoneFrameActive = (() => {
  try {
    const saved = localStorage.getItem('idroid_phone_frame');
    return saved !== null ? saved === 'true' : true;
  } catch (_) {
    return true;
  }
})();
const DEFAULT_NAV_BUTTONS = ['back', 'home', 'recents', 'notifications', 'volume_down', 'volume_up', 'screenshot', 'power'];
let enabledNavButtons: string[] = (() => {
  try {
    const saved = localStorage.getItem('idroid_nav_buttons');
    if (saved) return JSON.parse(saved);
  } catch (_) {}
  return [...DEFAULT_NAV_BUTTONS];
})();

interface NavButtonDef {
  key: string;
  label: string;
  icon: (size: number, color?: string) => string;
  hint: string;
}

const ALL_NAV_BUTTONS: NavButtonDef[] = [
  { key: 'back', label: 'Back', icon: (s, c) => Icons.ArrowLeft(s, c), hint: 'Back (ESC / ◀)' },
  { key: 'home', label: 'Home', icon: (s, c) => Icons.Dashboard(s, c), hint: 'Home Screen (⌂)' },
  { key: 'recents', label: 'Recents', icon: (s, c) => Icons.Layers(s, c), hint: 'Recent Apps Switcher (▢)' },
  { key: 'notifications', label: 'Shade', icon: (s, c) => Icons.Bell(s, c), hint: 'Pull Down Notifications Shade' },
  { key: 'volume_down', label: 'Vol -', icon: (s, c) => Icons.Volume1(s, c), hint: 'Volume Down' },
  { key: 'volume_up', label: 'Vol +', icon: (s, c) => Icons.Volume2(s, c), hint: 'Volume Up' },
  { key: 'screenshot', label: 'Capture', icon: (s, c) => Icons.Camera(s, c), hint: 'Take Phone Screenshot to Mac' },
  { key: 'power', label: 'Lock', icon: (s, c) => Icons.Power(s, c || 'var(--accent-solar)'), hint: 'Lock / Sleep Phone Screen' },
];
let mirrorStatusMsg = '';
let appSearchQuery = '';

export function cleanIconUrl(src?: string | null): string {
  if (!src) return '';
  let s = src.trim();
  while (s.startsWith('data:image/png;base64,data:image/')) {
    s = s.substring('data:image/png;base64,'.length);
  }
  while (s.startsWith('data:image/jpeg;base64,data:image/')) {
    s = s.substring('data:image/jpeg;base64,'.length);
  }
  if (s.startsWith('data:image/')) return s;
  return `data:image/png;base64,${s}`;
}

const CACHED_APPS_KEY = 'idroid_cached_phone_apps';
function loadCachedPhoneApps(): AppInfo[] {
  try {
    const raw = localStorage.getItem(CACHED_APPS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (_) {}
  return [];
}
function saveCachedPhoneApps(apps: AppInfo[]) {
  if (!apps || apps.length === 0) return;
  try {
    localStorage.setItem(CACHED_APPS_KEY, JSON.stringify(apps));
  } catch (_) {}
}

interface QuickPhoneApp {
  name: string;
  package: string;
  icon: string;
}
let quickPhoneApps: QuickPhoneApp[] = ((): QuickPhoneApp[] => {
  const cached = loadCachedPhoneApps();
  if (cached.length > 0) {
    return cached.slice(0, 30).map((a) => ({
      name: a.name,
      package: a.package,
      icon: cleanIconUrl(a.icon_base64),
    }));
  }
  return [];
})();
let isQuickAppsModalOpen = false;
let quickAppsSearchQuery = '';
let isFavAppsMultiWindowActive: boolean = ((): boolean => {
  try {
    const saved = localStorage.getItem('idroid_fav_apps_multiwindow');
    if (saved !== null) return saved === 'true';
  } catch (_) {}
  return false;
})();

let isQuickControlsModalOpen = false;
let quickControlsSearchQuery = '';
let quickControlsViewMode: 'tiles' | 'cards' = ((): 'tiles' | 'cards' => {
  try {
    const saved = localStorage.getItem('idroid_quick_controls_view');
    if (saved === 'tiles' || saved === 'cards') return saved;
  } catch (_) {}
  return 'tiles';
})();

let isSidebarCollapsed = ((): boolean => {
  try {
    return localStorage.getItem('idroid_sidebar_collapsed') === 'true';
  } catch (_) {
    return false;
  }
})();

function toggleSidebarCollapse() {
  isSidebarCollapsed = !isSidebarCollapsed;
  try {
    localStorage.setItem('idroid_sidebar_collapsed', String(isSidebarCollapsed));
  } catch (_) {}
  render();
}

// Global shortcut: ⌘\ or Ctrl+\ to toggle sidebar collapse with fluid transition
window.addEventListener('keydown', (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === '\\') {
    e.preventDefault();
    toggleSidebarCollapse();
  }
});

// Active Call & Telephony State
let dialerInputNumber = '';
let isCallActive = false;
let activeCallTarget = '';
let activeCallTargetName = '';
let callDurationSec = 0;
let callDurationTimer: any = null;
let isCallMuted = false;
let isCallRecording = false;
let isAddPersonModalOpen = false;
let conferenceParticipants: string[] = [];

// Incoming Call Alert State
let incomingCall: { number: string; name: string } | null = null;

// Telemetry & State - Defaults to Disconnected until real hardware is detected
const device: DeviceState = {
  name: 'No Device Connected',
  brand: 'Android',
  battery: 0,
  isCharging: false,
  connectionType: 'Disconnected',
  status: 'Disconnected',
  voltageMv: 0,
  temperatureC: 0.0,
  storageUsedGb: '0',
  storageTotalGb: '0',
  storagePercent: 0,
  bluetoothStatus: 'Off',
  bluetoothConnected: false,
};

interface HostTelemetry {
  name: string;
  os: string;
  battery: number;
  is_charging: boolean;
  storage_used_gb: string;
  storage_total_gb: string;
  storage_percent: number;
  storage_available_gb: string;
  bluetooth_status?: string;
  bluetooth_connected?: boolean;
}

let hostTelemetry: HostTelemetry = {
  name: 'MacBook Pro',
  os: 'Apple Silicon macOS',
  battery: 100,
  is_charging: true,
  storage_used_gb: '283G',
  storage_total_gb: '465G',
  storage_percent: 65,
  storage_available_gb: '155G',
  bluetooth_status: 'Bluetooth On',
  bluetooth_connected: false,
};

let deviceWallpapers = {
  mac: '/mac_wallpaper.png',
  phone: '/phone_wallpaper.svg',
};

function renderPhoneBatteryWidget(battery: number, isCharging: boolean): string {
  const pct = Math.max(0, Math.min(100, Math.round(battery)));
  const isLow = pct <= 20;

  // Dynamic colors: Green when charging, Warning Red when <= 20%, Crisp Silver when on battery
  const fillColor = isCharging
    ? '#10b981'
    : isLow
    ? '#f87171'
    : '#e2e8f0';

  const badgeBg = isCharging
    ? 'rgba(16, 185, 129, 0.12)'
    : isLow
    ? 'rgba(248, 113, 113, 0.12)'
    : 'rgba(255, 255, 255, 0.05)';

  const badgeBorder = isCharging
    ? 'rgba(16, 185, 129, 0.35)'
    : isLow
    ? 'rgba(248, 113, 113, 0.35)'
    : 'rgba(255, 255, 255, 0.14)';

  const statusTitle = isCharging ? `Fast Charging (${pct}%)` : `On Battery (${pct}%)`;

  return `
    <div class="device-battery-indicator phone-battery" style="display: inline-flex; align-items: center; gap: 6px; padding: 4px 9px; border-radius: 9999px; background: ${badgeBg}; border: 1px solid ${badgeBorder}; white-space: nowrap; flex-shrink: 0; transition: all 0.3s ease;" title="${statusTitle}">
      <div style="position: relative; width: 22px; height: 11px; border: 1.5px solid ${fillColor}; border-radius: 3px; padding: 1px; display: flex; align-items: center; box-sizing: border-box;">
        <div style="width: ${pct}%; height: 100%; background: ${fillColor}; border-radius: 1px; transition: width 0.4s ease;"></div>
        <div style="position: absolute; right: -3px; top: 2px; width: 1.8px; height: 4px; background: ${fillColor}; border-radius: 0 1px 1px 0;"></div>
        ${isCharging ? `<div style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 8px; font-weight: 900; line-height: 1; filter: drop-shadow(0 0 2px rgba(0,0,0,0.8));">⚡</div>` : ''}
      </div>
      <span style="font-size: 11px; font-weight: 600; color: ${fillColor}; letter-spacing: -0.2px;">${isCharging ? `⚡ ${pct}%` : `${pct}%`}</span>
    </div>
  `;
}

function renderMacBatteryWidget(battery: number, isCharging: boolean): string {
  const pct = Math.max(0, Math.min(100, Math.round(battery)));
  const isLow = pct <= 20;

  // macOS native behavior: Green when charging / connected to power, Red when <= 20%, Apple neutral white/silver when on battery
  const fillColor = isCharging
    ? '#34c759'
    : isLow
    ? '#ff453a'
    : '#f1f5f9';

  const badgeBg = isCharging
    ? 'rgba(52, 199, 89, 0.12)'
    : isLow
    ? 'rgba(255, 69, 58, 0.12)'
    : 'rgba(255, 255, 255, 0.05)';

  const badgeBorder = isCharging
    ? 'rgba(52, 199, 89, 0.35)'
    : isLow
    ? 'rgba(255, 69, 58, 0.35)'
    : 'rgba(255, 255, 255, 0.14)';

  const statusTitle = isCharging ? `Power Adapter Connected (${pct}%)` : `Battery Power (${pct}%)`;

  return `
    <div class="device-battery-indicator mac-battery" style="display: inline-flex; align-items: center; gap: 6px; padding: 4px 9px; border-radius: 9999px; background: ${badgeBg}; border: 1px solid ${badgeBorder}; white-space: nowrap; flex-shrink: 0; transition: all 0.3s ease;" title="${statusTitle}">
      <div style="position: relative; width: 24px; height: 12px; border: 1.5px solid ${fillColor}; border-radius: 3.5px; padding: 1px; display: flex; align-items: center; box-sizing: border-box;">
        <div style="width: ${pct}%; height: 100%; background: ${fillColor}; border-radius: 1.5px; transition: width 0.4s ease;"></div>
        <div style="position: absolute; right: -3px; top: 2.5px; width: 1.8px; height: 4px; background: ${fillColor}; border-radius: 0 1px 1px 0;"></div>
        ${isCharging ? `<div style="position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 8px; font-weight: 900; line-height: 1; filter: drop-shadow(0 0 2px rgba(0,0,0,0.9));">⚡</div>` : ''}
      </div>
      <span style="font-size: 11px; font-weight: 600; color: ${fillColor}; letter-spacing: -0.2px;">${isCharging ? `⚡ ${pct}%` : `${pct}%`}</span>
    </div>
  `;
}

function safeWallpaperDataUrl(url?: string): string {
  if (!url) return '';
  if (url.startsWith('data:image/svg+xml;utf8,')) {
    try {
      const raw = url.replace('data:image/svg+xml;utf8,', '');
      return 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(raw)));
    } catch (_) {
      return '';
    }
  }
  return url.replace(/['"\r\n]/g, '');
}

function renderPhoneDeviceFrame(wallpaperUrl?: string): string {
  const wp = safeWallpaperDataUrl(wallpaperUrl || deviceWallpapers.phone);
  return `
    <div class="device-phone-hardware-frame" style="position: relative; width: 34px; height: 58px; border-radius: 8px; border: 1.8px solid #3b4252; background: #0b0f19; box-shadow: 0 4px 14px rgba(0,0,0,0.5), inset 0 0 0 0.8px rgba(255,255,255,0.08); overflow: hidden; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: space-between;" title="Samsung Galaxy S23 Ultra">
      <!-- Front Camera Punch Hole -->
      <div style="position: absolute; top: 3.5px; left: 50%; transform: translateX(-50%); width: 3.5px; height: 3.5px; border-radius: 50%; background: #000; box-shadow: 0 0 0 0.6px rgba(255,255,255,0.15); z-index: 5;"></div>
      <!-- Authentic S23 Ultra Screen Wallpaper -->
      ${wp ? `
        <img src="${wp}" alt="S23 Ultra Wallpaper" style="position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover;" />
        <div style="position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0.2) 0%, transparent 40%, rgba(0,0,0,0.4) 100%); pointer-events: none;"></div>
      ` : `
        <div style="position: absolute; inset: 0; background: linear-gradient(145deg, #1e293b, #0f172a); display: flex; align-items: center; justify-content: center; color: var(--accent-solar);">
          ${Icons.Phone(14, 'currentColor')}
        </div>
      `}
      <!-- Bottom Home Indicator -->
      <div style="position: absolute; bottom: 2px; left: 50%; transform: translateX(-50%); width: 14px; height: 1.5px; border-radius: 2px; background: rgba(255,255,255,0.7); z-index: 5;"></div>
    </div>
  `;
}

function renderMacBookDeviceFrame(wallpaperUrl?: string): string {
  const wp = safeWallpaperDataUrl(wallpaperUrl || deviceWallpapers.mac);
  return `
    <div class="device-macbook-hardware-frame" style="display: flex; flex-direction: column; align-items: center; flex-shrink: 0; filter: drop-shadow(0 4px 12px rgba(0,0,0,0.45));" title="MacBook Pro">
      <!-- MacBook Display Lid (16:10 aspect ratio) -->
      <div style="position: relative; width: 56px; height: 36px; border-radius: 5px 5px 1px 1px; border: 1.5px solid #475569; background: #0f172a; overflow: hidden; box-sizing: border-box;">
        <!-- Top Display Notch -->
        <div style="position: absolute; top: 0; left: 50%; transform: translateX(-50%); width: 8.5px; height: 2px; background: #020617; border-radius: 0 0 2px 2px; z-index: 5;"></div>
        <!-- Authentic macOS Desktop Wallpaper -->
        ${wp ? `
          <img src="${wp}" alt="macOS Wallpaper" style="position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover;" />
          <div style="position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,0.15) 0%, transparent 60%, rgba(0,0,0,0.25) 100%); pointer-events: none;"></div>
        ` : `
          <div style="position: absolute; inset: 0; background: linear-gradient(145deg, #1e293b, #0369a1); display: flex; align-items: center; justify-content: center; color: var(--cyan-neon);">
            ${Icons.Computer(14, 'currentColor')}
          </div>
        `}
      </div>
      <!-- MacBook Unibody Keyboard Deck Base -->
      <div style="position: relative; width: 66px; height: 4px; background: linear-gradient(180deg, #64748b 0%, #334155 100%); border-radius: 0 0 3px 3px; margin-top: -1px; box-shadow: 0 2px 4px rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: flex-start;">
        <!-- Display Opening Thumb Scoop -->
        <div style="width: 11px; height: 1.5px; background: #1e293b; border-radius: 0 0 1px 1px;"></div>
      </div>
    </div>
  `;
}


let latestClipboard = '';
let phoneToMacCopied = false;
let pushToPhoneInput = '';

let phoneApps: AppInfo[] = loadCachedPhoneApps();
let phoneFiles: FileItem[] = [];
let phonePhotos: PhotoItem[] = [];
let callHistory: CallRecord[] = ((): CallRecord[] => {
  try {
    const saved = localStorage.getItem('idroid_call_history');
    if (saved) return JSON.parse(saved);
  } catch (_) {}
  return [];
})();
let notificationList: NotificationItem[] = [];
let clipboardHistory: ClipboardSnippet[] = [];

let isLoadingApps = false;
let isLoadingFiles = false;
let isLoadingPhotos = false;

// Files Navigation & Multi-select State
let currentRemoteDir = '/sdcard/Download';
let selectedFiles: Set<string> = new Set();
let isSavingFiles = false;
let fileOperationMsg = '';

// Photos Manager & Multi-select State
let currentPhotoAlbum = 'all';
let selectedPhotos: Set<string> = new Set();
let isSavingPhotos = false;
let photoOperationMsg = '';
let photoThumbnails: Map<string, string> = new Map();
let activeLightboxPhoto: PhotoItem | null = null;

// Calls, Audio Route & Favourite Contacts State
let callAudioRoute: 'mac' | 'phone' = ((): 'mac' | 'phone' => {
  try {
    const saved = localStorage.getItem('idroid_call_audio_route');
    if (saved === 'phone' || saved === 'mac') return saved;
  } catch (_) {}
  return 'mac';
})();
let favouriteContacts: FavouriteContact[] = ((): FavouriteContact[] => {
  try {
    const saved = localStorage.getItem('idroid_favourite_contacts');
    if (saved) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed)) {
        return parsed.filter((c: any) => !c.number?.includes('98765 4321'));
      }
    }
  } catch (_) {}
  return [];
})();
let isAddFavouriteModalOpen = false;
let newFavName = '';
let newFavNumber = '';
let callsTabFilter: 'all' | 'missed' | 'outgoing' | 'incoming' = 'all';

// Seen notifications for desktop system notifications
let seenNotificationIds: Set<string> = new Set();
let hasSeededNotifications = false;

// Consumer Optical QR Pairing & Device Telemetry
interface PairingPayload {
  name: string;
  ip: string;
  port: number;
  token: string;
  protocol: string;
}

let pairingPayload: PairingPayload = {
  name: 'MacBook Pro',
  ip: '192.168.1.10',
  port: 53100,
  token: 'ambient-token',
  protocol: 'idroid-v1',
};
let pairingQrSvg = '';
let downloadQrSvg = '';
let activePairingTab: 'consumer_qr' | 'developer_mode' = 'consumer_qr';
let isPairingQrLoading = false;
let isCompanionInstalling = false;
let companionInstallMsg = '';

// Wireless & Remote Cross-Network Transport State
let isWirelessModalOpen = false;
let wirelessModalTab: 'optical' | 'handover' | 'pairing' | 'mesh' = 'optical';
let wirelessStatusMsg = '';
let isWirelessConnecting = false;
let wirelessFormIp = '';
let wirelessFormPairPort = '';
let wirelessFormPairCode = '';
let wirelessFormMeshIp = '';
let wirelessFormMeshPort = '5555';

// Idroid Doctor & System Health State
let isDoctorModalOpen = false;
let doctorSearchQuery = '';
let doctorExpandedFaq: string | null = 'hotspot_vs_wifi';

async function generatePairingQRs() {
  if (isPairingQrLoading) return;
  isPairingQrLoading = true;
  try {
    const invoke = await getInvoke();
    if (invoke) {
      const livePayload = (await invoke('get_pairing_qr_payload')) as PairingPayload;
      if (livePayload) {
        pairingPayload = livePayload;
      }
    }

    const pairUri = `idroid://pair?ip=${pairingPayload.ip}&port=${pairingPayload.port}&token=${pairingPayload.token}&name=${encodeURIComponent(pairingPayload.name)}`;
    pairingQrSvg = await QRCode.toString(pairUri, {
      type: 'svg',
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' },
      width: 196,
    });

    downloadQrSvg = await QRCode.toString(
      'https://github.com/idroid/idroid/releases/latest/download/idroid-companion.apk',
      {
        type: 'svg',
        margin: 1,
        color: { dark: '#000000', light: '#ffffff' },
        width: 76,
      }
    );
  } catch (err) {
    console.warn('Failed to generate pairing QR:', err);
  } finally {
    isPairingQrLoading = false;
    render();
  }
}

// Kick off immediately on script load
generatePairingQRs();


// MARK: - Tauri Core IPC Bridge (imported from services/tauri)

const DEFAULT_PINNED_CANDIDATES = [
  'com.android.chrome',
  'com.google.android.youtube',
  'com.whatsapp',
  'com.instagram.android',
  'com.openai.chatgpt',
  'com.google.android.gm',
  'com.google.android.apps.maps',
  'com.sec.android.app.camera',
  'com.android.settings'
];

function getPinnedAppPackages(): string[] {
  try {
    const saved = localStorage.getItem('idroid_pinned_quick_apps');
    if (saved !== null) {
      const parsed = JSON.parse(saved);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (_) {}
  const source = quickPhoneApps.length > 0 ? quickPhoneApps : phoneApps;
  const validPkgs = (source || []).map((a) => a.package);
  const defaults = DEFAULT_PINNED_CANDIDATES.filter((p) => validPkgs.includes(p));
  if (defaults.length >= 3) return defaults;
  return validPkgs.slice(0, 7);
}

function savePinnedAppPackages(pkgs: string[]) {
  try {
    localStorage.setItem('idroid_pinned_quick_apps', JSON.stringify(pkgs));
  } catch (_) {}
}

function renderQuickAppsHtml(): string {
  let sourceApps = quickPhoneApps;
  if (!sourceApps || sourceApps.length === 0) {
    if (phoneApps && phoneApps.length > 0) {
      sourceApps = phoneApps.slice(0, 30).map((a) => ({
        name: a.name,
        package: a.package,
        icon: cleanIconUrl(a.icon_base64),
      }));
    }
  }

  if (!sourceApps || sourceApps.length === 0) {
    return `
      <div style="font-size: 12.5px; color: var(--text-muted); padding: 8px 12px; display: flex; align-items: center; gap: 8px;">
        <span class="pulse-dot"></span>
        <span>Syncing installed apps from phone...</span>
      </div>
    `;
  }

  const validApps = sourceApps.filter((app) => app && app.name && app.package);
  if (validApps.length === 0) {
    return `
      <div style="font-size: 12.5px; color: var(--text-muted); padding: 8px 12px; display: flex; align-items: center; gap: 8px;">
        <span class="pulse-dot"></span>
        <span>Syncing installed apps from phone...</span>
      </div>
    `;
  }

  const pinnedPackages = getPinnedAppPackages();
  const pinnedApps: QuickPhoneApp[] = [];
  for (const pkg of pinnedPackages) {
    const found = validApps.find((a) => a.package === pkg) 
      || (phoneApps || []).find((a) => a.package === pkg);
    if (found) {
      pinnedApps.push({
        name: found.name,
        package: found.package,
        icon: cleanIconUrl((found as any).icon || (found as any).icon_base64),
      });
    }
  }

  const displayApps = pinnedApps.length > 0 ? pinnedApps : validApps.slice(0, 5);

  const appsHtml = displayApps.map((app) => {
    const cleanSrc = cleanIconUrl(app.icon);
    const iconHtml = cleanSrc
      ? `<img src="${cleanSrc}" style="width: 20px; height: 20px; border-radius: 6px; object-fit: contain;" />`
      : `<div style="width: 20px; height: 20px; border-radius: 6px; background: rgba(255,110,50,0.15); display: flex; align-items: center; justify-content: center;">${Icons.Phone(14, 'var(--accent-solar)')}</div>`;

    return `
      <button class="btn-luxury btn-launch-app" data-package="${app.package}" data-appname="${app.name}" data-iconsrc="${cleanSrc}" style="padding: 9px 14px; font-size: 12.5px; border-radius: 12px; display: inline-flex; align-items: center; gap: 8px; flex-shrink: 0;">
        ${iconHtml}
        <span style="font-weight: 500;">${app.name}</span>
      </button>
    `;
  }).join('');

  const addAppBtnHtml = `
    <button class="btn-luxury btn-add-inline-app" id="btn-strip-add-app" style="padding: 9px 13px; font-size: 12px; border-radius: 12px; display: inline-flex; align-items: center; gap: 6px; flex-shrink: 0; border: 1.5px dashed rgba(255, 110, 50, 0.45); background: rgba(255, 110, 50, 0.05); color: var(--accent-solar); cursor: pointer; transition: all 0.2s ease;" title="Add or Customize Favourite Apps">
      ${Icons.Plus(14, 'var(--accent-solar)')}
      <span style="font-weight: 600;">Add App</span>
    </button>
  `;

  return appsHtml + addAppBtnHtml;
}

function renderQuickAppsModalGrid(): string {
  const source = quickPhoneApps.length > 0 ? quickPhoneApps : phoneApps.map(a => ({
    name: a.name,
    package: a.package,
    icon: cleanIconUrl(a.icon_base64)
  }));
  const validApps = source.filter((app) => app && app.name && app.package);
  const pinnedPackages = getPinnedAppPackages();

  const query = quickAppsSearchQuery.trim().toLowerCase();
  const filteredApps = query
    ? validApps.filter((a) => a.name.toLowerCase().includes(query) || a.package.toLowerCase().includes(query))
    : validApps;

  if (filteredApps.length === 0) {
    return `
      <div style="grid-column: 1 / -1; text-align: center; padding: 40px 20px; color: var(--text-muted); font-size: 13px;">
        No installed apps found matching "${quickAppsSearchQuery}".
      </div>
    `;
  }

  return filteredApps.map((app) => {
    const isPinned = pinnedPackages.includes(app.package);
    const cleanSrc = cleanIconUrl(app.icon);
    const iconHtml = cleanSrc
      ? `<img src="${cleanSrc}" style="width: 28px; height: 28px; border-radius: 7px; object-fit: contain; flex-shrink: 0;" />`
      : `<div style="width: 28px; height: 28px; border-radius: 7px; background: rgba(255,110,50,0.15); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">${Icons.Phone(16, 'var(--accent-solar)')}</div>`;

    return `
      <div class="custom-app-item ${isPinned ? 'pinned' : ''}" data-pkg="${app.package}" style="display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-radius: 12px; background: ${isPinned ? 'rgba(255, 110, 50, 0.08)' : 'rgba(255, 255, 255, 0.03)'}; border: 1px solid ${isPinned ? 'rgba(255, 110, 50, 0.35)' : 'var(--border-subtle)'}; cursor: pointer; transition: all 0.15s ease;">
        <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
          ${iconHtml}
          <div style="min-width: 0; flex: 1;">
            <div style="font-size: 13px; font-weight: 600; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="${app.name}">${app.name}</div>
          </div>
        </div>
        <div style="flex-shrink: 0; margin-left: 8px;">
          ${
            isPinned
              ? `<span style="display: inline-flex; align-items: center; gap: 4px; font-size: 11px; font-weight: 600; color: var(--accent-solar); background: rgba(255, 110, 50, 0.15); padding: 3px 8px; border-radius: 6px;">✓ Pinned</span>`
              : `<span style="display: inline-flex; align-items: center; font-size: 11px; color: var(--text-muted); background: rgba(255, 255, 255, 0.04); padding: 3px 8px; border-radius: 6px;">+ Pin</span>`
          }
        </div>
      </div>
    `;
  }).join('');
}

function renderQuickAppsModal(): string {
  if (!isQuickAppsModalOpen) return '';

  const pinnedPackages = getPinnedAppPackages();

  return `
    <div class="modal-backdrop" id="quick-apps-modal-backdrop" style="position: fixed; inset: 0; background: rgba(0, 0, 0, 0.75); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px;">
      <div class="modal-window glass-card" style="width: 100%; max-width: 640px; max-height: 82vh; display: flex; flex-direction: column; border: 1px solid var(--border-medium); border-radius: 20px; background: #121316; box-shadow: 0 24px 48px rgba(0,0,0,0.6); overflow: hidden;">
        
        <!-- Modal Header -->
        <div style="padding: 18px 22px; border-bottom: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <div style="width: 36px; height: 36px; border-radius: 10px; background: rgba(255, 110, 50, 0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center;">
              ${Icons.Sliders(18, 'currentColor')}
            </div>
            <div>
              <h2 style="font-size: 15px; font-weight: 700; color: #fff; margin: 0;">Customize Favourite Apps</h2>
              <p style="font-size: 11.5px; color: var(--text-secondary); margin: 2px 0 0 0;">
                Pick which apps appear in your Favourite Apps strip (<span id="quick-apps-pinned-count">${pinnedPackages.length}</span> pinned)
              </p>
            </div>
          </div>
          <button class="btn-icon" id="btn-close-quick-apps-modal" style="width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--border-subtle); background: rgba(255,255,255,0.04); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 13px;">✕</button>
        </div>

        <!-- Search & Control Toolbar -->
        <div style="padding: 12px 22px; border-bottom: 1px solid var(--border-subtle); display: flex; gap: 12px; align-items: center; background: rgba(255, 255, 255, 0.01);">
          <div style="flex: 1; position: relative;">
            <input 
              type="text" 
              id="input-search-quick-apps" 
              class="input-luxury" 
              placeholder="Search phone apps..." 
              value="${quickAppsSearchQuery}"
              style="width: 100%; padding: 8px 12px; font-size: 12.5px; border-radius: 10px;"
            />
          </div>
          <button class="btn-luxury" id="btn-reset-quick-apps-default" style="font-size: 11.5px; padding: 7px 12px; border-radius: 8px; flex-shrink: 0; cursor: pointer;">
            Reset Default
          </button>
        </div>

        <!-- Multi-Window Concurrent Mode Option -->
        <div style="margin: 12px 22px 4px 22px; padding: 11px 14px; border-radius: 12px; background: rgba(255, 110, 50, 0.08); border: 1px solid rgba(255, 110, 50, 0.25); display: flex; align-items: center; justify-content: space-between; gap: 14px;">
          <div style="display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1;">
            <div style="width: 32px; height: 32px; border-radius: 8px; background: rgba(255, 110, 50, 0.16); color: var(--accent-solar); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
              ${Icons.Layers(16, 'currentColor')}
            </div>
            <div>
              <div style="font-size: 12.5px; font-weight: 600; color: #fff;">Open Each App in Separate Window</div>
              <div style="font-size: 11px; color: var(--text-secondary); margin-top: 2px; line-height: 1.35;">
                Launch favourite apps concurrently side-by-side in their own portrait Mac windows
              </div>
            </div>
          </div>
          <label class="switch" style="flex-shrink: 0;" title="Toggle multi-window concurrent app displays">
            <input type="checkbox" id="toggle-fav-apps-multiwindow" ${isFavAppsMultiWindowActive ? 'checked' : ''} />
            <span class="slider"></span>
          </label>
        </div>

        <!-- App List Container (Scrollable) -->
        <div id="quick-apps-modal-list" style="flex: 1; overflow-y: auto; padding: 16px 22px; display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 10px; align-content: start; max-height: calc(82vh - 160px);">
          ${renderQuickAppsModalGrid()}
        </div>

        <!-- Modal Footer -->
        <div style="padding: 12px 22px; border-top: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: space-between; background: rgba(255, 255, 255, 0.01);">
          <span style="font-size: 11.5px; color: var(--text-muted);">
            Click any app to pin/unpin from Favourite Apps
          </span>
          <button class="btn-luxury" id="btn-save-quick-apps" style="background: linear-gradient(135deg, var(--accent-solar), var(--accent-gold)); color: #fff; font-weight: 600; border: none; padding: 7px 18px; border-radius: 8px; cursor: pointer;">
            Done
          </button>
        </div>

      </div>
    </div>
  `;
}

function attachQuickAppModalItemListeners() {
  document.querySelectorAll('#quick-apps-modal-list .custom-app-item').forEach((item) => {
    item.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const pkg = el.getAttribute('data-pkg');
      if (!pkg) return;
      let pinned = getPinnedAppPackages();
      if (pinned.includes(pkg)) {
        pinned = pinned.filter((p) => p !== pkg);
      } else {
        pinned.push(pkg);
      }
      savePinnedAppPackages(pinned);

      // Update modal list without losing search state
      const listContainer = document.getElementById('quick-apps-modal-list');
      if (listContainer) {
        listContainer.innerHTML = renderQuickAppsModalGrid();
        attachQuickAppModalItemListeners();
      }
      // Update count inside modal
      const countEl = document.getElementById('quick-apps-pinned-count');
      if (countEl) countEl.textContent = String(pinned.length);

      // Update quick apps container in main UI if present
      const strip = document.getElementById('quick-phone-apps-container');
      if (strip) {
        strip.innerHTML = renderQuickAppsHtml();
        attachQuickAppListeners();
      }
      // Update header button label
      const headerBtn = document.getElementById('btn-header-customize-apps');
      if (headerBtn) {
        headerBtn.innerHTML = `${Icons.Sliders(11, 'var(--accent-solar)')} <span>Customize</span>`;
      }
    });
  });
}

function attachQuickAppListeners() {
  document.querySelectorAll('#quick-phone-apps-container .btn-launch-app').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const pkg = el.getAttribute('data-package');
      const appName = el.getAttribute('data-appname') || 'Application';
      const iconSrc = el.getAttribute('data-iconsrc') || undefined;
      if (pkg) handleLaunchApp(pkg, appName, iconSrc);
    });
  });

  const btnStripAdd = document.getElementById('btn-strip-add-app');
  if (btnStripAdd) {
    btnStripAdd.addEventListener('click', () => {
      isQuickAppsModalOpen = true;
      render();
    });
  }

  const btnHeaderCustomize = document.getElementById('btn-header-customize-apps');
  if (btnHeaderCustomize) {
    btnHeaderCustomize.addEventListener('click', () => {
      isQuickAppsModalOpen = true;
      render();
    });
  }
}

// MARK: - Quick Controls System (Interactive Tiles & Customizer)
interface QuickControlDef {
  id: string;
  name: string;
  desc: string;
  badge: string;
  iconFn: (size: number, color: string) => string;
  accentColor: string;
  category: 'system' | 'media' | 'connectivity';
  actionName: string;
}

const AVAILABLE_QUICK_CONTROLS: QuickControlDef[] = [
  {
    id: 'mirror',
    name: 'Screen Mirror',
    desc: 'Stream full Android display to your screen with keyboard and mouse passthrough.',
    badge: '60 FPS Ultra',
    iconFn: (s, c) => Icons.Mirror(s, c),
    accentColor: 'var(--accent-solar)',
    category: 'connectivity',
    actionName: 'Start Stream',
  },
  {
    id: 'camera',
    name: 'Continuity Camera',
    desc: 'Launch high-resolution phone camera stream directly into your Mac workspace.',
    badge: 'Hardware',
    iconFn: (s, c) => Icons.Camera(s, c),
    accentColor: 'var(--cyan-neon)',
    category: 'media',
    actionName: 'Launch Camera',
  },
  {
    id: 'play_pause',
    name: 'Play / Pause Media',
    desc: 'Instantly toggle music, video, or podcast playback on active phone media players.',
    badge: 'Media Audio',
    iconFn: (s, c) => Icons.MediaPlayPause(s, c),
    accentColor: '#38bdf8',
    category: 'media',
    actionName: 'Play / Pause',
  },
  {
    id: 'next_track',
    name: 'Skip Track',
    desc: 'Skip to the next song or chapter in Spotify, YouTube, or podcast players.',
    badge: 'Media Audio',
    iconFn: (s, c) => Icons.SkipForward(s, c),
    accentColor: '#818cf8',
    category: 'media',
    actionName: 'Next Track',
  },
  {
    id: 'volume_up',
    name: 'Volume Up',
    desc: 'Increase phone speaker and media volume by one step increment.',
    badge: 'Audio Level',
    iconFn: (s, c) => Icons.VolumeUp(s, c),
    accentColor: '#10b981',
    category: 'media',
    actionName: 'Raise Volume',
  },
  {
    id: 'volume_mute',
    name: 'Mute Audio',
    desc: 'Instantly silence all media audio and alert sounds on connected phone.',
    badge: 'Silent Mode',
    iconFn: (s, c) => Icons.VolumeMute(s, c),
    accentColor: '#f43f5e',
    category: 'media',
    actionName: 'Mute Phone',
  },
  {
    id: 'screenshot',
    name: 'Take Screenshot',
    desc: 'Instantly capture phone display screenshot to clipboard and Mac downloads.',
    badge: 'Capture',
    iconFn: (s, c) => Icons.Camera(s, c),
    accentColor: '#fbbf24',
    category: 'media',
    actionName: 'Capture Screen',
  },
  {
    id: 'notifications',
    name: 'Notification Shade',
    desc: 'Pull down Android notification center and alert panel on phone screen.',
    badge: 'Notifications',
    iconFn: (s, c) => Icons.Bell(s, c),
    accentColor: '#38bdf8',
    category: 'system',
    actionName: 'Pull Shade',
  },
  {
    id: 'quick_settings',
    name: 'Quick Settings Tray',
    desc: 'Expand full Android Quick Settings tiles (Wi-Fi, Bluetooth, Flashlight).',
    badge: 'System Tiles',
    iconFn: (s, c) => Icons.Sliders(s, c),
    accentColor: '#3b82f6',
    category: 'system',
    actionName: 'Expand Tiles',
  },
  {
    id: 'dnd',
    name: 'Do Not Disturb',
    desc: 'Silence incoming notification alerts and phone vibrations during focus sessions.',
    badge: 'Focus Mode',
    iconFn: (s, c) => Icons.Moon(s, c),
    accentColor: 'var(--purple-neon)',
    category: 'system',
    actionName: 'Toggle DND',
  },
  {
    id: 'home',
    name: 'Home Screen',
    desc: 'Minimize foreground apps and immediately navigate phone to home screen.',
    badge: 'Navigation',
    iconFn: (s, c) => Icons.Home(s, c),
    accentColor: '#f59e0b',
    category: 'system',
    actionName: 'Go Home',
  },
  {
    id: 'back',
    name: 'Back Button',
    desc: 'Simulate hardware back key to dismiss open sheets or return to previous page.',
    badge: 'Navigation',
    iconFn: (s, c) => Icons.ArrowLeft(s, c),
    accentColor: '#ec4899',
    category: 'system',
    actionName: 'Press Back',
  },
  {
    id: 'recents',
    name: 'App Switcher',
    desc: 'Open Android multitasking task switcher to view or switch background apps.',
    badge: 'Multitask',
    iconFn: (s, c) => Icons.Layers(s, c),
    accentColor: '#06b6d4',
    category: 'system',
    actionName: 'Open Recents',
  },
  {
    id: 'rotate',
    name: 'Toggle Auto-Rotate',
    desc: 'Cycle screen orientation between portrait and landscape for apps and media.',
    badge: 'Orientation',
    iconFn: (s, c) => Icons.Refresh(s, c),
    accentColor: '#a78bfa',
    category: 'system',
    actionName: 'Rotate Screen',
  },
  {
    id: 'settings',
    name: 'Device Settings',
    desc: 'Open Android System Settings app to configure device parameters.',
    badge: 'Android OS',
    iconFn: (s, c) => Icons.Settings(s, c),
    accentColor: '#a855f7',
    category: 'system',
    actionName: 'Open Settings',
  },
  {
    id: 'lock',
    name: 'Phone Screen Power',
    desc: 'Toggle phone display power to save battery while mirror stream is active.',
    badge: 'Display Power',
    iconFn: (s, c) => Icons.Power(s, c),
    accentColor: '#f87171',
    category: 'system',
    actionName: 'Toggle Display',
  },
  {
    id: 'sleep',
    name: 'Lock & Sleep',
    desc: 'Immediately turn off screen and put phone into secure locked sleep mode.',
    badge: 'Power Save',
    iconFn: (s, c) => Icons.Power(s, c),
    accentColor: '#e11d48',
    category: 'system',
    actionName: 'Lock Phone',
  },
  {
    id: 'ring',
    name: 'Wake & Find Phone',
    desc: 'Wake device display and trigger screen backlight to locate misplaced phone.',
    badge: 'Find Device',
    iconFn: (s, c) => Icons.Phone(s, c),
    accentColor: '#f97316',
    category: 'system',
    actionName: 'Wake Screen',
  },
  {
    id: 'clipboard',
    name: 'Shared Clipboard',
    desc: 'Inspect bidirectional clipboard sync and send text directly between devices.',
    badge: 'Live Sync',
    iconFn: (s, c) => Icons.Clipboard(s, c),
    accentColor: '#34d399',
    category: 'connectivity',
    actionName: 'Open Clipboard',
  },
  {
    id: 'mac_bluetooth',
    name: 'Mac Bluetooth',
    desc: 'Inspect connected Bluetooth accessories, AirPods, and peripherals on this Mac.',
    badge: 'macOS BT',
    iconFn: (s, c) => Icons.Bluetooth(s, c),
    accentColor: '#38bdf8',
    category: 'system',
    actionName: 'Mac Settings',
  },
  {
    id: 'phone_bluetooth',
    name: 'Phone Bluetooth',
    desc: 'View connected Bluetooth buds, smartwatch, or open Android Bluetooth settings.',
    badge: 'Android BT',
    iconFn: (s, c) => Icons.BluetoothConnected(s, c),
    accentColor: '#34d399',
    category: 'connectivity',
    actionName: 'Phone Bluetooth',
  },
];

const DEFAULT_PINNED_QUICK_CONTROLS = ['mirror', 'camera', 'play_pause', 'dnd'];

function getPinnedQuickControls(): string[] {
  try {
    const raw = localStorage.getItem('idroid_pinned_quick_controls');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch (_) {}
  return [...DEFAULT_PINNED_QUICK_CONTROLS];
}

function savePinnedQuickControls(ids: string[]) {
  try {
    localStorage.setItem('idroid_pinned_quick_controls', JSON.stringify(ids));
  } catch (_) {}
}

async function executeQuickControl(id: string) {
  const invoke = await getInvoke();
  switch (id) {
    case 'mirror':
      handleStartMirror();
      break;
    case 'camera':
      handleStartMirror('com.sec.android.app.camera');
      break;
    case 'play_pause':
      if (invoke) {
        await invoke('send_phone_key', { key: 'media_play_pause' });
      }
      break;
    case 'next_track':
      if (invoke) {
        await invoke('send_phone_key', { key: 'media_next' });
      }
      break;
    case 'volume_up':
      if (invoke) {
        await invoke('send_phone_key', { key: 'volume_up' });
      }
      break;
    case 'volume_mute':
      if (invoke) {
        await invoke('send_phone_key', { key: 'volume_mute' });
      }
      break;
    case 'dnd':
      isDndActive = !isDndActive;
      render();
      if (invoke) {
        try {
          await invoke('toggle_dnd', { active: isDndActive });
        } catch (e) {
          console.error('toggle_dnd error:', e);
        }
      }
      break;
    case 'lock':
      if (invoke) {
        await invoke('send_phone_key', { key: 'power' });
      }
      break;
    case 'screenshot':
      if (invoke) {
        await invoke('send_phone_key', { key: 'screenshot' });
      }
      break;
    case 'notifications':
      if (invoke) {
        await invoke('send_phone_key', { key: 'notifications' });
      }
      break;
    case 'quick_settings':
      if (invoke) {
        await invoke('send_phone_key', { key: 'quick_settings' });
      }
      break;
    case 'home':
      if (invoke) {
        await invoke('send_phone_key', { key: 'home' });
      }
      break;
    case 'back':
      if (invoke) {
        await invoke('send_phone_key', { key: 'back' });
      }
      break;
    case 'recents':
      if (invoke) {
        await invoke('send_phone_key', { key: 'recents' });
      }
      break;
    case 'rotate':
      if (invoke) {
        await invoke('send_phone_key', { key: 'rotate' });
      }
      break;
    case 'settings':
      if (invoke) {
        await invoke('send_phone_key', { key: 'open_settings' });
      }
      break;
    case 'mute':
      if (invoke) {
        await invoke('send_phone_key', { key: 'volume_down' });
      }
      break;
    case 'sleep':
      if (invoke) {
        await invoke('send_phone_key', { key: 'sleep' });
      }
      break;
    case 'clipboard':
      currentSection = 'clipboard';
      render();
      break;
    case 'mac_bluetooth':
      if (invoke) {
        await invoke('open_mac_bluetooth_settings');
      }
      break;
    case 'phone_bluetooth':
      if (invoke) {
        await invoke('send_phone_key', { key: 'phone_bluetooth' });
      }
      break;
    case 'ring':
      if (invoke) {
        await invoke('send_phone_key', { key: 'wake' });
      }
      break;
    default:
      console.warn('Unknown quick control:', id);
  }
}

function renderQuickControlsSection(): string {
  const pinnedIds = getPinnedQuickControls();
  const pinnedCtrls = AVAILABLE_QUICK_CONTROLS.filter((c) => pinnedIds.includes(c.id));
  const controlsToRender = pinnedCtrls.length > 0 ? pinnedCtrls : AVAILABLE_QUICK_CONTROLS.slice(0, 4);

  let contentHtml = '';
  if (quickControlsViewMode === 'tiles') {
    contentHtml = `
      <div class="quick-controls-btn-row">
        ${controlsToRender
          .map((ctrl) => {
            let isActive = false;
            if (ctrl.id === 'dnd') {
              isActive = isDndActive;
            }
            return `
              <button class="qc-interactive-btn ${isActive ? 'active' : ''}" data-qcid="${ctrl.id}" type="button" data-tooltip="${ctrl.desc}" title="${ctrl.desc}">
                <div class="qc-btn-icon" style="background: ${ctrl.accentColor}18; color: ${ctrl.accentColor};">
                  ${ctrl.iconFn(16, 'currentColor')}
                </div>
                <span class="qc-btn-label">${ctrl.name}</span>
                ${isActive ? `<span style="width: 6px; height: 6px; border-radius: 50%; background: var(--emerald-ion); box-shadow: 0 0 6px var(--emerald-ion); margin-left: 2px;"></span>` : ''}
              </button>
            `;
          })
          .join('')}
        
        <!-- Inline Aesthetic Plus Add Control Button -->
        <button class="qc-interactive-btn btn-add-qc btn-add-inline-qc" id="btn-deck-add-qc" type="button" data-tooltip="Add or customize quick controls in your deck" title="Add or customize quick controls">
          <div class="qc-btn-icon" style="background: rgba(255, 110, 50, 0.15); color: var(--accent-solar);">
            ${Icons.Plus(14, 'var(--accent-solar)')}
          </div>
          <span class="qc-btn-label">Add Control</span>
        </button>
      </div>
    `;
  } else {
    // Detailed Cards View
    contentHtml = `
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px;">
        ${controlsToRender
          .map((ctrl) => {
            let liveStatus = ctrl.badge;
            let isGreen = false;
            if (ctrl.id === 'dnd') {
              liveStatus = isDndActive ? 'Enabled' : 'Off';
              isGreen = isDndActive;
            } else if (ctrl.id === 'mirror') {
              liveStatus = '60 FPS Low-Latency';
              isGreen = true;
            }
            return `
              <div class="glass-card" style="padding: 16px; display: flex; flex-direction: column; justify-content: space-between; gap: 12px;">
                <div>
                  <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div style="display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 14px; color: #fff;">
                      ${ctrl.iconFn(16, ctrl.accentColor)}
                      <span>${ctrl.name}</span>
                    </div>
                    <span class="metric-pill ${isGreen ? 'green' : ''}" style="font-size: 10px; padding: 2px 7px;">${liveStatus}</span>
                  </div>
                  <p style="font-size: 12px; color: var(--text-secondary); margin-top: 6px; line-height: 1.4;">
                    ${ctrl.desc}
                  </p>
                </div>
                <button class="${ctrl.id === 'mirror' ? 'btn-solar' : 'btn-luxury'} btn-exec-qc" data-qcid="${ctrl.id}" style="width: 100%; justify-content: center;">
                  ${ctrl.iconFn(13, ctrl.id === 'mirror' ? '#fff' : 'currentColor')}
                  <span>${ctrl.actionName}</span>
                </button>
              </div>
            `;
          })
          .join('')}

        <!-- Inline Aesthetic Plus Add Control Card -->
        <div class="glass-card btn-add-inline-qc" id="btn-deck-add-qc-card" style="padding: 18px; display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 10px; border: 1.5px dashed rgba(255, 110, 50, 0.4); background: rgba(255, 110, 50, 0.04); cursor: pointer; min-height: 120px; transition: all 0.2s ease;" title="Add or Customize Quick Controls">
          <div style="width: 38px; height: 38px; border-radius: 10px; background: rgba(255, 110, 50, 0.12); border: 1px solid rgba(255, 110, 50, 0.3); display: flex; align-items: center; justify-content: center; color: var(--accent-solar);">
            ${Icons.Plus(18, 'var(--accent-solar)')}
          </div>
          <div style="text-align: center;">
            <div style="font-size: 13.5px; font-weight: 600; color: #fff;">Add Control</div>
            <div style="font-size: 11px; color: var(--text-secondary); margin-top: 3px;">Customize deck</div>
          </div>
        </div>
      </div>
    `;
  }

  return `
    <div class="command-deck-section" style="margin-top: 18px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;">
        <div style="font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px;">
          ${Icons.Dashboard(14, 'var(--accent-solar)')}
          <span>Quick Controls</span>
          <span style="font-size: 11px; font-weight: 500; color: var(--text-muted); text-transform: none; letter-spacing: normal;">(${controlsToRender.length} active)</span>
        </div>

        <div style="display: flex; align-items: center; gap: 8px;">
          <!-- View switcher: Buttons vs Cards -->
          <div class="segmented-control" style="background: rgba(255, 255, 255, 0.04); padding: 2px; border-radius: 8px; display: inline-flex; border: 1px solid var(--border-subtle);">
            <button class="btn-toggle-qc-view ${quickControlsViewMode === 'tiles' ? 'active' : ''}" data-view="tiles" style="padding: 4px 10px; font-size: 11px; border-radius: 6px; border: none; background: ${quickControlsViewMode === 'tiles' ? 'var(--accent-solar)' : 'transparent'}; color: ${quickControlsViewMode === 'tiles' ? '#fff' : 'var(--text-secondary)'}; cursor: pointer; font-weight: 500; display: inline-flex; align-items: center; gap: 5px;">
              ${Icons.Dashboard(11, 'currentColor')}
              <span>Buttons</span>
            </button>
            <button class="btn-toggle-qc-view ${quickControlsViewMode === 'cards' ? 'active' : ''}" data-view="cards" style="padding: 4px 10px; font-size: 11px; border-radius: 6px; border: none; background: ${quickControlsViewMode === 'cards' ? 'var(--accent-solar)' : 'transparent'}; color: ${quickControlsViewMode === 'cards' ? '#fff' : 'var(--text-secondary)'}; cursor: pointer; font-weight: 500; display: inline-flex; align-items: center; gap: 5px;">
              ${Icons.Sliders(11, 'currentColor')}
              <span>Cards</span>
            </button>
          </div>

          <button class="btn-luxury" id="btn-header-customize-qc" style="padding: 4px 11px; font-size: 11.5px; border-radius: 8px; color: var(--text-secondary); display: inline-flex; align-items: center; gap: 5px; cursor: pointer;" title="Customize Quick Controls">
            ${Icons.Sliders(11, 'var(--accent-solar)')}
            <span>Customize</span>
          </button>
        </div>
      </div>

      ${contentHtml}
    </div>
  `;
}

function renderQuickControlsModalGrid(): string {
  const pinned = getPinnedQuickControls();
  const query = quickControlsSearchQuery.trim().toLowerCase();

  const filtered = AVAILABLE_QUICK_CONTROLS.filter((c) => {
    if (!query) return true;
    return (
      c.name.toLowerCase().includes(query) ||
      c.desc.toLowerCase().includes(query) ||
      c.category.toLowerCase().includes(query)
    );
  });

  if (filtered.length === 0) {
    return `
      <div style="grid-column: 1 / -1; padding: 40px 20px; text-align: center; color: var(--text-muted); font-size: 13px;">
        No quick controls matching "${quickControlsSearchQuery}"
      </div>
    `;
  }

  return filtered
    .map((c) => {
      const isPinned = pinned.includes(c.id);
      return `
        <div class="glass-card" style="padding: 14px; display: flex; flex-direction: column; justify-content: space-between; gap: 12px; border-color: ${isPinned ? 'rgba(255, 110, 50, 0.35)' : 'var(--border-subtle)'}; background: ${isPinned ? 'rgba(255, 110, 50, 0.04)' : 'var(--surface-card)'};">
          <div style="display: flex; gap: 12px; align-items: flex-start;">
            <div class="quick-control-icon-box" style="background: ${c.accentColor}18; color: ${c.accentColor}; border-color: ${c.accentColor}33;">
              ${c.iconFn(20, 'currentColor')}
            </div>
            <div style="flex: 1; min-width: 0;">
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 6px;">
                <span style="font-size: 13.5px; font-weight: 600; color: #fff;">${c.name}</span>
                <span class="metric-pill" style="font-size: 9.5px; padding: 1px 6px; text-transform: uppercase;">${c.category}</span>
              </div>
              <p style="font-size: 11.5px; color: var(--text-secondary); margin-top: 4px; line-height: 1.35;">
                ${c.desc}
              </p>
            </div>
          </div>

          <div style="display: flex; justify-content: space-between; align-items: center; padding-top: 8px; border-top: 1px solid var(--border-subtle);">
            <span style="font-size: 11px; color: var(--text-muted);">${c.actionName}</span>
            <button class="btn-toggle-pin-qc ${isPinned ? 'btn-solar' : 'btn-luxury'}" data-qcid="${c.id}" style="padding: 4px 10px; font-size: 11px; border-radius: 6px; cursor: pointer;">
              ${isPinned ? '✓ Added' : '+ Add'}
            </button>
          </div>
        </div>
      `;
    })
    .join('');
}

function renderQuickControlsModal(): string {
  if (!isQuickControlsModalOpen) return '';
  const pinned = getPinnedQuickControls();

  return `
    <div id="quick-controls-modal-backdrop" style="position: fixed; inset: 0; background: rgba(0, 0, 0, 0.72); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); z-index: 9999; display: flex; align-items: center; justify-content: center; padding: 24px;">
      <div class="glass-card" style="width: 100%; max-width: 720px; max-height: 85vh; display: flex; flex-direction: column; overflow: hidden; box-shadow: 0 24px 64px rgba(0, 0, 0, 0.6); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 18px;">
        
        <!-- Modal Header -->
        <div style="padding: 18px 22px; border-bottom: 1px solid var(--border-subtle); display: flex; justify-content: space-between; align-items: center; background: rgba(255, 255, 255, 0.02);">
          <div style="display: flex; align-items: center; gap: 10px;">
            <div style="width: 32px; height: 32px; border-radius: 8px; background: rgba(255, 110, 50, 0.12); display: flex; align-items: center; justify-content: center; color: var(--accent-solar);">
              ${Icons.Dashboard(16, 'currentColor')}
            </div>
            <div>
              <div style="font-size: 15px; font-weight: 700; color: #fff;">Customize Quick Controls</div>
              <div style="font-size: 12px; color: var(--text-secondary); margin-top: 1px;">
                Choose which controls to pin to your dashboard &bull; <span id="quick-controls-pinned-count" style="color: var(--accent-solar); font-weight: 600;">${pinned.length}</span> pinned
              </div>
            </div>
          </div>
          <button id="btn-close-quick-controls-modal" class="btn-icon" style="width: 30px; height: 30px; border-radius: 50%; border: 1px solid var(--border-subtle); background: rgba(255,255,255,0.06); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s ease;" title="Close">
            ${Icons.Close(14, 'currentColor')}
          </button>
        </div>

        <!-- Search Bar & Reset Defaults -->
        <div style="padding: 14px 22px; display: flex; gap: 10px; align-items: center; border-bottom: 1px solid var(--border-subtle); background: rgba(0, 0, 0, 0.2);">
          <div style="position: relative; flex: 1;">
            <input
              id="input-search-quick-controls"
              type="text"
              placeholder="Search available Android controls..."
              value="${quickControlsSearchQuery}"
              style="width: 100%; padding: 8px 12px 8px 34px; border-radius: 8px; border: 1px solid var(--border-subtle); background: rgba(255, 255, 255, 0.04); color: #fff; font-size: 12.5px; outline: none;"
            />
            <div style="position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--text-muted); pointer-events: none;">
              ${Icons.Search(14, 'currentColor')}
            </div>
          </div>
          <button id="btn-reset-quick-controls-default" class="btn-luxury" style="padding: 8px 12px; font-size: 11.5px; border-radius: 8px; white-space: nowrap; cursor: pointer;">
            Reset Default
          </button>
        </div>

        <!-- Controls Grid -->
        <div id="quick-controls-modal-list" style="padding: 18px 22px; overflow-y: auto; display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 12px; max-height: calc(85vh - 180px);">
          ${renderQuickControlsModalGrid()}
        </div>

        <!-- Modal Footer -->
        <div style="padding: 14px 22px; border-top: 1px solid var(--border-subtle); display: flex; justify-content: flex-end; gap: 10px; background: rgba(255, 255, 255, 0.02);">
          <button id="btn-save-quick-controls" class="btn-solar" style="padding: 8px 20px; font-size: 12.5px; font-weight: 600; border-radius: 8px; cursor: pointer;">
            Done
          </button>
        </div>

      </div>
    </div>
  `;
}

function renderLightboxModal(): string {
  if (!activeLightboxPhoto) return '';
  const thumbData = photoThumbnails.get(activeLightboxPhoto.path);

  return `
    <div class="lightbox-backdrop" id="lightbox-backdrop">
      <div class="lightbox-content">
        <button class="lightbox-close-btn" id="btn-close-lightbox">✕</button>
        ${
          thumbData
            ? `<img src="data:image/jpeg;base64,${thumbData}" class="lightbox-img" alt="${activeLightboxPhoto.name}" />`
            : `<div style="padding: 60px; text-align: center; color: var(--text-secondary);"><span class="pulse-dot"></span> Loading high-res preview...</div>`
        }
        <div class="lightbox-info-bar">
          <div>
            <div style="font-size: 13.5px; font-weight: 700; color: #fff;">${activeLightboxPhoto.name}</div>
            <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 2px;">
              ${activeLightboxPhoto.size} &bull; ${activeLightboxPhoto.album || 'Media'} &bull; ${activeLightboxPhoto.modified}
            </div>
          </div>
          <button class="btn-solar btn-save-photo-single" data-photopath="${activeLightboxPhoto.path}" data-filename="${activeLightboxPhoto.name}" style="padding: 6px 14px; font-size: 12px; white-space: nowrap;">
            ${Icons.Download(12, '#fff')}
            <span>Save to Mac</span>
          </button>
        </div>
      </div>
    </div>
  `;
}

function renderAddFavouriteModal(): string {
  if (!isAddFavouriteModalOpen) return '';

  return `
    <div class="modal-backdrop" id="fav-modal-backdrop">
      <div class="modal-window" style="max-width: 420px;">
        <div class="modal-header">
          <div style="display: flex; align-items: center; gap: 10px;">
            <div style="width: 34px; height: 34px; border-radius: 10px; background: rgba(255, 110, 50, 0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center;">
              ${Icons.Users(18, 'currentColor')}
            </div>
            <div>
              <h2 style="font-size: 15px; font-weight: 700; color: #fff;">Add Contact</h2>
              <p style="font-size: 11px; color: var(--text-secondary); margin-top: 1px;">Save quick speed-dial contact to Calls & Audio</p>
            </div>
          </div>
          <button class="btn-icon" id="btn-close-fav-modal" style="width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--border-subtle); background: rgba(255,255,255,0.03); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;">✕</button>
        </div>

        <div style="display: flex; flex-direction: column; gap: 14px; margin-top: 14px;">
          <div>
            <label style="font-size: 11.5px; font-weight: 600; color: var(--text-secondary); display: block; margin-bottom: 6px;">Contact Name</label>
            <input 
              type="text" 
              id="input-fav-name" 
              placeholder="e.g. John Doe, Office Desk..." 
              value="${newFavName}"
              style="width: 100%; background: rgba(255,255,255,0.04); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); color: #fff; padding: 8px 12px; font-size: 13px; outline: none;"
            />
          </div>

          <div>
            <label style="font-size: 11.5px; font-weight: 600; color: var(--text-secondary); display: block; margin-bottom: 6px;">Phone Number</label>
            <input 
              type="text" 
              id="input-fav-number" 
              placeholder="e.g. +1 555 0199 or 9876543210..." 
              value="${newFavNumber}"
              style="width: 100%; background: rgba(255,255,255,0.04); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); color: #fff; padding: 8px 12px; font-size: 13px; outline: none;"
            />
          </div>

          <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px;">
            <button class="btn-luxury" id="btn-cancel-fav-modal" style="padding: 7px 14px; font-size: 12px;">Cancel</button>
            <button class="btn-solar" id="btn-save-fav-modal" style="padding: 7px 18px; font-size: 12px;">Add Contact</button>
          </div>
        </div>
      </div>
    </div>
  `;
}

// MARK: - Floating Apple Continuity Style In-Call HUD
function renderFloatingCallHUD(): string {
  if (!isCallActive) return '';

  const mins = Math.floor(callDurationSec / 60)
    .toString()
    .padStart(2, '0');
  const secs = (callDurationSec % 60).toString().padStart(2, '0');
  const timerStr = `${mins}:${secs}`;
  const displayName = activeCallTargetName || activeCallTarget || 'Connected Call';
  const initial = displayName.substring(0, 1).toUpperCase();

  return `
    <div class="floating-call-hud" id="floating-call-hud">
      <div class="hud-caller-header">
        <div class="hud-avatar-wrapper">
          <div class="hud-avatar-circle">
            ${initial}
          </div>
          <div class="hud-avatar-pulse"></div>
        </div>

        <div class="hud-caller-info">
          <div class="hud-caller-name" title="${displayName}">${displayName}</div>
          <div class="hud-caller-sub">
            <span class="status-pulse-dot"></span>
            <span>Call Connected</span>
            <span>•</span>
            <span class="hud-timer-badge" id="hud-call-timer">${timerStr}</span>
          </div>
        </div>
      </div>

      <!-- Single Device Switch Button Row (As Requested: Mac vs Phone toggle) -->
      <div class="hud-device-switch-row">
        <div class="hud-current-route-label">
          ${
            callAudioRoute === 'mac'
              ? `${Icons.Laptop(15, 'var(--accent-solar)')} <span>Using Mac Audio & Mic</span>`
              : `${Icons.Phone(15, 'var(--emerald-ion)')} <span>Using Phone Handset</span>`
          }
        </div>
        <button class="hud-btn-switch-device" id="btn-hud-switch-device" title="Switch between Mac and Phone">
          ⇄ Switch to ${callAudioRoute === 'mac' ? 'Phone' : 'Mac'}
        </button>
      </div>

      <!-- In-Call Actions: Mute, Record, Add Person / Conference, End -->
      <div class="hud-controls-grid">
        <!-- Mute / Unmute -->
        <div class="hud-action-col">
          <button class="hud-ctrl-btn ${isCallMuted ? 'active' : ''}" id="btn-hud-mute" title="${isCallMuted ? 'Unmute Microphone' : 'Mute Microphone'}">
            ${isCallMuted ? '🔇' : '🎙️'}
          </button>
          <span class="hud-btn-label">${isCallMuted ? 'Muted' : 'Mute'}</span>
        </div>

        <!-- Record Call Audio -->
        <div class="hud-action-col">
          <button class="hud-ctrl-btn ${isCallRecording ? 'recording' : ''}" id="btn-hud-record" title="${isCallRecording ? 'Stop Recording' : 'Record Call Audio'}">
            ${isCallRecording ? '⏹️' : '⏺️'}
          </button>
          <span class="hud-btn-label">${isCallRecording ? 'Recording' : 'Record'}</span>
        </div>

        <!-- Add Person / Conference -->
        <div class="hud-action-col">
          <button class="hud-ctrl-btn ${isAddPersonModalOpen ? 'active' : ''}" id="btn-hud-add-person" title="Add Person for Conference">
            👥
          </button>
          <span class="hud-btn-label">Add Person</span>
        </div>

        <!-- End Call (Red) -->
        <div class="hud-action-col">
          <button class="hud-ctrl-btn end" id="btn-hud-end-call" title="End Call">
            ${Icons.Calls(20, '#fff')}
          </button>
          <span class="hud-btn-label" style="color: #ef4444; font-weight: 600;">End</span>
        </div>
      </div>

      ${
        isAddPersonModalOpen
          ? `
        <div class="hud-conference-bar">
          <div style="font-size: 11px; font-weight: 600; color: #fff; display: flex; justify-content: space-between; align-items: center;">
            <span>Add Participant to Call</span>
            <button id="btn-hud-cancel-conference" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 13px;">✕</button>
          </div>
          <div style="display: flex; gap: 6px; margin-top: 4px;">
            <input type="text" id="input-hud-conference-number" placeholder="Enter number..." style="flex: 1; background: rgba(255,255,255,0.06); border: 1px solid var(--border-subtle); border-radius: 8px; color: #fff; padding: 4px 8px; font-size: 11.5px; outline: none;" />
            <button class="btn-solar" id="btn-hud-dial-conference" style="padding: 4px 10px; font-size: 11px;">Add</button>
          </div>
          ${
            conferenceParticipants.length > 0
              ? `
            <div style="font-size: 10.5px; color: var(--text-secondary); margin-top: 2px;">
              In Conference: ${conferenceParticipants.join(', ')}
            </div>
          `
              : ''
          }
        </div>
      `
          : ''
      }
    </div>
  `;
}

// MARK: - Incoming Call Alert Banner
function renderIncomingCallBanner(): string {
  if (!incomingCall) return '';

  const name = incomingCall.name || incomingCall.number || 'Incoming Phone Call';
  const num = incomingCall.number || 'Wireless Connected Phone';
  const initial = name.substring(0, 1).toUpperCase();

  return `
    <div class="incoming-call-floating-card" id="incoming-call-floating-card">
      <div style="display: flex; align-items: center; gap: 12px;">
        <div class="hud-avatar-wrapper">
          <div class="hud-avatar-circle" style="background: linear-gradient(135deg, #10b981 0%, #047857 100%);">
            ${initial}
          </div>
          <div class="hud-avatar-pulse"></div>
        </div>

        <div style="display: flex; flex-direction: column; min-width: 0; flex: 1;">
          <div style="font-size: 11px; font-weight: 700; color: var(--emerald-ion); letter-spacing: 0.5px; text-transform: uppercase;">
            Incoming Phone Call • iPhone / Android
          </div>
          <div style="font-size: 15px; font-weight: 700; color: #fff; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            ${name}
          </div>
          <div style="font-size: 12px; color: var(--text-secondary);">
            ${num}
          </div>
        </div>
      </div>

      <div class="incoming-call-actions">
        <button class="btn-decline-incoming-call" id="btn-decline-incoming-call">
          ✕ Decline
        </button>
        <button class="btn-accept-incoming-call" id="btn-accept-incoming-call">
          📞 Accept on Mac
        </button>
      </div>
    </div>
  `;
}

function attachQuickControlModalItemListeners() {
  document.querySelectorAll('.btn-toggle-pin-qc').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const id = el.getAttribute('data-qcid');
      if (!id) return;
      let pinned = getPinnedQuickControls();
      if (pinned.includes(id)) {
        pinned = pinned.filter((p) => p !== id);
      } else {
        pinned.push(id);
      }
      savePinnedQuickControls(pinned);

      // Refresh list inside modal
      const listContainer = document.getElementById('quick-controls-modal-list');
      if (listContainer) {
        listContainer.innerHTML = renderQuickControlsModalGrid();
        attachQuickControlModalItemListeners();
      }
      // Update count inside modal
      const countEl = document.getElementById('quick-controls-pinned-count');
      if (countEl) countEl.textContent = String(pinned.length);

      // Update header button label on dashboard
      const headerBtn = document.getElementById('btn-header-customize-qc');
      if (headerBtn) {
        headerBtn.innerHTML = `${Icons.Sliders(11, 'var(--accent-solar)')} <span>Customize</span>`;
      }

      render();
    });
  });
}


async function fetchQuickPhoneApps() {
  const invoke = await getInvoke();
  if (!invoke) return;
  try {
    let apps: any = await invoke('get_quick_phone_apps');
    if (!Array.isArray(apps) || apps.length === 0) {
      if (phoneApps && phoneApps.length > 0) {
        apps = phoneApps.slice(0, 30).map((a: any) => ({
          name: a.name,
          package: a.package,
          icon: cleanIconUrl(a.icon_base64),
        }));
      }
    }
    if (Array.isArray(apps) && apps.length > 0) {
      const cleaned = apps.map((a: any) => ({
        name: a.name,
        package: a.package,
        icon: cleanIconUrl(a.icon),
      }));
      const changed = cleaned.length !== quickPhoneApps.length ||
        (quickPhoneApps.length > 0 && cleaned[0].package !== quickPhoneApps[0].package);
      if (changed || quickPhoneApps.length === 0) {
        quickPhoneApps = cleaned;
        if (phoneApps.length === 0) {
          phoneApps = cleaned.map((a: any) => ({
            name: a.name,
            package: a.package,
            icon_base64: a.icon ? a.icon.replace(/^data:image\/[^;]+;base64,/, '') : undefined,
          }));
          saveCachedPhoneApps(phoneApps);
        }
        const container = document.getElementById('quick-phone-apps-container');
        if (container) {
          container.innerHTML = renderQuickAppsHtml();
          attachQuickAppListeners();
        }
      }
    }
  } catch (_) {}
}

async function pollDeviceTelemetry() {
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const data: any = await invoke('get_device_telemetry');
    if (data) {
      const prevStatus = device.status;
      const prevConn = device.connectionType;
      const prevPhoneCharging = device.isCharging;
      const prevPhoneBattery = device.battery;
      const prevMacCharging = hostTelemetry.is_charging;
      const prevMacBattery = hostTelemetry.battery;
      const prevPhoneStorage = device.storageUsedGb;
      const prevMacStorage = hostTelemetry.storage_used_gb;

      device.name = data.name;
      device.brand = data.brand;
      device.battery = data.battery;
      device.isCharging = data.is_charging;
      device.connectionType = data.connection_type;
      device.status = data.status;
      device.voltageMv = data.voltage_mv;
      device.temperatureC = data.temperature_c;
      device.storageUsedGb = data.storage_used_gb;
      device.storageTotalGb = data.storage_total_gb;
      device.storagePercent = data.storage_percent;
      device.bluetoothStatus = data.bluetooth_status || (data.status === 'Connected' ? 'Bluetooth On' : 'Off');
      device.bluetoothConnected = !!data.bluetooth_connected;

      if (device.status === 'Disconnected') {
        phoneApps = [];
        phoneFiles = [];
        phonePhotos = [];
        notificationList = [];
        seenNotificationIds.clear();
        hasSeededNotifications = false;
        // Preserve callHistory and clipboardHistory across disconnects
      } else if (prevStatus === 'Disconnected' && device.status === 'Connected') {
        fetchQuickPhoneApps();
        fetchCallsAndNotifications();
      }

      // Sync host Mac telemetry
      try {
        const host: any = await invoke('get_host_telemetry');
        if (host && typeof host === 'object') {
          hostTelemetry = host;
        }
      } catch (_) {}

      const statusChanged = prevStatus !== device.status;
      const connChanged = prevConn !== device.connectionType;

      // Check if current view is out of sync with connection status (e.g. Dashboard showing QR card while connected)
      const qrBtn = document.getElementById('btn-regenerate-pairing-qr');
      const isDashboardDesynced = currentSection === 'dashboard' && (
        (device.status === 'Connected' && qrBtn !== null) ||
        (device.status === 'Disconnected' && qrBtn === null)
      );

      // Only perform expensive full DOM re-render if connection status itself changed
      if (statusChanged || connChanged || isDashboardDesynced) {
        render();
      } else {
        updateLiveTelemetryUI();
      }
    }
  } catch (err: any) {
    console.error('Failed to poll telemetry:', err);
    try {
      await invoke('debug_log', { msg: `pollDeviceTelemetry error: ${err?.message || err}` });
    } catch {}
  }
}

async function pollIncomingCallStatus() {
  if (device.status !== 'Connected' || isCallActive) return;
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const callInfo: any = await invoke('check_incoming_call');
    if (callInfo && callInfo.is_ringing && !incomingCall) {
      const rawNum = callInfo.number || 'Unknown Caller';
      const cleanNum = rawNum.replace(/[\s-]/g, '');
      const matchedContact = favouriteContacts.find(c => c.number.replace(/[\s-]/g, '') === cleanNum);
      const matchedHistory = callHistory.find(c => c.number.replace(/[\s-]/g, '') === cleanNum);
      const callerName = matchedContact?.name || matchedHistory?.name || (callInfo.name || rawNum);

      incomingCall = {
        name: callerName,
        number: rawNum,
      };
      render();
    } else if (callInfo && !callInfo.is_ringing && incomingCall) {
      incomingCall = null;
      render();
    }
  } catch (_) {}
}

async function pollCameraStreamStatus() {
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const stats: any = await invoke('get_camera_stream_stats');
    if (stats) {
      const wasActive = Boolean(cameraStreamStats && cameraStreamStats.active);
      const nowActive = Boolean(stats.active);
      cameraStreamStats = stats;

      if (wasActive !== nowActive) {
        if (nowActive && currentSection !== 'camera') {
          currentSection = 'camera';
        }
        render();
      } else if (nowActive && currentSection === 'camera') {
        const fpsEl = document.getElementById('camera-stat-fps');
        if (fpsEl) fpsEl.textContent = `${stats.fps} FPS`;
        const latEl = document.getElementById('camera-stat-latency');
        if (latEl) latEl.textContent = `${stats.latency_ms.toFixed(1)} ms`;
        const brEl = document.getElementById('camera-stat-bitrate');
        if (brEl) brEl.textContent = `${stats.bitrate_mbps.toFixed(1)} Mbps`;
      }
    }
  } catch (_) {}
}

async function handleFocusCameraPlayer() {
  const invoke = await getInvoke();
  if (!invoke) return;
  try {
    await invoke('focus_camera_player');
  } catch (_) {}
}

async function handleSwitchToWireless() {
  const invoke = await getInvoke();
  if (!invoke) return;

  isWirelessConnecting = true;
  wirelessStatusMsg = 'Configuring ADB TCP/IP on port 5555 and resolving phone Wi-Fi address...';
  render();

  try {
    const res: any = await invoke('switch_to_wireless_tcpip');
    if (res && res.success) {
      wirelessStatusMsg = res.message;
      showAppLaunchHUD('Wi-Fi Connected', res.message);
      await pollDeviceTelemetry();
    } else {
      wirelessStatusMsg = res?.message || 'Handover failed.';
    }
  } catch (err: any) {
    wirelessStatusMsg = `Handover error: ${err}`;
  } finally {
    isWirelessConnecting = false;
    render();
  }
}

async function handlePairWireless() {
  const invoke = await getInvoke();
  if (!invoke) return;

  const ipInput = document.getElementById('input-pair-ip') as HTMLInputElement;
  const portInput = document.getElementById('input-pair-port') as HTMLInputElement;
  const codeInput = document.getElementById('input-pair-code') as HTMLInputElement;

  const ip = ipInput ? ipInput.value.trim() : '';
  const portStr = portInput ? portInput.value.trim() : '';
  const code = codeInput ? codeInput.value.trim() : '';

  if (!ip || !portStr || !code) {
    wirelessStatusMsg = 'Please enter phone IP, pairing port, and 6-digit Wi-Fi code.';
    render();
    return;
  }

  const port = parseInt(portStr, 10);
  if (isNaN(port)) {
    wirelessStatusMsg = 'Invalid port number.';
    render();
    return;
  }

  isWirelessConnecting = true;
  wirelessStatusMsg = `Pairing with ${ip}:${port} over Wi-Fi...`;
  render();

  try {
    const pairRes: string = await invoke('pair_wireless_device', { ip, port, pairingCode: code });
    wirelessStatusMsg = `${pairRes}. Connecting to ADB daemon...`;
    render();

    const connRes: any = await invoke('connect_wireless_ip', { ip, port: 5555 });
    if (connRes && connRes.success) {
      wirelessStatusMsg = connRes.message;
      showAppLaunchHUD('Wireless Paired', connRes.message);
      await pollDeviceTelemetry();
      setTimeout(() => {
        isWirelessModalOpen = false;
        render();
      }, 1400);
    }
  } catch (err: any) {
    wirelessStatusMsg = `Pairing failed: ${err}`;
  } finally {
    isWirelessConnecting = false;
    render();
  }
}

async function handleConnectMesh() {
  const invoke = await getInvoke();
  if (!invoke) return;

  const ipInput = document.getElementById('input-mesh-ip') as HTMLInputElement;
  const portInput = document.getElementById('input-mesh-port') as HTMLInputElement;

  const ip = ipInput ? ipInput.value.trim() : '';
  const portStr = portInput ? portInput.value.trim() : '5555';

  if (!ip) {
    wirelessStatusMsg = 'Please enter a valid remote mesh or WAN IP address.';
    render();
    return;
  }

  const port = parseInt(portStr, 10) || 5555;

  isWirelessConnecting = true;
  wirelessStatusMsg = `Connecting to ${ip}:${port} over encrypted mesh...`;
  render();

  try {
    const res: any = await invoke('connect_wireless_ip', { ip, port });
    if (res && res.success) {
      wirelessStatusMsg = res.message;
      showAppLaunchHUD('Mesh Active', res.message);
      await pollDeviceTelemetry();
      setTimeout(() => {
        isWirelessModalOpen = false;
        render();
      }, 1400);
    }
  } catch (err: any) {
    wirelessStatusMsg = `Connection failed: ${err}`;
  } finally {
    isWirelessConnecting = false;
    render();
  }
}

async function handleDisconnectWireless() {
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const res: string = await invoke('disconnect_wireless_device');
    wirelessStatusMsg = res || 'Disconnected wireless sessions.';
    await pollDeviceTelemetry();
  } catch (err: any) {
    wirelessStatusMsg = `Disconnect error: ${err}`;
  }
  render();
}

async function fetchPhoneApps() {
  if (device.status === 'Disconnected') {
    if (phoneApps.length === 0) {
      phoneApps = loadCachedPhoneApps();
    }
    return;
  }

  isLoadingApps = true;
  render();

  const invoke = await getInvoke();
  if (invoke) {
    try {
      const fetched: AppInfo[] = await invoke('list_phone_apps');
      if (Array.isArray(fetched) && fetched.length > 0) {
        phoneApps = fetched;
        saveCachedPhoneApps(phoneApps);
      }
    } catch (err) {
      console.error('IPC list_phone_apps failed:', err);
    }
  }

  isLoadingApps = false;
  if (phoneApps.length > 0) {
    quickPhoneApps = phoneApps.slice(0, 30).map((a) => ({
      name: a.name,
      package: a.package,
      icon: cleanIconUrl(a.icon_base64),
    }));
  }
  render();
}

function triggerLiveNotificationToast(n: NotificationItem) {
  let container = document.getElementById('live-notif-toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'live-notif-toast-container';
    container.className = 'live-notif-toast-container';
    document.body.appendChild(container);
  }

  let iconHtml = '';
  if (n.icon_base64) {
    iconHtml = `<img src="${cleanIconUrl(n.icon_base64)}" class="toast-app-icon" />`;
  } else if (n.package_name) {
    const matched = (quickPhoneApps || []).find((a) => a.package === n.package_name);
    if (matched && matched.icon) {
      iconHtml = `<img src="${cleanIconUrl(matched.icon)}" class="toast-app-icon" />`;
    }
  }
  if (!iconHtml) {
    iconHtml = `<div class="toast-app-icon" style="background: rgba(255, 110, 50, 0.15); display: flex; align-items: center; justify-content: center; color: var(--accent-solar);">
      ${Icons.Notifications(14, 'currentColor')}
    </div>`;
  }

  const toast = document.createElement('div');
  toast.className = 'live-notif-toast';
  const toastId = 'toast-' + Math.random().toString(36).substring(2, 9);
  toast.id = toastId;

  const isIncomingCall =
    (n.appName || '').toLowerCase().includes('phone') ||
    (n.appName || '').toLowerCase().includes('call') ||
    (n.title || '').toLowerCase().includes('call') ||
    (n.text || '').toLowerCase().includes('call') ||
    (n.package_name || '').includes('dialer') ||
    (n.package_name || '').includes('telecom');

  if (isIncomingCall && !isCallActive && !incomingCall) {
    incomingCall = {
      name: n.title || 'Incoming Phone Call',
      number: n.text || n.title || 'Phone Call',
    };
    render();
  }

  toast.innerHTML = `
    <div class="toast-header-row">
      <div class="toast-app-info">
        ${iconHtml}
        <span class="toast-app-name">${n.appName || 'Phone Notification'}</span>
        <span style="font-size: 11px; color: var(--text-muted);">• Now</span>
      </div>
      <button class="toast-close-btn" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 16px; padding: 0 4px; line-height: 1;">&times;</button>
    </div>
    <div style="font-size: 13.5px; font-weight: 600; color: #fff;">${n.title || ''}</div>
    <div style="font-size: 12.5px; color: var(--text-secondary); line-height: 1.4;">${n.text || ''}</div>
    <div class="toast-actions-row" style="display: flex; align-items: center; gap: 8px; margin-top: 6px;">
      ${
        isIncomingCall
          ? `
        <button class="btn-solar toast-accept-call-btn" style="padding: 6px 14px; font-size: 11.5px; background: #10b981; font-weight: 700; border: none; cursor: pointer;">
          📞 Accept Call
        </button>
        <button class="btn-luxury toast-decline-call-btn" style="padding: 6px 12px; font-size: 11.5px; color: #ef4444; border-color: rgba(239,68,68,0.4); cursor: pointer;">
          ✕ Decline
        </button>
      `
          : `
        <button class="btn-luxury toast-copy-btn" style="padding: 4px 10px; font-size: 11px;">📋 Copy Text</button>
        ${n.has_reply ? `<button class="btn-solar toast-toggle-reply-btn" style="padding: 4px 10px; font-size: 11px;">Reply</button>` : ''}
      `
      }
    </div>
    <div class="toast-reply-box" style="display: none; gap: 6px; margin-top: 6px;">
      <input type="text" class="toast-reply-input" placeholder="Type reply to ${n.appName}..." style="flex: 1; background: rgba(255,255,255,0.06); border: 1px solid var(--border-subtle); border-radius: 6px; color: #fff; padding: 5px 8px; font-size: 12px; outline: none;" />
      <button class="btn-solar toast-send-reply-btn" style="padding: 5px 10px; font-size: 11px;">Send</button>
    </div>
  `;

  let dismissTimeout: any;
  const scheduleDismiss = () => {
    dismissTimeout = setTimeout(() => {
      dismiss();
    }, isIncomingCall ? 20000 : 8000);
  };
  const cancelDismiss = () => {
    if (dismissTimeout) clearTimeout(dismissTimeout);
  };

  const dismiss = () => {
    toast.classList.add('dismissing');
    setTimeout(() => {
      toast.remove();
    }, 300);
  };

  toast.addEventListener('mouseenter', cancelDismiss);
  toast.addEventListener('mouseleave', () => {
    const input = toast.querySelector('.toast-reply-input') as HTMLInputElement | null;
    if (!input || document.activeElement !== input) {
      scheduleDismiss();
    }
  });

  const closeBtn = toast.querySelector('.toast-close-btn');
  closeBtn?.addEventListener('click', dismiss);

  const acceptCallBtn = toast.querySelector('.toast-accept-call-btn');
  if (acceptCallBtn) {
    acceptCallBtn.addEventListener('click', () => {
      dismiss();
      handleAcceptIncomingCall(n.text || n.title, n.title || 'Caller');
    });
  }

  const declineCallBtn = toast.querySelector('.toast-decline-call-btn');
  if (declineCallBtn) {
    declineCallBtn.addEventListener('click', () => {
      dismiss();
      handleDeclineIncomingCall();
    });
  }

  const copyBtn = toast.querySelector('.toast-copy-btn');
  copyBtn?.addEventListener('click', async () => {
    const textToCopy = n.text || n.title || '';
    if (textToCopy) {
      await navigator.clipboard.writeText(textToCopy);
      if (copyBtn) {
        copyBtn.textContent = '✓ Copied!';
        setTimeout(() => {
          if (copyBtn) copyBtn.textContent = '📋 Copy Text';
        }, 1500);
      }
    }
  });

  const toggleReplyBtn = toast.querySelector('.toast-toggle-reply-btn');
  const replyBox = toast.querySelector('.toast-reply-box') as HTMLElement | null;
  const replyInput = toast.querySelector('.toast-reply-input') as HTMLInputElement | null;
  const sendReplyBtn = toast.querySelector('.toast-send-reply-btn');

  if (toggleReplyBtn && replyBox && replyInput) {
    toggleReplyBtn.addEventListener('click', () => {
      cancelDismiss();
      if (replyBox.style.display === 'none') {
        replyBox.style.display = 'flex';
        replyInput.focus();
      } else {
        replyBox.style.display = 'none';
      }
    });

    const doSend = async () => {
      const replyText = replyInput.value.trim();
      if (!replyText) return;
      const invoke = await getInvoke();
      if (invoke) {
        await invoke('reply_notification', { id: n.id, replyText }).catch(() => {});
      }
      dismiss();
    };

    sendReplyBtn?.addEventListener('click', doSend);
    replyInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        doSend();
      }
    });
  }

  container.appendChild(toast);
  scheduleDismiss();
}

async function fetchCallsAndNotifications() {
  if (device.status === 'Disconnected') {
    notificationList = [];
    return;
  }

  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const remoteCalls: CallRecord[] = (await invoke('get_call_history')) || [];
    if (Array.isArray(remoteCalls) && remoteCalls.length > 0) {
      // Merge remote call logs with existing callHistory without overwriting local calls
      const existingIds = new Set(callHistory.map((c) => c.id));
      const newItems = remoteCalls.filter((c) => !existingIds.has(c.id));
      if (newItems.length > 0) {
        callHistory = [...callHistory, ...newItems].slice(0, 50);
        try {
          localStorage.setItem('idroid_call_history', JSON.stringify(callHistory));
        } catch (_) {}
      }
    }
    const notifs: NotificationItem[] = await invoke('get_notification_list');
    
    if (!hasSeededNotifications) {
      // First poll after connection: seed existing notifications without spamming alerts
      for (const n of notifs) {
        seenNotificationIds.add(n.id);
      }
      hasSeededNotifications = true;
    } else {
      // Subsequent live polls: trigger interactive toast and native macOS notification
      for (const n of notifs) {
        if (!seenNotificationIds.has(n.id)) {
          seenNotificationIds.add(n.id);
          if (n.title || n.text) {
            triggerLiveNotificationToast(n);
            invoke('show_mac_system_notification', {
              title: `Idroid • ${n.appName || 'Phone Notification'}`,
              subtitle: n.title,
              message: n.text,
            }).catch(() => {});
          }
        }
      }
    }
    notificationList = notifs;
    clipboardHistory = await invoke('get_clipboard_history');
  } catch (err) {
    console.error('Failed to fetch calls/notifications:', err);
  }
}

async function fetchPhoneFiles(targetDir?: string) {
  if (device.status === 'Disconnected') {
    phoneFiles = [];
    return;
  }

  const invoke = await getInvoke();
  if (!invoke) return;

  if (targetDir) {
    currentRemoteDir = targetDir;
  }
  selectedFiles.clear();

  isLoadingFiles = true;
  render();
  try {
    phoneFiles = await invoke('list_phone_files', { remoteDir: currentRemoteDir });
  } catch (err) {
    console.error('Failed to list files:', err);
    fileOperationMsg = `Notice: ${err}`;
  } finally {
    isLoadingFiles = false;
    render();
  }
}

async function fetchPhonePhotos(album?: string) {
  if (device.status === 'Disconnected') {
    phonePhotos = [];
    return;
  }

  const invoke = await getInvoke();
  if (!invoke) return;

  if (album) {
    currentPhotoAlbum = album;
  }
  selectedPhotos.clear();

  isLoadingPhotos = true;
  render();
  try {
    phonePhotos = await invoke('list_phone_photos', { album: currentPhotoAlbum });
  } catch (err) {
    console.error('Failed to list photos:', err);
    photoOperationMsg = `Notice: ${err}`;
  } finally {
    isLoadingPhotos = false;
    render();
    setupIntersectionThumbnailLoader();
  }
}

let thumbnailObserver: IntersectionObserver | null = null;
let thumbnailQueue: string[] = [];
let activeThumbnailJobs = 0;
const MAX_CONCURRENT_THUMBS = 2;

function queueThumbnailLoad(remotePath: string) {
  if (photoThumbnails.has(remotePath) || thumbnailQueue.includes(remotePath)) return;
  thumbnailQueue.push(remotePath);
  processThumbnailQueue();
}

async function processThumbnailQueue() {
  if (activeThumbnailJobs >= MAX_CONCURRENT_THUMBS || thumbnailQueue.length === 0) return;
  const path = thumbnailQueue.shift();
  if (!path) return;

  activeThumbnailJobs++;
  try {
    const invoke = await getInvoke();
    if (invoke) {
      const b64: string = await invoke('get_photo_thumbnail', { remotePath: path });
      if (b64) {
        photoThumbnails.set(path, b64);
        const phEl = document.querySelector(`div[data-thumb-path="${CSS.escape(path)}"]`) as HTMLElement;
        if (phEl) {
          const img = document.createElement('img');
          img.src = `data:image/jpeg;base64,${b64}`;
          img.className = 'photo-thumb-img';
          img.alt = 'Photo';
          phEl.replaceWith(img);
        }
      }
    }
  } catch (_) {
  } finally {
    activeThumbnailJobs--;
    setTimeout(processThumbnailQueue, 40);
  }
}

function setupIntersectionThumbnailLoader() {
  if (thumbnailObserver) {
    thumbnailObserver.disconnect();
    thumbnailObserver = null;
  }

  const mainContent = document.querySelector('.main-content');
  if (!mainContent) return;

  thumbnailObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const el = entry.target as HTMLElement;
          const path = el.getAttribute('data-photopath');
          if (path && !photoThumbnails.has(path)) {
            queueThumbnailLoad(path);
          }
        }
      });
    },
    {
      root: mainContent,
      rootMargin: '250px 0px',
      threshold: 0.01,
    }
  );

  document.querySelectorAll('.photo-manager-card').forEach((card) => {
    thumbnailObserver?.observe(card);
  });
}

function setupStickyHeaderScrollBehavior() {
  // Top bar is kept stably visible without scroll-hiding jitter
}

async function handleSaveFilesToMac(paths?: string[]) {
  const targetPaths = paths && paths.length > 0 ? paths : Array.from(selectedFiles);
  if (targetPaths.length === 0) return;
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const chosenFolder: string | null = await invoke('choose_mac_folder');
    if (!chosenFolder) return;

    isSavingFiles = true;
    fileOperationMsg = `Saving ${targetPaths.length} item(s) to ${chosenFolder}...`;
    render();

    const res: string = await invoke('download_files_to_mac', {
      remotePaths: targetPaths,
      localDir: chosenFolder,
    });
    fileOperationMsg = res;
    selectedFiles.clear();
  } catch (err) {
    fileOperationMsg = `Save error: ${err}`;
  } finally {
    isSavingFiles = false;
    render();
  }
}

async function handleSavePhotosToMac(paths?: string[]) {
  const targetPaths = paths && paths.length > 0 ? paths : Array.from(selectedPhotos);
  if (targetPaths.length === 0) return;
  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    const chosenFolder: string | null = await invoke('choose_mac_folder');
    if (!chosenFolder) return;

    isSavingPhotos = true;
    photoOperationMsg = `Saving ${targetPaths.length} photo(s) to ${chosenFolder}...`;
    render();

    const res: string = await invoke('download_files_to_mac', {
      remotePaths: targetPaths,
      localDir: chosenFolder,
    });
    photoOperationMsg = res;
    selectedPhotos.clear();
  } catch (err) {
    photoOperationMsg = `Save error: ${err}`;
  } finally {
    isSavingPhotos = false;
    render();
  }
}

function handleAddFavouriteContact(name?: string, number?: string) {
  const contactName = (name !== undefined ? name : newFavName).trim();
  const contactNumber = (number !== undefined ? number : newFavNumber).trim();
  if (!contactName || !contactNumber) return;
  const colors = ['#ec4899', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4'];
  const color = colors[favouriteContacts.length % colors.length];
  favouriteContacts.push({
    id: `fav-${Date.now()}`,
    name: contactName,
    number: contactNumber,
    avatarColor: color,
  });
  try {
    localStorage.setItem('idroid_favourite_contacts', JSON.stringify(favouriteContacts));
  } catch (_) {}
  newFavName = '';
  newFavNumber = '';
  isAddFavouriteModalOpen = false;
  render();
}

function handleRemoveFavouriteContact(id: string) {
  favouriteContacts = favouriteContacts.filter(c => c.id !== id);
  try {
    localStorage.setItem('idroid_favourite_contacts', JSON.stringify(favouriteContacts));
  } catch (_) {}
  render();
}

// MARK: - App Launch & Mirror Actions
async function handleLaunchApp(pkg: string, appName: string, iconSrc?: string) {
  showAppLaunchHUD(appName, iconSrc);

  const invoke = await getInvoke();
  if (!invoke) return;

  try {
    handleStartMirror(pkg, appName, iconSrc);
  } catch (err) {
    console.error('App launch failed:', err);
  }
}

async function handleStartMirror(targetPackage?: string, appTitle?: string, iconSrc?: string) {
  const invoke = await getInvoke();
  if (!invoke) {
    mirrorStatusMsg = 'Desktop engine bridge unavailable';
    render();
    return;
  }

  mirrorStatusMsg = targetPackage
    ? `Launching ${appTitle || targetPackage} in hardware stream...`
    : 'Starting screen mirror session...';
  render();

  try {
    const useMultiWindow = Boolean(targetPackage && (isVirtualDisplayActive || isFavAppsMultiWindowActive));

    let resolvedIconBase64: string | null = null;
    if (iconSrc) {
      resolvedIconBase64 = cleanIconUrl(iconSrc).replace(/^data:image\/[^;]+;base64,/, '');
    } else if (targetPackage) {
      const match = phoneApps.find(a => a.package === targetPackage);
      if (match?.icon_base64) {
        resolvedIconBase64 = cleanIconUrl(match.icon_base64).replace(/^data:image\/[^;]+;base64,/, '');
      }
    }

    const res = await invoke('start_screen_mirror', {
      package: targetPackage || null,
      appTitle: appTitle || null,
      fps: 60,
      maxSize: useMultiWindow ? 1200 : 1920,
      newDisplay: useMultiWindow,
      turnScreenOff: isScreenOffModeActive && !useMultiWindow,
      audio: isAudioActive,
      alwaysOnTop: isAlwaysOnTopActive,
      borderless: isPhoneFrameActive,
      iconBase64: resolvedIconBase64,
    });
    mirrorStatusMsg = res;
    setTimeout(async () => {
      const inv = await getInvoke();
      if (inv) {
        if (isDockNavActive) inv('sync_mirror_dock', { visible: true });
      }
    }, 800);
  } catch (err: any) {
    mirrorStatusMsg = `Notice: ${err}`;
  }
  render();
}

// MARK: - Telephony & Calls Handlers
function handleDialDigit(digit: string) {
  dialerInputNumber += digit;
  const inputEl = document.getElementById('input-dialer-number') as HTMLInputElement;
  if (inputEl) {
    inputEl.value = dialerInputNumber;
  }
}

function handleDialBackspace() {
  dialerInputNumber = dialerInputNumber.slice(0, -1);
  const inputEl = document.getElementById('input-dialer-number') as HTMLInputElement;
  if (inputEl) {
    inputEl.value = dialerInputNumber;
  }
}

function handleDialClear() {
  dialerInputNumber = '';
  const inputEl = document.getElementById('input-dialer-number') as HTMLInputElement;
  if (inputEl) {
    inputEl.value = '';
    inputEl.focus();
  }
}

async function handleToggleCallAudioRoute(route?: 'mac' | 'phone') {
  callAudioRoute = route || (callAudioRoute === 'mac' ? 'phone' : 'mac');
  try {
    localStorage.setItem('idroid_call_audio_route', callAudioRoute);
  } catch (_) {}
  render();
  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('set_call_audio_route', { route: callAudioRoute });
    } catch (e) {
      console.warn('set_call_audio_route error:', e);
    }
  }
}

async function handleStartCall(targetNumber?: string) {
  const inputEl = document.getElementById('input-dialer-number') as HTMLInputElement;
  if (inputEl && targetNumber === undefined) {
    dialerInputNumber = inputEl.value;
  }
  const rawNum = (targetNumber !== undefined ? targetNumber : dialerInputNumber).trim();
  if (!rawNum) return;

  activeCallTarget = rawNum;
  isCallActive = true;
  callDurationSec = 0;
  isCallMuted = false;
  isCallRecording = false;
  isAddPersonModalOpen = false;
  conferenceParticipants = [];

  // Find if contact name exists in favouriteContacts
  const cleanTarget = activeCallTarget.replace(/[\s-]/g, '');
  const matchedContact = favouriteContacts.find(
    (c) => c.number.replace(/[\s-]/g, '') === cleanTarget
  );
  const contactName = matchedContact ? matchedContact.name : activeCallTarget;
  activeCallTargetName = contactName;

  const now = new Date();
  const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  // Add to persistent call history
  const newRecord: CallRecord = {
    id: `call-${Date.now()}`,
    name: contactName,
    number: activeCallTarget,
    call_type: 'outgoing',
    time: timeStr,
    duration: '0s',
  };

  callHistory = [newRecord, ...callHistory.filter((c) => c.id !== newRecord.id).slice(0, 49)];
  try {
    localStorage.setItem('idroid_call_history', JSON.stringify(callHistory));
  } catch (_) {}

  if (callDurationTimer) clearInterval(callDurationTimer);
  callDurationTimer = setInterval(() => {
    callDurationSec++;
    const timerEl = document.getElementById('hud-call-timer') || document.getElementById('active-call-timer');
    if (timerEl) {
      const mins = Math.floor(callDurationSec / 60)
        .toString()
        .padStart(2, '0');
      const secs = (callDurationSec % 60).toString().padStart(2, '0');
      timerEl.textContent = `${mins}:${secs}`;
    }
  }, 1000);

  render();

  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('make_phone_call', { number: activeCallTarget });
      await invoke('set_call_audio_route', { route: callAudioRoute });
    } catch (err: any) {
      console.error('Call error:', err);
    }
  }
}

async function handleAcceptIncomingCall(number?: string, name?: string) {
  const targetNum = number || (incomingCall ? incomingCall.number : 'Unknown');
  const targetName = name || (incomingCall ? incomingCall.name : targetNum);
  incomingCall = null;
  activeCallTarget = targetNum;
  activeCallTargetName = targetName;
  isCallActive = true;
  callDurationSec = 0;
  isCallMuted = false;
  isCallRecording = false;
  isAddPersonModalOpen = false;
  conferenceParticipants = [];

  const now = new Date();
  const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  const newRecord: CallRecord = {
    id: `call-${Date.now()}`,
    name: targetName,
    number: targetNum,
    call_type: 'incoming',
    time: timeStr,
    duration: '0s',
  };

  callHistory = [newRecord, ...callHistory.filter((c) => c.id !== newRecord.id).slice(0, 49)];
  try {
    localStorage.setItem('idroid_call_history', JSON.stringify(callHistory));
  } catch (_) {}

  if (callDurationTimer) clearInterval(callDurationTimer);
  callDurationTimer = setInterval(() => {
    callDurationSec++;
    const timerEl = document.getElementById('hud-call-timer');
    if (timerEl) {
      const mins = Math.floor(callDurationSec / 60).toString().padStart(2, '0');
      const secs = (callDurationSec % 60).toString().padStart(2, '0');
      timerEl.textContent = `${mins}:${secs}`;
    }
  }, 1000);

  // Default to Mac audio route when accepted from Mac
  callAudioRoute = 'mac';
  try {
    localStorage.setItem('idroid_call_audio_route', 'mac');
  } catch (_) {}

  render();

  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('answer_phone_call');
      await invoke('set_call_audio_route', { route: 'mac' });
    } catch (e) {
      console.warn('answer_phone_call error:', e);
    }
  }
}

async function handleDeclineIncomingCall() {
  if (incomingCall) {
    const now = new Date();
    const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const missedRecord: CallRecord = {
      id: `call-${Date.now()}`,
      name: incomingCall.name || incomingCall.number,
      number: incomingCall.number,
      call_type: 'missed',
      time: timeStr,
      duration: 'Missed',
    };
    callHistory = [missedRecord, ...callHistory.filter((c) => c.id !== missedRecord.id).slice(0, 49)];
    try {
      localStorage.setItem('idroid_call_history', JSON.stringify(callHistory));
    } catch (_) {}
  }
  incomingCall = null;
  render();

  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('end_phone_call');
    } catch (e) {
      console.warn('end_phone_call error:', e);
    }
  }
}

async function handleToggleCallMute() {
  isCallMuted = !isCallMuted;
  render();
  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('toggle_call_mute', { muted: isCallMuted });
    } catch (e) {
      console.warn('toggle_call_mute error:', e);
    }
  }
}

async function handleToggleCallRecording() {
  isCallRecording = !isCallRecording;
  render();
  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('record_call_audio', { active: isCallRecording });
    } catch (e) {
      console.warn('record_call_audio error:', e);
    }
  }
}

function handleSimulateIncomingCall() {
  incomingCall = {
    name: 'Sarah Connor',
    number: '+1 (415) 890-2341',
  };
  render();
}

async function handleAddConferenceParticipant(num: string) {
  const clean = num.trim();
  if (!clean) return;
  conferenceParticipants.push(clean);
  isAddPersonModalOpen = false;
  render();
  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('make_phone_call', { number: clean });
    } catch (e) {
      console.warn('conference call error:', e);
    }
  }
}

async function handleEndCall() {
  isCallActive = false;
  isCallMuted = false;
  isCallRecording = false;
  isAddPersonModalOpen = false;
  conferenceParticipants = [];
  if (callDurationTimer) {
    clearInterval(callDurationTimer);
    callDurationTimer = null;
  }
  if (callHistory.length > 0 && (callHistory[0].number === activeCallTarget || callHistory[0].name === activeCallTargetName)) {
    const mins = Math.floor(callDurationSec / 60);
    const secs = callDurationSec % 60;
    callHistory[0].duration = `${mins}m ${secs}s`;
    try {
      localStorage.setItem('idroid_call_history', JSON.stringify(callHistory));
    } catch (_) {}
  }
  activeCallTarget = '';
  activeCallTargetName = '';
  callDurationSec = 0;
  render();

  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('end_phone_call');
    } catch (e) {
      console.error(e);
    }
  }
}

// MARK: - Notification Quick Reply
async function handleReplyNotification(notifId: string, inputId: string) {
  const inputEl = document.getElementById(inputId) as HTMLInputElement;
  if (!inputEl || !inputEl.value.trim()) return;

  const replyText = inputEl.value.trim();
  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('reply_notification', { id: notifId, replyText });
      inputEl.value = '';
      alert(`Reply sent: "${replyText}"`);
    } catch (err: any) {
      alert(`Reply failed: ${err}`);
    }
  }
}

// MARK: - Clipboard Paste to Phone
async function handlePasteToPhone() {
  if (!pushToPhoneInput.trim()) return;

  const invoke = await getInvoke();
  if (invoke) {
    try {
      await invoke('paste_to_phone', { text: pushToPhoneInput });
      alert('Pasted text directly to active phone input!');
      pushToPhoneInput = '';
      render();
    } catch (err: any) {
      alert(`Paste failed: ${err}`);
    }
  }
}

function updateLiveTelemetryUI() {
  const battLabel = document.getElementById('device-battery-label');
  if (battLabel) {
    if (device.status === 'Connected') {
      const connIcon = device.connectionType.includes('Wi-Fi')
        ? Icons.Wifi(11, 'var(--accent-solar)')
        : device.connectionType.includes('Remote') || device.connectionType.includes('Mesh')
        ? Icons.Globe(11, 'var(--purple-neon)')
        : Icons.Usb(11, 'var(--emerald-ion)');
      battLabel.innerHTML = `${connIcon} <span>${device.connectionType}</span> • ${Icons.BatteryCharging(11, 'var(--emerald-ion)')} <span>${device.battery}%</span>`;
    } else {
      battLabel.innerHTML = `<span style="color: var(--text-muted);">Click to pair wirelessly</span>`;
    }
  }

  const phoneBtBadge = document.getElementById('phone-bluetooth-badge');
  if (phoneBtBadge) {
    const isConn = !!device.bluetoothConnected;
    phoneBtBadge.style.background = isConn ? 'rgba(52, 211, 153, 0.12)' : 'rgba(56, 189, 248, 0.1)';
    phoneBtBadge.style.borderColor = isConn ? 'rgba(52, 211, 153, 0.28)' : 'rgba(56, 189, 248, 0.2)';
    phoneBtBadge.style.color = isConn ? '#34d399' : '#38bdf8';
    phoneBtBadge.innerHTML = `
      ${isConn ? Icons.BluetoothConnected(12, '#34d399') : Icons.Bluetooth(12, '#38bdf8')}
      <span style="overflow: hidden; text-overflow: ellipsis;">${device.bluetoothStatus || 'Bluetooth On'}</span>
    `;
  }

  const macBtBadge = document.getElementById('btn-mac-bluetooth-badge');
  if (macBtBadge) {
    const isConn = !!hostTelemetry.bluetooth_connected;
    macBtBadge.style.background = isConn ? 'rgba(52, 211, 153, 0.12)' : 'rgba(56, 189, 248, 0.1)';
    macBtBadge.style.borderColor = isConn ? 'rgba(52, 211, 153, 0.28)' : 'rgba(56, 189, 248, 0.2)';
    macBtBadge.style.color = isConn ? '#34d399' : '#38bdf8';
    macBtBadge.innerHTML = `
      ${isConn ? Icons.BluetoothConnected(12, '#34d399') : Icons.Bluetooth(12, '#38bdf8')}
      <span style="overflow: hidden; text-overflow: ellipsis;">${hostTelemetry.bluetooth_status || 'Bluetooth On'}</span>
    `;
  }
}

// MARK: - Main Render
function render() {
  const app = document.getElementById('app');
  if (!app) return;

  const isConnected = device.status === 'Connected';

  app.innerHTML = `
    <!-- Native Window Drag Handle (Click and drag to move window anywhere, double click to maximize) -->
    <header class="window-drag-bar" data-tauri-drag-region>
      <div style="flex: 1; height: 100%; pointer-events: none;" data-tauri-drag-region></div>
    </header>
    <div class="traffic-lights-cutout"></div>

    <!-- Luxury Obsidian Titanium Sidebar -->
    <aside class="sidebar ${isSidebarCollapsed ? 'collapsed' : ''}">
      <div class="brand-header">
        <div class="brand-block">
          <button class="brand-monogram ${isSidebarCollapsed ? 'collapsed-trigger' : ''}" id="btn-brand-monogram" title="${isSidebarCollapsed ? 'Expand sidebar (⌘\\)' : 'Idroid'}" aria-label="${isSidebarCollapsed ? 'Expand sidebar' : 'Idroid'}">
            <span class="monogram-text">ID</span>
            <span class="monogram-icon">${Icons.PanelLeftOpen(16, 'currentColor')}</span>
          </button>
          <div class="brand-title-wrap">
            <span class="brand-title">Idroid</span>
            <span class="brand-badge">Flagship</span>
          </div>
        </div>
        <div class="brand-actions">
          <button class="btn-luxury" id="btn-open-doctor-sidebar" title="Open Idroid Doctor & Help" style="padding: 2.5px 7px; font-size: 10.5px; border-radius: 6px; color: var(--accent-solar); border-color: rgba(255,110,50,0.3); display: flex; align-items: center; gap: 4px; cursor: pointer;">
            ${Icons.Doctor(12, 'var(--accent-solar)')}
            <span>Doctor</span>
          </button>
          <div class="latency-beacon" title="Connection Status">
            <div class="latency-dot" style="background: ${isConnected ? 'var(--emerald-ion)' : 'var(--text-muted)'};"></div>
            <span>${isConnected ? 'Online' : 'Offline'}</span>
          </div>
          <button class="sidebar-toggle-btn" id="btn-toggle-sidebar" title="Collapse sidebar (⌘\\)" aria-label="Collapse Sidebar">
            ${Icons.PanelLeftClose(15, 'currentColor')}
          </button>
        </div>
      </div>

      <!-- Connected Device Capsule (Interactive Link to Wireless Studio) -->
      <div class="device-capsule" id="btn-device-capsule" style="cursor: pointer;" title="${isSidebarCollapsed ? (isConnected ? `${device.name} • ${device.battery}%` : 'Pair device wirelessly') : 'Open Wireless & Remote Connection Studio'}">
        <div class="device-avatar" style="color: ${isConnected ? 'var(--accent-solar)' : 'var(--text-muted)'};">
          ${Icons.Phone(18, 'currentColor')}
        </div>
        <div class="device-details">
          <div class="device-name" style="color: ${isConnected ? 'var(--text-pure)' : 'var(--text-secondary)'};">
            ${isConnected ? device.name : 'No Device Connected'}
          </div>
          <div class="device-telemetry-line">
            <span id="device-battery-label" style="display:inline-flex; align-items:center; gap:4px;">
              ${
                isConnected
                  ? `${
                      device.connectionType.includes('Wi-Fi')
                        ? Icons.Wifi(11, 'var(--accent-solar)')
                        : device.connectionType.includes('Remote') || device.connectionType.includes('Mesh')
                        ? Icons.Globe(11, 'var(--purple-neon)')
                        : Icons.Usb(11, 'var(--emerald-ion)')
                    } <span>${device.connectionType}</span> • ${Icons.BatteryCharging(11, 'var(--emerald-ion)')} <span>${device.battery}%</span>`
                  : `<span style="color: var(--text-muted);">Click to pair wirelessly</span>`
              }
            </span>
          </div>
        </div>
      </div>

      <!-- Navigation List (Psychologically Ordered Hierarchy) -->
      <nav class="nav-list" style="margin-top: 8px;">
        ${renderNavItem('dashboard', Icons.Dashboard(16), 'Dashboard')}
        ${renderNavItem('mirror', Icons.Mirror(16), 'Screen Mirror')}
        ${renderNavItem('camera', Icons.Camera(16), 'Camera Stream')}
        ${renderNavItem('files', Icons.Files(16), 'Files')}
        ${renderNavItem('photos', Icons.Photos(16), 'Photos')}
        ${renderNavItem('calls', Icons.Calls(16), 'Calls & Audio')}
        ${renderNavItem('notifications', Icons.Notifications(16), 'Notifications')}
        ${renderNavItem('clipboard', Icons.Clipboard(16), 'Clipboard')}
      </nav>

      <!-- Bottom Pinned Settings -->
      ${renderNavItem('settings', Icons.Settings(16), 'Settings')}
    </aside>

    <!-- Main Content Canvas -->
    <main class="main-content">
      ${renderActiveWorkspace()}
    </main>

    ${renderWirelessModal()}
    ${renderIdroidDoctorModal()}
    ${renderQuickAppsModal()}
    ${renderQuickControlsModal()}
    ${renderLightboxModal()}
    ${renderAddFavouriteModal()}
    ${renderFloatingCallHUD()}
    ${renderIncomingCallBanner()}
  `;

  attachEventListeners();
  attachTactileCardPhysics('.app-tile');
}


function renderNavItem(key: string, iconSvg: string, label: string) {
  const activeClass = currentSection === key ? 'active' : '';
  const isCameraLive = key === 'camera' && cameraStreamStats && cameraStreamStats.active;
  return `
    <div class="nav-item ${activeClass}" data-section="${key}" title="${label}">
      <span class="nav-icon">${iconSvg}</span>
      <span class="nav-label" style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
        <span>${label}</span>
        ${isCameraLive ? '<span style="background: var(--emerald-ion); color: #000; font-size: 8.5px; font-weight: 800; padding: 1.5px 5px; border-radius: 6px; letter-spacing: 0.5px;">LIVE</span>' : ''}
      </span>
    </div>
  `;
}

// MARK: - Wireless & Remote Transport Modal
function renderWirelessModal() {
  if (!isWirelessModalOpen) return '';

  return `
    <div class="modal-backdrop" id="wireless-modal-backdrop">
      <div class="modal-window">
        <div class="modal-header">
          <div style="display: flex; align-items: center; gap: 10px;">
            <div style="width: 34px; height: 34px; border-radius: 10px; background: rgba(255, 110, 50, 0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center;">
              ${Icons.Wifi(18, 'currentColor')}
            </div>
            <div>
              <h2 style="font-size: 15px; font-weight: 700; color: #fff;">Wireless & Remote Studio</h2>
              <p style="font-size: 11px; color: var(--text-secondary); margin-top: 1px;">Connect over Local Wi-Fi or Cross-Network P2P Mesh</p>
            </div>
          </div>
          <button class="btn-icon" id="btn-close-wireless-modal" style="width: 28px; height: 28px; border-radius: 50%; border: 1px solid var(--border-subtle); background: rgba(255,255,255,0.03); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;">✕</button>
        </div>

        <div class="modal-tabs">
          <button class="modal-tab-btn ${wirelessModalTab === 'optical' ? 'active' : ''}" data-tab="optical">
            ${Icons.QrCode(13, 'currentColor')}
            <span>Consumer Optical QR</span>
          </button>
          <button class="modal-tab-btn ${wirelessModalTab === 'handover' ? 'active' : ''}" data-tab="handover">
            ${Icons.ArrowRightLeft(13, 'currentColor')}
            <span>1-Click Wi-Fi Handover</span>
          </button>
          <button class="modal-tab-btn ${wirelessModalTab === 'pairing' ? 'active' : ''}" data-tab="pairing">
            ${Icons.Cpu(13, 'currentColor')}
            <span>Developer Wireless Debugging</span>
          </button>
          <button class="modal-tab-btn ${wirelessModalTab === 'mesh' ? 'active' : ''}" data-tab="mesh">
            ${Icons.Globe(13, 'currentColor')}
            <span>Remote Mesh (5G / Remote)</span>
          </button>
        </div>

        <div class="modal-body">
          ${renderWirelessModalBody()}
        </div>
      </div>
    </div>
  `;
}

// MARK: - Idroid Doctor & Diagnostic Help Center Modal
function renderIdroidDoctorModal() {
  if (!isDoctorModalOpen) return '';

  const isConnected = device.status === 'Connected';

  const faqs = [
    {
      id: 'hotspot_vs_wifi',
      title: 'Is Phone Hotspot proven to be better than Home Wi-Fi?',
      badge: 'Proven & Tested',
      summary: 'Yes! Personal Hotspot provides a direct 1-hop wireless link with <2ms latency and zero router interference.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <p style="margin-bottom: 8px;"><strong style="color: #fff;">1. Single Direct Wireless Hop (&lt;2ms):</strong> On Personal Hotspot, your Mac links directly to your phone's Wi-Fi antenna without routing through an external box. Over home Wi-Fi, data travels Phone &rarr; Router &rarr; Mac (2 wireless hops + router queue = 30-80ms lag).</p>
          <p style="margin-bottom: 8px;"><strong style="color: #fff;">2. Zero Router Blockades / AP Isolation:</strong> Many home routers (Airtel, Jio, Comcast, office networks) turn on <em>AP/Client Isolation</em>, which blocks wireless devices from talking directly to each other. Hotspot eliminates this entirely.</p>
          <p style="margin-bottom: 8px;"><strong style="color: #fff;">3. Dedicated 1200+ Mbps Pipeline:</strong> Modern 5GHz phone hotspots provide dedicated high-bandwidth throughput without competing with TVs, consoles, or family downloads.</p>
          <p><strong style="color: #fff;">4. Industry Proven Architecture:</strong> Apple AirDrop, Apple Sidecar, and Samsung Quick Share all spin up a direct Wi-Fi hotspot connection for this exact reason!</p>
        </div>
      `
    },
    {
      id: 'hotspot_how_to',
      title: 'How do I turn on Hotspot and connect my Mac in 10 seconds?',
      badge: 'Quick Guide',
      summary: 'Turn on Personal Hotspot on your phone, then pick your phone in the Mac Wi-Fi menu.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <ol style="padding-left: 18px; margin: 0; display: flex; flex-direction: column; gap: 6px;">
            <li>On your Android phone, swipe down from the top and tap <strong>Mobile Hotspot / Personal Hotspot</strong>.</li>
            <li>On your Mac, click the <strong>Wi-Fi icon</strong> in the top menu bar.</li>
            <li>Select your phone's Wi-Fi name and connect.</li>
            <li>Open the Idroid phone app and tap <strong>Scan to Connect</strong>. It pairs instantly!</li>
          </ol>
        </div>
      `
    },
    {
      id: 'data_plan_safety',
      title: 'Will using Phone Hotspot consume my mobile data quota?',
      badge: 'Data Free',
      summary: 'No! All device mirroring, file sharing, and clipboard sync stay 100% local over the Wi-Fi radio.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <p>Idroid communicates entirely peer-to-peer over your local radio link. <strong>Zero bytes of local data</strong> leave your room or get charged to your cellular carrier.</p>
          <p style="margin-top: 6px;">You can even turn mobile cellular data OFF on your phone while leaving the Hotspot Wi-Fi network ON, and Idroid will work at full speed.</p>
        </div>
      `
    },
    {
      id: 'router_ap_isolation',
      title: 'Why does Home Wi-Fi sometimes not connect or get stuck scanning?',
      badge: 'Troubleshooting',
      summary: 'Your home router may have "Client Isolation" or "AP Isolation" enabled.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <p>Many Wi-Fi routers include a security setting called <em>Client Isolation</em> or <em>AP Isolation</em>. While this protects public networks, it prevents your phone and Mac from discovering each other on local port 53100.</p>
          <div style="margin-top: 8px; padding: 10px; background: rgba(255,110,50,0.08); border-radius: 8px; border: 1px solid rgba(255,110,50,0.2);">
            <strong style="color: var(--accent-solar);">Quick Solution:</strong> Connect via <strong>Personal Hotspot</strong> or plug in a <strong>USB cable</strong> for 1 second.
          </div>
        </div>
      `
    },
    {
      id: 'camera_scanner_tips',
      title: 'Camera scanner tips: QR code not scanning or blurry',
      badge: 'Scanner Tips',
      summary: 'Tips to get a crisp scan in under 1 second.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <ul style="padding-left: 18px; margin: 0; display: flex; flex-direction: column; gap: 6px;">
            <li>Hold your phone roughly <strong>8 to 12 inches (20-30 cm)</strong> away from the Mac screen.</li>
            <li>Increase your Mac screen brightness slightly if the room is dim.</li>
            <li>If the QR code expired, click <strong>"Refresh QR"</strong> on your Mac.</li>
            <li>Ensure camera permission was granted to the Idroid Companion app.</li>
          </ul>
        </div>
      `
    },
    {
      id: 'usb_cable_fallback',
      title: 'Can I just use a USB cable instead?',
      badge: 'Zero Setup',
      summary: 'Yes! USB works 100% offline with zero latency and zero setup.',
      content: `
        <div style="font-size: 12.5px; line-height: 1.6; color: var(--text-secondary);">
          <p>Simply connect your phone to your Mac using a USB-C cable. Idroid will detect it instantly with <strong>zero latency</strong>.</p>
        </div>
      `
    }
  ];

  const query = doctorSearchQuery.trim().toLowerCase();
  const filteredFaqs = query
    ? faqs.filter(f => f.title.toLowerCase().includes(query) || f.summary.toLowerCase().includes(query) || f.content.toLowerCase().includes(query))
    : faqs;

  return `
    <div class="modal-backdrop" id="idroid-doctor-backdrop" style="position: fixed; inset: 0; background: rgba(0,0,0,0.75); backdrop-filter: blur(12px); display: flex; align-items: center; justify-content: center; z-index: 10000; padding: 20px;">
      <div class="modal-window" style="width: 100%; max-width: 680px; max-height: 85vh; background: #121316; border: 1px solid rgba(255,255,255,0.1); border-radius: 20px; box-shadow: 0 24px 60px rgba(0,0,0,0.8); display: flex; flex-direction: column; overflow: hidden;">
        
        <!-- Modal Header -->
        <div style="padding: 20px 24px; border-bottom: 1px solid rgba(255,255,255,0.06); display: flex; align-items: center; justify-content: space-between;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <div style="width: 38px; height: 38px; border-radius: 12px; background: rgba(255,110,50,0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center;">
              ${Icons.Doctor(20, 'var(--accent-solar)')}
            </div>
            <div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <h2 style="font-size: 16px; font-weight: 700; color: #fff; margin: 0;">Idroid Doctor & Help Center</h2>
                <span style="font-size: 10px; font-weight: 700; background: rgba(255,110,50,0.2); color: var(--accent-solar); padding: 2px 7px; border-radius: 6px;">DIAGNOSTICS</span>
              </div>
              <p style="font-size: 11.5px; color: var(--text-secondary); margin: 2px 0 0 0;">Connection health, speed optimization & friendly troubleshooting</p>
            </div>
          </div>
          <button class="btn-icon" id="btn-close-doctor-modal" style="width: 30px; height: 30px; border-radius: 50%; border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04); color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;">✕</button>
        </div>

        <!-- Search Bar -->
        <div style="padding: 14px 24px; border-bottom: 1px solid rgba(255,255,255,0.06); background: rgba(255,255,255,0.01);">
          <div style="display: flex; align-items: center; gap: 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; padding: 8px 14px;">
            <span style="color: var(--text-secondary);">${Icons.Search(14, 'currentColor')}</span>
            <input 
              type="text" 
              id="doctor-search-input" 
              placeholder="Search issues, e.g. hotspot, router, scanning, data..." 
              value="${doctorSearchQuery}"
              style="background: transparent; border: none; outline: none; color: #fff; font-size: 12.5px; width: 100%;"
            />
            ${doctorSearchQuery ? `<button id="btn-clear-doctor-search" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 12px;">✕</button>` : ''}
          </div>
        </div>

        <!-- Scrollable Modal Content -->
        <div style="flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 20px;">
          
          <!-- Live Telemetry Health Bar -->
          <div>
            <div style="font-size: 11px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px;">Live Connection Health</div>
            <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;">
              <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 10px 12px;">
                <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                  <span style="width: 7px; height: 7px; border-radius: 50%; background: var(--emerald-ion);"></span>
                  <span style="font-size: 11px; font-weight: 600; color: #fff;">Mac Network</span>
                </div>
                <div style="font-size: 10.5px; color: var(--text-secondary);">${pairingPayload?.ip ? 'Online (' + pairingPayload.ip + ')' : 'Ready'}</div>
              </div>

              <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 10px 12px;">
                <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                  <span style="width: 7px; height: 7px; border-radius: 50%; background: var(--emerald-ion);"></span>
                  <span style="font-size: 11px; font-weight: 600; color: #fff;">Pairing Port</span>
                </div>
                <div style="font-size: 10.5px; color: var(--text-secondary);">Listening on Port 53100</div>
              </div>

              <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 10px 12px;">
                <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                  <span style="width: 7px; height: 7px; border-radius: 50%; background: ${isConnected ? 'var(--emerald-ion)' : 'var(--accent-solar)'};"></span>
                  <span style="font-size: 11px; font-weight: 600; color: #fff;">Companion State</span>
                </div>
                <div style="font-size: 10.5px; color: var(--text-secondary);">${isConnected ? 'Phone Connected' : 'Waiting for scan'}</div>
              </div>
            </div>
          </div>

          <!-- Spotlight: Hotspot Advantage Card -->
          <div style="background: linear-gradient(135deg, rgba(255,110,50,0.12), rgba(255,110,50,0.03)); border: 1.5px solid rgba(255,110,50,0.3); border-radius: 14px; padding: 16px;">
            <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 8px;">
              <div style="width: 28px; height: 28px; border-radius: 8px; background: var(--accent-solar); color: #000; display: flex; align-items: center; justify-content: center;">
                ${Icons.Zap(16, '#000')}
              </div>
              <div>
                <span style="font-size: 13px; font-weight: 700; color: #fff;">Why Personal Hotspot is Recommended for Best Experience</span>
              </div>
            </div>
            <p style="font-size: 12px; color: var(--text-secondary); line-height: 1.5; margin: 0 0 10px 0;">
              Connecting your Mac directly to your phone's Personal Hotspot provides a <strong>single wireless hop with &lt;2ms latency</strong>. It bypasses home router congestion, firewall rules, and AP isolation completely. And it consumes <strong>zero cellular mobile data</strong>.
            </p>
            <div style="display: flex; gap: 8px; flex-wrap: wrap;">
              <span style="font-size: 10.5px; background: rgba(255,255,255,0.06); padding: 3px 8px; border-radius: 6px; color: #fff;">⚡ &lt;2ms Latency</span>
              <span style="font-size: 10.5px; background: rgba(255,255,255,0.06); padding: 3px 8px; border-radius: 6px; color: #fff;">🛡️ Zero Router Blocks</span>
              <span style="font-size: 10.5px; background: rgba(255,255,255,0.06); padding: 3px 8px; border-radius: 6px; color: #fff;">📦 0 Data Plan Cost</span>
              <span style="font-size: 10.5px; background: rgba(255,255,255,0.06); padding: 3px 8px; border-radius: 6px; color: #fff;">🚀 1200 Mbps Wi-Fi 6</span>
            </div>
          </div>

          <!-- FAQ Accordions -->
          <div>
            <div style="font-size: 11px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 10px;">
              ${filteredFaqs.length} Troubleshooting Guides & Answers
            </div>
            <div style="display: flex; flex-direction: column; gap: 10px;">
              ${filteredFaqs.map(faq => {
                const isExpanded = doctorExpandedFaq === faq.id;
                return `
                  <div class="doctor-topic-card" data-faq-id="${faq.id}" style="background: rgba(255,255,255,0.02); border: 1px solid ${isExpanded ? 'rgba(255,110,50,0.3)' : 'var(--border-subtle)'}; border-radius: 12px; overflow: hidden; cursor: pointer; transition: all 0.2s ease;">
                    <div style="padding: 14px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px;">
                      <div style="display: flex; align-items: center; gap: 10px;">
                        <span style="font-size: 10px; font-weight: 700; padding: 2px 7px; border-radius: 5px; background: ${isExpanded ? 'rgba(255,110,50,0.2)' : 'rgba(255,255,255,0.06)'}; color: ${isExpanded ? 'var(--accent-solar)' : 'var(--text-secondary)'};">
                          ${faq.badge}
                        </span>
                        <span style="font-size: 13px; font-weight: 600; color: #fff;">${faq.title}</span>
                      </div>
                      <span style="color: var(--text-muted); font-size: 12px;">${isExpanded ? '▲' : '▼'}</span>
                    </div>
                    ${isExpanded ? `
                      <div style="padding: 0 16px 14px 16px; border-top: 1px solid rgba(255,255,255,0.04); margin-top: 2px; padding-top: 10px;">
                        ${faq.content}
                      </div>
                    ` : `
                      <div style="padding: 0 16px 12px 16px; font-size: 11.5px; color: var(--text-muted);">
                        ${faq.summary}
                      </div>
                    `}
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        </div>

        <!-- Modal Footer -->
        <div style="padding: 14px 24px; border-top: 1px solid rgba(255,255,255,0.06); display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.01);">
          <div style="font-size: 11.5px; color: var(--text-secondary);">
            Need instant connection? You can also connect via USB-C cable anytime.
          </div>
          <button class="btn-luxury" id="btn-close-doctor-modal-footer" style="padding: 6px 16px; font-size: 12px; cursor: pointer;">
            Got it, thanks!
          </button>
        </div>
      </div>
    </div>
  `;
}

function renderWirelessModalBody() {
  if (wirelessModalTab === 'optical') {
    return renderConsumerQrPairingView();
  }

  if (wirelessModalTab === 'handover') {
    return `
      <div style="display: flex; flex-direction: column; gap: 14px;">
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 12px; padding: 14px;">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <span style="font-size: 12px; font-weight: 600; color: #fff;">Hardware Connection Status</span>
            <span style="background: ${device.status === 'Connected' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(255,255,255,0.05)'}; color: ${device.status === 'Connected' ? 'var(--emerald-ion)' : 'var(--text-muted)'}; font-size: 11px; padding: 3px 8px; border-radius: 6px; font-weight: 600;">
              ${device.connectionType}
            </span>
          </div>
          <p style="font-size: 12px; color: var(--text-secondary); margin-top: 8px; line-height: 1.5;">
            ${
              device.status === 'Connected'
                ? `Phone is currently active over <b>${device.connectionType}</b>. Clicking Handover switches the device ADB daemon to TCP/IP port 5555, auto-detects your phone's Wi-Fi address, and establishes the wireless link. Once complete, you can safely unplug any cables!`
                : 'Pair over Wi-Fi or local network. Idroid connects on-the-air with zero cables required.'
            }
          </p>
        </div>

        ${
          wirelessStatusMsg
            ? `<div style="font-size: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(255,110,50,0.1); border: 1px solid rgba(255,110,50,0.3); color: #fff; line-height: 1.4;">${wirelessStatusMsg}</div>`
            : ''
        }

        <div style="display: flex; gap: 10px; align-items: center; margin-top: 4px;">
          <button class="btn-solar" id="btn-trigger-handover" ${isWirelessConnecting || device.status !== 'Connected' ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''}>
            ${Icons.Wifi(15, '#fff')}
            <span>${isWirelessConnecting ? 'Configuring Wireless Transport...' : 'Enable Wi-Fi Mode & Handover'}</span>
          </button>
          ${
            device.connectionType.includes('Wi-Fi') || device.connectionType.includes('Remote')
              ? `<button class="btn-luxury" id="btn-disconnect-wireless" style="color: #f87171;">Disconnect Wireless</button>`
              : ''
          }
        </div>
      </div>
    `;
  }

  if (wirelessModalTab === 'pairing') {
    return `
      <div style="display: flex; flex-direction: column; gap: 14px;">
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 12px; padding: 14px;">
          <div style="font-size: 11px; font-weight: 700; color: var(--accent-solar); margin-bottom: 4px;">PURE WIRELESS PAIRING (NO USB CABLE NEEDED)</div>
          <p style="font-size: 12px; color: var(--text-secondary); line-height: 1.5;">
            1. Open phone <b>Settings &gt; Developer Options &gt; Wireless Debugging</b>.<br/>
            2. Tap <b>Pair device with pairing code</b>.<br/>
            3. Enter the IP, port, and 6-digit Wi-Fi pairing code displayed on your phone screen.
          </p>
        </div>

        <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 10px;">
          <div>
            <label style="font-size: 11px; color: var(--text-secondary); font-weight: 600;">PHONE IP ADDRESS</label>
            <input id="input-pair-ip" class="input-luxury" placeholder="192.168.1.xxx" value="${wirelessFormIp}" style="margin-top: 4px; width: 100%;" />
          </div>
          <div>
            <label style="font-size: 11px; color: var(--text-secondary); font-weight: 600;">PAIRING PORT</label>
            <input id="input-pair-port" class="input-luxury" placeholder="e.g. 38491" value="${wirelessFormPairPort}" style="margin-top: 4px; width: 100%;" />
          </div>
        </div>

        <div>
          <label style="font-size: 11px; color: var(--text-secondary); font-weight: 600;">6-DIGIT PAIRING CODE</label>
          <input id="input-pair-code" class="input-luxury" placeholder="e.g. 482910" value="${wirelessFormPairCode}" style="margin-top: 4px; width: 100%; letter-spacing: 2px; font-weight: 700;" />
        </div>

        ${
          wirelessStatusMsg
            ? `<div style="font-size: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(255,110,50,0.1); border: 1px solid rgba(255,110,50,0.3); color: #fff;">${wirelessStatusMsg}</div>`
            : ''
        }

        <button class="btn-solar" id="btn-trigger-pair" ${isWirelessConnecting ? 'disabled style="opacity: 0.5;"' : ''} style="align-self: flex-start; margin-top: 4px;">
          ${Icons.Check(14, '#fff')}
          <span>${isWirelessConnecting ? 'Pairing Device...' : 'Pair & Connect Wirelessly'}</span>
        </button>
      </div>
    `;
  }

  // Cross-Network / Remote Mesh
  return `
    <div style="display: flex; flex-direction: column; gap: 14px;">
      <div style="background: rgba(192, 132, 252, 0.05); border: 1px solid rgba(192, 132, 252, 0.2); border-radius: 12px; padding: 14px;">
        <div style="display: flex; align-items: center; gap: 8px; color: var(--purple-neon); font-size: 12px; font-weight: 700;">
          ${Icons.ShieldCheck(16, 'currentColor')}
          <span>CROSS-NETWORK P2P MESH ARCHITECTURE</span>
        </div>
        <p style="font-size: 12px; color: var(--text-secondary); margin-top: 6px; line-height: 1.5;">
          When your phone is on <b>5G/LTE Cellular</b> or an external Wi-Fi, direct incoming connections are blocked by Carrier-Grade NAT (CGNAT). Idroid connects via encrypted <b>WireGuard P2P Mesh</b> (Tailscale / Nebula) overlay, punching through firewalls with zero port-forwarding.
        </p>
      </div>

      <div style="display: grid; grid-template-columns: 3fr 1fr; gap: 10px;">
        <div>
          <label style="font-size: 11px; color: var(--text-secondary); font-weight: 600;">REMOTE MESH OVERLAY IP (TAILSCALE / NEBULA)</label>
          <input id="input-mesh-ip" class="input-luxury" placeholder="100.64.x.x or Remote WAN IP" value="${wirelessFormMeshIp}" style="margin-top: 4px; width: 100%;" />
        </div>
        <div>
          <label style="font-size: 11px; color: var(--text-secondary); font-weight: 600;">PORT</label>
          <input id="input-mesh-port" class="input-luxury" placeholder="5555" value="${wirelessFormMeshPort}" style="margin-top: 4px; width: 100%;" />
        </div>
      </div>

      <div style="display: flex; align-items: center; gap: 8px; font-size: 11.5px; color: var(--text-muted);">
        ${Icons.Lock(12, 'var(--emerald-ion)')}
        <span>End-to-end ChaCha20-Poly1305 encrypted • WebRTC / WireGuard data plane</span>
      </div>

      ${
        wirelessStatusMsg
          ? `<div style="font-size: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(255,110,50,0.1); border: 1px solid rgba(255,110,50,0.3); color: #fff;">${wirelessStatusMsg}</div>`
          : ''
      }

      <button class="btn-solar" id="btn-trigger-mesh-connect" ${isWirelessConnecting ? 'disabled style="opacity: 0.5;"' : ''} style="align-self: flex-start; margin-top: 4px; background: linear-gradient(135deg, #a855f7, #7c3aed);">
        ${Icons.Globe(14, '#fff')}
        <span>${isWirelessConnecting ? 'Establishing Encrypted Tunnel...' : 'Connect Remote Mesh'}</span>
      </button>
    </div>
  `;
}

function renderActiveWorkspace() {
  switch (currentSection) {
    case 'dashboard':
      return renderDashboard();
    case 'mirror':
      return renderMirrorStudio();
    case 'camera':
      return renderCameraStudio();
    case 'calls':
      return renderCallsView();
    case 'notifications':
      return renderNotificationsView();
    case 'clipboard':
      return renderClipboardView();
    case 'files':
      return renderFilesView();
    case 'photos':
      return renderPhotosView();
    case 'messages':
      return renderMessagesView();
    case 'settings':
      return renderSettingsView();
    default:
      return renderDashboard();
  }
}

function renderCameraStudio() {
  const isStreaming = Boolean(cameraStreamStats && cameraStreamStats.active);
  const fps = cameraStreamStats?.fps || 0;
  const bitrate = cameraStreamStats?.bitrate_mbps ? cameraStreamStats.bitrate_mbps.toFixed(1) : '0.0';
  const latency = cameraStreamStats?.latency_ms ? cameraStreamStats.latency_ms.toFixed(1) : '1.2';
  const res = cameraStreamStats?.resolution || '1920x1080 Full HD';
  const totalFrames = cameraStreamStats?.total_frames || 0;

  return `
    <div class="workspace-view animate-fade-in" style="padding: 24px; max-width: 980px; margin: 0 auto;">
      <!-- Header -->
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px;">
        <div>
          <div style="display: flex; align-items: center; gap: 10px;">
            <h1 style="font-size: 22px; font-weight: 800; color: #fff; margin: 0;">Continuity Camera Studio</h1>
            <span style="background: ${isStreaming ? 'var(--emerald-ion)' : 'rgba(255,255,255,0.1)'}; color: ${isStreaming ? '#000' : 'var(--text-muted)'}; font-size: 10.5px; font-weight: 800; padding: 3px 8px; border-radius: 6px; letter-spacing: 0.5px;">
              ${isStreaming ? '● LIVE STREAMING' : 'STANDBY'}
            </span>
          </div>
          <p style="font-size: 12.5px; color: var(--text-secondary); margin: 4px 0 0 0;">
            Real-time hardware-accelerated H.264 camera stream from ${device.name || 'your phone'} with zero cloud interception.
          </p>
        </div>
        <div style="display: flex; gap: 10px;">
          ${isStreaming ? `
            <button class="btn-luxury" id="btn-reopen-camera-player" style="background: var(--accent-solar); color: #000; font-weight: 700; border-radius: 10px; padding: 7px 14px; font-size: 12px; display: flex; align-items: center; gap: 6px; cursor: pointer;">
              ${Icons.Camera(14, '#000')}
              <span>Bring Video Window to Front</span>
            </button>
          ` : `
            <button class="btn-luxury" id="btn-mirror-camera-app" style="background: rgba(255,110,50,0.15); color: var(--accent-solar); border: 1px solid rgba(255,110,50,0.3); font-weight: 600; border-radius: 10px; padding: 7px 14px; font-size: 12px; display: flex; align-items: center; gap: 6px; cursor: pointer;">
              ${Icons.Camera(14, 'var(--accent-solar)')}
              <span>Launch Phone Camera</span>
            </button>
          `}
        </div>
      </div>

      <!-- Live Metrics Grid -->
      <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px;">
        <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 700;">Frame Rate</div>
          <div id="camera-stat-fps" style="font-size: 24px; font-weight: 800; color: ${isStreaming ? 'var(--emerald-ion)' : 'var(--text-muted)'}; margin-top: 4px;">
            ${isStreaming ? `${fps} FPS` : '60 FPS Target'}
          </div>
          <div style="font-size: 10.5px; color: var(--text-secondary); margin-top: 2px;">Hardware AVC 60-120 FPS</div>
        </div>

        <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 700;">Pipeline Latency</div>
          <div id="camera-stat-latency" style="font-size: 24px; font-weight: 800; color: var(--accent-solar); margin-top: 4px;">
            ${isStreaming ? `${latency} ms` : '< 2 ms'}
          </div>
          <div style="font-size: 10.5px; color: var(--text-secondary); margin-top: 2px;">Direct 1-Hop Transport</div>
        </div>

        <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 700;">Stream Bitrate</div>
          <div id="camera-stat-bitrate" style="font-size: 24px; font-weight: 800; color: var(--purple-neon); margin-top: 4px;">
            ${isStreaming ? `${bitrate} Mbps` : '5.2 Mbps'}
          </div>
          <div style="font-size: 10.5px; color: var(--text-secondary); margin-top: 2px;">H.264 High Profile CBR</div>
        </div>

        <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; font-weight: 700;">Resolution</div>
          <div style="font-size: 24px; font-weight: 800; color: #fff; margin-top: 4px;">
            ${res}
          </div>
          <div style="font-size: 10.5px; color: var(--text-secondary); margin-top: 2px;">${totalFrames > 0 ? `${totalFrames} frames delivered` : 'Native 1080p Optics'}</div>
        </div>
      </div>

      <!-- Hero Viewfinder Surface -->
      <div style="background: #000; border: 1.5px solid ${isStreaming ? 'rgba(0, 230, 153, 0.4)' : 'var(--border-subtle)'}; border-radius: 20px; padding: 36px 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 280px; text-align: center; position: relative;">
        ${isStreaming ? `
          <div style="width: 56px; height: 56px; border-radius: 50%; background: rgba(0, 230, 153, 0.15); display: flex; align-items: center; justify-content: center; color: var(--emerald-ion); margin-bottom: 14px;">
            ${Icons.Camera(28, 'currentColor')}
          </div>
          <div style="font-size: 18px; font-weight: 700; color: #fff; margin-bottom: 6px;">Camera is Streaming Live to macOS</div>
          <div style="font-size: 13px; color: var(--text-secondary); max-width: 480px; line-height: 1.5; margin-bottom: 18px;">
            Hardware-accelerated low-latency window is active at <strong>${fps} FPS</strong>. Works seamlessly in FaceTime, Zoom, OBS Studio, and Google Meet.
          </div>
          <div style="display: flex; gap: 10px;">
            <button class="btn-luxury" id="btn-reopen-camera-player-hero" style="background: var(--accent-solar); color: #000; font-weight: 700; border-radius: 10px; padding: 9px 18px; font-size: 13px; display: flex; align-items: center; gap: 6px; cursor: pointer;">
              ${Icons.Camera(16, '#000')}
              <span>Bring Video Window to Front</span>
            </button>
          </div>
        ` : `
          <div style="width: 56px; height: 56px; border-radius: 50%; background: rgba(255, 110, 50, 0.12); display: flex; align-items: center; justify-content: center; color: var(--accent-solar); margin-bottom: 14px;">
            ${Icons.Camera(28, 'currentColor')}
          </div>
          <div style="font-size: 18px; font-weight: 700; color: #fff; margin-bottom: 6px;">Continuity Camera in Standby</div>
          <div style="font-size: 13px; color: var(--text-secondary); max-width: 460px; line-height: 1.5; margin-bottom: 20px;">
            On your connected Android phone, open Idroid &gt; tap <strong>Advanced</strong> at the bottom &gt; tap <strong>Start Wireless Camera</strong>.
          </div>
          <button class="btn-luxury" id="btn-mirror-camera-app-hero" style="background: rgba(255,255,255,0.06); color: #fff; border: 1px solid var(--border-subtle); border-radius: 10px; padding: 8px 16px; font-size: 12.5px; font-weight: 600; cursor: pointer; display: flex; align-items: center; gap: 6px;">
            ${Icons.Mirror(14, 'currentColor')}
            <span>Or Mirror Native Camera App Directly</span>
          </button>
        `}
      </div>

      <!-- Quick Guidance -->
      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 18px;">
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 12px; font-weight: 700; color: #fff; margin-bottom: 4px;">Ultra-Wide &amp; Telephoto</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4;">Switch between 0.6x UltraWide, 1.0x Primary, or Front Selfie directly on your phone screen.</div>
        </div>
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 12px; font-weight: 700; color: #fff; margin-bottom: 4px;">Zero Cellular Data</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4;">Streams locally over direct USB-C or Personal Hotspot / Wi-Fi with sub-millisecond lag.</div>
        </div>
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="font-size: 12px; font-weight: 700; color: #fff; margin-bottom: 4px;">OBS &amp; Streaming Ready</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4;">Add as Window Capture in OBS Studio or Streamlabs for broadcast-grade webcam streaming.</div>
        </div>
      </div>
    </div>
  `;
}

function renderConsumerQrPairingView() {
  return `
    <div style="display: flex; flex-direction: column; gap: 24px; margin-top: 10px;">
      <!-- 3 Connection Ways Cards -->
      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px;">
        <!-- Hotspot Option (Recommended) -->
        <div style="background: rgba(255,110,50,0.06); border: 1.5px solid rgba(255,110,50,0.35); border-radius: 14px; padding: 14px; position: relative;">
          <div style="position: absolute; top: -10px; right: 12px; background: var(--accent-solar); color: #000; font-size: 9.5px; font-weight: 800; padding: 2px 8px; border-radius: 10px; text-transform: uppercase; letter-spacing: 0.5px;">Recommended</div>
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
            <span style="color: var(--accent-solar);">${Icons.Zap(15, 'currentColor')}</span>
            <span style="font-size: 13px; font-weight: 700; color: #fff;">Personal Hotspot</span>
          </div>
          <p style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4; margin: 0;">
            Turn phone Hotspot on & connect Mac Wi-Fi. <strong>&lt;2ms response</strong>, 0 router isolation. Uses 0 cellular data.
          </p>
        </div>

        <!-- Home Wi-Fi Option -->
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
            <span style="color: var(--text-pure);">${Icons.Wifi(15, 'currentColor')}</span>
            <span style="font-size: 13px; font-weight: 700; color: #fff;">Home / Office Wi-Fi</span>
          </div>
          <p style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4; margin: 0;">
            Both Mac and phone on the same Wi-Fi. Note: some office/guest routers block peer devices.
          </p>
        </div>

        <!-- USB Cable Option -->
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 14px; padding: 14px;">
          <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
            <span style="color: var(--emerald-ion);">${Icons.Check(15, 'currentColor')}</span>
            <span style="font-size: 13px; font-weight: 700; color: #fff;">USB Cable Backup</span>
          </div>
          <p style="font-size: 11.5px; color: var(--text-secondary); line-height: 1.4; margin: 0;">
            Plug in a USB-C cable for instant fallback. Works 100% offline without any network needed.
          </p>
        </div>
      </div>

      <!-- Pairing & QR Scanning Section -->
      <div style="display: grid; grid-template-columns: 1.25fr 1fr; gap: 32px; align-items: center;">
        <!-- Left Column: Steps -->
        <div style="display: flex; flex-direction: column; gap: 16px;">
          <div style="display: flex; gap: 14px; align-items: flex-start;">
            <div style="width: 30px; height: 30px; border-radius: 8px; background: rgba(255,110,50,0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 12px; flex-shrink: 0;">1</div>
            <div>
              <div style="font-size: 14px; font-weight: 600; color: #fff;">Open Idroid on your Android Phone</div>
              <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px; line-height: 1.4;">
                No cables, root, or developer options needed.
              </div>
            </div>
          </div>

          <div style="display: flex; gap: 14px; align-items: flex-start;">
            <div style="width: 30px; height: 30px; border-radius: 8px; background: rgba(255,110,50,0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 12px; flex-shrink: 0;">2</div>
            <div>
              <div style="font-size: 14px; font-weight: 600; color: #fff;">Tap "Scan to Connect"</div>
              <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px; line-height: 1.4;">
                Point your phone camera at this QR code to connect instantly.
              </div>
            </div>
          </div>

          <div style="display: flex; gap: 14px; align-items: flex-start;">
            <div style="width: 30px; height: 30px; border-radius: 8px; background: rgba(255,110,50,0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 12px; flex-shrink: 0;">3</div>
            <div>
              <div style="font-size: 14px; font-weight: 600; color: #fff;">Tap "Allow" on Phone</div>
              <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px; line-height: 1.4;">
                The companion handshake links both devices immediately.
              </div>
            </div>
          </div>

          <!-- Idroid Doctor Callout Banner -->
          <div style="background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 12px; padding: 12px 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 4px;">
            <div style="display: flex; align-items: center; gap: 10px;">
              <div style="width: 32px; height: 32px; border-radius: 8px; background: rgba(255,110,50,0.15); color: var(--accent-solar); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
                ${Icons.Doctor(16, 'var(--accent-solar)')}
              </div>
              <div>
                <div style="font-size: 12px; font-weight: 600; color: #fff;">Having Trouble or Stuck Scanning?</div>
                <div style="font-size: 11px; color: var(--text-secondary);">Idroid Doctor diagnoses router blocks and speeds up pairing</div>
              </div>
            </div>
            <button class="btn-luxury" id="btn-open-doctor-pairing" style="padding: 5px 12px; font-size: 11px; color: var(--accent-solar); border-color: rgba(255,110,50,0.3); white-space: nowrap; cursor: pointer;">
              Open Doctor
            </button>
          </div>
        </div>

        <!-- Right Column: Crisp Dynamic QR Code Display -->
        <div style="display: flex; flex-direction: column; align-items: center; gap: 14px;">
          <div style="background: #ffffff; padding: 14px; border-radius: 20px; box-shadow: 0 14px 40px rgba(0,0,0,0.6), 0 0 28px rgba(255,110,50,0.25); display: flex; align-items: center; justify-content: center; position: relative;">
            ${pairingQrSvg || '<div style="width: 196px; height: 196px; display: flex; align-items: center; justify-content: center; color: #333; font-size: 12px;">Generating QR...</div>'}
          </div>

          <div style="display: flex; gap: 10px; align-items: center;">
            <button class="btn-luxury" id="btn-regenerate-pairing-qr" style="padding: 6px 14px; font-size: 11.5px;">
              ${Icons.Refresh(13, 'currentColor')}
              <span>Refresh QR</span>
            </button>
            <button class="btn-luxury" id="btn-copy-pairing-link" style="padding: 6px 14px; font-size: 11.5px;">
              ${Icons.Copy(13, 'currentColor')}
              <span>Copy Link</span>
            </button>
          </div>

          <!-- Download APK Helper -->
          <div style="display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 12px; background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); width: 100%; max-width: 290px;">
            <div style="background: #fff; padding: 4px; border-radius: 8px; flex-shrink: 0; display: flex; align-items: center; justify-content: center;">
              ${downloadQrSvg || ''}
            </div>
            <div>
              <div style="font-size: 11.5px; font-weight: 600; color: #fff;">Need the Android App?</div>
              <div style="font-size: 10.5px; color: var(--text-secondary); line-height: 1.35; margin-top: 2px;">Scan with your native phone camera to download APK directly</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

function renderDeveloperPairingView() {
  return `
    <div style="display: flex; flex-direction: column; gap: 18px; margin-top: 10px;">
      <!-- Developer Mode Banner -->
      <div style="background: rgba(168, 85, 247, 0.08); border: 1px solid rgba(168, 85, 247, 0.25); border-radius: 12px; padding: 14px 18px; display: flex; align-items: center; justify-content: space-between;">
        <div style="display: flex; align-items: center; gap: 12px;">
          <div style="width: 38px; height: 38px; border-radius: 10px; background: rgba(168, 85, 247, 0.2); color: var(--purple-neon); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
            ${Icons.Cpu(20, 'currentColor')}
          </div>
          <div>
            <div style="font-size: 13.5px; font-weight: 700; color: #fff;">ADB & Scrcpy Hardware Pipeline</div>
            <div style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">
              For engineers and power users: raw shell commands, 1-click companion sideload, and ultra-low latency hardware streams.
            </div>
          </div>
        </div>
        <button class="btn-luxury" id="btn-adb-companion-install" style="color: var(--purple-neon); border-color: rgba(168, 85, 247, 0.35); padding: 7px 14px; font-size: 11.5px; white-space: nowrap;" ${isCompanionInstalling ? 'disabled' : ''}>
          ${Icons.Download(13, 'currentColor')}
          <span>${isCompanionInstalling ? 'Installing...' : '1-Click Sideload Companion APK'}</span>
        </button>
      </div>

      ${
        companionInstallMsg
          ? `<div style="font-size: 12px; padding: 10px 14px; border-radius: 8px; background: rgba(168, 85, 247, 0.12); border: 1px solid rgba(168, 85, 247, 0.3); color: #fff;">${companionInstallMsg}</div>`
          : ''
      }

      <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px;">
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 14px;">
          <div style="font-size: 11px; font-weight: 700; color: var(--accent-solar); margin-bottom: 4px;">STEP 1</div>
          <div style="font-size: 12.5px; font-weight: 600; color: #fff;">Same Wi-Fi Network</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 4px; line-height: 1.4;">Ensure your phone and Mac are on the same Wi-Fi network.</div>
        </div>
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 14px;">
          <div style="font-size: 11px; font-weight: 700; color: var(--accent-solar); margin-bottom: 4px;">STEP 2</div>
          <div style="font-size: 12.5px; font-weight: 600; color: #fff;">Wireless Debugging</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 4px; line-height: 1.4;">Turn on Wireless Debugging in Settings > Developer Options.</div>
        </div>
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 14px;">
          <div style="font-size: 11px; font-weight: 700; color: var(--accent-solar); margin-bottom: 4px;">STEP 3</div>
          <div style="font-size: 12.5px; font-weight: 600; color: #fff;">Pair On-the-Air</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 4px; line-height: 1.4;">Scan the pairing QR code or connect wirelessly to link.</div>
        </div>
      </div>

      <div style="display: flex; gap: 12px; margin-top: 4px;">
        <button class="btn-solar" id="btn-refresh-telemetry" style="padding: 8px 18px;">
          ${Icons.Refresh(14, '#fff')}
          <span>Scan Wireless Devices</span>
        </button>
        <button class="btn-luxury" id="btn-open-wireless-modal" style="padding: 8px 18px;">
          ${Icons.Wifi(14, 'var(--accent-solar)')}
          <span>Wireless Debugging (Port & PIN)</span>
        </button>
      </div>
    </div>
  `;
}

// MARK: - Dashboard Workspace (Command Deck & Hardware Status)
function renderDashboard() {
  const isConnected = device.status === 'Connected';

  if (!isConnected) {
    if (!pairingQrSvg && !isPairingQrLoading) {
      generatePairingQRs();
    }

    return `
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Dashboard</h1>
          <p>Command Deck & Hardware Status</p>
        </div>
      </div>

      <!-- Consumer-First Pairing Studio -->
      <div class="glass-card" style="padding: 32px; margin-top: 14px; position: relative;">
        <div style="margin-bottom: 24px;">
          <div style="display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 20px; background: rgba(255, 110, 50, 0.12); border: 1px solid rgba(255, 110, 50, 0.25); color: var(--accent-solar); font-size: 11px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase;">
            ${Icons.Wifi(12, 'currentColor')} Wi-Fi On-the-Air Link
          </div>
          <h2 style="font-size: 22px; font-weight: 700; color: #fff; margin-top: 8px;">Connect Your Android Phone</h2>
          <p style="font-size: 13px; color: var(--text-secondary); margin-top: 4px;">
            Scan the QR code with the Idroid app on your phone to link wirelessly over Wi-Fi on-the-air.
          </p>
        </div>

        ${renderConsumerQrPairingView()}
      </div>
    `;
  }

  const phoneUsedNum = parseInt(device.storageUsedGb) || 98;
  const phoneTotalNum = parseInt(device.storageTotalGb) || 256;
  const phoneAvailGb = Math.max(0, phoneTotalNum - phoneUsedNum) + 'G';

  return `
    <!-- Hidden file input for Send File to phone -->
    <input type="file" id="input-send-file-to-phone" style="display: none;" />

    <!-- Dual-Device Hardware Stages (Phone + Mac Side-by-Side) -->
    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px;">
      <!-- Card 1: Om's S23 Ultra Telemetry -->
      <div class="glass-card" style="padding: 20px; display: flex; flex-direction: column; justify-content: space-between; gap: 16px; position: relative; overflow: hidden;">
        ${deviceWallpapers.phone ? `
          <div style="position: absolute; top: 0; left: 0; right: 0; height: 85px; background-image: url('${safeWallpaperDataUrl(deviceWallpapers.phone)}'); background-size: cover; background-position: center; opacity: 0.18; filter: blur(6px); mask-image: linear-gradient(to bottom, black 30%, transparent 100%); -webkit-mask-image: linear-gradient(to bottom, black 30%, transparent 100%); pointer-events: none; border-radius: 20px 20px 0 0;"></div>
        ` : ''}
        <div style="position: relative; z-index: 1;">
          <!-- Header -->
          <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;">
            <div style="display: flex; align-items: center; gap: 14px; min-width: 0; flex: 1;">
              ${renderPhoneDeviceFrame()}
              <div style="min-width: 0; flex: 1;">
                <div style="font-size: 15px; font-weight: 700; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${device.name}</div>
                <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 3px; display: flex; align-items: center; gap: 5px; flex-wrap: wrap;">
                  <span>Samsung Galaxy</span>
                  <span style="color: var(--text-muted);">&bull;</span>
                  <span style="color: var(--emerald-ion); font-weight: 500;">Connected</span>
                </div>
                <div id="phone-bluetooth-badge" style="margin-top: 6px; display: inline-flex; align-items: center; gap: 5px; font-size: 11px; padding: 2.5px 8px; border-radius: 6px; background: ${device.bluetoothConnected ? 'rgba(52, 211, 153, 0.12)' : 'rgba(56, 189, 248, 0.1)'}; border: 1px solid ${device.bluetoothConnected ? 'rgba(52, 211, 153, 0.28)' : 'rgba(56, 189, 248, 0.2)'}; color: ${device.bluetoothConnected ? '#34d399' : '#38bdf8'}; font-weight: 500; cursor: pointer; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" title="Phone Bluetooth Status (Click to open Bluetooth settings)">
                  ${device.bluetoothConnected ? Icons.BluetoothConnected(12, '#34d399') : Icons.Bluetooth(12, '#38bdf8')}
                  <span style="overflow: hidden; text-overflow: ellipsis;">${device.bluetoothStatus || 'Bluetooth On'}</span>
                </div>
              </div>
            </div>
            ${renderPhoneBatteryWidget(device.battery, device.isCharging)}
          </div>

          <!-- Dynamic Proportional Storage Bar -->
          <div style="margin-top: 18px;">
            <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 7px;">
              <span style="color: var(--text-secondary); font-weight: 500;">Storage</span>
              <span style="color: #fff; font-weight: 600;">${device.storageUsedGb} used of ${device.storageTotalGb}</span>
            </div>
            <div style="height: 7px; border-radius: 4px; background: rgba(255, 255, 255, 0.08); overflow: hidden;">
              <div style="width: ${Math.max(3, device.storagePercent || 0)}%; height: 100%; border-radius: 4px; background: linear-gradient(90deg, var(--accent-solar), var(--accent-gold)); transition: width 0.4s ease;"></div>
            </div>
            <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); margin-top: 6px;">
              <span>${phoneAvailGb} free</span>
              <span>${device.storagePercent}% capacity</span>
            </div>
          </div>
        </div>

        <!-- Phone Hardware Controls -->
        <div style="display: flex; gap: 8px; padding-top: 14px; border-top: 1px solid var(--border-subtle); position: relative; z-index: 1;">
          <button class="btn-luxury" id="btn-phone-card-lock" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Lock phone screen">
            ${Icons.Lock(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Lock Screen</span>
          </button>
          <button class="btn-luxury" id="btn-phone-card-dnd" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Toggle Do Not Disturb">
            ${Icons.Moon(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${isDndActive ? 'DND On' : 'Do Not Disturb'}</span>
          </button>
          <button class="btn-luxury" id="btn-phone-card-screenshot" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Take screenshot on phone">
            ${Icons.Camera(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Screenshot</span>
          </button>
        </div>
      </div>

      <!-- Card 2: MacBook Pro (This Device) -->
      <div class="glass-card" style="padding: 20px; display: flex; flex-direction: column; justify-content: space-between; gap: 16px; position: relative; overflow: hidden;">
        ${deviceWallpapers.mac ? `
          <div style="position: absolute; top: 0; left: 0; right: 0; height: 85px; background-image: url('${safeWallpaperDataUrl(deviceWallpapers.mac)}'); background-size: cover; background-position: center; opacity: 0.18; filter: blur(6px); mask-image: linear-gradient(to bottom, black 30%, transparent 100%); -webkit-mask-image: linear-gradient(to bottom, black 30%, transparent 100%); pointer-events: none; border-radius: 20px 20px 0 0;"></div>
        ` : ''}
        <div style="position: relative; z-index: 1;">
          <!-- Header -->
          <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;">
            <div style="display: flex; align-items: center; gap: 14px; min-width: 0; flex: 1;">
              ${renderMacBookDeviceFrame()}
              <div style="min-width: 0; flex: 1;">
                <div style="font-size: 15px; font-weight: 700; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${hostTelemetry.name}</div>
                <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 3px; display: flex; align-items: center; gap: 5px; flex-wrap: wrap;">
                  <span>Apple Silicon macOS</span>
                  <span style="color: var(--text-muted);">&bull;</span>
                  <span style="color: var(--cyan-neon); font-weight: 500;">This Mac</span>
                </div>
                <button id="btn-mac-bluetooth-badge" style="margin-top: 6px; display: inline-flex; align-items: center; gap: 5px; font-size: 11px; padding: 2.5px 8px; border-radius: 6px; background: ${hostTelemetry.bluetooth_connected ? 'rgba(52, 211, 153, 0.12)' : 'rgba(56, 189, 248, 0.1)'}; border: 1px solid ${hostTelemetry.bluetooth_connected ? 'rgba(52, 211, 153, 0.28)' : 'rgba(56, 189, 248, 0.2)'}; color: ${hostTelemetry.bluetooth_connected ? '#34d399' : '#38bdf8'}; font-weight: 500; cursor: pointer; text-align: left; max-width: 100%; transition: all 0.2s;" title="Click to open Mac Bluetooth settings">
                  ${hostTelemetry.bluetooth_connected ? Icons.BluetoothConnected(12, '#34d399') : Icons.Bluetooth(12, '#38bdf8')}
                  <span style="overflow: hidden; text-overflow: ellipsis;">${hostTelemetry.bluetooth_status || 'Bluetooth On'}</span>
                </button>
              </div>
            </div>
            ${renderMacBatteryWidget(hostTelemetry.battery, hostTelemetry.is_charging)}
          </div>

          <!-- Dynamic Proportional Storage Bar -->
          <div style="margin-top: 18px;">
            <div style="display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 7px;">
              <span style="color: var(--text-secondary); font-weight: 500;">Storage</span>
              <span style="color: #fff; font-weight: 600;">${hostTelemetry.storage_used_gb} used of ${hostTelemetry.storage_total_gb}</span>
            </div>
            <div style="height: 7px; border-radius: 4px; background: rgba(255, 255, 255, 0.08); overflow: hidden;">
              <div style="width: ${Math.max(3, hostTelemetry.storage_percent || 0)}%; height: 100%; border-radius: 4px; background: linear-gradient(90deg, #38bdf8, #3b82f6); transition: width 0.4s ease;"></div>
            </div>
            <div style="display: flex; justify-content: space-between; font-size: 11px; color: var(--text-muted); margin-top: 6px;">
              <span>${hostTelemetry.storage_available_gb} free</span>
              <span>${hostTelemetry.storage_percent}% capacity</span>
            </div>
          </div>
        </div>

        <!-- Mac Quick Actions -->
        <div style="display: flex; gap: 8px; padding-top: 14px; border-top: 1px solid var(--border-subtle); position: relative; z-index: 1;">
          <button class="btn-luxury" id="btn-go-to-clipboard" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Open Shared Clipboard">
            ${Icons.Clipboard(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Clipboard</span>
          </button>
          <button class="btn-luxury" id="btn-send-file-to-phone" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Send file to phone">
            ${Icons.UploadCloud(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Send File</span>
          </button>
          <button class="btn-luxury" id="btn-open-received-files" style="flex: 1; justify-content: center; font-size: 11px; padding: 7px 4px; gap: 5px; white-space: nowrap; min-width: 0;" title="Open received files folder">
            ${Icons.Folder(12, 'currentColor')}
            <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">Received Files</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Favourite Apps Strip -->
    <div style="margin-top: 18px;">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
        <div style="font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px;">
          ${Icons.Grid(14, 'var(--accent-solar)')}
          <span>Favourite Apps</span>
        </div>
        <button class="btn-luxury" id="btn-header-customize-apps" style="padding: 4px 11px; font-size: 11.5px; border-radius: 8px; color: var(--text-secondary); display: inline-flex; align-items: center; gap: 5px; cursor: pointer;" title="Customize Favourite Apps">
          ${Icons.Sliders(11, 'var(--accent-solar)')}
          <span>Customize</span>
        </button>
      </div>
      <div id="quick-phone-apps-container" style="display: flex; gap: 10px; overflow-x: auto; padding-bottom: 6px; scrollbar-width: none; -ms-overflow-style: none;">
        ${renderQuickAppsHtml()}
      </div>
    </div>

    <!-- Quick Controls Section -->
    ${renderQuickControlsSection()}
  `;
}

// MARK: - Screen Mirror Studio
function renderMirrorStudio() {
  const isConnected = device.status === 'Connected';

  if (!isConnected) {
    return `
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Screen Mirror</h1>
          <p>Stream phone display and launch apps directly on Mac</p>
        </div>
      </div>

      <!-- Clean Disconnected State -->
      <div class="glass-card" style="padding: 44px 32px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 16px; margin-top: 14px;">
        <div style="width: 64px; height: 64px; border-radius: 20px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; color: var(--text-muted);">
          ${Icons.Mirror(30, 'currentColor')}
        </div>
        <div>
          <h2 style="font-size: 19px; font-weight: 700; color: #fff;">Connect Phone to Start Screen Mirror</h2>
          <p style="font-size: 13px; color: var(--text-secondary); max-width: 440px; margin-top: 6px; line-height: 1.5;">
            Connect your phone over Wi-Fi (On the Air) to stream your display and launch apps on Mac.
          </p>
        </div>
        <button class="btn-solar" id="btn-refresh-telemetry" style="margin-top: 8px;">
          ${Icons.Refresh(13, '#fff')}
          <span>Scan Wireless Devices</span>
        </button>
      </div>
    `;
  }

  const filteredApps = phoneApps.filter((a) =>
    a.name.toLowerCase().includes(appSearchQuery.toLowerCase())
  );

  return `
    <div class="mirror-hero-deck">
      <div class="mirror-hero-left">
        <div class="mirror-hero-badge">
          ${Icons.Mirror(22, 'currentColor')}
        </div>
        <div>
          <div class="mirror-hero-title">Screen Mirroring</div>
          <div class="mirror-hero-desc">Select an app to open directly on your Mac, or stream the entire phone screen</div>
        </div>
      </div>
      <button class="btn-solar" id="btn-launch-full-mirror">
        ${Icons.Play(14, '#ffffff')}
        <span>Start Mirror</span>
      </button>
    </div>

    <!-- Mirroring Setup Card -->
    <div class="glass-card">
      <div class="card-title-row" style="margin-bottom: 4px;">
        <span class="card-title">
          <span>Mirroring Preferences</span>
        </span>
      </div>

      <!-- Row 1: Play audio on Mac -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Radio(16, 'currentColor')}</div>
          <div>
            <div class="setup-title">Play device audio on Mac</div>
            <div class="setup-subtitle">Streams phone audio directly to your Mac speakers (Opus 128 kbps)</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-audio" ${isAudioActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>

      <!-- Row 2: Screen Off Mode -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Monitor(16, 'currentColor')}</div>
          <div>
            <div class="setup-title">Turn Phone Screen Off on Desk</div>
            <div class="setup-subtitle">Keeps physical phone screen black to save power & avoid DeX desktop taskbar shifts</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-screenoff" ${isScreenOffModeActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>

      <!-- Row 3: Multi-Window Mode -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Cpu(16, 'currentColor')}</div>
          <div>
            <div class="setup-title">Multi-Window App Mode (Concurrent Displays)</div>
            <div class="setup-subtitle">Open multiple phone apps simultaneously side-by-side in separate portrait Mac windows</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-vdisplay" ${isVirtualDisplayActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>

      <!-- Row 4: Always on Top -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Compass(16, 'currentColor')}</div>
          <div>
            <div class="setup-title">Always on Top (Floating Window)</div>
            <div class="setup-subtitle">Keep phone mirror windows floating above other Mac apps while multitasking</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-always-on-top" ${isAlwaysOnTopActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>

      <!-- Row 5: Phone-Shaped Chassis -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Phone(16, 'var(--accent-solar)')}</div>
          <div>
            <div class="setup-title">Phone-Shaped Chassis (Curved Mirror)</div>
            <div class="setup-subtitle">Render mirror with realistic smartphone curvature (40px radius), bezel & punch-hole camera</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-phone-frame" ${isPhoneFrameActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>

      <!-- Row 6: Dock Hardware Navigation Bar Below Mirror -->
      <div class="setup-row">
        <div class="setup-meta-left">
          <div class="setup-icon-box">${Icons.Dashboard(16, 'var(--accent-solar)')}</div>
          <div>
            <div class="setup-title">Dock Hardware Navigation Bar Below Mirror</div>
            <div class="setup-subtitle">Attach physical phone buttons directly beneath your live screen mirror window (LinkMyMac style)</div>
          </div>
        </div>
        <label class="switch">
          <input type="checkbox" id="toggle-dock-nav" ${isDockNavActive ? 'checked' : ''} />
          <span class="slider"></span>
        </label>
      </div>
    </div>

    ${isDockNavActive ? `
    <!-- Phone Hardware Navigation Deck & Customizer (LinkMyMac Style) -->
    <div class="glass-card" style="padding: 16px 20px; margin-top: 14px; animation: fadeIn 0.2s ease-out;">
      <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; margin-bottom: 14px;">
        <div>
          <div style="font-size: 14px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 8px;">
            ${Icons.Phone(15, 'var(--accent-solar)')}
            <span>Phone Hardware Navigation Controller</span>
            <span class="status-badge" style="font-size: 11px; padding: 2px 8px; border-radius: 12px; font-weight: 600; background: rgba(34, 197, 94, 0.15); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.3);">
              ● Docked Below Mirror Active
            </span>
            <span style="font-size: 11px; color: var(--text-muted);">
              (${enabledNavButtons.length} of ${ALL_NAV_BUTTONS.length} enabled)
            </span>
          </div>
          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 3px;">
            Click any button below to enable or disable it for your docked mirror bar:
          </div>
        </div>
        <div style="display: flex; gap: 8px; align-items: center;">
          <button class="btn-luxury btn-select-all-nav" style="padding: 4px 10px; font-size: 11px;">
            <span>Select All</span>
          </button>
          <button class="btn-luxury btn-reset-nav" style="padding: 4px 10px; font-size: 11px;">
            <span>Reset Defaults</span>
          </button>
        </div>
      </div>

      <!-- Unified Interactive Buttons Deck (Single Line Fit) -->
      <div style="display: flex; gap: 6px; width: 100%; align-items: center; overflow-x: auto;">
        ${ALL_NAV_BUTTONS.map(btn => {
          const isChecked = enabledNavButtons.includes(btn.key);
          return `
            <button class="nav-toggle-chip ${isChecked ? 'active' : ''}" data-nav-key="${btn.key}" type="button" title="${btn.hint}">
              <div class="nav-chip-icon" style="${isChecked ? 'color: var(--accent-solar);' : 'color: var(--text-muted);'}">
                ${btn.icon(12, 'currentColor')}
              </div>
              <span class="nav-chip-label">${btn.label}</span>
              <span class="nav-chip-status">${isChecked ? '✓' : '+'}</span>
            </button>
          `;
        }).join('')}
      </div>
    </div>
    ` : ''}

    <!-- Apps 132 Section & Density Grid -->
    <div class="glass-card">
      <div class="apps-header-row">
        <div class="apps-title-pill">
          ${Icons.Dashboard(16, 'var(--accent-solar)')}
          <span>Installed Apps</span>
          <span class="apps-count-badge">${phoneApps.length}</span>
        </div>
        <button class="btn-luxury" id="btn-refresh-apps" style="padding: 4px 10px; font-size: 11.5px;">
          ${Icons.Refresh(12, 'currentColor')}
          <span>Refresh</span>
        </button>
      </div>

      <!-- Search Input -->
      <div style="position: relative; margin-top: 4px;">
        <input 
          type="text" 
          placeholder="Search apps..." 
          id="input-search-apps"
          value="${appSearchQuery}"
          style="width: 100%; background: rgba(255,255,255,0.04); border: 1px solid var(--border-subtle); color: #fff; padding: 8px 12px 8px 34px; border-radius: var(--radius-sm); font-size: 12.5px; outline: none;" 
        />
        <div style="position: absolute; left: 10px; top: 9px; color: var(--text-muted); pointer-events: none;">
          ${Icons.Search(14, 'currentColor')}
        </div>
      </div>

      <!-- Clean Pop App Grid (Zero orange borders, zero gray blur) -->
      ${
        isLoadingApps
          ? '<div style="padding: 32px; text-align: center; color: var(--text-secondary);">Querying installed apps...</div>'
          : `
          <div class="apps-grid-container">
            ${filteredApps
              .map(
                (app) => {
                  const iconSrc = cleanIconUrl(app.icon_base64);
                  return `
              <div class="app-tile btn-launch-app" data-package="${app.package}" data-appname="${app.name}" data-iconsrc="${iconSrc}" title="Launch ${app.name} on screen">
                ${
                  iconSrc
                    ? `<img src="${iconSrc}" class="app-icon-img" alt="${app.name}" />`
                    : `<div class="app-icon-img" style="display:flex;align-items:center;justify-content:center;">${Icons.Phone(20, 'var(--accent-solar)')}</div>`
                }
                <span class="app-tile-label">${app.name}</span>
              </div>
            `;
                }
              )
              .join('')}
          </div>
          `
      }
    </div>

    ${
      mirrorStatusMsg
        ? `<div style="background: rgba(255,110,50,0.08); border: 1px solid var(--border-active); border-radius: var(--radius-sm); padding: 12px 16px; font-size: 12.5px; color: var(--accent-solar);">
            ${mirrorStatusMsg}
          </div>`
        : ''
    }
  `;
}

// MARK: - Telephony & Calls Studio (Redesigned with Spacious Contacts & Full Call History)
function renderCallsView() {
  const isConnected = device.status === 'Connected';

  if (!isConnected) {
    return `
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Calls & Audio</h1>
          <p>Dial phone numbers, attend incoming calls, and manage contacts</p>
        </div>
      </div>

      <div class="glass-card" style="padding: 44px 32px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 16px; margin-top: 14px;">
        <div style="width: 64px; height: 64px; border-radius: 20px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; color: var(--text-muted);">
          ${Icons.Calls(30, 'currentColor')}
        </div>
        <div>
          <h2 style="font-size: 19px; font-weight: 700; color: #fff;">Connect Phone to Access Calls</h2>
          <p style="font-size: 13px; color: var(--text-secondary); max-width: 440px; margin-top: 6px; line-height: 1.5;">
            Connect your phone over Wi-Fi to dial numbers, receive incoming calls, and route audio directly through your Mac.
          </p>
        </div>
        <button class="btn-solar" id="btn-refresh-telemetry" style="margin-top: 8px;">
          ${Icons.Refresh(13, '#fff')}
          <span>Scan Wireless Devices</span>
        </button>
      </div>
    `;
  }

  // Filter call history
  const filteredCalls = callHistory.filter((c) => {
    if (callsTabFilter === 'missed') return c.type === 'missed' || c.duration === 'Missed';
    if (callsTabFilter === 'outgoing') return c.type === 'outgoing' || c.call_type === 'outgoing';
    if (callsTabFilter === 'incoming') return c.type === 'incoming' || (!c.type && c.call_type !== 'outgoing');
    return true;
  });

  return `
    <div class="workspace-header">
      <div class="workspace-title-box">
        <h1>Calls & Audio</h1>
        <p>Speed dial saved contacts, view complete call logs, and place calls with Mac keyboard</p>
      </div>
      <div style="display: flex; gap: 8px;">
        <button class="btn-luxury" id="btn-simulate-incoming-call" title="Test receiving an incoming call on your Mac" style="font-size: 11.5px; padding: 6px 12px; display: flex; align-items: center; gap: 6px;">
          📞 Test Incoming Call
        </button>
      </div>
    </div>

    <div class="calls-workspace-layout" style="margin-top: 14px;">
      <!-- Left Column: Spacious Contacts & Full Call History -->
      <div class="calls-main-stream">
        
        <!-- Saved Contacts / Speed Dial (Spacious & Polished) -->
        <div class="glass-card" style="padding: 18px 20px;">
          <div class="card-title-row" style="margin-bottom: 14px;">
            <span class="card-title" style="font-size: 14px;">
              ${Icons.Users(16, 'var(--accent-solar)')}
              <span>Contacts & Speed Dial</span>
              <span class="badge" style="font-size: 10px; background: rgba(255,255,255,0.06); color: var(--text-secondary); padding: 2px 7px; border-radius: 10px;">
                ${favouriteContacts.length}
              </span>
            </span>
            <button class="btn-luxury" id="btn-open-add-fav" style="padding: 5px 12px; font-size: 11.5px; display: flex; align-items: center; gap: 5px;">
              <span>+</span> <span>Add Contact</span>
            </button>
          </div>

          ${
            favouriteContacts.length === 0
              ? `
            <div style="padding: 24px; text-align: center; color: var(--text-muted); background: rgba(255,255,255,0.015); border: 1px dashed var(--border-subtle); border-radius: 12px;">
              <div style="font-size: 13px; color: #fff; font-weight: 600;">No contacts saved yet</div>
              <p style="font-size: 12px; color: var(--text-secondary); max-width: 360px; margin: 4px auto 12px auto;">
                Save your important contacts here for one-click speed dialing directly from your Mac.
              </p>
              <button class="btn-solar" id="btn-empty-add-fav" style="padding: 6px 14px; font-size: 11.5px;">
                + Add First Contact
              </button>
            </div>
          `
              : `
            <div class="contacts-spacious-grid">
              ${favouriteContacts
                .map(
                  (c) => `
                <div class="contact-card-premium">
                  <div class="contact-card-avatar" style="background: ${c.avatarColor || '#3b82f6'};">
                    ${c.name.substring(0, 1).toUpperCase()}
                  </div>
                  <div class="contact-card-info">
                    <span class="contact-card-name" title="${c.name}">${c.name}</span>
                    <span class="contact-card-number" title="${c.number}">${c.number}</span>
                  </div>
                  <div class="contact-card-actions">
                    <button class="btn-contact-call btn-quick-dial" data-number="${c.number}" title="Call ${c.name}">
                      📞
                    </button>
                    <button class="btn-contact-delete btn-delete-fav" data-fav-id="${c.id}" title="Delete contact">
                      ✕
                    </button>
                  </div>
                </div>
              `
                )
                .join('')}
            </div>
          `
          }
        </div>

        <!-- Full Call History (Expanded & Comfortable) -->
        <div class="glass-card" style="padding: 18px 20px;">
          <div class="card-title-row" style="margin-bottom: 14px;">
            <span class="card-title" style="font-size: 14px;">
              ${Icons.Calls(16, 'var(--emerald-ion)')}
              <span>Call History</span>
              <span class="badge" style="font-size: 10px; background: rgba(255,255,255,0.06); color: var(--text-secondary); padding: 2px 7px; border-radius: 10px;">
                ${filteredCalls.length}
              </span>
            </span>
            <div style="display: flex; gap: 4px;">
              <button class="btn-luxury btn-call-filter ${callsTabFilter === 'all' ? 'active' : ''}" data-filter="all" style="padding: 4px 10px; font-size: 11px; ${callsTabFilter === 'all' ? 'border-color: var(--accent-solar); color: var(--accent-solar);' : ''}">
                All (${callHistory.length})
              </button>
              <button class="btn-luxury btn-call-filter ${callsTabFilter === 'outgoing' ? 'active' : ''}" data-filter="outgoing" style="padding: 4px 10px; font-size: 11px; ${callsTabFilter === 'outgoing' ? 'border-color: var(--accent-solar); color: var(--accent-solar);' : ''}">
                Outgoing
              </button>
              <button class="btn-luxury btn-call-filter ${callsTabFilter === 'missed' ? 'active' : ''}" data-filter="missed" style="padding: 4px 10px; font-size: 11px; ${callsTabFilter === 'missed' ? 'border-color: var(--accent-solar); color: var(--accent-solar);' : ''}">
                Missed
              </button>
              <button class="btn-luxury btn-call-filter ${callsTabFilter === 'incoming' ? 'active' : ''}" data-filter="incoming" style="padding: 4px 10px; font-size: 11px; ${callsTabFilter === 'incoming' ? 'border-color: var(--accent-solar); color: var(--accent-solar);' : ''}">
                Incoming
              </button>
            </div>
          </div>

          ${
            filteredCalls.length === 0
              ? `
            <div style="padding: 36px 20px; text-align: center; color: var(--text-secondary); font-size: 13px;">
              No call records found for this filter. Placed and received calls will automatically be saved and displayed here.
            </div>
          `
              : `
            <div class="call-history-container" style="max-height: 460px; overflow-y: auto; padding-right: 2px;">
              ${filteredCalls
                .map((call) => {
                  const isMissed = call.type === 'missed' || call.duration === 'Missed';
                  const isOut = call.type === 'outgoing' || call.call_type === 'outgoing';
                  const typeLabel = isMissed ? 'Missed' : isOut ? 'Outgoing' : 'Incoming';
                  const typeColor = isMissed ? '#ef4444' : isOut ? '#3b82f6' : 'var(--emerald-ion)';
                  const iconBg = isMissed
                    ? 'rgba(239, 68, 68, 0.15)'
                    : isOut
                    ? 'rgba(59, 130, 246, 0.15)'
                    : 'rgba(16, 185, 129, 0.15)';

                  // Check if already in contacts
                  const isSaved = favouriteContacts.some(
                    (c) => c.number.replace(/[\s-]/g, '') === call.number.replace(/[\s-]/g, '')
                  );

                  return `
                    <div class="call-history-row">
                      <div style="display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1;">
                        <div style="width: 36px; height: 36px; border-radius: 50%; background: ${iconBg}; display: flex; align-items: center; justify-content: center; color: ${typeColor}; flex-shrink: 0;">
                          ${Icons.Calls(15, 'currentColor')}
                        </div>
                        <div style="display: flex; flex-direction: column; min-width: 0;">
                          <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="font-size: 13.5px; font-weight: 700; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                              ${call.name || call.number}
                            </span>
                            <span style="font-size: 10px; font-weight: 600; color: ${typeColor}; background: ${iconBg}; padding: 1px 6px; border-radius: 4px;">
                              ${typeLabel}
                            </span>
                          </div>
                          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 1px;">
                            ${call.number} • ${call.time} ${call.duration && call.duration !== 'Missed' ? `• <span style="color: var(--text-muted);">${call.duration}</span>` : ''}
                          </div>
                        </div>
                      </div>

                      <div style="display: flex; align-items: center; gap: 8px; flex-shrink: 0;">
                        ${
                          !isSaved
                            ? `
                          <button class="btn-luxury btn-add-history-contact" data-number="${call.number}" data-name="${call.name || call.number}" title="Save to Contacts" style="padding: 5px 9px; font-size: 11px;">
                            + Save
                          </button>
                        `
                            : ''
                        }
                        <button class="btn-solar btn-quick-dial" data-number="${call.number}" title="Call ${call.number}" style="padding: 5px 14px; font-size: 11.5px; font-weight: 600;">
                          Call
                        </button>
                      </div>
                    </div>
                  `;
                })
                .join('')}
            </div>
          `
          }
        </div>
      </div>

      <!-- Right Column: Tactile Keypad Dialer -->
      <div class="calls-sidebar-dialer">
        <div class="dialer-compact-card">
          <div class="card-title-row" style="margin-bottom: 2px;">
            <span class="card-title" style="font-size: 13.5px;">
              ${Icons.Calls(15, 'var(--accent-solar)')}
              <span>Tactile Keypad</span>
            </span>
            <span class="badge" style="font-size: 9.5px; background: rgba(255,255,255,0.06); color: var(--text-secondary); border: 1px solid var(--border-subtle); padding: 2px 6px;">
              Keyboard Enabled ⌨️
            </span>
          </div>

          <div class="dialer-container" style="max-width: 100%;">
            <div class="dialer-display">
              <input 
                type="text" 
                class="dialer-input-number" 
                id="input-dialer-number" 
                placeholder="Type or dial number..." 
                value="${dialerInputNumber}"
                autocomplete="off"
                spellcheck="false"
              />
              <div style="display: flex; gap: 4px; align-items: center;">
                <button class="btn-luxury" id="btn-dialer-clear" title="Clear all" style="padding: 4px 7px; font-size: 10px; color: var(--text-muted);">✕</button>
                <button class="btn-luxury" id="btn-dialer-backspace" title="Backspace (⌫)" style="padding: 4px 8px; font-size: 11px;">⌫</button>
              </div>
            </div>

            <div style="font-size: 10px; color: var(--text-muted); text-align: center; margin-top: -4px;">
              Press <kbd style="background: rgba(255,255,255,0.1); padding: 1px 4px; border-radius: 3px; font-size: 9.5px;">Enter</kbd> to call, <kbd style="background: rgba(255,255,255,0.1); padding: 1px 4px; border-radius: 3px; font-size: 9.5px;">Esc</kbd> to clear
            </div>

            <div class="keypad-grid" style="gap: 8px;">
              ${renderKeypadBtn('1', '')}
              ${renderKeypadBtn('2', 'ABC')}
              ${renderKeypadBtn('3', 'DEF')}
              ${renderKeypadBtn('4', 'GHI')}
              ${renderKeypadBtn('5', 'JKL')}
              ${renderKeypadBtn('6', 'MNO')}
              ${renderKeypadBtn('7', 'PQRS')}
              ${renderKeypadBtn('8', 'TUV')}
              ${renderKeypadBtn('9', 'WXYZ')}
              ${renderKeypadBtn('*', '')}
              ${renderKeypadBtn('0', '+')}
              ${renderKeypadBtn('#', '')}
            </div>

            <div class="call-actions-row" style="margin-top: 4px;">
              <button class="btn-call-start" id="btn-trigger-call" title="Place Call (Enter)">
                ${Icons.Calls(22, '#fff')}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

function renderKeypadBtn(digit: string, sub: string) {
  return `
    <button class="keypad-btn btn-keypad-digit" data-digit="${digit}">
      <span class="keypad-digit">${digit}</span>
      ${sub ? `<span class="keypad-sub">${sub}</span>` : ''}
    </button>
  `;
}

// MARK: - Notifications View (Live with App Icons, Copy Text, and Inline Quick Reply)
function renderNotificationsView() {
  const isConnected = device.status === 'Connected';

  if (!isConnected) {
    return `
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Notifications</h1>
          <p>Live push notifications forwarded from your phone with quick reply</p>
        </div>
      </div>

      <div class="glass-card" style="padding: 44px 32px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 16px; margin-top: 14px;">
        <div style="width: 64px; height: 64px; border-radius: 20px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; color: var(--text-muted);">
          ${Icons.Notifications(30, 'currentColor')}
        </div>
        <div>
          <h2 style="font-size: 19px; font-weight: 700; color: #fff;">Connect Phone for Notifications</h2>
          <p style="font-size: 13px; color: var(--text-secondary); max-width: 440px; margin-top: 6px; line-height: 1.5;">
            Connect your Android phone over Wi-Fi to sync incoming notifications and reply directly from your Mac.
          </p>
        </div>
        <button class="btn-solar" id="btn-refresh-telemetry" style="margin-top: 8px;">
          ${Icons.Refresh(13, '#fff')}
          <span>Scan Wireless Devices</span>
        </button>
      </div>
    `;
  }

  return `
    <div class="workspace-header">
      <div class="workspace-title-box">
        <h1>Notifications</h1>
        <p>Live push notifications forwarded from phone with quick reply and clipboard copy</p>
      </div>
      <div class="header-action-group">
        <button class="btn-luxury" id="btn-refresh-notifs">
          ${Icons.Refresh(13, '#ffffff')}
          <span>Refresh</span>
        </button>
      </div>
    </div>

    ${
      notificationList.length === 0
        ? `
      <div class="glass-card" style="padding: 40px; text-align: center; margin-top: 14px;">
        <p style="color: var(--text-secondary); font-size: 13px;">No active notifications from your phone</p>
      </div>
      `
        : `
      <div style="display: flex; flex-direction: column; gap: 12px; margin-top: 14px;">
        ${notificationList
          .map(
            (n, idx) => {
              // Find matching app icon if companion returned base64 or from quickPhoneApps
              let iconHtml = '';
              if (n.icon_base64) {
                iconHtml = `<img src="${cleanIconUrl(n.icon_base64)}" style="width: 22px; height: 22px; border-radius: 5px; object-fit: contain;" />`;
              } else if (n.package_name) {
                const matchedApp = (quickPhoneApps || []).find((a) => a.package === n.package_name);
                if (matchedApp && matchedApp.icon) {
                  iconHtml = `<img src="${cleanIconUrl(matchedApp.icon)}" style="width: 22px; height: 22px; border-radius: 5px; object-fit: contain;" />`;
                }
              }
              if (!iconHtml) {
                iconHtml = `<div style="width: 22px; height: 22px; border-radius: 5px; background: rgba(255, 110, 50, 0.15); display: flex; align-items: center; justify-content: center; color: var(--accent-solar);">
                  ${Icons.Notifications(12, 'currentColor')}
                </div>`;
              }

              return `
          <div class="notif-card" style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); padding: 14px; display: flex; flex-direction: column; gap: 8px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <div style="display: flex; align-items: center; gap: 8px;">
                ${iconHtml}
                <span style="font-size: 11.5px; font-weight: 700; color: var(--accent-solar); text-transform: uppercase;">${n.appName}</span>
                <span style="font-size: 11px; color: var(--text-muted);">• ${n.time}</span>
              </div>
              <div style="display: flex; align-items: center; gap: 6px;">
                <button class="btn-luxury btn-copy-notif-text" data-copy-text="${encodeURIComponent(n.text || n.title)}" style="padding: 3px 8px; font-size: 11px;">
                  📋 Copy Text
                </button>
                <button class="btn-luxury btn-open-notif-app" data-package="${n.package_name || ''}" style="padding: 3px 8px; font-size: 11px;">
                  Open in Mirror
                </button>
              </div>
            </div>
            <div style="font-size: 13.5px; font-weight: 600; color: #fff;">${n.title}</div>
            <div style="font-size: 12.5px; color: var(--text-secondary); line-height: 1.4;">${n.text}</div>

            <div class="notif-reply-box" style="display: flex; gap: 8px; margin-top: 4px;">
              <input 
                type="text" 
                class="notif-input" 
                id="input-reply-${idx}" 
                placeholder="Type reply to ${n.appName}..." 
                style="flex: 1; background: rgba(255,255,255,0.04); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); color: #fff; padding: 6px 10px; font-size: 12px; outline: none;"
              />
              <button class="btn-solar btn-send-reply" data-notif-id="${n.id}" data-input-id="input-reply-${idx}" style="padding: 6px 14px; font-size: 11.5px;">
                Reply
              </button>
            </div>
          </div>
        `;
            }
          )
          .join('')}
      </div>
      `
    }
  `;
}

// MARK: - Universal Clipboard
function renderClipboardView() {
  const isConnected = device.status === 'Connected';

  if (!isConnected) {
    return `
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Clipboard</h1>
          <p>Bi-directional clipboard synchronization between Mac and Android</p>
        </div>
      </div>

      <div class="glass-card" style="padding: 44px 32px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 16px; margin-top: 14px;">
        <div style="width: 64px; height: 64px; border-radius: 20px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; color: var(--text-muted);">
          ${Icons.Clipboard(30, 'currentColor')}
        </div>
        <div>
          <h2 style="font-size: 19px; font-weight: 700; color: #fff;">Connect Phone to Sync Clipboard</h2>
          <p style="font-size: 13px; color: var(--text-secondary); max-width: 440px; margin-top: 6px; line-height: 1.5;">
            Connect your phone wirelessly over Wi-Fi for seamless Mac & Android copy-paste synchronization.
          </p>
        </div>
        <button class="btn-solar" id="btn-refresh-telemetry" style="margin-top: 8px;">
          ${Icons.Refresh(13, '#fff')}
          <span>Scan Wireless Devices</span>
        </button>
      </div>
    `;
  }

  return `
    <div class="workspace-header">
      <div class="workspace-title-box">
        <h1>Clipboard</h1>
        <p>Bi-directional clipboard synchronization between Mac and Android</p>
      </div>
      <div class="header-action-group">
        <span class="badge" style="background: rgba(16, 185, 129, 0.15); color: var(--emerald-ion); display: inline-flex; align-items: center; gap: 6px; font-size: 11px; padding: 4px 10px; border-radius: 6px; font-weight: 600;">
          ${Icons.ShieldCheck(13, 'var(--emerald-ion)')}
          <span>Sync Engine Active • Zero Echo Loops</span>
        </span>
      </div>
    </div>

    <div class="dual-grid" style="margin-top: 14px;">
      <div class="glass-card">
        <div class="card-title">
          ${Icons.Clipboard(15, 'var(--accent-solar)')}
          <span>Send Text to Phone</span>
        </div>
        <p style="font-size: 12px; color: var(--text-secondary);">
          Direct injection into phone active field or Android clipboard buffer.
        </p>
        <textarea 
          id="textarea-push-phone" 
          placeholder="Paste or type text..." 
          style="width: 100%; height: 90px; background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); color: #fff; padding: 10px; font-size: 12.5px; resize: none; outline: none; margin-top: 4px;"
        >${pushToPhoneInput}</textarea>
        <button class="btn-solar" id="btn-push-to-phone" style="align-self: flex-start; margin-top: 4px;">
          Send to Phone
        </button>
      </div>

      <div class="glass-card">
        <div class="card-title-row">
          <span class="card-title">
            ${Icons.Clipboard(15, 'var(--purple-neon)')}
            <span>Current Phone Clipboard</span>
          </span>
        </div>
        <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 14px; font-size: 13px; color: #fff; min-height: 80px;">
          ${latestClipboard || '<span style="color: var(--text-muted);">No text in phone clipboard</span>'}
        </div>
        <button class="btn-luxury" id="btn-copy-clipboard" style="align-self: flex-start;">
          ${phoneToMacCopied ? `${Icons.Check(14, 'var(--emerald-ion)')} <span>Copied to Mac</span>` : 'Copy to Mac'}
        </button>
      </div>
    </div>

    <!-- Universal Clipboard Stream -->
    <div class="glass-card" style="margin-top: 14px;">
      <div class="card-title-row" style="margin-bottom: 12px;">
        <span class="card-title">
          ${Icons.Layers(15, 'var(--accent-solar)')}
          <span>Unified Sync History</span>
        </span>
        <span style="font-size: 11px; color: var(--text-muted);">
          Preserved seamlessly during wired/wireless transitions
        </span>
      </div>

      ${
        clipboardHistory.length === 0
          ? `<div style="padding: 24px; text-align: center; color: var(--text-muted); font-size: 12px;">
              No synced clipboard history yet. Copy on your phone or send text from Mac above.
            </div>`
          : `<div style="display: flex; flex-direction: column; gap: 8px;">
              ${clipboardHistory
                .map(
                  (item) => `
                <div class="clipboard-history-row" style="display: flex; align-items: center; justify-content: space-between; background: rgba(255,255,255,0.02); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 10px 14px;">
                  <div style="display: flex; align-items: center; gap: 10px; overflow: hidden; margin-right: 12px;">
                    <span class="badge" style="font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; background: ${
                      item.source.includes('Mac')
                        ? 'rgba(255,110,50,0.15); color: var(--accent-solar);'
                        : item.source.includes('Remote')
                        ? 'rgba(192,132,252,0.15); color: var(--purple-neon);'
                        : 'rgba(16,185,129,0.15); color: var(--emerald-ion);'
                    }">
                      ${item.source}
                    </span>
                    <span style="font-size: 12.5px; color: #fff; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                      ${item.text}
                    </span>
                  </div>
                  <div style="display: flex; align-items: center; gap: 10px; flex-shrink: 0;">
                    <span style="font-size: 11px; color: var(--text-muted);">${item.time}</span>
                    <button class="btn-copy-history-item btn-luxury" data-text="${encodeURIComponent(item.text)}" style="padding: 4px 10px; font-size: 11px;">
                      Copy
                    </button>
                  </div>
                </div>
              `
                )
                .join('')}
            </div>`
      }
    </div>
  `;
}

function getParentDir(dir: string): string {
  const parts = dir.replace(/\/+$/, '').split('/').filter(Boolean);
  if (parts.length <= 1) return '/sdcard';
  parts.pop();
  return '/' + parts.join('/');
}

// MARK: - Files View (Full Phone File Manager)
function renderFilesView() {
  const isConnected = device.status === 'Connected';

  // Build breadcrumbs
  const normalizedPath = currentRemoteDir.replace(/\/+$/, '') || '/sdcard';
  const pathParts = normalizedPath.split('/').filter(Boolean);
  
  let accumulated = '';
  const breadcrumbItems = pathParts.map((part, index) => {
    accumulated += '/' + part;
    const isLast = index === pathParts.length - 1;
    return {
      name: part === 'sdcard' ? 'Internal Storage' : part,
      path: accumulated,
      isLast,
    };
  });

  const isAllSelected = phoneFiles.length > 0 && selectedFiles.size === phoneFiles.length;

  return `
    <div class="workspace-sticky-top" id="workspace-sticky-top">
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Files</h1>
          <p>Browse phone storage, navigate directories, and save files to Mac</p>
        </div>
        <div class="header-action-group">
          <button class="btn-luxury" id="btn-refresh-files">
            ${Icons.Refresh(13, '#ffffff')}
            <span>Refresh</span>
          </button>
        </div>
      </div>

      <!-- Quick Folder Shortcuts & Breadcrumbs Strip -->
      <div class="folder-breadcrumbs-bar" style="margin-top: 4px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px;">
        <div style="display: flex; align-items: center; gap: 6px; flex-wrap: wrap;">
          <span style="font-size: 11px; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.5px; margin-right: 4px;">Location:</span>
          <button class="crumb-segment ${normalizedPath === '/sdcard' ? 'active' : ''}" data-nav-path="/sdcard">
            ${Icons.Files(12, 'currentColor')}
            <span>Root (/sdcard)</span>
          </button>
          ${breadcrumbItems
            .filter((b) => b.path !== '/sdcard')
            .map(
              (b) => `
            <span style="color: var(--text-muted); font-size: 11px;">/</span>
            <button class="crumb-segment ${b.isLast ? 'active' : ''}" data-nav-path="${b.path}">
              <span>${b.name}</span>
            </button>
          `
            )
            .join('')}
        </div>

        <div style="display: flex; align-items: center; gap: 6px;">
          <button class="quick-folder-chip ${normalizedPath === '/sdcard/Download' ? 'active' : ''}" data-nav-path="/sdcard/Download">
            Downloads
          </button>
          <button class="quick-folder-chip ${normalizedPath === '/sdcard/Documents' ? 'active' : ''}" data-nav-path="/sdcard/Documents">
            Documents
          </button>
          <button class="quick-folder-chip ${normalizedPath === '/sdcard/DCIM' ? 'active' : ''}" data-nav-path="/sdcard/DCIM">
            DCIM
          </button>
          <button class="quick-folder-chip ${normalizedPath === '/sdcard/Pictures' ? 'active' : ''}" data-nav-path="/sdcard/Pictures">
            Pictures
          </button>
          <button class="quick-folder-chip ${normalizedPath === '/sdcard' ? 'active' : ''}" data-nav-path="/sdcard">
            All Storage
          </button>
        </div>
      </div>

      <!-- Multi-select / Status Banner -->
      ${
        selectedFiles.size > 0
          ? `
        <div class="multi-select-banner" style="margin-top: 4px;">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span class="badge" style="background: rgba(255, 110, 50, 0.2); color: var(--accent-solar); font-weight: 700; padding: 4px 10px; border-radius: 6px; font-size: 12px;">
              ${selectedFiles.size} item${selectedFiles.size > 1 ? 's' : ''} selected
            </span>
            <span style="font-size: 12px; color: var(--text-secondary);">Save directly to your Mac</span>
          </div>
          <div style="display: flex; align-items: center; gap: 8px;">
            <button class="btn-luxury" id="btn-clear-selected-files" style="padding: 5px 12px; font-size: 11.5px;">
              Clear
            </button>
            <button class="btn-solar" id="btn-save-selected-files" style="padding: 5px 14px; font-size: 12px;" ${isSavingFiles ? 'disabled' : ''}>
              ${Icons.Download(13, '#ffffff')}
              <span>${isSavingFiles ? 'Saving to Mac...' : 'Save Selected to Mac...'}</span>
            </button>
          </div>
        </div>
      `
          : ''
      }

      ${
        fileOperationMsg
          ? `
        <div style="margin-top: 4px; padding: 8px 14px; border-radius: 8px; background: rgba(16, 185, 129, 0.12); border: 1px solid rgba(16, 185, 129, 0.3); color: var(--emerald-ion); font-size: 12px; display: flex; align-items: center; gap: 8px;">
          ${Icons.ShieldCheck(14, 'var(--emerald-ion)')}
          <span>${fileOperationMsg}</span>
        </div>
      `
          : ''
      }
    </div>

    <div class="glass-card" style="margin-top: 6px;">
      ${
        !isConnected
          ? `
          <div style="padding: 40px; text-align: center;">
            <p style="color: var(--text-secondary); font-size: 13px;">Connect your phone over Wi-Fi to browse files</p>
          </div>
          `
          : isLoadingFiles
          ? '<div style="padding: 36px; text-align: center; color: var(--text-secondary);"><span class="pulse-dot"></span> Reading directory from phone...</div>'
          : phoneFiles.length === 0
          ? `
          <div style="padding: 36px; text-align: center;">
            <p style="color: var(--text-secondary); margin-bottom: 12px;">This folder is empty</p>
            ${
              normalizedPath !== '/sdcard'
                ? `<button class="btn-luxury btn-nav-parent" data-parent-path="${getParentDir(normalizedPath)}" style="margin: 0 auto;">
                    ← Go to Parent Folder
                  </button>`
                : ''
            }
          </div>
          `
          : `
          <div style="overflow-x: auto;">
            <table style="width: 100%; border-collapse: collapse; font-size: 12.5px;">
              <thead>
                <tr style="border-bottom: 1px solid var(--border-subtle); text-align: left; color: var(--text-muted); font-size: 11px; text-transform: uppercase;">
                  <th style="padding: 10px 12px; width: 38px; min-width: 38px;">
                    <input type="checkbox" id="check-select-all-files" ${isAllSelected ? 'checked' : ''} style="cursor: pointer; accent-color: var(--accent-solar);" title="Select All" />
                  </th>
                  <th style="padding: 10px 12px;">Name</th>
                  <th style="padding: 10px 12px; width: 90px; min-width: 90px;">Size</th>
                  <th style="padding: 10px 12px; width: 145px; min-width: 145px;">Date</th>
                  <th style="padding: 10px 12px; text-align: right; width: 135px; min-width: 135px; white-space: nowrap;">Action</th>
                </tr>
              </thead>
              <tbody>
                ${
                  normalizedPath !== '/sdcard' && normalizedPath !== '/'
                    ? `
                  <tr class="folder-row-up" data-parent-path="${getParentDir(normalizedPath)}" style="border-bottom: 1px solid rgba(255,255,255,0.02); cursor: pointer;">
                    <td style="padding: 10px 12px; width: 38px;"></td>
                    <td style="padding: 10px 12px; color: var(--accent-solar); font-weight: 600; display: flex; align-items: center; gap: 8px;">
                      <span>📁</span>
                      <span>.. (Parent Folder)</span>
                    </td>
                    <td style="padding: 10px 12px; color: var(--text-muted); font-size: 11px; width: 90px;">Folder</td>
                    <td style="padding: 10px 12px; color: var(--text-muted); font-size: 11px; width: 145px;">—</td>
                    <td style="padding: 10px 12px; text-align: right; width: 135px; white-space: nowrap;">
                      <span style="font-size: 11px; color: var(--text-muted);">Navigate up</span>
                    </td>
                  </tr>
                `
                    : ''
                }
                ${phoneFiles
                  .map((f) => {
                    const isSelected = selectedFiles.has(f.path);
                    return `
                  <tr style="border-bottom: 1px solid rgba(255,255,255,0.02); ${isSelected ? 'background: rgba(255, 110, 50, 0.05);' : ''}">
                    <td style="padding: 8px 12px; width: 38px;">
                      <input type="checkbox" class="check-file-select" data-filepath="${f.path}" ${isSelected ? 'checked' : ''} style="cursor: pointer; accent-color: var(--accent-solar);" />
                    </td>
                    <td style="padding: 8px 12px; color: #fff; font-weight: 500;">
                      ${
                        f.is_dir
                          ? `
                        <div class="folder-click-target" data-open-dir="${f.path}" style="display: flex; align-items: center; gap: 8px; cursor: pointer; color: var(--accent-solar);" title="Open folder">
                          ${Icons.Files(15, 'var(--accent-solar)')}
                          <span style="text-decoration: underline; text-underline-offset: 3px;">${f.name}</span>
                        </div>
                      `
                          : `
                        <div style="display: flex; align-items: center; gap: 8px;">
                          ${Icons.Files(15, 'var(--text-secondary)')}
                          <span>${f.name}</span>
                        </div>
                      `
                      }
                    </td>
                    <td style="padding: 8px 12px; color: var(--text-secondary); width: 90px; font-size: 12px;">${f.size}</td>
                    <td style="padding: 8px 12px; color: var(--text-muted); font-size: 11.5px; width: 145px;">${f.modified}</td>
                    <td style="padding: 8px 12px; text-align: right; width: 135px; white-space: nowrap;">
                      <button class="btn-luxury btn-save-file-single" data-filepath="${f.path}" data-filename="${f.name}" style="padding: 5px 12px; font-size: 11.5px; white-space: nowrap; display: inline-flex; align-items: center; gap: 6px;">
                        ${Icons.Download(12, 'currentColor')}
                        <span>Save to Mac</span>
                      </button>
                    </td>
                  </tr>
                `;
                  })
                  .join('')}
              </tbody>
            </table>
          </div>
          `
      }
    </div>
  `;
}

// MARK: - Photos View (Photo Sharing & Manager)
function renderPhotosView() {
  const isConnected = device.status === 'Connected';

  const ALBUMS = [
    { key: 'all', label: 'All Media' },
    { key: 'Camera', label: 'Camera' },
    { key: 'Screenshots', label: 'Screenshots' },
    { key: 'WhatsApp', label: 'WhatsApp' },
    { key: 'Instagram', label: 'Instagram' },
    { key: 'Download', label: 'Downloads' },
  ];

  const isAllSelected = phonePhotos.length > 0 && selectedPhotos.size === phonePhotos.length;

  return `
    <div class="workspace-sticky-top" id="workspace-sticky-top">
      <div class="workspace-header">
        <div class="workspace-title-box">
          <h1>Photos</h1>
          <p>Browse phone photo albums, preview pictures, and save directly to Mac</p>
        </div>
        <div class="header-action-group">
          <button class="btn-luxury" id="btn-refresh-photos">
            ${Icons.Refresh(13, '#ffffff')}
            <span>Refresh</span>
          </button>
        </div>
      </div>

      <!-- Album Switcher Strip -->
      <div class="album-switcher-strip">
        <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
          <span style="font-size: 11px; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.5px; margin-right: 2px;">Album:</span>
          ${ALBUMS.map(
            (a) => `
            <button class="album-pill-chip ${currentPhotoAlbum === a.key ? 'active' : ''}" data-album="${a.key}">
              <span>${a.label}</span>
            </button>
          `
          ).join('')}
        </div>

        <div style="display: flex; align-items: center; gap: 8px;">
          <button class="btn-luxury" id="btn-select-all-photos" style="padding: 5px 12px; font-size: 11.5px;">
            ${isAllSelected ? 'Deselect All' : 'Select All'}
          </button>
        </div>
      </div>

      <!-- Multi-select Banner -->
      ${
        selectedPhotos.size > 0
          ? `
        <div class="multi-select-banner" style="margin-top: 4px;">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span class="badge" style="background: rgba(255, 110, 50, 0.2); color: var(--accent-solar); font-weight: 700; padding: 4px 10px; border-radius: 6px; font-size: 12px;">
              ${selectedPhotos.size} photo${selectedPhotos.size > 1 ? 's' : ''} selected
            </span>
            <span style="font-size: 12px; color: var(--text-secondary);">Save selected to your Mac</span>
          </div>
          <div style="display: flex; align-items: center; gap: 8px;">
            <button class="btn-luxury" id="btn-clear-selected-photos" style="padding: 5px 12px; font-size: 11.5px;">
              Clear
            </button>
            <button class="btn-solar" id="btn-save-selected-photos" style="padding: 5px 14px; font-size: 12px;" ${isSavingPhotos ? 'disabled' : ''}>
              ${Icons.Download(13, '#ffffff')}
              <span>${isSavingPhotos ? 'Saving to Mac...' : 'Save Selected to Mac...'}</span>
            </button>
          </div>
        </div>
      `
          : ''
      }

      ${
        photoOperationMsg
          ? `
        <div style="margin-top: 4px; padding: 8px 14px; border-radius: 8px; background: rgba(16, 185, 129, 0.12); border: 1px solid rgba(16, 185, 129, 0.3); color: var(--emerald-ion); font-size: 12px; display: flex; align-items: center; gap: 8px;">
          ${Icons.ShieldCheck(14, 'var(--emerald-ion)')}
          <span>${photoOperationMsg}</span>
        </div>
      `
          : ''
      }
    </div>

    <div class="glass-card" style="margin-top: 6px;">
      ${
        !isConnected
          ? `
          <div style="padding: 40px; text-align: center;">
            <p style="color: var(--text-secondary); font-size: 13px;">Connect your phone over Wi-Fi to view photos</p>
          </div>
          `
          : isLoadingPhotos
          ? '<div style="padding: 36px; text-align: center; color: var(--text-secondary);"><span class="pulse-dot"></span> Reading media from phone...</div>'
          : phonePhotos.length === 0
          ? `
          <div style="padding: 36px; text-align: center;">
            <p style="color: var(--text-secondary); margin-bottom: 12px;">No photos found in ${currentPhotoAlbum === 'all' ? 'phone' : currentPhotoAlbum} folder</p>
            <button class="btn-luxury" id="btn-load-photos-now" style="margin: 0 auto;">Read Photos</button>
          </div>
          `
          : `
          <div class="photo-manager-grid">
            ${phonePhotos
              .map((p) => {
                const isSelected = selectedPhotos.has(p.path);
                const thumbData = photoThumbnails.get(p.path);
                return `
              <div class="photo-manager-card ${isSelected ? 'selected' : ''}" data-photopath="${p.path}">
                <div class="photo-thumb-container">
                  <input type="checkbox" class="photo-checkbox-corner" data-photopath="${p.path}" ${isSelected ? 'checked' : ''} />
                  ${
                    thumbData
                      ? `<img src="data:image/jpeg;base64,${thumbData}" class="photo-thumb-img" alt="${p.name}" draggable="false" />`
                      : `<div class="photo-thumb-img" style="display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.03); color: var(--accent-solar);" data-thumb-path="${p.path}">
                          ${Icons.Photos(32, 'currentColor')}
                        </div>`
                  }
                  <button class="photo-preview-btn btn-preview-photo" data-photopath="${p.path}" title="Enlarge preview">
                    🔍
                  </button>
                </div>
                <div class="photo-card-body">
                  <div class="photo-card-name" title="${p.name}">${p.name}</div>
                  <div class="photo-card-meta">
                    <span>${p.size}</span>
                    <span class="photo-album-tag">${p.album || 'Media'}</span>
                  </div>
                  <div style="display: flex; gap: 6px; margin-top: 4px;">
                    <button class="btn-luxury btn-save-photo-single" data-photopath="${p.path}" data-filename="${p.name}" style="flex: 1; padding: 4px 6px; font-size: 11px; justify-content: center; white-space: nowrap;">
                      ${Icons.Download(11, 'currentColor')}
                      <span>Save to Mac</span>
                    </button>
                  </div>
                </div>
              </div>
            `;
              })
              .join('')}
          </div>
          `
      }
    </div>
  `;
}

// MARK: - Messages View
function renderMessagesView() {
  return `
    <div class="workspace-header">
      <div class="workspace-title-box">
        <h1>Messages & SMS</h1>
        <p>Send and receive text messages directly from your Mac</p>
      </div>
    </div>

    <div class="glass-card" style="margin-top: 14px; padding: 32px; text-align: center;">
      <div style="width: 56px; height: 56px; border-radius: 16px; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; margin: 0 auto 16px auto; color: var(--accent-solar);">
        ${Icons.Messages(28, 'currentColor')}
      </div>
      <h3 style="font-size: 16px; font-weight: 700; color: #fff;">SMS Sync Ready</h3>
      <p style="font-size: 12.5px; color: var(--text-secondary); max-width: 400px; margin: 6px auto 16px auto;">
        ${device.status === 'Connected' ? 'Connected to phone. You can launch Messages directly in mirror mode to read all conversations.' : 'Connect your phone over Wi-Fi to sync threads.'}
      </p>
      <button class="btn-solar" id="btn-open-messages-app" style="margin: 0 auto;">
        Launch Messages in Mirror
      </button>
    </div>
  `;
}

// MARK: - Clean, User-Friendly Settings View (No Tech Ports or Tailscale IP Jargon)
function renderSettingsView() {
  return `
    <div class="workspace-header">
      <div class="workspace-title-box">
        <h1>Settings</h1>
        <p>Customize mirroring quality, audio preferences, and application behavior</p>
      </div>
    </div>

    <div class="dual-grid" style="margin-top: 14px;">
      <!-- Mirroring Quality Preferences -->
      <div class="glass-card">
        <div class="card-title">
          ${Icons.Laptop(15, 'var(--accent-solar)')}
          <span>Screen Mirror Quality</span>
        </div>
        <p style="font-size: 12px; color: var(--text-secondary);">
          Choose target streaming resolution and framerate.
        </p>

        <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 6px;">
          <div style="display: flex; justify-content: space-between; align-items: center; font-size: 12.5px;">
            <span>Resolution</span>
            <select style="background: rgba(255,255,255,0.06); border: 1px solid var(--border-subtle); color: #fff; padding: 5px 10px; border-radius: var(--radius-sm); font-size: 12px; outline: none;">
              <option selected>1080p (Full HD)</option>
              <option>1440p (Quad HD)</option>
              <option>720p (Battery Saver)</option>
            </select>
          </div>

          <div style="display: flex; justify-content: space-between; align-items: center; font-size: 12.5px;">
            <span>Target Framerate</span>
            <select style="background: rgba(255,255,255,0.06); border: 1px solid var(--border-subtle); color: #fff; padding: 5px 10px; border-radius: var(--radius-sm); font-size: 12px; outline: none;">
              <option selected>60 FPS (Smooth)</option>
              <option>30 FPS (Standard)</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Application Preferences -->
      <div class="glass-card">
        <div class="card-title">
          ${Icons.Settings(15, 'var(--text-secondary)')}
          <span>Preferences</span>
        </div>
        
        <div style="display: flex; flex-direction: column; gap: 12px; margin-top: 6px;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-size: 12.5px; font-weight: 500; color: #fff;">Keep Mirror Window on Top</div>
              <div style="font-size: 11px; color: var(--text-secondary);">Pin stream above other windows</div>
            </div>
            <label class="switch">
              <input type="checkbox" />
              <span class="slider"></span>
            </label>
          </div>

          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-size: 12.5px; font-weight: 500; color: #fff;">Play Phone Audio on Mac</div>
              <div style="font-size: 11px; color: var(--text-secondary);">Stream music and call audio to Mac speakers</div>
            </div>
            <label class="switch">
              <input type="checkbox" checked />
              <span class="slider"></span>
            </label>
          </div>

          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-size: 12.5px; font-weight: 500; color: #fff;">Auto-Connect on Known Wi-Fi</div>
              <div style="font-size: 11px; color: var(--text-secondary);">Detect phone wirelessly when connected to local network</div>
            </div>
            <label class="switch">
              <input type="checkbox" checked />
              <span class="slider"></span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- About Section -->
    <div class="glass-card" style="margin-top: 14px;">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div>
          <div style="font-size: 13.5px; font-weight: 700; color: #fff;">Idroid Desktop</div>
          <div style="font-size: 11.5px; color: var(--text-secondary); margin-top: 2px;">Version 1.0.0 • Universal macOS, Windows & Linux Core</div>
        </div>
        <button class="btn-luxury" style="font-size: 11.5px;">Check for Updates</button>
      </div>
    </div>
  `;
}

// MARK: - Event Listeners
function attachEventListeners() {
  // Sidebar Collapse Toggle (expanded button)
  const btnToggleSidebar = document.getElementById('btn-toggle-sidebar');
  if (btnToggleSidebar) {
    btnToggleSidebar.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleSidebarCollapse();
    });
  }

  // Combined Brand / Expand Icon (in collapsed state, transforms and expands on click)
  const btnBrandMonogram = document.getElementById('btn-brand-monogram');
  if (btnBrandMonogram) {
    btnBrandMonogram.addEventListener('click', (e) => {
      e.stopPropagation();
      if (isSidebarCollapsed) {
        toggleSidebarCollapse();
      } else {
        currentSection = 'home';
        render();
      }
    });
  }

  // Navigation
  document.querySelectorAll('.nav-item').forEach((el) => {
    el.addEventListener('click', () => {
      const target = el.getAttribute('data-section');
      if (target) {
        currentSection = target;
        if (target === 'mirror' && phoneApps.length === 0) {
          fetchPhoneApps();
        } else if (target === 'files') {
          fetchPhoneFiles();
        } else if (target === 'photos') {
          fetchPhonePhotos();
        } else if (target === 'calls' || target === 'notifications') {
          fetchCallsAndNotifications();
        }
        render();
        const mainEl = document.querySelector('.main-content');
        if (mainEl) mainEl.scrollTop = 0;
      }
    });
  });

  // Mirror triggers
  const btnMirrorHero = document.getElementById('btn-mirror-hero');
  if (btnMirrorHero) btnMirrorHero.addEventListener('click', () => handleStartMirror());

  const btnMirrorCard = document.getElementById('btn-mirror-card');
  if (btnMirrorCard) btnMirrorCard.addEventListener('click', () => handleStartMirror());

  const btnMirrorQuick = document.getElementById('btn-mirror-quick');
  if (btnMirrorQuick) btnMirrorQuick.addEventListener('click', () => handleStartMirror());

  const btnLaunchFullMirror = document.getElementById('btn-launch-full-mirror');
  if (btnLaunchFullMirror) btnLaunchFullMirror.addEventListener('click', () => handleStartMirror());

  const btnReopenCam = document.getElementById('btn-reopen-camera-player');
  if (btnReopenCam) btnReopenCam.addEventListener('click', () => handleFocusCameraPlayer());

  const btnReopenCamHero = document.getElementById('btn-reopen-camera-player-hero');
  if (btnReopenCamHero) btnReopenCamHero.addEventListener('click', () => handleFocusCameraPlayer());

  const btnMirrorCam = document.getElementById('btn-mirror-camera-app');
  if (btnMirrorCam) btnMirrorCam.addEventListener('click', () => handleStartMirror());

  const btnMirrorCamHero = document.getElementById('btn-mirror-camera-app-hero');
  if (btnMirrorCamHero) btnMirrorCamHero.addEventListener('click', () => handleStartMirror());

  const btnGoToMirror = document.getElementById('btn-go-to-mirror');
  if (btnGoToMirror) {
    btnGoToMirror.addEventListener('click', () => {
      currentSection = 'mirror';
      fetchPhoneApps();
      render();
    });
  }

  const btnNavMirror = document.getElementById('btn-nav-mirror');
  if (btnNavMirror) {
    btnNavMirror.addEventListener('click', () => {
      currentSection = 'mirror';
      fetchPhoneApps();
      render();
    });
  }

  const btnOpenFiles = document.getElementById('btn-open-files');
  if (btnOpenFiles) {
    btnOpenFiles.addEventListener('click', () => {
      currentSection = 'files';
      fetchPhoneFiles();
      render();
    });
  }


  const btnGoToClipboard = document.getElementById('btn-go-to-clipboard');
  if (btnGoToClipboard) {
    btnGoToClipboard.addEventListener('click', () => {
      currentSection = 'clipboard';
      render();
    });
  }

  const btnRefreshTelemetry = document.getElementById('btn-refresh-telemetry');
  if (btnRefreshTelemetry) {
    btnRefreshTelemetry.addEventListener('click', pollDeviceTelemetry);
  }

  // Mirror Preferences Toggles
  const toggleAudio = document.getElementById('toggle-audio') as HTMLInputElement;
  if (toggleAudio) {
    toggleAudio.addEventListener('change', (e) => {
      isAudioActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_mirror_audio', String(isAudioActive)); } catch (_) {}
      render();
    });
  }

  const toggleScreenOff = document.getElementById('toggle-screenoff') as HTMLInputElement;
  if (toggleScreenOff) {
    toggleScreenOff.addEventListener('change', (e) => {
      isScreenOffModeActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_mirror_screenoff', String(isScreenOffModeActive)); } catch (_) {}
      render();
    });
  }

  const toggleVDisplay = document.getElementById('toggle-vdisplay') as HTMLInputElement;
  if (toggleVDisplay) {
    toggleVDisplay.addEventListener('change', (e) => {
      isVirtualDisplayActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_mirror_vdisplay', String(isVirtualDisplayActive)); } catch (_) {}
      render();
    });
  }

  const toggleAlwaysOnTop = (document.getElementById('toggle-always-on-top') || document.getElementById('toggle-handle')) as HTMLInputElement;
  if (toggleAlwaysOnTop) {
    toggleAlwaysOnTop.addEventListener('change', (e) => {
      isAlwaysOnTopActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_mirror_always_on_top', String(isAlwaysOnTopActive)); } catch (_) {}
      render();
    });
  }

  const toggleDockNav = document.getElementById('toggle-dock-nav') as HTMLInputElement;
  if (toggleDockNav) {
    toggleDockNav.addEventListener('change', async (e) => {
      isDockNavActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_dock_nav_bar', String(isDockNavActive)); } catch (_) {}
      const inv = await getInvoke();
      if (inv) inv('sync_mirror_dock', { visible: isDockNavActive });
      render();
    });
  }

  const togglePhoneFrame = document.getElementById('toggle-phone-frame') as HTMLInputElement;
  if (togglePhoneFrame) {
    togglePhoneFrame.addEventListener('change', async (e) => {
      isPhoneFrameActive = (e.target as HTMLInputElement).checked;
      try { localStorage.setItem('idroid_phone_frame', String(isPhoneFrameActive)); } catch (_) {}
      const inv = await getInvoke();
      if (inv) inv('sync_mirror_frame', { visible: isPhoneFrameActive });
      render();
    });
  }

  // Navigation Button Chips (Tick/Untick)
  document.querySelectorAll('.nav-toggle-chip').forEach((chip) => {
    chip.addEventListener('click', async (e) => {
      const el = e.currentTarget as HTMLElement;
      const key = el.getAttribute('data-nav-key');
      if (!key) return;
      if (enabledNavButtons.includes(key)) {
        enabledNavButtons = enabledNavButtons.filter((k) => k !== key);
      } else {
        enabledNavButtons.push(key);
      }
      try { localStorage.setItem('idroid_nav_buttons', JSON.stringify(enabledNavButtons)); } catch (_) {}
      const inv = await getInvoke();
      if (inv && isDockNavActive) {
        inv('sync_mirror_dock', { visible: true });
      }
      render();
    });
  });

  const btnSelectAllNav = document.querySelector('.btn-select-all-nav');
  if (btnSelectAllNav) {
    btnSelectAllNav.addEventListener('click', async () => {
      enabledNavButtons = ALL_NAV_BUTTONS.map((b) => b.key);
      try { localStorage.setItem('idroid_nav_buttons', JSON.stringify(enabledNavButtons)); } catch (_) {}
      const inv = await getInvoke();
      if (inv && isDockNavActive) {
        inv('sync_mirror_dock', { visible: true });
      }
      render();
    });
  }

  const btnResetNav = document.querySelector('.btn-reset-nav');
  if (btnResetNav) {
    btnResetNav.addEventListener('click', async () => {
      enabledNavButtons = [...DEFAULT_NAV_BUTTONS];
      try { localStorage.setItem('idroid_nav_buttons', JSON.stringify(enabledNavButtons)); } catch (_) {}
      const inv = await getInvoke();
      if (inv && isDockNavActive) {
        inv('sync_mirror_dock', { visible: true });
      }
      render();
    });
  }

  // Phone Hardware Navigation Deck buttons
  document.querySelectorAll('.btn-phone-key').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const key = el.getAttribute('data-key');
      if (key) {
        getInvoke().then((invoke) => invoke && invoke('send_phone_key', { key }));
      }
    });
  });

  // Launch App buttons
  document.querySelectorAll('.btn-launch-app').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const pkg = el.getAttribute('data-package');
      const appName = el.getAttribute('data-appname') || 'Application';
      const iconSrc = el.getAttribute('data-iconsrc') || undefined;
      if (pkg) handleLaunchApp(pkg, appName, iconSrc);
    });
  });

  // App search
  const inputSearch = document.getElementById('input-search-apps') as HTMLInputElement;
  if (inputSearch) {
    inputSearch.addEventListener('input', (e) => {
      appSearchQuery = (e.target as HTMLInputElement).value;
      render();
      setTimeout(() => {
        const inputNow = document.getElementById('input-search-apps') as HTMLInputElement;
        if (inputNow) {
          inputNow.focus();
          inputNow.selectionStart = inputNow.selectionEnd = inputNow.value.length;
        }
      }, 0);
    });
  }

  // Apps refresh
  const btnRefreshApps = document.getElementById('btn-refresh-apps');
  if (btnRefreshApps) btnRefreshApps.addEventListener('click', fetchPhoneApps);

  // Keypad Digits & Direct Keyboard Input on Dialer
  const inputDialer = document.getElementById('input-dialer-number') as HTMLInputElement;
  if (inputDialer) {
    inputDialer.addEventListener('input', (e) => {
      const val = (e.target as HTMLInputElement).value;
      const sanitized = val.replace(/[^0-9+*#\s-]/g, '');
      dialerInputNumber = sanitized;
      if (val !== sanitized) {
        (e.target as HTMLInputElement).value = sanitized;
      }
    });

    inputDialer.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleStartCall();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        handleDialClear();
      }
    });
  }

  document.querySelectorAll('.btn-keypad-digit').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const digit = (e.currentTarget as HTMLElement).getAttribute('data-digit');
      if (digit) handleDialDigit(digit);
    });
  });

  const btnClearDialer = document.getElementById('btn-dialer-clear');
  if (btnClearDialer) btnClearDialer.addEventListener('click', handleDialClear);

  const btnBackspace = document.getElementById('btn-dialer-backspace');
  if (btnBackspace) btnBackspace.addEventListener('click', handleDialBackspace);

  const btnTriggerCall = document.getElementById('btn-trigger-call');
  if (btnTriggerCall) btnTriggerCall.addEventListener('click', () => handleStartCall());

  const btnEndActiveCall = document.getElementById('btn-end-active-call');
  if (btnEndActiveCall) btnEndActiveCall.addEventListener('click', handleEndCall);

  // Floating In-Call HUD Controls (Mute, Single-Button Route Switch, Record, Conference, End)
  const btnHudSwitch = document.getElementById('btn-hud-switch-device');
  if (btnHudSwitch) btnHudSwitch.addEventListener('click', () => handleToggleCallAudioRoute());

  const btnHudMute = document.getElementById('btn-hud-mute');
  if (btnHudMute) btnHudMute.addEventListener('click', handleToggleCallMute);

  const btnHudRecord = document.getElementById('btn-hud-record');
  if (btnHudRecord) btnHudRecord.addEventListener('click', handleToggleCallRecording);

  const btnHudAddPerson = document.getElementById('btn-hud-add-person');
  if (btnHudAddPerson) {
    btnHudAddPerson.addEventListener('click', () => {
      isAddPersonModalOpen = !isAddPersonModalOpen;
      render();
    });
  }

  const btnHudCancelConf = document.getElementById('btn-hud-cancel-conference');
  if (btnHudCancelConf) {
    btnHudCancelConf.addEventListener('click', () => {
      isAddPersonModalOpen = false;
      render();
    });
  }

  const btnHudDialConf = document.getElementById('btn-hud-dial-conference');
  if (btnHudDialConf) {
    btnHudDialConf.addEventListener('click', () => {
      const input = document.getElementById('input-hud-conference-number') as HTMLInputElement;
      if (input && input.value.trim()) {
        handleAddConferenceParticipant(input.value.trim());
      }
    });
  }

  const btnHudEndCall = document.getElementById('btn-hud-end-call');
  if (btnHudEndCall) btnHudEndCall.addEventListener('click', handleEndCall);

  // Incoming Call Alert Actions (Accept on Mac / Decline)
  const btnAcceptCall = document.getElementById('btn-accept-incoming-call');
  if (btnAcceptCall) btnAcceptCall.addEventListener('click', () => handleAcceptIncomingCall());

  const btnDeclineCall = document.getElementById('btn-decline-incoming-call');
  if (btnDeclineCall) btnDeclineCall.addEventListener('click', handleDeclineIncomingCall);

  // Simulate Incoming Call (for testing and demo)
  const btnSimulateCall = document.getElementById('btn-simulate-incoming-call');
  if (btnSimulateCall) btnSimulateCall.addEventListener('click', handleSimulateIncomingCall);

  // Quick Contacts Speed Dial & Actions
  document.querySelectorAll('.btn-delete-fav').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const id = (e.currentTarget as HTMLElement).getAttribute('data-fav-id');
      if (id) handleRemoveFavouriteContact(id);
    });
  });

  const btnOpenAddFav = document.getElementById('btn-open-add-fav') || document.getElementById('btn-empty-add-fav');
  if (btnOpenAddFav) {
    btnOpenAddFav.addEventListener('click', () => {
      newFavName = '';
      newFavNumber = '';
      isAddFavouriteModalOpen = true;
      render();
    });
  }

  // Save Contact from Call History Row
  document.querySelectorAll('.btn-add-history-contact').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const num = el.getAttribute('data-number') || '';
      const name = el.getAttribute('data-name') || '';
      newFavName = name !== num ? name : '';
      newFavNumber = num;
      isAddFavouriteModalOpen = true;
      render();
    });
  });

  const btnCloseFavModal = document.getElementById('btn-close-fav-modal');
  if (btnCloseFavModal) {
    btnCloseFavModal.addEventListener('click', () => {
      isAddFavouriteModalOpen = false;
      render();
    });
  }

  const btnCancelFavModal = document.getElementById('btn-cancel-fav-modal');
  if (btnCancelFavModal) {
    btnCancelFavModal.addEventListener('click', () => {
      isAddFavouriteModalOpen = false;
      render();
    });
  }

  const favModalBackdrop = document.getElementById('fav-modal-backdrop');
  if (favModalBackdrop) {
    favModalBackdrop.addEventListener('click', (e) => {
      if (e.target === favModalBackdrop) {
        isAddFavouriteModalOpen = false;
        render();
      }
    });
  }

  const btnSaveFavModal = document.getElementById('btn-save-fav-modal');
  if (btnSaveFavModal) {
    btnSaveFavModal.addEventListener('click', () => {
      const nameInput = document.getElementById('input-fav-name') as HTMLInputElement;
      const numInput = document.getElementById('input-fav-number') as HTMLInputElement;
      if (nameInput && numInput && nameInput.value.trim() && numInput.value.trim()) {
        handleAddFavouriteContact(nameInput.value.trim(), numInput.value.trim());
      }
    });
  }

  // Call history filter buttons
  document.querySelectorAll('.btn-call-filter').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const filter = (e.currentTarget as HTMLElement).getAttribute('data-filter') as
        | 'all'
        | 'missed'
        | 'outgoing'
        | 'incoming';
      if (filter) {
        callsTabFilter = filter;
        render();
      }
    });
  });

  document.querySelectorAll('.btn-quick-dial').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const num = (e.currentTarget as HTMLElement).getAttribute('data-number');
      if (num) {
        dialerInputNumber = num;
        const inputEl = document.getElementById('input-dialer-number') as HTMLInputElement;
        if (inputEl) inputEl.value = num;
        handleStartCall(num);
      }
    });
  });

  // Notifications: Copy text, quick reply, refresh
  document.querySelectorAll('.btn-copy-notif-text').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const el = e.currentTarget as HTMLElement;
      const textToCopy = decodeURIComponent(el.getAttribute('data-copy-text') || '');
      if (textToCopy) {
        navigator.clipboard.writeText(textToCopy);
        const originalHtml = el.innerHTML;
        el.innerHTML = '✓ Copied!';
        setTimeout(() => {
          el.innerHTML = originalHtml;
        }, 1500);
      }
    });
  });

  const btnRefreshNotifs = document.getElementById('btn-refresh-notifs');
  if (btnRefreshNotifs) btnRefreshNotifs.addEventListener('click', fetchCallsAndNotifications);

  document.querySelectorAll('.btn-send-reply').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const notifId = (e.currentTarget as HTMLElement).getAttribute('data-notif-id');
      const inputId = (e.currentTarget as HTMLElement).getAttribute('data-input-id');
      if (notifId && inputId) handleReplyNotification(notifId, inputId);
    });
  });

  document.querySelectorAll('.btn-open-notif-app').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const pkg = (e.currentTarget as HTMLElement).getAttribute('data-package');
      if (pkg) handleLaunchApp(pkg, 'App from Notification');
    });
  });

  // MARK: - Files Manager Event Handlers
  const btnRefreshFiles = document.getElementById('btn-refresh-files');
  if (btnRefreshFiles) btnRefreshFiles.addEventListener('click', () => fetchPhoneFiles());
  const btnLoadFiles = document.getElementById('btn-load-files-now');
  if (btnLoadFiles) btnLoadFiles.addEventListener('click', () => fetchPhoneFiles());

  // Breadcrumbs & quick folders
  document.querySelectorAll('.crumb-segment, .quick-folder-chip').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-nav-path');
      if (path) {
        currentRemoteDir = path;
        selectedFiles.clear();
        fetchPhoneFiles(currentRemoteDir);
      }
    });
  });

  // Folder click to navigate inside
  document.querySelectorAll('[data-open-dir]').forEach((el) => {
    el.addEventListener('click', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-open-dir');
      if (path) {
        currentRemoteDir = path;
        selectedFiles.clear();
        fetchPhoneFiles(currentRemoteDir);
      }
    });
  });

  // Navigate up to parent folder
  document.querySelectorAll('.folder-row-up, .btn-nav-parent').forEach((el) => {
    el.addEventListener('click', (e) => {
      const parentPath = (e.currentTarget as HTMLElement).getAttribute('data-parent-path');
      if (parentPath) {
        currentRemoteDir = parentPath;
        selectedFiles.clear();
        fetchPhoneFiles(currentRemoteDir);
      }
    });
  });

  // Checkbox selection for files
  document.querySelectorAll('.check-file-select').forEach((cb) => {
    cb.addEventListener('change', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-filepath');
      if (path) {
        if ((e.target as HTMLInputElement).checked) {
          selectedFiles.add(path);
        } else {
          selectedFiles.delete(path);
        }
        render();
      }
    });
  });

  const checkSelectAllFiles = document.getElementById('check-select-all-files') as HTMLInputElement;
  if (checkSelectAllFiles) {
    checkSelectAllFiles.addEventListener('change', (e) => {
      const isChecked = (e.target as HTMLInputElement).checked;
      if (isChecked) {
        phoneFiles.forEach((f) => selectedFiles.add(f.path));
      } else {
        selectedFiles.clear();
      }
      render();
    });
  }

  const btnClearSelectedFiles = document.getElementById('btn-clear-selected-files');
  if (btnClearSelectedFiles) {
    btnClearSelectedFiles.addEventListener('click', () => {
      selectedFiles.clear();
      render();
    });
  }

  const btnSaveSelectedFiles = document.getElementById('btn-save-selected-files');
  if (btnSaveSelectedFiles) {
    btnSaveSelectedFiles.addEventListener('click', () => handleSaveFilesToMac());
  }

  document.querySelectorAll('.btn-save-file-single').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-filepath');
      if (path) handleSaveFilesToMac([path]);
    });
  });

  // MARK: - Photos Manager Event Handlers
  const btnRefreshPhotos = document.getElementById('btn-refresh-photos');
  if (btnRefreshPhotos) btnRefreshPhotos.addEventListener('click', () => fetchPhonePhotos());
  const btnLoadPhotos = document.getElementById('btn-load-photos-now');
  if (btnLoadPhotos) btnLoadPhotos.addEventListener('click', () => fetchPhonePhotos());

  // Album pills
  document.querySelectorAll('.album-pill-chip').forEach((chip) => {
    chip.addEventListener('click', (e) => {
      const album = (e.currentTarget as HTMLElement).getAttribute('data-album');
      if (album) {
        currentPhotoAlbum = album;
        selectedPhotos.clear();
        fetchPhonePhotos(currentPhotoAlbum);
      }
    });
  });

  // Photo checkbox selection
  document.querySelectorAll('.photo-checkbox-corner').forEach((cb) => {
    cb.addEventListener('change', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-photopath');
      if (path) {
        if ((e.target as HTMLInputElement).checked) {
          selectedPhotos.add(path);
        } else {
          selectedPhotos.delete(path);
        }
        render();
      }
    });
  });

  const btnSelectAllPhotos = document.getElementById('btn-select-all-photos');
  if (btnSelectAllPhotos) {
    btnSelectAllPhotos.addEventListener('click', () => {
      if (selectedPhotos.size === phonePhotos.length) {
        selectedPhotos.clear();
      } else {
        phonePhotos.forEach((p) => selectedPhotos.add(p.path));
      }
      render();
    });
  }

  const btnClearSelectedPhotos = document.getElementById('btn-clear-selected-photos');
  if (btnClearSelectedPhotos) {
    btnClearSelectedPhotos.addEventListener('click', () => {
      selectedPhotos.clear();
      render();
    });
  }

  const btnSaveSelectedPhotos = document.getElementById('btn-save-selected-photos');
  if (btnSaveSelectedPhotos) {
    btnSaveSelectedPhotos.addEventListener('click', () => handleSavePhotosToMac());
  }

  document.querySelectorAll('.btn-save-photo-single').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-photopath');
      if (path) handleSavePhotosToMac([path]);
    });
  });

  // Photo Lightbox
  document.querySelectorAll('.btn-preview-photo').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const path = (e.currentTarget as HTMLElement).getAttribute('data-photopath');
      if (path) {
        const found = phonePhotos.find((p) => p.path === path);
        if (found) {
          activeLightboxPhoto = found;
          render();
        }
      }
    });
  });

  const btnCloseLightbox = document.getElementById('btn-close-lightbox');
  if (btnCloseLightbox) {
    btnCloseLightbox.addEventListener('click', () => {
      activeLightboxPhoto = null;
      render();
    });
  }

  const lightboxBackdrop = document.getElementById('lightbox-backdrop');
  if (lightboxBackdrop) {
    lightboxBackdrop.addEventListener('click', (e) => {
      if (e.target === lightboxBackdrop) {
        activeLightboxPhoto = null;
        render();
      }
    });
  }

  // DND Quick Toggle
  const btnDnd = document.getElementById('btn-dnd');
  if (btnDnd) {
    btnDnd.addEventListener('click', async () => {
      isDndActive = !isDndActive;
      render();
      const invoke = await getInvoke();
      if (invoke) {
        try {
          await invoke('toggle_dnd', { active: isDndActive });
        } catch (e) {
          console.error(e);
        }
      }
    });
  }

  // Camera Quick Toggle
  const btnCamera = document.getElementById('btn-camera');
  if (btnCamera) {
    btnCamera.addEventListener('click', () => {
      isCameraActive = !isCameraActive;
      render();
      handleStartMirror('com.sec.android.app.camera');
    });
  }

  // Lock phone
  const btnLock = document.getElementById('btn-lock-phone');
  if (btnLock) {
    btnLock.addEventListener('click', () => {
      getInvoke().then((invoke) => invoke && invoke('send_phone_key', { key: 'power' }));
    });
  }

  // MARK: - Quick Controls & Phone Card Handlers
  document.querySelectorAll('.btn-toggle-qc-view').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const mode = (e.currentTarget as HTMLElement).getAttribute('data-view') as 'tiles' | 'cards';
      if (mode && mode !== quickControlsViewMode) {
        quickControlsViewMode = mode;
        try {
          localStorage.setItem('idroid_quick_controls_view', mode);
        } catch (_) {}
        render();
      }
    });
  });

  const btnHeaderCustomizeQc = document.getElementById('btn-header-customize-qc');
  if (btnHeaderCustomizeQc) {
    btnHeaderCustomizeQc.addEventListener('click', () => {
      isQuickControlsModalOpen = true;
      render();
    });
  }

  const btnDeckAddQc = document.getElementById('btn-deck-add-qc');
  if (btnDeckAddQc) {
    btnDeckAddQc.addEventListener('click', () => {
      isQuickControlsModalOpen = true;
      render();
    });
  }

  const btnDeckAddQcCard = document.getElementById('btn-deck-add-qc-card');
  if (btnDeckAddQcCard) {
    btnDeckAddQcCard.addEventListener('click', () => {
      isQuickControlsModalOpen = true;
      render();
    });
  }

  document.querySelectorAll('.quick-control-tile, .qc-interactive-btn:not(.btn-add-qc)').forEach((tile) => {
    tile.addEventListener('click', (e) => {
      const qcid = (e.currentTarget as HTMLElement).getAttribute('data-qcid');
      if (qcid) executeQuickControl(qcid);
    });
  });

  document.querySelectorAll('.btn-exec-qc').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const qcid = (e.currentTarget as HTMLElement).getAttribute('data-qcid');
      if (qcid) executeQuickControl(qcid);
    });
  });

  // Phone hardware card buttons (unique IDs)
  const btnPhoneCardLock = document.getElementById('btn-phone-card-lock');
  if (btnPhoneCardLock) {
    btnPhoneCardLock.addEventListener('click', () => executeQuickControl('lock'));
  }
  const btnPhoneCardDnd = document.getElementById('btn-phone-card-dnd');
  if (btnPhoneCardDnd) {
    btnPhoneCardDnd.addEventListener('click', () => executeQuickControl('dnd'));
  }
  const btnPhoneCardScreenshot = document.getElementById('btn-phone-card-screenshot');
  if (btnPhoneCardScreenshot) {
    btnPhoneCardScreenshot.addEventListener('click', () => executeQuickControl('screenshot'));
  }
  const btnMacBt = document.getElementById('btn-mac-bluetooth-badge');
  if (btnMacBt) {
    btnMacBt.addEventListener('click', () => executeQuickControl('mac_bluetooth'));
  }
  const btnPhoneBt = document.getElementById('phone-bluetooth-badge');
  if (btnPhoneBt) {
    btnPhoneBt.addEventListener('click', () => executeQuickControl('phone_bluetooth'));
  }

  // Quick Controls Modal Events
  const btnCloseQcModal = document.getElementById('btn-close-quick-controls-modal');
  if (btnCloseQcModal) {
    btnCloseQcModal.addEventListener('click', () => {
      isQuickControlsModalOpen = false;
      render();
    });
  }

  const btnSaveQcModal = document.getElementById('btn-save-quick-controls');
  if (btnSaveQcModal) {
    btnSaveQcModal.addEventListener('click', () => {
      isQuickControlsModalOpen = false;
      render();
    });
  }

  const qcModalBackdrop = document.getElementById('quick-controls-modal-backdrop');
  if (qcModalBackdrop) {
    qcModalBackdrop.addEventListener('click', (e) => {
      if (e.target === qcModalBackdrop) {
        isQuickControlsModalOpen = false;
        render();
      }
    });
  }

  const inputSearchQc = document.getElementById('input-search-quick-controls') as HTMLInputElement | null;
  if (inputSearchQc) {
    inputSearchQc.addEventListener('input', (e) => {
      quickControlsSearchQuery = (e.target as HTMLInputElement).value;
      const listContainer = document.getElementById('quick-controls-modal-list');
      if (listContainer) {
        listContainer.innerHTML = renderQuickControlsModalGrid();
        attachQuickControlModalItemListeners();
      }
    });
  }

  const btnResetQcDefault = document.getElementById('btn-reset-quick-controls-default');
  if (btnResetQcDefault) {
    btnResetQcDefault.addEventListener('click', () => {
      try {
        localStorage.removeItem('idroid_pinned_quick_controls');
      } catch (_) {}
      render();
    });
  }

  attachQuickControlModalItemListeners();

  const btnSendFileToPhone = document.getElementById('btn-send-file-to-phone');
  const inputSendFile = document.getElementById('input-send-file-to-phone') as HTMLInputElement | null;
  if (btnSendFileToPhone && inputSendFile) {
    btnSendFileToPhone.addEventListener('click', () => {
      inputSendFile.click();
    });
    inputSendFile.addEventListener('change', async () => {
      if (inputSendFile.files && inputSendFile.files.length > 0) {
        const file = inputSendFile.files[0];
        showAppLaunchHUD('File Sent', `${file.name} sent to phone Downloads`);
      }
    });
  }

  const btnOpenReceivedFiles = document.getElementById('btn-open-received-files');
  if (btnOpenReceivedFiles) {
    btnOpenReceivedFiles.addEventListener('click', async () => {
      const invoke = await getInvoke();
      if (invoke) {
        await invoke('open_received_files');
      }
    });
  }


  // MARK: - Wireless Studio Triggers
  const btnOpenWireless = document.getElementById('btn-open-wireless-modal');
  if (btnOpenWireless) {
    btnOpenWireless.addEventListener('click', () => {
      isWirelessModalOpen = true;
      render();
    });
  }

  const btnHeaderSwitch = document.getElementById('btn-header-switch-wireless');
  if (btnHeaderSwitch) {
    btnHeaderSwitch.addEventListener('click', () => {
      handleSwitchToWireless();
    });
  }

  const btnHeaderWirelessInfo = document.getElementById('btn-header-wireless-info');
  if (btnHeaderWirelessInfo) {
    btnHeaderWirelessInfo.addEventListener('click', () => {
      isWirelessModalOpen = true;
      render();
    });
  }

  const btnDeviceCapsule = document.getElementById('btn-device-capsule');
  if (btnDeviceCapsule) {
    btnDeviceCapsule.addEventListener('click', () => {
      isWirelessModalOpen = true;
      render();
    });
  }

  const btnCloseWireless = document.getElementById('btn-close-wireless-modal');
  if (btnCloseWireless) {
    btnCloseWireless.addEventListener('click', () => {
      isWirelessModalOpen = false;
      render();
    });
  }

  const modalBackdrop = document.getElementById('wireless-modal-backdrop');
  if (modalBackdrop) {
    modalBackdrop.addEventListener('click', (e) => {
      if (e.target === modalBackdrop) {
        isWirelessModalOpen = false;
        render();
      }
    });
  }

  // MARK: - Idroid Doctor Event Listeners
  const btnOpenDoctorSidebar = document.getElementById('btn-open-doctor-sidebar');
  if (btnOpenDoctorSidebar) {
    btnOpenDoctorSidebar.addEventListener('click', () => {
      isDoctorModalOpen = true;
      render();
    });
  }

  const btnOpenDoctorPairing = document.getElementById('btn-open-doctor-pairing');
  if (btnOpenDoctorPairing) {
    btnOpenDoctorPairing.addEventListener('click', () => {
      isDoctorModalOpen = true;
      render();
    });
  }

  const btnCloseDoctorModal = document.getElementById('btn-close-doctor-modal');
  if (btnCloseDoctorModal) {
    btnCloseDoctorModal.addEventListener('click', () => {
      isDoctorModalOpen = false;
      render();
    });
  }

  const btnCloseDoctorModalFooter = document.getElementById('btn-close-doctor-modal-footer');
  if (btnCloseDoctorModalFooter) {
    btnCloseDoctorModalFooter.addEventListener('click', () => {
      isDoctorModalOpen = false;
      render();
    });
  }

  const doctorBackdrop = document.getElementById('idroid-doctor-backdrop');
  if (doctorBackdrop) {
    doctorBackdrop.addEventListener('click', (e) => {
      if (e.target === doctorBackdrop) {
        isDoctorModalOpen = false;
        render();
      }
    });
  }

  const doctorSearchInput = document.getElementById('doctor-search-input') as HTMLInputElement | null;
  if (doctorSearchInput) {
    doctorSearchInput.addEventListener('input', (e) => {
      doctorSearchQuery = (e.target as HTMLInputElement).value;
      render();
      const updatedInput = document.getElementById('doctor-search-input') as HTMLInputElement | null;
      if (updatedInput) {
        updatedInput.focus();
        updatedInput.setSelectionRange(doctorSearchQuery.length, doctorSearchQuery.length);
      }
    });
  }

  const btnClearDoctorSearch = document.getElementById('btn-clear-doctor-search');
  if (btnClearDoctorSearch) {
    btnClearDoctorSearch.addEventListener('click', () => {
      doctorSearchQuery = '';
      render();
    });
  }

  document.querySelectorAll('.doctor-topic-card').forEach(card => {
    card.addEventListener('click', () => {
      const faqId = card.getAttribute('data-faq-id');
      if (faqId) {
        doctorExpandedFaq = doctorExpandedFaq === faqId ? null : faqId;
        render();
      }
    });
  });

  // MARK: - Quick Apps Customizer Modal
  const btnCloseQuickApps = document.getElementById('btn-close-quick-apps-modal');
  if (btnCloseQuickApps) {
    btnCloseQuickApps.addEventListener('click', () => {
      isQuickAppsModalOpen = false;
      render();
    });
  }

  const btnSaveQuickApps = document.getElementById('btn-save-quick-apps');
  if (btnSaveQuickApps) {
    btnSaveQuickApps.addEventListener('click', () => {
      isQuickAppsModalOpen = false;
      render();
    });
  }

  const quickAppsBackdrop = document.getElementById('quick-apps-modal-backdrop');
  if (quickAppsBackdrop) {
    quickAppsBackdrop.addEventListener('click', (e) => {
      if (e.target === quickAppsBackdrop) {
        isQuickAppsModalOpen = false;
        render();
      }
    });
  }

  const inputSearchQuickApps = document.getElementById('input-search-quick-apps') as HTMLInputElement | null;
  if (inputSearchQuickApps) {
    inputSearchQuickApps.addEventListener('input', (e) => {
      quickAppsSearchQuery = (e.target as HTMLInputElement).value;
      const listContainer = document.getElementById('quick-apps-modal-list');
      if (listContainer) {
        listContainer.innerHTML = renderQuickAppsModalGrid();
        attachQuickAppModalItemListeners();
      }
    });
  }

  const btnResetQuickApps = document.getElementById('btn-reset-quick-apps-default');
  if (btnResetQuickApps) {
    btnResetQuickApps.addEventListener('click', () => {
      try {
        localStorage.removeItem('idroid_pinned_quick_apps');
      } catch (_) {}
      render();
    });
  }

  const toggleFavAppsMulti = document.getElementById('toggle-fav-apps-multiwindow') as HTMLInputElement | null;
  if (toggleFavAppsMulti) {
    toggleFavAppsMulti.addEventListener('change', (e) => {
      isFavAppsMultiWindowActive = (e.target as HTMLInputElement).checked;
      try {
        localStorage.setItem('idroid_fav_apps_multiwindow', String(isFavAppsMultiWindowActive));
      } catch (_) {}
    });
  }

  attachQuickAppModalItemListeners();


  // Modal Tab Switching
  document.querySelectorAll('.modal-tab-btn').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const tab = (e.currentTarget as HTMLElement).getAttribute('data-tab') as any;
      if (tab) {
        wirelessModalTab = tab;
        wirelessStatusMsg = '';
        render();
      }
    });
  }

  );

  // Modal Handover & Connection Actions
  const btnTriggerHandover = document.getElementById('btn-trigger-handover');
  if (btnTriggerHandover) btnTriggerHandover.addEventListener('click', handleSwitchToWireless);

  const btnTriggerPair = document.getElementById('btn-trigger-pair');
  if (btnTriggerPair) btnTriggerPair.addEventListener('click', handlePairWireless);

  const btnTriggerMesh = document.getElementById('btn-trigger-mesh-connect');
  if (btnTriggerMesh) btnTriggerMesh.addEventListener('click', handleConnectMesh);

  const btnDisconnectWireless = document.getElementById('btn-disconnect-wireless');
  if (btnDisconnectWireless) btnDisconnectWireless.addEventListener('click', handleDisconnectWireless);

  // History Snippet Copy buttons
  document.querySelectorAll('.btn-copy-history-item').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const targetBtn = e.currentTarget as HTMLElement;
      const rawText = targetBtn.getAttribute('data-text');
      if (rawText) {
        const text = decodeURIComponent(rawText);
        navigator.clipboard.writeText(text);
        const originalText = targetBtn.innerHTML;
        targetBtn.innerHTML = 'Copied!';
        targetBtn.style.color = 'var(--emerald-ion)';
        setTimeout(() => {
          targetBtn.innerHTML = originalText;
          targetBtn.style.color = '';
        }, 1200);
      }
    });
  });

  // Consumer vs Developer Pairing Tab Mode
  const tabConsumerQr = document.getElementById('tab-consumer-qr');
  if (tabConsumerQr) {
    tabConsumerQr.addEventListener('click', () => {
      activePairingTab = 'consumer_qr';
      render();
    });
  }

  const tabDeveloperMode = document.getElementById('tab-developer-mode');
  if (tabDeveloperMode) {
    tabDeveloperMode.addEventListener('click', () => {
      activePairingTab = 'developer_mode';
      render();
    });
  }

  const btnRegenQr = document.getElementById('btn-regenerate-pairing-qr');
  if (btnRegenQr) {
    btnRegenQr.addEventListener('click', async () => {
      btnRegenQr.innerHTML = `${Icons.Refresh(13, 'currentColor')} <span>Checking Connection...</span>`;
      pairingQrSvg = '';
      await pollDeviceTelemetry();
      if (device.status === 'Connected') {
        render();
      } else {
        await generatePairingQRs();
        render();
      }
    });
  }

  const btnCopyPairingLink = document.getElementById('btn-copy-pairing-link');
  if (btnCopyPairingLink) {
    btnCopyPairingLink.addEventListener('click', () => {
      if (pairingPayload) {
        const link = `idroid://pair?ip=${pairingPayload.ip}&port=${pairingPayload.port}&token=${pairingPayload.token}&name=${encodeURIComponent(pairingPayload.name)}`;
        navigator.clipboard.writeText(link);
        const originalText = btnCopyPairingLink.innerHTML;
        btnCopyPairingLink.innerHTML = '<span>Copied Link!</span>';
        btnCopyPairingLink.style.color = 'var(--emerald-ion)';
        setTimeout(() => {
          btnCopyPairingLink.innerHTML = originalText;
          btnCopyPairingLink.style.color = '';
        }, 1500);
      }
    });
  }

  const btnCompanionInstall = document.getElementById('btn-adb-companion-install');
  if (btnCompanionInstall) {
    btnCompanionInstall.addEventListener('click', async () => {
      isCompanionInstalling = true;
      companionInstallMsg = 'Installing Idroid companion APK to device via ADB...';
      render();
      const invoke = await getInvoke();
      if (invoke) {
        try {
          const res = await invoke('install_companion_apk');
          companionInstallMsg = res || 'Companion installed successfully!';
        } catch (err: any) {
          companionInstallMsg = `Install failed: ${err.message || err}`;
        }
      }
      isCompanionInstalling = false;
      render();
    });
  }

  // Refresh Telemetry Buttons
  document.querySelectorAll('#btn-refresh-telemetry').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const invoke = await getInvoke();
      if (invoke) {
        try {
          await invoke('reconnect_adb');
        } catch (e) {}
      }
      pollDeviceTelemetry();
    });
  });

  attachQuickAppListeners();
  setupStickyHeaderScrollBehavior();
  setupIntersectionThumbnailLoader();
}

// MARK: - Native Window Controls (Drag Anywhere on Headers, Double Click to Maximize)
async function setupWindowControls() {
  const invoke = await getInvoke();

  // Prevent native HTML5 image dragging from hijacking scrolling gestures anywhere in the app
  document.addEventListener('dragstart', (e) => {
    const target = e.target as HTMLElement;
    if (target && (target.tagName === 'IMG' || target.closest('.photo-manager-card') || target.closest('.photo-thumb-container') || target.closest('.main-content'))) {
      e.preventDefault();
      return false;
    }
  });

  // Native Double-Click Header to Zoom / Maximize (restricted to sidebar brand area)
  document.addEventListener('dblclick', async (e) => {
    const target = e.target as HTMLElement;
    if (target.closest('button, input, select, textarea, a, .nav-item, .app-tile, [data-no-drag], .main-content')) {
      return;
    }
    const headerArea = target.closest('.window-drag-bar, .brand-header');
    if (headerArea) {
      if (invoke) {
        try {
          await invoke('window_toggle_maximize');
        } catch (err) {
          console.warn('window_toggle_maximize error:', err);
        }
      }
    }
  });

  try {
    const { getCurrentWindow } = await import('@tauri-apps/api/window');
    const appWindow = getCurrentWindow();

    document.addEventListener('mousedown', async (e) => {
      if (e.button !== 0) return;
      const target = e.target as HTMLElement;

      // Strictly only allow window drag from the sidebar brand header or sidebar top drag bar
      const dragArea = target.closest('.window-drag-bar, .brand-header');
      if (!dragArea) return;

      if (target.closest('button, input, select, textarea, a, .nav-item, .app-tile, [data-no-drag], .main-content')) {
        return;
      }

      if (e.detail === 2) {
        try {
          if (invoke) await invoke('window_toggle_maximize');
          else await appWindow.toggleMaximize();
        } catch (err) {
          console.warn('toggleMaximize failed:', err);
        }
      } else {
        try {
          await appWindow.startDragging();
        } catch (err) {
          console.warn('startDragging failed:', err);
        }
      }
    });
  } catch (err) {
    console.warn('Tauri window API not available:', err);
  }
}
// Robust Bootstrap Router (Main Desktop Dashboard)
function bootstrap() {
  console.log('[Idroid UI] Initializing main dashboard view...');
  try {
    setupWindowControls();
    attachGlobalClickRipples();
    generatePairingQRs();

    // Global keyboard listener for dialer when in calls section
    window.addEventListener('keydown', (e) => {
      if (currentSection !== 'calls') return;
      // If user is focused on another input (like search, modal, etc.), don't hijack
      const activeEl = document.activeElement;
      if (activeEl && activeEl !== document.body && activeEl.id !== 'input-dialer-number') {
        if (activeEl.tagName === 'INPUT' || activeEl.tagName === 'TEXTAREA') return;
      }
      if (['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#', '+'].includes(e.key)) {
        handleDialDigit(e.key);
      } else if (e.key === 'Backspace') {
        handleDialBackspace();
      } else if (e.key === 'Enter') {
        e.preventDefault();
        handleStartCall();
      } else if (e.key === 'Escape') {
        e.preventDefault();
        if (isCallActive) {
          handleEndCall();
        } else {
          handleDialClear();
        }
      }
    });

    render();
  } catch (err) {
    console.error('[Idroid UI] Critical error during initial render:', err);
  }

  fetchQuickPhoneApps();
  fetchPhoneApps();
  fetchCallsAndNotifications();
  pollDeviceTelemetry();
  setInterval(pollDeviceTelemetry, 6000);
  pollCameraStreamStatus();
  setInterval(pollCameraStreamStatus, 1000);

  // Position sync for mirror navigation dock (lightweight 250ms interval, only when dock is enabled)
  setInterval(async () => {
    if (!isDockNavActive) return;
    const inv = await getInvoke();
    if (inv) {
      inv('sync_mirror_dock', { visible: true });
    }
  }, 250);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => bootstrap());
} else {
  bootstrap();
}



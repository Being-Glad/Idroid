import { Icons } from '../icons';
import {
  AppInfo,
  CallRecord,
  ClipboardSnippet,
  DeviceState,
  FavouriteContact,
  FileItem,
  HostTelemetry,
  NotificationItem,
  PhotoItem,
  QuickControlItem,
} from '../types';

export const CACHED_APPS_KEY = 'idroid_cached_phone_apps';
export const PINNED_APPS_KEY = 'idroid_pinned_app_packages';
export const PINNED_QUICK_CONTROLS_KEY = 'idroid_pinned_quick_controls';

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

export interface NavButtonDef {
  key: string;
  label: string;
  icon: (size: number, color?: string) => string;
  hint: string;
}

export const DEFAULT_NAV_BUTTONS = ['back', 'home', 'recents', 'notifications', 'volume_down', 'volume_up', 'screenshot', 'power'];

export const ALL_NAV_BUTTONS: NavButtonDef[] = [
  { key: 'back', label: 'Back', icon: (s, c) => Icons.ArrowLeft(s, c), hint: 'Back (ESC / ◀)' },
  { key: 'home', label: 'Home', icon: (s, c) => Icons.Dashboard(s, c), hint: 'Home Screen (⌂)' },
  { key: 'recents', label: 'Recents', icon: (s, c) => Icons.Layers(s, c), hint: 'Recent Apps Switcher (▢)' },
  { key: 'notifications', label: 'Shade', icon: (s, c) => Icons.Bell(s, c), hint: 'Pull Down Notifications Shade' },
  { key: 'volume_down', label: 'Vol -', icon: (s, c) => Icons.Volume1(s, c), hint: 'Volume Down' },
  { key: 'volume_up', label: 'Vol +', icon: (s, c) => Icons.Volume2(s, c), hint: 'Volume Up' },
  { key: 'screenshot', label: 'Capture', icon: (s, c) => Icons.Camera(s, c), hint: 'Take Phone Screenshot to Mac' },
  { key: 'power', label: 'Lock', icon: (s, c) => Icons.Power(s, c || 'var(--accent-solar)'), hint: 'Lock / Sleep Phone Screen' },
];

export interface QuickPhoneApp {
  name: string;
  package: string;
  icon: string;
}

export function loadCachedPhoneApps(): AppInfo[] {
  try {
    const raw = localStorage.getItem(CACHED_APPS_KEY);
    if (raw) return JSON.parse(raw);
  } catch (_) {}
  return [];
}

export function saveCachedPhoneApps(apps: AppInfo[]) {
  try {
    localStorage.setItem(CACHED_APPS_KEY, JSON.stringify(apps));
  } catch (_) {}
}

export interface IdroidStoreState {
  currentSection: string;
  isSidebarCollapsed: boolean;
  isDndActive: boolean;
  isCameraActive: boolean;
  isAudioActive: boolean;
  isVirtualDisplayActive: boolean;
  isScreenOffModeActive: boolean;
  isAlwaysOnTopActive: boolean;
  isDockNavActive: boolean;
  isPhoneFrameActive: boolean;
  enabledNavButtons: string[];
  mirrorStatusMsg: string;
  appSearchQuery: string;

  quickPhoneApps: QuickPhoneApp[];
  isQuickAppsModalOpen: boolean;
  quickAppsSearchQuery: string;
  isFavAppsMultiWindowActive: boolean;

  isQuickControlsModalOpen: boolean;
  quickControlsSearchQuery: string;
  quickControlsViewMode: 'tiles' | 'cards';

  isWirelessModalOpen: boolean;
  wirelessModalTab: 'optical' | 'handover' | 'pairing' | 'mesh';
  isDoctorModalOpen: boolean;
  doctorSelectedFaq: string | null;

  isLightboxOpen: boolean;
  lightboxPhoto: PhotoItem | null;

  isAddFavouriteModalOpen: boolean;
  isCallActive: boolean;
  activeCallNumber: string;
  activeCallName: string;
  callSeconds: number;
  isIncomingCallBannerOpen: boolean;
  incomingCallerName: string;
  incomingCallerNumber: string;
  isCallMuted: boolean;
  currentAudioRoute: string;
  isRecordingCallAudio: boolean;

  device: DeviceState;
  host: HostTelemetry;
  phoneApps: AppInfo[];
  files: FileItem[];
  photos: PhotoItem[];
  callRecords: CallRecord[];
  notifications: NotificationItem[];
  clipboardHistory: ClipboardSnippet[];
  favouriteContacts: FavouriteContact[];
  currentFilesDir: string;
  currentPhotosAlbum: string;
  opticalQrDataUrl: string;
  apkDownloadQrDataUrl: string;
}

export const initialStoreState: IdroidStoreState = {
  currentSection: 'dashboard',
  isSidebarCollapsed: (() => {
    try {
      return localStorage.getItem('idroid_sidebar_collapsed') === 'true';
    } catch (_) {
      return false;
    }
  })(),
  isDndActive: false,
  isCameraActive: false,
  isAudioActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_mirror_audio');
      return saved !== null ? saved === 'true' : false;
    } catch (_) {
      return false;
    }
  })(),
  isVirtualDisplayActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_mirror_vdisplay');
      return saved !== null ? saved === 'true' : false;
    } catch (_) {
      return false;
    }
  })(),
  isScreenOffModeActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_mirror_screenoff');
      return saved !== null ? saved === 'true' : true;
    } catch (_) {
      return true;
    }
  })(),
  isAlwaysOnTopActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_mirror_always_on_top');
      return saved !== null ? saved === 'true' : false;
    } catch (_) {
      return false;
    }
  })(),
  isDockNavActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_dock_nav_bar');
      return saved !== null ? saved === 'true' : true;
    } catch (_) {
      return true;
    }
  })(),
  isPhoneFrameActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_phone_frame');
      return saved !== null ? saved === 'true' : true;
    } catch (_) {
      return true;
    }
  })(),
  enabledNavButtons: (() => {
    try {
      const saved = localStorage.getItem('idroid_nav_buttons');
      if (saved) return JSON.parse(saved);
    } catch (_) {}
    return [...DEFAULT_NAV_BUTTONS];
  })(),
  mirrorStatusMsg: '',
  appSearchQuery: '',

  quickPhoneApps: (() => {
    const cached = loadCachedPhoneApps();
    if (cached.length > 0) {
      return cached.slice(0, 30).map((a) => ({
        name: a.name,
        package: a.package,
        icon: cleanIconUrl(a.icon_base64),
      }));
    }
    return [];
  })(),
  isQuickAppsModalOpen: false,
  quickAppsSearchQuery: '',
  isFavAppsMultiWindowActive: (() => {
    try {
      const saved = localStorage.getItem('idroid_fav_apps_multiwindow');
      if (saved !== null) return saved === 'true';
    } catch (_) {}
    return false;
  })(),

  isQuickControlsModalOpen: false,
  quickControlsSearchQuery: '',
  quickControlsViewMode: (() => {
    try {
      const saved = localStorage.getItem('idroid_quick_controls_view');
      if (saved === 'tiles' || saved === 'cards') return saved;
    } catch (_) {}
    return 'tiles';
  })(),

  isWirelessModalOpen: false,
  wirelessModalTab: 'optical',
  isDoctorModalOpen: false,
  doctorSelectedFaq: null,

  isLightboxOpen: false,
  lightboxPhoto: null,

  isAddFavouriteModalOpen: false,
  isCallActive: false,
  activeCallNumber: '',
  activeCallName: '',
  callSeconds: 0,
  isIncomingCallBannerOpen: false,
  incomingCallerName: '',
  incomingCallerNumber: '',
  isCallMuted: false,
  currentAudioRoute: 'phone',
  isRecordingCallAudio: false,

  device: {
    name: 'No Device Connected',
    brand: 'Android',
    battery: 0,
    isCharging: false,
    connectionType: 'Disconnected',
    status: 'Disconnected',
    voltageMv: 0,
    temperatureC: 0,
    storageUsedGb: '0',
    storageTotalGb: '0',
    storagePercent: 0,
    bluetoothStatus: 'Off',
    bluetoothConnected: false,
  },
  host: {
    name: 'MacBook Pro',
    os: 'Apple Silicon macOS',
    battery: 100,
    is_charging: false,
    storage_used_gb: '0',
    storage_total_gb: '0',
    storage_percent: 0,
    storage_available_gb: '0',
    bluetooth_status: 'Off',
    bluetooth_connected: false,
  },
  phoneApps: [],
  files: [],
  photos: [],
  callRecords: [],
  notifications: [],
  clipboardHistory: [],
  favouriteContacts: (() => {
    try {
      const saved = localStorage.getItem('idroid_fav_contacts');
      if (saved) return JSON.parse(saved);
    } catch (_) {}
    return [
      { id: 'fav-1', name: 'Papa', number: '+91 98765 43210', avatarColor: '#ff6e32' },
      { id: 'fav-2', name: 'Maa', number: '+91 98765 12345', avatarColor: '#ff3b30' },
      { id: 'fav-3', name: 'Home', number: '+91 98111 22334', avatarColor: '#34c759' },
    ];
  })(),
  currentFilesDir: '/sdcard/Download',
  currentPhotosAlbum: 'all',
  opticalQrDataUrl: '',
  apkDownloadQrDataUrl: '',
};

class Store {
  private state: IdroidStoreState = { ...initialStoreState };
  private listeners: Set<(state: IdroidStoreState) => void> = new Set();

  getState(): IdroidStoreState {
    return this.state;
  }

  setState(updates: Partial<IdroidStoreState>) {
    this.state = { ...this.state, ...updates };
    this.notify();
  }

  subscribe(listener: (state: IdroidStoreState) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notify() {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

export const store = new Store();

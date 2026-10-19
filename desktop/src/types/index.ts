export interface DeviceState {
  name: string;
  brand: string;
  battery: number;
  isCharging: boolean;
  connectionType: string;
  status: string;
  voltageMv: number;
  temperatureC: number;
  storageUsedGb: string;
  storageTotalGb: string;
  storagePercent: number;
  bluetoothStatus?: string;
  bluetoothConnected?: boolean;
}

export interface AppInfo {
  name: string;
  package: string;
  icon_base64?: string;
}

export interface FileItem {
  name: string;
  path: string;
  size: string;
  is_dir: boolean;
  modified: string;
}

export interface PhotoItem {
  name: string;
  path: string;
  size: string;
  modified: string;
  album?: string;
  thumbnail_base64?: string;
}

export interface FavouriteContact {
  id: string;
  name: string;
  number: string;
  avatarColor?: string;
}

export interface CallRecord {
  id: string;
  name: string;
  number: string;
  call_type: string;
  type?: string;
  time: string;
  duration: string;
}

export interface NotificationItem {
  id: string;
  appName?: string;
  app_name?: string;
  title: string;
  text: string;
  time: string;
  hasReply?: boolean;
  has_reply?: boolean;
  package_name?: string;
  icon_base64?: string;
}

export interface ClipboardSnippet {
  id: string;
  text: string;
  source: string;
  time: string;
}

export interface QuickControlItem {
  id: string;
  label: string;
  category: string;
  iconName: string;
  color: string;
  description: string;
}

export interface HostTelemetry {
  name: string;
  os: string;
  battery: number;
  is_charging: boolean;
  storage_used_gb: string;
  storage_total_gb: string;
  storage_percent: number;
  storage_available_gb: string;
  bluetooth_status: string;
  bluetooth_connected: boolean;
}

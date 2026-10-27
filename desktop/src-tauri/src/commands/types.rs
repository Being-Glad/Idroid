use serde::{Deserialize, Serialize};
use std::sync::{LazyLock, Mutex};

pub static PREFERRED_TARGET: LazyLock<Mutex<Option<String>>> = LazyLock::new(|| Mutex::new(None));
pub static CLIPBOARD_HISTORY: LazyLock<Mutex<Vec<ClipboardSnippet>>> = LazyLock::new(|| Mutex::new(Vec::new()));
pub static LIVE_NOTIFICATIONS: LazyLock<Mutex<Vec<NotificationRecord>>> = LazyLock::new(|| Mutex::new(Vec::new()));
pub static COMPANION_SESSION: LazyLock<Mutex<Option<CompanionSession>>> = LazyLock::new(|| Mutex::new(None));
pub static QUICK_PHONE_APPS: LazyLock<Mutex<Vec<PhoneAppIconItem>>> = LazyLock::new(|| Mutex::new(Vec::new()));
pub static ACTIVE_SERVER_PORT: LazyLock<Mutex<u16>> = LazyLock::new(|| Mutex::new(53100));
pub static CURRENT_CALL_STATE: LazyLock<Mutex<CallStateInfo>> = LazyLock::new(|| Mutex::new(CallStateInfo {
    state: "idle".into(),
    is_ringing: false,
    is_active: false,
    number: String::new(),
    name: String::new(),
    duration_sec: 0,
    audio_route: None,
    audio_route_name: None,
}));
pub static PHONE_CONTACTS_CACHE: LazyLock<Mutex<Vec<PhoneContact>>> = LazyLock::new(|| Mutex::new(Vec::new()));
pub static PHONE_CALL_HISTORY_CACHE: LazyLock<Mutex<Vec<CallRecord>>> = LazyLock::new(|| Mutex::new(Vec::new()));

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceTelemetry {
    pub name: String,
    pub brand: String,
    pub battery: u8,
    pub is_charging: bool,
    pub connection_type: String,
    pub status: String,
    pub voltage_mv: u32,
    pub temperature_c: f32,
    pub storage_used_gb: String,
    pub storage_total_gb: String,
    pub storage_percent: u8,
    #[serde(default)]
    pub bluetooth_status: Option<String>,
    #[serde(default)]
    pub bluetooth_connected: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WirelessConnectionResult {
    pub success: bool,
    pub message: String,
    pub ip: Option<String>,
    pub port: Option<u16>,
    pub connection_type: String,
    pub target: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairingPayload {
    pub name: String,
    pub ip: String,
    pub port: u16,
    pub token: String,
    pub protocol: String,
    #[serde(default)]
    pub ssid: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActiveTransportInfo {
    pub is_connected: bool,
    pub target: String,
    pub is_wireless: bool,
    pub is_remote_mesh: bool,
    pub connection_type: String,
    pub ip: Option<String>,
    pub port: Option<u16>,
    pub latency_ms: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppInfo {
    pub name: String,
    pub package: String,
    pub icon_base64: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileItem {
    pub name: String,
    pub path: String,
    pub size: String,
    pub is_dir: bool,
    pub modified: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhotoItem {
    pub name: String,
    pub path: String,
    pub size: String,
    pub modified: String,
    pub album: String,
    pub thumbnail_base64: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallRecord {
    pub id: String,
    #[serde(default)]
    pub phone_id: Option<String>,
    pub name: String,
    pub number: String,
    pub call_type: String, // incoming, outgoing, missed
    pub time: String,
    pub duration: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NotificationRecord {
    pub id: String,
    pub app_name: String,
    pub package_name: String,
    pub title: String,
    pub text: String,
    pub time: String,
    pub has_reply: bool,
    pub icon_base64: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClipboardSnippet {
    pub id: String,
    pub text: String,
    pub source: String,
    pub time: String,
    #[serde(default)]
    pub pinned: bool,
    #[serde(default)]
    pub image_base64: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CompanionSession {
    pub stream_id: u64,
    pub device_name: String,
    pub brand: String,
    pub battery: u8,
    pub is_charging: bool,
    pub storage_used_gb: String,
    pub storage_total_gb: String,
    pub storage_percent: u8,
    pub ip: String,
    pub connected_at: u64,
    pub last_seen: u64,
    pub bluetooth_status: Option<String>,
    pub bluetooth_connected: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MacBluetoothStatus {
    pub is_enabled: bool,
    pub is_connected: bool,
    pub connected_device_name: Option<String>,
    pub status_text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MacBluetoothDevice {
    pub name: String,
    pub address: String,
    pub connected: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MacWifiInfo {
    pub power: bool,
    pub ssid: String,
    pub interface: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HostTelemetry {
    pub name: String,
    pub os: String,
    pub battery: u8,
    pub is_charging: bool,
    pub storage_used_gb: String,
    pub storage_total_gb: String,
    pub storage_percent: u8,
    pub storage_available_gb: String,
    pub bluetooth_status: String,
    pub bluetooth_connected: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeviceWallpapers {
    pub mac_wallpaper: Option<String>,
    pub phone_wallpaper: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MacAppEntry {
    pub name: String,
    pub bundle_id: String,
    pub path: String,
    pub category: String,
    pub icon: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneAppIconItem {
    pub name: String,
    pub package: String,
    pub icon: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MirrorWindowBounds {
    pub title: String,
    pub x: f64,
    pub y: f64,
    pub w: f64,
    pub h: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IncomingCallInfo {
    pub is_ringing: bool,
    pub number: String,
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CallStateInfo {
    pub state: String, // "idle", "ringing", "active"
    pub is_ringing: bool,
    pub is_active: bool,
    pub number: String,
    pub name: String,
    #[serde(default)]
    pub duration_sec: u64,
    #[serde(default)]
    pub audio_route: Option<String>,
    #[serde(default)]
    pub audio_route_name: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneContact {
    pub id: String,
    pub name: String,
    pub number: String,
    pub contact_type: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneBluetoothDevice {
    pub name: String,
    pub address: String,
    pub connected: bool,
    pub bonded: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneBluetoothState {
    pub enabled: bool,
    pub connected_device: Option<String>,
    pub devices: Vec<PhoneBluetoothDevice>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneWifiState {
    pub enabled: bool,
    pub ssid: Option<String>,
    pub ip: Option<String>,
    pub link_speed: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhoneHotspotState {
    pub enabled: bool,
    pub ip: Option<String>,
    pub client_count: u32,
}


use std::process::Command;
use std::sync::{LazyLock, Mutex};
use std::sync::atomic::{AtomicU16, Ordering};
use super::types::{
    ActiveTransportInfo, DeviceTelemetry, DeviceWallpapers, MirrorWindowBounds,
    PairingPayload, WirelessConnectionResult, ACTIVE_SERVER_PORT, COMPANION_SESSION,
    PREFERRED_TARGET,
};
use super::apps::{find_companion_icon_bytes, map_package_to_app};

#[cfg(target_os = "macos")]
unsafe extern "C" {
    fn find_scrcpy_window(x: *mut f64, y: *mut f64, w: *mut f64, h: *mut f64) -> i32;
}

static PORT_COUNTER: AtomicU16 = AtomicU16::new(0);
pub static MIRROR_PIDS: Mutex<Vec<u32>> = Mutex::new(Vec::new());

// MARK: - Path Resolvers
pub fn get_adb_path() -> String {
    let home = std::env::var("HOME").unwrap_or_default();
    let candidates = [
        format!("{}/Library/Android/sdk/platform-tools/adb", home),
        "/usr/local/bin/adb".to_string(),
        "/opt/homebrew/bin/adb".to_string(),
        "adb".to_string(),
    ];
    for c in &candidates {
        if std::path::Path::new(c).exists() {
            return c.clone();
        }
    }
    "adb".to_string()
}

pub fn get_scrcpy_path() -> String {
    let candidates = [
        "/usr/local/bin/scrcpy".to_string(),
        "/opt/homebrew/bin/scrcpy".to_string(),
        "scrcpy".to_string(),
    ];
    for c in &candidates {
        if std::path::Path::new(c).exists() {
            return c.clone();
        }
    }
    "scrcpy".to_string()
}

// MARK: - Device Connection State & Transport Resolver
pub fn parse_adb_devices_list(output: &str) -> Vec<(String, String)> {
    let mut devices = Vec::new();
    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with("List of devices") || trimmed.starts_with('*') {
            continue;
        }
        let parts: Vec<&str> = trimmed.split_whitespace().collect();
        if parts.len() >= 2 {
            devices.push((parts[0].to_string(), parts[1].to_string()));
        }
    }
    devices
}

pub fn ensure_usb_reverse_tunnels(target: &str) {
    if target.is_empty() || target.contains(':') {
        return;
    }
    let adb = get_adb_path();
    for port in [53100, 53102, 53104, 53105] {
        let rule = format!("tcp:{}", port);
        let _ = Command::new(&adb)
            .args(["-s", target, "reverse", &rule, &rule])
            .output();
    }
}

pub fn is_target_alive(target: &str) -> bool {
    let adb = get_adb_path();
    if let Ok(output) = Command::new(&adb).args(["devices"]).output() {
        let stdout = String::from_utf8_lossy(&output.stdout);
        let list = parse_adb_devices_list(&stdout);
        return list.iter().any(|(s, st)| s == target && st == "device");
    }
    false
}

static RESOLVED_TARGET_CACHE: LazyLock<Mutex<(Option<String>, std::time::Instant)>> =
    LazyLock::new(|| Mutex::new((None, std::time::Instant::now() - std::time::Duration::from_secs(10))));

pub fn resolve_device_target() -> Option<String> {
    if let Ok(guard) = RESOLVED_TARGET_CACHE.lock() {
        if guard.1.elapsed().as_secs() < 3 {
            return guard.0.clone();
        }
    }

    let target = resolve_device_target_uncached();
    if let Ok(mut guard) = RESOLVED_TARGET_CACHE.lock() {
        *guard = (target.clone(), std::time::Instant::now());
    }
    target
}

fn resolve_device_target_uncached() -> Option<String> {
    if let Ok(guard) = PREFERRED_TARGET.lock() {
        if let Some(ref target) = *guard {
            if target.contains(':') && is_target_alive(target) {
                return Some(target.clone());
            }
        }
    }

    let adb = get_adb_path();
    let output = Command::new(&adb).args(["devices"]).output().ok()?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let devices = parse_adb_devices_list(&stdout);

    // First priority: active USB connected device (zero latency)
    let active_usb = devices.iter().find(|(s, st)| st == "device" && !s.contains(':'));
    if let Some((usb, _)) = active_usb {
        ensure_usb_reverse_tunnels(usb);
        return Some(usb.clone());
    }

    // Secondary priority: active wireless device
    let active_wireless = devices.iter().find(|(s, st)| st == "device" && s.contains(':'));
    if let Some((wireless, _)) = active_wireless {
        return Some(wireless.clone());
    }

    // Fallback: any active device
    let any_device = devices.iter().find(|(_, st)| st == "device");
    if let Some((dev, _)) = any_device {
        return Some(dev.clone());
    }

    None
}

pub fn classify_connection(target: &str) -> String {
    if target.is_empty() {
        return "Disconnected".to_string();
    }
    if !target.contains(':') {
        return "USB Direct High-Speed".to_string();
    }
    let ip = target.split(':').next().unwrap_or(target);
    if ip.starts_with("100.") {
        "Remote Mesh (Encrypted)".to_string()
    } else if ip.starts_with("192.168.") || ip.starts_with("10.") || ip.starts_with("172.") {
        "Wi-Fi 5GHz (Local)".to_string()
    } else if ip == "localhost" || ip == "127.0.0.1" {
        "Local Loopback / Tunneled".to_string()
    } else {
        "Remote WAN (Secure)".to_string()
    }
}

pub fn parse_wlan_ip(output: &str) -> Option<String> {
    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("inet ") {
            if let Some(ip_with_mask) = trimmed.strip_prefix("inet ") {
                let ip_str = ip_with_mask.split_whitespace().next().unwrap_or("");
                let ip = ip_str.split('/').next().unwrap_or("");
                if !ip.is_empty() && ip != "127.0.0.1" {
                    return Some(ip.to_string());
                }
            }
        }
        if trimmed.contains("src ") {
            if let Some(src_part) = trimmed.split("src ").nth(1) {
                let ip = src_part.split_whitespace().next().unwrap_or("");
                if !ip.is_empty() && ip != "127.0.0.1" {
                    return Some(ip.to_string());
                }
            }
        }
    }
    None
}

pub fn adb_command(args: &[&str]) -> Command {
    let adb = get_adb_path();
    let mut cmd = Command::new(&adb);
    if let Some(target) = resolve_device_target() {
        cmd.arg("-s").arg(&target);
    }
    cmd.args(args);
    cmd
}

pub fn is_device_connected() -> bool {
    if let Ok(guard) = COMPANION_SESSION.lock() {
        if let Some(ref session) = *guard {
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0);
            if now.saturating_sub(session.last_seen) < 30 {
                return true;
            }
        }
    }
    false
}

#[tauri::command]
pub fn get_device_telemetry() -> DeviceTelemetry {
    if let Ok(guard) = COMPANION_SESSION.lock() {
        if let Some(ref session) = *guard {
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_secs())
                .unwrap_or(0);
            if now.saturating_sub(session.last_seen) < 30 {
                let name = if session.device_name.is_empty() || session.device_name == "SM-S918B" {
                    "Om's S23 Ultra".to_string()
                } else {
                    session.device_name.clone()
                };
                let conn_type = if session.ip == "127.0.0.1" {
                    "USB Cable Bridge"
                } else {
                    "on the air"
                };
                return DeviceTelemetry {
                    name,
                    brand: session.brand.clone(),
                    battery: session.battery,
                    is_charging: session.is_charging,
                    connection_type: conn_type.into(),
                    status: "Connected".into(),
                    voltage_mv: 4200,
                    temperature_c: 32.5,
                    storage_used_gb: session.storage_used_gb.clone(),
                    storage_total_gb: session.storage_total_gb.clone(),
                    storage_percent: session.storage_percent,
                    bluetooth_status: session.bluetooth_status.clone(),
                    bluetooth_connected: session.bluetooth_connected,
                };
            }
        }
    }

    DeviceTelemetry {
        name: "No Device Connected".into(),
        brand: "Android".into(),
        battery: 0,
        is_charging: false,
        connection_type: "Disconnected".into(),
        status: "Disconnected".into(),
        voltage_mv: 0,
        temperature_c: 0.0,
        storage_used_gb: "0".into(),
        storage_total_gb: "0".into(),
        storage_percent: 0,
        bluetooth_status: None,
        bluetooth_connected: None,
    }
}

pub fn parse_battery_info(output: &str) -> (u8, bool, u32, f32) {
    let mut level = 90;
    let mut voltage = 4350;
    let mut temp = 35.1;
    let mut ac_powered = false;
    let mut usb_powered = false;
    let mut wireless_powered = false;
    let mut status_val = 0u32;

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("level:") {
            if let Some(val) = trimmed.strip_prefix("level:") {
                level = val.trim().parse::<u8>().unwrap_or(level);
            }
        } else if trimmed.starts_with("AC powered:") {
            ac_powered = trimmed.contains("true");
        } else if trimmed.starts_with("USB powered:") {
            usb_powered = trimmed.contains("true");
        } else if trimmed.starts_with("Wireless powered:") {
            wireless_powered = trimmed.contains("true");
        } else if trimmed.starts_with("status:") {
            if let Some(val) = trimmed.strip_prefix("status:") {
                status_val = val.trim().parse::<u32>().unwrap_or(0);
            }
        } else if trimmed.starts_with("voltage:") {
            if let Some(val) = trimmed.strip_prefix("voltage:") {
                voltage = val.trim().parse::<u32>().unwrap_or(voltage);
            }
        } else if trimmed.starts_with("temperature:") {
            if let Some(val) = trimmed.strip_prefix("temperature:") {
                let t = val.trim().parse::<f32>().unwrap_or(350.0);
                temp = t / 10.0;
            }
        }
    }

    let is_connected_to_power = ac_powered || usb_powered || wireless_powered;
    let is_charging = status_val == 2 || status_val == 5 || (is_connected_to_power && status_val != 3);

    (level, is_charging, voltage, temp)
}

pub fn parse_storage_info(output: &str) -> (String, String, u8) {
    for line in output.lines().skip(1) {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 5 {
            let total = parts[1].to_string();
            let used = parts[2].to_string();
            let percent_str = parts[4].trim_end_matches('%');
            let percent = percent_str.parse::<u8>().unwrap_or(41);
            return (used, total, percent);
        }
    }
    ("90G".into(), "222G".into(), 41)
}

#[tauri::command]
pub fn get_device_wallpapers() -> Result<DeviceWallpapers, String> {
    Ok(DeviceWallpapers {
        mac_wallpaper: None,
        phone_wallpaper: None,
    })
}

fn prepare_native_app_bundle(
    app_name: &str,
    pkg: Option<&str>,
    icon_base64: Option<&str>,
    scrcpy_path: &str,
) -> (String, Option<String>) {
    let clean_name: String = app_name
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == ' ' || *c == '-' || *c == '_')
        .collect::<String>()
        .trim()
        .to_string();
    let display_name = if clean_name.is_empty() {
        "Idroid Mirror".to_string()
    } else {
        clean_name
    };

    let apps_base = std::env::temp_dir().join("idroid_apps");
    let bundle_dir = apps_base.join(format!("{}.app", display_name));
    let macos_dir = bundle_dir.join("Contents").join("MacOS");
    let res_dir = bundle_dir.join("Contents").join("Resources");

    if let Err(e) = std::fs::create_dir_all(&macos_dir) {
        log::warn!("Could not create macos app bundle dir: {}", e);
        return (scrcpy_path.to_string(), None);
    }
    let _ = std::fs::create_dir_all(&res_dir);

    let exec_path = macos_dir.join(&display_name);
    let _ = std::fs::remove_file(&exec_path);
    #[cfg(unix)]
    let _ = std::os::unix::fs::symlink(scrcpy_path, &exec_path);

    let icon_png_path = apps_base.join(format!("{}_icon.png", display_name));
    let mut wrote_icon = false;

    if let Some(b64) = icon_base64 {
        let clean_b64 = b64.trim_start_matches("data:image/png;base64,")
            .trim_start_matches("data:image/jpeg;base64,")
            .trim_start_matches("data:image/webp;base64,");
        use base64::Engine;
        if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(clean_b64) {
            if std::fs::write(&icon_png_path, &bytes).is_ok() {
                wrote_icon = true;
            }
        }
    }

    if !wrote_icon {
        if let Some(p) = pkg {
            if let Some(bytes) = find_companion_icon_bytes(p) {
                if std::fs::write(&icon_png_path, &bytes).is_ok() {
                    wrote_icon = true;
                }
            }
        }
    }

    if !wrote_icon && !icon_png_path.exists() {
        let fallback_icons = [
            "src-tauri/icons/128x128.png",
            "../src-tauri/icons/128x128.png",
            "public/icon.png",
            "../public/icon.png",
        ];
        for f in &fallback_icons {
            if let Ok(bytes) = std::fs::read(f) {
                let _ = std::fs::write(&icon_png_path, &bytes);
                break;
            }
        }
    }

    let icns_path = res_dir.join("AppIcon.icns");
    if icon_png_path.exists() {
        let _ = Command::new("sips")
            .args(&[
                "-s",
                "format",
                "icns",
                icon_png_path.to_str().unwrap_or_default(),
                "--out",
                icns_path.to_str().unwrap_or_default(),
            ])
            .output();
    }

    let bundle_id = format!(
        "com.idroid.mirror.{}",
        display_name.to_lowercase().replace(' ', "")
    );
    let plist_content = format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>{}</string>
    <key>CFBundleDisplayName</key>
    <string>{}</string>
    <key>CFBundleIdentifier</key>
    <string>{}</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleExecutable</key>
    <string>{}</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
</dict>
</plist>"#,
        display_name, display_name, bundle_id, display_name
    );
    let _ = std::fs::write(bundle_dir.join("Contents").join("Info.plist"), plist_content);

    let final_exec = if exec_path.exists() {
        exec_path.to_string_lossy().to_string()
    } else {
        scrcpy_path.to_string()
    };

    let final_icon = if icon_png_path.exists() {
        Some(icon_png_path.to_string_lossy().to_string())
    } else {
        None
    };

    (final_exec, final_icon)
}

#[tauri::command]
pub fn start_screen_mirror(
    package: Option<String>,
    fps: Option<u32>,
    max_size: Option<u32>,
    new_display: Option<bool>,
    turn_screen_off: Option<bool>,
    app_title: Option<String>,
    audio: Option<bool>,
    always_on_top: Option<bool>,
    borderless: Option<bool>,
    icon_base64: Option<String>,
) -> Result<String, String> {
    let scrcpy = get_scrcpy_path();
    let adb = get_adb_path();

    let fps_str = fps.unwrap_or(60).to_string();
    let is_multi = new_display.unwrap_or(false) && package.as_ref().map_or(false, |p| !p.trim().is_empty());

    if !is_multi {
        if let Ok(mut pids) = MIRROR_PIDS.lock() {
            for pid in pids.drain(..) {
                let _ = Command::new("kill").arg(pid.to_string()).output();
            }
        }
        let _ = Command::new("killall").arg("scrcpy").output();
    }

    let port_offset = PORT_COUNTER.fetch_add(1, Ordering::Relaxed) % 8;
    let base_port = 27183 + (port_offset * 2);
    let port_arg = format!("{}:{}", base_port, base_port + 1);

    let friendly_name = if let Some(ref title) = app_title {
        title.clone()
    } else if let Some(ref pkg) = package {
        let (friendly, _) = map_package_to_app(pkg);
        friendly
    } else {
        "Phone Display".to_string()
    };

    let window_title = format!("Idroid • {}", friendly_name);

    let (exec_binary, custom_icon_path) = prepare_native_app_bundle(
        &friendly_name,
        package.as_deref(),
        icon_base64.as_deref(),
        &scrcpy,
    );

    let stream_max_size = if is_multi {
        "1200".to_string()
    } else {
        max_size.unwrap_or(1920).to_string()
    };
    let stream_bitrate = if is_multi { "4M" } else { "8M" };

    let mut args = vec![
        "--max-fps".to_string(),
        fps_str,
        "--max-size".to_string(),
        stream_max_size,
        "--stay-awake".to_string(),
        format!("--video-bit-rate={}", stream_bitrate),
        "--video-buffer=0".to_string(),
        "--no-mouse-hover".to_string(),
        format!("--window-title={}", window_title),
        "--window-width=380".to_string(),
        format!("--port={}", port_arg),
    ];

    let resolved_target = resolve_device_target();
    if let Some(ref target) = resolved_target {
        args.push("-s".to_string());
        args.push(target.clone());
    }

    if let Some(ref pkg) = package {
        if !pkg.is_empty() {
            args.push(format!("--start-app={}", pkg));
        }
    }

    if is_multi {
        args.push("--new-display=1080x2316/420".to_string());
        args.push("--no-vd-system-decorations".to_string());
    }

    if turn_screen_off.unwrap_or(false) && !is_multi {
        args.push("--turn-screen-off".to_string());
    }

    if audio.unwrap_or(false) {
        args.push("--audio-codec=opus".to_string());
        args.push("--audio-bit-rate=128K".to_string());
    } else {
        args.push("--no-audio".to_string());
    }

    if always_on_top.unwrap_or(false) {
        args.push("--always-on-top".to_string());
    }

    let _ = borderless;

    let adb_parent = std::path::Path::new(&adb)
        .parent()
        .map(|p| p.to_string_lossy().to_string())
        .unwrap_or_default();
    let sys_path = std::env::var("PATH").unwrap_or_default();
    let full_path = format!("{}:/usr/local/bin:/opt/homebrew/bin:{}", adb_parent, sys_path);

    let mut cmd = Command::new(&exec_binary);
    cmd.args(&args)
        .env("ADB", &adb)
        .env("PATH", &full_path);

    if let Some(ref target) = resolved_target {
        cmd.env("ANDROID_SERIAL", target);
    }

    if let Some(ref icon_p) = custom_icon_path {
        cmd.env("SCRCPY_ICON_PATH", icon_p);
    }

    let child = cmd.spawn();

    match child {
        Ok(proc) => {
            if let Ok(mut pids) = MIRROR_PIDS.lock() {
                pids.push(proc.id());
            }
            Ok(format!("Hardware stream active (PID {}) for {}", proc.id(), window_title))
        }
        Err(e) => Err(format!("Stream error: {}. Ensure USB Debugging is ON or Wi-Fi paired.", e)),
    }
}

#[tauri::command]
pub fn get_mirror_window_bounds() -> Option<MirrorWindowBounds> {
    if let Ok(guard) = MIRROR_PIDS.lock() {
        if guard.is_empty() {
            return None;
        }
    }

    #[cfg(target_os = "macos")]
    {
        let mut x = 0.0;
        let mut y = 0.0;
        let mut w = 0.0;
        let mut h = 0.0;
        unsafe {
            if find_scrcpy_window(&mut x, &mut y, &mut w, &mut h) == 1 {
                return Some(MirrorWindowBounds {
                    title: "Mirror".to_string(),
                    x,
                    y,
                    w,
                    h,
                });
            }
        }
    }
    None
}

#[tauri::command]
pub fn sync_mirror_dock(app: tauri::AppHandle, visible: bool) -> Result<(), String> {
    use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

    if !visible {
        if let Some(win) = app.get_webview_window("mirror-dock") {
            let _ = win.hide();
        }
        return Ok(());
    }

    if let Ok(guard) = MIRROR_PIDS.lock() {
        if guard.is_empty() {
            if let Some(win) = app.get_webview_window("mirror-dock") {
                let _ = win.hide();
            }
            return Ok(());
        }
    }

    if let Some(bounds) = get_mirror_window_bounds() {
        let dock_w = 320.0;
        let dock_h = 44.0;
        let dock_x = bounds.x + (bounds.w - dock_w) / 2.0;
        let dock_y = bounds.y + bounds.h + 6.0;

        if let Some(win) = app.get_webview_window("mirror-dock") {
            let _ = win.set_position(tauri::Position::Logical(tauri::LogicalPosition::new(dock_x, dock_y)));
            let _ = win.set_size(tauri::Size::Logical(tauri::LogicalSize::new(dock_w, dock_h)));
            let _ = win.show();
            let _ = win.set_always_on_top(true);
            let _ = win.set_shadow(false);
        } else {
            let win = WebviewWindowBuilder::new(
                &app,
                "mirror-dock",
                WebviewUrl::App("dock.html".into()),
            )
            .title("Idroid Navigation Dock")
            .decorations(false)
            .transparent(true)
            .always_on_top(true)
            .inner_size(dock_w, dock_h)
            .position(dock_x, dock_y)
            .resizable(false)
            .skip_taskbar(true)
            .shadow(false)
            .build()
            .map_err(|e| format!("Failed to create dock window: {}", e))?;

            let _ = win.show();
            let _ = win.set_always_on_top(true);
            let _ = win.set_shadow(false);
        }
    } else if let Some(win) = app.get_webview_window("mirror-dock") {
        let _ = win.hide();
    }
    Ok(())
}

#[tauri::command]
pub fn sync_mirror_frame(app: tauri::AppHandle, _visible: bool) -> Result<(), String> {
    use tauri::Manager;
    if let Some(win) = app.get_webview_window("mirror-frame") {
        let _ = win.close();
    }
    Ok(())
}

#[tauri::command]
pub fn send_phone_key(key: String) -> Result<String, String> {
    if key == "notifications" {
        let _ = adb_command(&["shell", "cmd", "statusbar", "expand-notifications"]).output();
        return Ok("Notifications expanded".into());
    } else if key == "quick_settings" {
        let _ = adb_command(&["shell", "cmd", "statusbar", "expand-settings"]).output();
        return Ok("Quick settings expanded".into());
    } else if key == "open_settings" {
        let _ = adb_command(&["shell", "am", "start", "-a", "android.settings.SETTINGS"]).output();
        return Ok("Settings opened".into());
    } else if key == "bluetooth_settings" || key == "phone_bluetooth" {
        let _ = adb_command(&["shell", "am", "start", "-a", "android.settings.BLUETOOTH_SETTINGS"]).output();
        return Ok("Bluetooth settings opened".into());
    } else if key == "rotate" {
        let _ = adb_command(&["shell", "content", "insert", "--uri", "content://settings/system", "--bind", "name:s:accelerometer_rotation", "--bind", "value:i:1"]).output();
        return Ok("Auto-rotate enabled".into());
    }

    let keycode = match key.as_str() {
        "home" => "3",
        "back" => "4",
        "recents" => "187",
        "power" => "26",
        "volume_up" => "24",
        "volume_down" => "25",
        "volume_mute" => "164",
        "media_play_pause" => "85",
        "media_next" => "87",
        "media_prev" => "88",
        "wake" => "224",
        "sleep" => "223",
        "screenshot" => "120",
        _ => "3",
    };

    let _ = adb_command(&["shell", "input", "keyevent", keycode]).output();
    Ok(format!("Key {} sent", key))
}

#[tauri::command]
pub fn toggle_dnd(active: bool) -> Result<bool, String> {
    let val = if active { "1" } else { "0" };
    let _ = adb_command(&["shell", "settings", "put", "global", "zen_mode", val]).output();
    Ok(active)
}

#[tauri::command]
pub fn switch_to_wireless_tcpip() -> Result<WirelessConnectionResult, String> {
    let adb = get_adb_path();
    if !is_device_connected() {
        return Err("No device connected. Please connect via USB first to initialize wireless mode.".into());
    }

    let target = resolve_device_target().unwrap_or_default();

    let mut tcpip_cmd = Command::new(&adb);
    if !target.is_empty() {
        tcpip_cmd.arg("-s").arg(&target);
    }
    tcpip_cmd.args(["tcpip", "5555"]);
    let tcpip_out = tcpip_cmd.output().map_err(|e| format!("Failed to set tcpip mode: {}", e))?;

    if !tcpip_out.status.success() {
        let err = String::from_utf8_lossy(&tcpip_out.stderr);
        return Err(format!("Could not enable TCP/IP port 5555: {}", err));
    }

    std::thread::sleep(std::time::Duration::from_millis(600));

    let mut ip_cmd = Command::new(&adb);
    if !target.is_empty() {
        ip_cmd.arg("-s").arg(&target);
    }
    ip_cmd.args(["shell", "ip", "-f", "inet", "addr", "show", "wlan0"]);
    let ip_out = ip_cmd.output().ok();

    let mut phone_ip = None;
    if let Some(out) = ip_out {
        phone_ip = parse_wlan_ip(&String::from_utf8_lossy(&out.stdout));
    }

    if phone_ip.is_none() {
        let mut route_cmd = Command::new(&adb);
        if !target.is_empty() {
            route_cmd.arg("-s").arg(&target);
        }
        route_cmd.args(["shell", "ip", "route"]);
        if let Ok(out) = route_cmd.output() {
            phone_ip = parse_wlan_ip(&String::from_utf8_lossy(&out.stdout));
        }
    }

    let ip = match phone_ip {
        Some(ip) => ip,
        None => {
            return Ok(WirelessConnectionResult {
                success: true,
                message: "TCP/IP 5555 enabled. Please enter your phone's Wi-Fi IP to complete wireless pairing.".into(),
                ip: None,
                port: Some(5555),
                connection_type: "Wi-Fi 5GHz (Local)".into(),
                target: "".into(),
            });
        }
    };

    let connect_target = format!("{}:5555", ip);
    let conn_out = Command::new(&adb)
        .args(["connect", &connect_target])
        .output()
        .map_err(|e| format!("Failed to connect to {}: {}", connect_target, e))?;

    let conn_msg = String::from_utf8_lossy(&conn_out.stdout).trim().to_string();

    if let Ok(mut guard) = PREFERRED_TARGET.lock() {
        *guard = Some(connect_target.clone());
    }

    let conn_type = classify_connection(&connect_target);

    Ok(WirelessConnectionResult {
        success: true,
        message: format!("Wireless active! You can now unplug the USB cable. ({})", conn_msg),
        ip: Some(ip),
        port: Some(5555),
        connection_type: conn_type,
        target: connect_target,
    })
}

#[tauri::command]
pub fn connect_wireless_ip(ip: String, port: Option<u16>) -> Result<WirelessConnectionResult, String> {
    let clean_ip = ip.trim().to_string();
    if clean_ip.is_empty() {
        return Err("Please provide a valid IP address.".into());
    }
    let p = port.unwrap_or(5555);
    let target = format!("{}:{}", clean_ip, p);

    let adb = get_adb_path();
    let conn_out = Command::new(&adb)
        .args(["connect", &target])
        .output()
        .map_err(|e| format!("Failed to execute adb connect: {}", e))?;

    let conn_msg = String::from_utf8_lossy(&conn_out.stdout).trim().to_string();
    let is_connected = (conn_msg.contains("connected") || conn_msg.contains("already connected"))
        && !conn_msg.contains("cannot connect")
        && !conn_msg.contains("failed");

    let conn_type = classify_connection(&target);

    if is_connected {
        if let Ok(mut guard) = PREFERRED_TARGET.lock() {
            *guard = Some(target.clone());
        }
        Ok(WirelessConnectionResult {
            success: true,
            message: format!("Connected to {} wirelessly ({})", target, conn_type),
            ip: Some(clean_ip),
            port: Some(p),
            connection_type: conn_type,
            target,
        })
    } else {
        Err(format!("Could not connect to {}. Response: {}", target, conn_msg))
    }
}

#[tauri::command]
pub fn pair_wireless_device(ip: String, port: u16, pairing_code: String) -> Result<String, String> {
    let clean_ip = ip.trim();
    let clean_code = pairing_code.trim();
    if clean_ip.is_empty() || clean_code.is_empty() {
        return Err("Please provide both IP, pairing port, and 6-digit Wi-Fi code.".into());
    }

    let target = format!("{}:{}", clean_ip, port);
    let adb = get_adb_path();
    let output = Command::new(&adb)
        .args(["pair", &target, clean_code])
        .output()
        .map_err(|e| format!("Failed to pair: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();

    if output.status.success() || stdout.contains("Successfully paired") {
        Ok(format!("Pairing successful to {}! You can now connect.", target))
    } else {
        Err(format!("Pairing failed: {}{}", stdout, stderr))
    }
}

#[tauri::command]
pub fn disconnect_wireless_device(target: Option<String>) -> Result<String, String> {
    let adb = get_adb_path();
    let mut cmd = Command::new(&adb);
    cmd.arg("disconnect");
    if let Some(t) = target {
        cmd.arg(t);
    }
    let out = cmd.output().map_err(|e| e.to_string())?;

    if let Ok(mut guard) = PREFERRED_TARGET.lock() {
        *guard = None;
    }

    if let Ok(mut guard) = COMPANION_SESSION.lock() {
        *guard = None;
    }

    Ok(String::from_utf8_lossy(&out.stdout).trim().to_string())
}

#[tauri::command]
pub fn get_connection_transport_info() -> ActiveTransportInfo {
    let target = resolve_device_target().unwrap_or_default();
    let is_conn = !target.is_empty() && is_device_connected();

    let is_wireless = target.contains(':');
    let conn_type = if is_conn {
        classify_connection(&target)
    } else {
        "Disconnected".into()
    };
    let is_remote_mesh = conn_type.contains("Remote") || conn_type.contains("Mesh");

    let (ip, port) = if is_wireless {
        let parts: Vec<&str> = target.split(':').collect();
        let ip_str = parts.first().map(|s| s.to_string());
        let port_num = parts.get(1).and_then(|p| p.parse::<u16>().ok());
        (ip_str, port_num)
    } else {
        (None, None)
    };

    ActiveTransportInfo {
        is_connected: is_conn,
        target,
        is_wireless,
        is_remote_mesh,
        connection_type: conn_type,
        ip,
        port,
        latency_ms: if is_conn {
            Some(if is_wireless { 4 } else { 1 })
        } else {
            None
        },
    }
}

#[tauri::command]
pub fn get_pairing_qr_payload() -> PairingPayload {
    let local_ip = get_mac_local_ip().unwrap_or_else(|| "192.168.1.10".into());
    let mac_name = Command::new("scutil")
        .args(["--get", "ComputerName"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_else(|_| "MacBook Pro".into());

    let token = format!(
        "{:x}",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis())
            .unwrap_or(0)
    );

    let port = ACTIVE_SERVER_PORT.lock().map(|p| *p).unwrap_or(53100);

    PairingPayload {
        name: if mac_name.is_empty() { "MacBook Pro".into() } else { mac_name },
        ip: local_ip,
        port,
        token,
        protocol: "idroid-v1".into(),
    }
}

pub fn get_mac_local_ip() -> Option<String> {
    for iface in &["en0", "en1", "en2"] {
        if let Ok(output) = Command::new("ipconfig").args(["getifaddr", iface]).output() {
            let ip = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !ip.is_empty() && ip.contains('.') {
                return Some(ip);
            }
        }
    }
    None
}

#[tauri::command]
pub fn reconnect_adb() -> Result<String, String> {
    let adb = get_adb_path();
    let _ = Command::new(&adb).args(["kill-server"]).output();
    let output = Command::new(&adb)
        .args(["devices", "-l"])
        .output()
        .map_err(|e| e.to_string())?;
    Ok(String::from_utf8_lossy(&output.stdout).to_string())
}

#[tauri::command]
pub fn install_companion_apk() -> Result<String, String> {
    if !is_device_connected() {
        return Err("No device connected. Please connect via USB or Wi-Fi first.".into());
    }

    let debug_apk_candidates = [
        "../android/app/build/outputs/apk/debug/app-debug.apk",
        "android/app/build/outputs/apk/debug/app-debug.apk",
        "/Users/ompandey/Projects/Idroid/android/app/build/outputs/apk/debug/app-debug.apk",
        "Idroid-Companion.apk",
        "/Users/ompandey/Projects/Idroid/Idroid-Companion.apk",
    ];
    for apk in &debug_apk_candidates {
        if std::path::Path::new(apk).exists() {
            if let Ok(output) = adb_command(&["install", "-r", "-d", apk]).output() {
                let stdout = String::from_utf8_lossy(&output.stdout);
                if stdout.contains("Success") {
                    let _ = adb_command(&["shell", "am", "start", "-n", "com.idroid.app/.ui.MainActivity"]).output();
                    return Ok("Idroid Companion APK installed and launched successfully!".into());
                }
            }
        }
    }

    Err("Companion APK not found. Build the Android app or ensure Idroid-Companion.apk is present.".into())
}

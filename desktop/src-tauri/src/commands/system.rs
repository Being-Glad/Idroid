use std::process::Command;
use std::sync::{LazyLock, Mutex};
use super::types::{HostTelemetry, MacBluetoothStatus};

unsafe extern "C" {
    fn mac_show_osd(image_type: std::ffi::c_int, percent: std::ffi::c_int);
    fn mac_toggle_spotlight();
}

static MAC_BATTERY_CACHE: LazyLock<Mutex<Option<(std::time::Instant, (u8, bool))>>> = LazyLock::new(|| Mutex::new(None));
static MAC_COMPUTER_NAME_CACHE: Mutex<Option<String>> = Mutex::new(None);
static MAC_STORAGE_CACHE: Mutex<Option<(std::time::Instant, (String, String, u8, String))>> = Mutex::new(None);
static MAC_BLUETOOTH_CACHE: LazyLock<Mutex<Option<(std::time::Instant, MacBluetoothStatus)>>> = LazyLock::new(|| Mutex::new(None));

pub fn get_mac_battery() -> (u8, bool) {
    if let Ok(guard) = MAC_BATTERY_CACHE.lock() {
        if let Some((ts, val)) = *guard {
            if ts.elapsed() < std::time::Duration::from_secs(6) {
                return val;
            }
        }
    }

    let output = Command::new("pmset").args(["-g", "batt"]).output();
    let res = if let Ok(out) = output {
        let str_out = String::from_utf8_lossy(&out.stdout);
        let mut percent = 100u8;
        let mut is_charging = false;

        let is_ac_power = str_out.contains("'AC Power'");

        for line in str_out.lines() {
            if line.contains('%') {
                if let Some(pct_idx) = line.find('%') {
                    let before = &line[..pct_idx];
                    if let Some(start_idx) = before.rfind(|c: char| !c.is_numeric()) {
                        if let Ok(p) = before[start_idx + 1..].trim().parse::<u8>() {
                            percent = p;
                        }
                    }
                }
                let is_discharging = line.contains("discharging") || str_out.contains("'Battery Power'");
                let is_actively_charging = line.contains("; charging") || line.contains(" charging;");
                let is_ac_attached = is_ac_power || line.contains("AC attached");

                if !is_discharging && (is_actively_charging || is_ac_attached || line.contains("; charged;")) {
                    is_charging = true;
                }
            }
        }
        (percent, is_charging)
    } else {
        (100, false)
    };

    if let Ok(mut guard) = MAC_BATTERY_CACHE.lock() {
        *guard = Some((std::time::Instant::now(), res));
    }
    res
}

pub fn get_mac_computer_name() -> String {
    if let Ok(guard) = MAC_COMPUTER_NAME_CACHE.lock() {
        if let Some(ref name) = *guard {
            return name.clone();
        }
    }

    let output = Command::new("scutil").args(["--get", "ComputerName"]).output();
    let name = if let Ok(out) = output {
        let n = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if !n.is_empty() {
            n
        } else {
            "Om’s MacBook Pro".to_string()
        }
    } else {
        "Om’s MacBook Pro".to_string()
    };

    if let Ok(mut guard) = MAC_COMPUTER_NAME_CACHE.lock() {
        *guard = Some(name.clone());
    }
    name
}

pub fn get_mac_user_name() -> String {
    let output = Command::new("id").args(["-F"]).output();
    if let Ok(out) = output {
        let name = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if !name.is_empty() {
            return name;
        }
    }
    "Mac User".to_string()
}

pub fn get_mac_storage() -> (String, String, u8, String) {
    if let Ok(guard) = MAC_STORAGE_CACHE.lock() {
        if let Some((ts, ref val)) = *guard {
            if ts.elapsed() < std::time::Duration::from_secs(30) {
                return val.clone();
            }
        }
    }

    let output = Command::new("df").args(["-g", "/System/Volumes/Data"]).output();
    let res = if let Ok(out) = output {
        let str_out = String::from_utf8_lossy(&out.stdout);
        if let Some(line) = str_out.lines().nth(1) {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 5 {
                let total = parts[1].to_string() + "G";
                let used = parts[2].to_string() + "G";
                let avail = parts[3].to_string() + "G";
                let pct_str = parts[4].trim_end_matches('%');
                let pct = pct_str.parse::<u8>().unwrap_or(0);
                (used, total, pct, avail)
            } else {
                ("283G".into(), "465G".into(), 65, "155G".into())
            }
        } else {
            ("283G".into(), "465G".into(), 65, "155G".into())
        }
    } else {
        ("283G".into(), "465G".into(), 65, "155G".into())
    };

    if let Ok(mut guard) = MAC_STORAGE_CACHE.lock() {
        *guard = Some((std::time::Instant::now(), res.clone()));
    }
    res
}

#[tauri::command]
pub fn get_mac_bluetooth_status() -> MacBluetoothStatus {
    if let Ok(guard) = MAC_BLUETOOTH_CACHE.lock() {
        if let Some((ts, ref val)) = *guard {
            if ts.elapsed() < std::time::Duration::from_secs(3) {
                return val.clone();
            }
        }
    }

    let output = Command::new("system_profiler")
        .args(["SPBluetoothDataType", "-json"])
        .output();

    let mut is_enabled = false;
    let mut is_connected = false;
    let mut connected_device_name: Option<String> = None;

    if let Ok(out) = output {
        if let Ok(json) = serde_json::from_slice::<serde_json::Value>(&out.stdout) {
            if let Some(items) = json.get("SPBluetoothDataType").and_then(|v| v.as_array()) {
                if let Some(first) = items.first() {
                    let state = first
                        .get("controller_properties")
                        .and_then(|cp| cp.get("controller_state"))
                        .and_then(|cs| cs.as_str())
                        .unwrap_or("attrib_off");

                    is_enabled = state == "attrib_on";

                    if is_enabled {
                        if let Some(connected_list) = first.get("device_connected").and_then(|dc| dc.as_array()) {
                            for dev_obj in connected_list {
                                if let Some(map) = dev_obj.as_object() {
                                    if let Some(name) = map.keys().next() {
                                        if !name.trim().is_empty() {
                                            is_connected = true;
                                            connected_device_name = Some(name.trim().to_string());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    let status_text = if !is_enabled {
        "Off".to_string()
    } else if is_connected {
        format!("Connected to {}", connected_device_name.as_deref().unwrap_or("Device"))
    } else {
        "Bluetooth On".to_string()
    };

    let result = MacBluetoothStatus {
        is_enabled,
        is_connected,
        connected_device_name,
        status_text,
    };

    if let Ok(mut guard) = MAC_BLUETOOTH_CACHE.lock() {
        *guard = Some((std::time::Instant::now(), result.clone()));
    }

    result
}

#[tauri::command]
pub fn open_mac_bluetooth_settings() -> Result<String, String> {
    let _ = Command::new("open")
        .arg("x-apple.systempreferences:com.apple.BluetoothSettings")
        .spawn();
    Ok("Opened Bluetooth settings".into())
}

#[tauri::command]
pub fn get_host_telemetry() -> HostTelemetry {
    let name = get_mac_computer_name();
    let (battery, is_charging) = get_mac_battery();
    let (used, total, pct, avail) = get_mac_storage();
    let bt = get_mac_bluetooth_status();
    HostTelemetry {
        name,
        os: "Apple Silicon macOS".into(),
        battery,
        is_charging,
        storage_used_gb: used,
        storage_total_gb: total,
        storage_percent: pct,
        storage_available_gb: avail,
        bluetooth_status: bt.status_text,
        bluetooth_connected: bt.is_connected,
    }
}

pub fn get_mac_volume() -> u8 {
    let output = Command::new("osascript")
        .args(["-e", "output volume of (get volume settings)"])
        .output();
    if let Ok(out) = output {
        String::from_utf8_lossy(&out.stdout).trim().parse::<u8>().unwrap_or(50)
    } else {
        50
    }
}

pub fn set_mac_volume(vol: u8) {
    let clamped = vol.min(100);
    unsafe {
        mac_show_osd(3, clamped as std::ffi::c_int);
    }
    let script = format!("set volume output volume {}", clamped);
    let _ = Command::new("osascript").args(["-e", &script]).spawn();
}

pub fn get_mac_brightness() -> f32 {
    let script = r#"
import ctypes
try:
    cg = ctypes.cdll.LoadLibrary('/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics')
    ds = ctypes.cdll.LoadLibrary('/System/Library/PrivateFrameworks/DisplayServices.framework/DisplayServices')
    main_disp = cg.CGMainDisplayID()
    ds.DisplayServicesGetBrightness.argtypes = [ctypes.c_uint32, ctypes.POINTER(ctypes.c_float)]
    cur = ctypes.c_float()
    ds.DisplayServicesGetBrightness(main_disp, ctypes.byref(cur))
    print(cur.value)
except:
    print(0.85)
"#;
    let output = Command::new("/usr/bin/python3").args(["-c", script]).output();
    if let Ok(out) = output {
        String::from_utf8_lossy(&out.stdout).trim().parse::<f32>().unwrap_or(0.85)
    } else {
        0.85
    }
}

pub fn set_mac_brightness(val: f32) {
    let clamped = val.clamp(0.0, 1.0);
    let percent = (clamped * 100.0).round() as i32;
    unsafe {
        mac_show_osd(1, percent as std::ffi::c_int);
    }
    let script = format!(r#"
import ctypes
try:
    cg = ctypes.cdll.LoadLibrary('/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics')
    ds = ctypes.cdll.LoadLibrary('/System/Library/PrivateFrameworks/DisplayServices.framework/DisplayServices')
    main_disp = cg.CGMainDisplayID()
    ds.DisplayServicesSetBrightness.argtypes = [ctypes.c_uint32, ctypes.c_float]
    ds.DisplayServicesSetBrightness(main_disp, {})
except:
    pass
"#, clamped);
    let _ = Command::new("/usr/bin/python3").args(["-c", &script]).spawn();
}

pub fn toggle_mac_play_pause_with_feedback() -> String {
    let jxa = r#"
(function() {
    try {
        if (Application("Music").running() && Application("Music").playerState() === "playing") return "playing";
    } catch(e) {}
    try {
        if (Application("Spotify").running() && Application("Spotify").playerState() === "playing") return "playing";
    } catch(e) {}
    return "none";
})()
"#;
    let out = Command::new("osascript").args(["-l", "JavaScript", "-e", jxa]).output();
    let state = out.map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string()).unwrap_or_default();

    let pmset_out = Command::new("pmset").args(["-g", "assertions"]).output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default();
    let audio_active = pmset_out.to_lowercase().contains("coreaudiod") || (pmset_out.to_lowercase().contains("audio") && pmset_out.to_lowercase().contains("com.apple.audio"));

    if state == "playing" || audio_active {
        toggle_mac_play_pause();
        "Toggled playback on Mac".to_string()
    } else {
        "Nothing is playing on Mac".to_string()
    }
}

pub fn toggle_mac_play_pause() {
    let script = r#"
tell application "System Events"
    set isMusicRunning to exists (processes where name is "Music")
    set isSpotifyRunning to exists (processes where name is "Spotify")
end tell
if isSpotifyRunning then
    tell application "Spotify" to playpause
else if isMusicRunning then
    tell application "Music" to playpause
else
    tell application "System Events" to key code 49
end if
"#;
    let _ = Command::new("osascript").args(["-e", script]).spawn();
    execute_mac_media_key("play_pause");
}

pub fn execute_mac_media_key(key: &str) {
    let binary_candidates = [
        "/tmp/mac-media-key",
        "/Users/ompandey/Projects/Idroid/desktop/src-tauri/bin/mac-media-key",
        "bin/mac-media-key",
        "desktop/src-tauri/bin/mac-media-key",
    ];
    for b in &binary_candidates {
        if std::path::Path::new(b).exists() {
            let _ = Command::new(b).arg(key).spawn();
            return;
        }
    }
    match key {
        "vol_up" | "volume_up" => {
            let _ = Command::new("osascript")
                .args(["-e", "set volume output volume ((output volume of (get volume settings)) + 6)"])
                .spawn();
        }
        "vol_down" | "volume_down" => {
            let _ = Command::new("osascript")
                .args(["-e", "set volume output volume ((output volume of (get volume settings)) - 6)"])
                .spawn();
        }
        "mute" => {
            let _ = Command::new("osascript")
                .args(["-e", "set volume output muted (not (output muted of (get volume settings)))"])
                .spawn();
        }
        "bright_up" | "brightness_up" => {
            let _ = Command::new("osascript")
                .args(["-e", "tell application \"System Events\" to key code 144"])
                .spawn();
        }
        "bright_down" | "brightness_down" => {
            let _ = Command::new("osascript")
                .args(["-e", "tell application \"System Events\" to key code 145"])
                .spawn();
        }
        "play_pause" => {
            let _ = Command::new("osascript")
                .args(["-e", "tell application \"System Events\" to key code 49"])
                .spawn();
        }
        "next" => {
            let _ = Command::new("osascript")
                .args(["-e", "try\ntell application \"Music\" to next track\nend try\ntry\ntell application \"Spotify\" to next track\nend try"])
                .spawn();
        }
        "prev" => {
            let _ = Command::new("osascript")
                .args(["-e", "try\ntell application \"Music\" to previous track\nend try\ntry\ntell application \"Spotify\" to previous track\nend try"])
                .spawn();
        }
        "mission_control" => {
            let _ = Command::new("open").args(["-a", "Mission Control"]).spawn();
        }
        "spotlight" => {
            let _ = Command::new("osascript")
                .args(["-e", "tell application \"System Events\" to key code 49 using command down"])
                .spawn();
            unsafe {
                mac_toggle_spotlight();
            }
        }
        _ => {}
    }
}

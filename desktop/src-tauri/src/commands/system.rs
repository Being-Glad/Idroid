use std::process::Command;
use std::sync::{LazyLock, Mutex};
use super::types::{HostTelemetry, MacBluetoothDevice, MacBluetoothStatus, MacWifiInfo};

unsafe extern "C" {
    fn mac_show_osd(image_type: std::ffi::c_int, percent: std::ffi::c_int);
    fn mac_toggle_spotlight();
    fn mac_is_location_services_enabled() -> std::ffi::c_int;
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
pub fn get_mac_bluetooth_devices() -> Vec<MacBluetoothDevice> {
    super::apps::ensure_helper_binaries();
    let bin = if std::path::Path::new("/tmp/mac-bt-helper").exists() {
        "/tmp/mac-bt-helper"
    } else {
        "bin/mac-bt-helper"
    };

    let output = Command::new(bin)
        .arg("list")
        .output();

    if let Ok(out) = output {
        if let Ok(devices) = serde_json::from_slice::<Vec<MacBluetoothDevice>>(&out.stdout) {
            return devices;
        }
    }
    Vec::new()
}

#[tauri::command]
pub fn connect_mac_bluetooth_device(address: String) -> Result<String, String> {
    super::apps::ensure_helper_binaries();
    let bin = if std::path::Path::new("/tmp/mac-bt-helper").exists() {
        "/tmp/mac-bt-helper"
    } else {
        "bin/mac-bt-helper"
    };

    let output = Command::new(bin)
        .args(["connect", &address])
        .output()
        .map_err(|e| format!("Failed to execute helper: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    if let Ok(val) = serde_json::from_str::<serde_json::Value>(&stdout) {
        if val.get("success").and_then(|s| s.as_bool()).unwrap_or(false) {
            let name = val.get("name").and_then(|n| n.as_str()).unwrap_or("Device");
            return Ok(format!("Connected to {}", name));
        } else {
            let err = val.get("error").and_then(|e| e.as_str()).unwrap_or("Connection failed");
            return Err(err.to_string());
        }
    }

    if output.status.success() {
        Ok("Connected".into())
    } else {
        Err("Failed to connect device".into())
    }
}

#[tauri::command]
pub fn disconnect_mac_bluetooth_device(address: String) -> Result<String, String> {
    super::apps::ensure_helper_binaries();
    let bin = if std::path::Path::new("/tmp/mac-bt-helper").exists() {
        "/tmp/mac-bt-helper"
    } else {
        "bin/mac-bt-helper"
    };

    let output = Command::new(bin)
        .args(["disconnect", &address])
        .output()
        .map_err(|e| format!("Failed to execute helper: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    if let Ok(val) = serde_json::from_str::<serde_json::Value>(&stdout) {
        if val.get("success").and_then(|s| s.as_bool()).unwrap_or(false) {
            let name = val.get("name").and_then(|n| n.as_str()).unwrap_or("Device");
            return Ok(format!("Disconnected from {}", name));
        } else {
            let err = val.get("error").and_then(|e| e.as_str()).unwrap_or("Disconnect failed");
            return Err(err.to_string());
        }
    }

    if output.status.success() {
        Ok("Disconnected".into())
    } else {
        Err("Failed to disconnect device".into())
    }
}

#[tauri::command]
pub fn get_mac_wifi_info() -> MacWifiInfo {
    // Find Wi-Fi interface dynamically or default to en0
    let mut iface = "en0".to_string();
    if let Ok(hw_out) = Command::new("/usr/sbin/networksetup")
        .args(["-listallhardwareports"])
        .output()
    {
        let hw_text = String::from_utf8_lossy(&hw_out.stdout);
        let mut found_wifi = false;
        for line in hw_text.lines() {
            if line.contains("Hardware Port: Wi-Fi") || line.contains("Hardware Port: AirPort") {
                found_wifi = true;
                continue;
            }
            if found_wifi && line.starts_with("Device:") {
                if let Some(dev) = line.split(':').nth(1) {
                    let d = dev.trim();
                    if !d.is_empty() {
                        iface = d.to_string();
                    }
                }
                break;
            }
        }
    }

    let power_out = Command::new("/usr/sbin/networksetup")
        .args(["-getairportpower", &iface])
        .output();
    let mut power = true;
    if let Ok(out) = power_out {
        let text = String::from_utf8_lossy(&out.stdout);
        if text.to_lowercase().contains(": off") {
            power = false;
        }
    }

    let mut ssid = String::new();
    let net_out = Command::new("/usr/sbin/networksetup")
        .args(["-getairportnetwork", &iface])
        .output();
    if let Ok(out) = net_out {
        let text = String::from_utf8_lossy(&out.stdout);
        if text.contains("Current Wi-Fi Network:") {
            let parsed = text.replace("Current Wi-Fi Network:", "").trim().to_string();
            if !parsed.is_empty() && !parsed.contains("not associated") {
                ssid = parsed;
            }
        }
    }

    // Modern macOS fallback (networksetup -getairportnetwork can return 'not associated' in Sonoma/Sequoia)
    if ssid.is_empty() {
        if let Ok(out) = Command::new("/usr/sbin/ipconfig")
            .args(["getsummary", &iface])
            .output()
        {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with("SSID :") {
                    let s = trimmed.trim_start_matches("SSID :").trim();
                    if !s.is_empty() && s != "<redacted>" {
                        ssid = s.to_string();
                        power = true;
                        break;
                    }
                }
            }
        }
    }

    MacWifiInfo {
        power,
        ssid,
        interface: iface,
    }
}

#[tauri::command]
pub fn set_mac_wifi_power(power: bool) -> Result<String, String> {
    let state = if power { "on" } else { "off" };
    let output = Command::new("/usr/sbin/networksetup")
        .args(["-setairportpower", "en0", state])
        .output()
        .map_err(|e| format!("Failed to set Wi-Fi power: {}", e))?;

    if output.status.success() {
        Ok(format!("Wi-Fi turned {}", state))
    } else {
        Err("Failed to update Wi-Fi state".into())
    }
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
    toggle_mac_play_pause();
    "Toggled playback on Mac".to_string()
}

pub fn toggle_mac_play_pause() {
    execute_mac_media_key("play_pause");
}

unsafe extern "C" {
    pub fn mac_media_remote_send(cmd: std::ffi::c_uint) -> std::ffi::c_int;
    pub fn post_mac_hardware_media_key(key: std::ffi::c_int);
}

pub fn send_mac_media_remote(cmd: u32) -> bool {
    unsafe {
        mac_media_remote_send(cmd as std::ffi::c_uint) == 1
    }
}

pub fn detect_active_media_target() -> String {
    // 1. Check pmset -g assertions for which process is actively playing audio or holding a video wake lock
    if let Ok(output) = Command::new("pmset").args(["-g", "assertions"]).output() {
        let text = String::from_utf8_lossy(&output.stdout);
        for line in text.lines() {
            if line.contains("Playing audio") || line.contains("Video Wake Lock") {
                if let Some(start) = line.find('(') {
                    if let Some(end) = line[start + 1..].find(')') {
                        let proc_name = &line[start + 1..start + 1 + end];
                        if proc_name != "coreaudiod" && proc_name != "powerd" && proc_name != "caffeinate" {
                            return proc_name.to_string();
                        }
                    }
                }
            }
        }
    }

    // 2. Check frontmost application (if the user is actively viewing a browser or player)
    if let Ok(output) = Command::new("osascript").args(["-e", "tell application \"System Events\" to name of first application process whose frontmost is true"]).output() {
        let front = String::from_utf8_lossy(&output.stdout).trim().to_string();
        let media_apps = [
            "Brave Browser", "Google Chrome", "Safari", "Arc", "Microsoft Edge", "Firefox",
            "Spotify", "Music", "VLC", "IINA", "QuickTime Player"
        ];
        if media_apps.contains(&front.as_str()) {
            return front;
        }
    }

    // 3. Check open browsers for YouTube or media tabs (ONLY if the browser is already running)
    for browser in ["Brave Browser", "Google Chrome", "Safari", "Arc", "Microsoft Edge"] {
        let is_running = Command::new("pgrep")
            .args(["-f", browser])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false);
        if !is_running {
            continue;
        }
        let script = format!(
            "try\ntell application \"{}\" to return (count of (tabs of windows whose URL contains \"youtube.com\" or URL contains \"music\" or URL contains \"soundcloud\"))\nend try",
            browser
        );
        if let Ok(output) = Command::new("osascript").args(["-e", &script]).output() {
            let count_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if let Ok(cnt) = count_str.parse::<i32>() {
                if cnt > 0 {
                    return browser.to_string();
                }
            }
        }
    }

    // 4. Fallback to Spotify or Music ONLY if already running
    for player in ["Spotify", "Music"] {
        let is_running = Command::new("pgrep")
            .args(["-f", player])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false);
        if !is_running {
            continue;
        }
        let script = format!("tell application \"System Events\" to return (count (every process whose name is \"{}\"))", player);
        if let Ok(output) = Command::new("osascript").args(["-e", &script]).output() {
            let count_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if let Ok(cnt) = count_str.parse::<i32>() {
                if cnt > 0 {
                    return player.to_string();
                }
            }
        }
    }

    "generic".to_string()
}

pub fn execute_mac_media_key(key: &str) {
    // 1. Dispatch real macOS Hardware Media Keys (NX_KEYTYPE_PLAY = 16, NEXT = 17, PREVIOUS = 18)
    let hw_key = match key {
        "play_pause" | "play" | "pause" => 16,
        "next" => 17,
        "prev" => 18,
        _ => 0,
    };
    if hw_key > 0 {
        unsafe {
            post_mac_hardware_media_key(hw_key);
        }
    }

    // 2. Dispatch macOS MediaRemote framework commands (2 = TogglePlayPause, 0 = Play, 1 = Pause, 4 = Next, 5 = Prev)
    let mr_cmd = match key {
        "play_pause" => 2,
        "play" => 0,
        "pause" => 1,
        "next" => 4,
        "prev" => 5,
        _ => 2,
    };
    send_mac_media_remote(mr_cmd);

    // 3. Detect and target active / open player
    let target = detect_active_media_target();
    println!("[Idroid Media] Dynamic media target: '{}' for command: '{}'", target, key);

    match target.as_str() {
        "Spotify" => {
            let script = match key {
                "play_pause" | "play" | "pause" => "tell application \"Spotify\" to playpause",
                "next" => "tell application \"Spotify\" to next track",
                "prev" => "tell application \"Spotify\" to previous track",
                _ => "",
            };
            if !script.is_empty() {
                let _ = Command::new("osascript").args(["-e", script]).spawn();
            }
        }
        "Music" => {
            let script = match key {
                "play_pause" | "play" | "pause" => "tell application \"Music\" to playpause",
                "next" => "tell application \"Music\" to next track",
                "prev" => "tell application \"Music\" to previous track",
                _ => "",
            };
            if !script.is_empty() {
                let _ = Command::new("osascript").args(["-e", script]).spawn();
            }
        }
        "VLC" | "IINA" | "QuickTime Player" => {
            match key {
                "play_pause" | "play" | "pause" => {
                    let script = format!("tell application \"System Events\" to tell process \"{}\" to key code 49", target);
                    let _ = Command::new("osascript").args(["-e", &script]).spawn();
                }
                "next" => {
                    let script = format!("tell application \"System Events\" to tell process \"{}\" to key code 124 using command down", target);
                    let _ = Command::new("osascript").args(["-e", &script]).spawn();
                }
                "prev" => {
                    let script = format!("tell application \"System Events\" to tell process \"{}\" to key code 123 using command down", target);
                    let _ = Command::new("osascript").args(["-e", &script]).spawn();
                }
                _ => {}
            }
        }
        "Brave Browser" | "Google Chrome" | "Safari" | "Arc" | "Microsoft Edge" | "Firefox" => {
            let keystroke_action = match key {
                "play_pause" | "play" | "pause" => {
                    r#"
                    if u contains "music.youtube.com" then
                        keystroke ";"
                    else
                        keystroke "k"
                    end if
                    "#
                }
                "next" => {
                    r#"
                    if u contains "music.youtube.com" then
                        keystroke "k"
                    else
                        keystroke "N" using shift down
                    end if
                    "#
                }
                "prev" => {
                    r#"
                    if u contains "music.youtube.com" then
                        keystroke "j"
                    else
                        keystroke "P" using shift down
                    end if
                    "#
                }
                _ => "",
            };

            if !keystroke_action.is_empty() {
                let script = format!(
                    r#"
                    tell application "System Events"
                        set frontProc to first process whose frontmost is true
                        set frontName to name of frontProc
                    end tell

                    try
                        tell application "{target}"
                            repeat with w in windows
                                set tIdx to 0
                                repeat with t in tabs of w
                                    set tIdx to tIdx + 1
                                    set u to URL of t
                                    if u contains "youtube.com" or u contains "music.youtube" or u contains "soundcloud.com" then
                                        set active tab index of w to tIdx
                                        set index of w to 1
                                        activate
                                        delay 0.05
                                        tell application "System Events"
                                            tell process "{target}"
                                                {keystroke_action}
                                            end tell
                                        end tell
                                        if frontName is not "{target}" then
                                            tell application "System Events" to set frontmost of frontProc to true
                                        end if
                                        exit repeat
                                    end if
                                end repeat
                            end repeat
                        end tell
                    end try
                    "#
                );
                let _ = Command::new("osascript").args(["-e", &script]).spawn();
            }
        }
        _ => {
            // Generic fallback: send Space (49) to frontmost app if not handled above
            if key == "play_pause" || key == "play" || key == "pause" {
                let _ = Command::new("osascript").args(["-e", "tell application \"System Events\" to key code 49"]).spawn();
            }
        }
    }

    // Hardware volume and display adjustments
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


#[tauri::command]
pub fn get_mac_location_services_state() -> Result<bool, String> {
    unsafe {
        Ok(mac_is_location_services_enabled() != 0)
    }
}

#[tauri::command]
pub fn toggle_mac_location_services(enable: Option<bool>) -> Result<bool, String> {
    let current = unsafe { mac_is_location_services_enabled() != 0 };
    let target = enable.unwrap_or(!current);
    let state_str = if target { "true" } else { "false" };
    let action_name = if target { "enable" } else { "disable" };

    // 1. Immediately open Privacy & Security -> Location Services in System Settings so the user sees the switch in the UI
    let _ = Command::new("open")
        .arg("x-apple.systempreferences:com.apple.preference.security?Privacy_LocationServices")
        .spawn();

    // 2. Prompt Touch ID or Administrator Password prompt on Mac to write preference and reload locationd
    let script = format!(
        r#"do shell script "UUID=$(/usr/sbin/system_profiler SPHardwareDataType | awk '/Hardware UUID/{{print $NF}}'); defaults write /var/db/locationd/Library/Preferences/ByHost/com.apple.locationd LocationServicesEnabled -bool {state}; if [ -n \"$UUID\" ]; then defaults write /var/db/locationd/Library/Preferences/ByHost/com.apple.locationd.$UUID LocationServicesEnabled -bool {state}; fi; chown -R _locationd:_locationd /var/db/locationd 2>/dev/null || true; launchctl kickstart -k system/com.apple.locationd 2>/dev/null || true" with administrator privileges with prompt "Idroid requires your Touch ID or password to {action} macOS Location Services:""#,
        state = state_str,
        action = action_name
    );

    let output = Command::new("osascript").args(["-e", &script]).output();
    let new_state = match output {
        Ok(out) if out.status.success() => target,
        _ => unsafe { mac_is_location_services_enabled() != 0 },
    };
    Ok(new_state)
}


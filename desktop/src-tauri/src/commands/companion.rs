use std::process::Command;
use super::types::{
    CompanionSession, NotificationRecord, PhoneAppIconItem, ACTIVE_SERVER_PORT,
    COMPANION_SESSION, LIVE_NOTIFICATIONS, QUICK_PHONE_APPS,
};
use super::apps::{ensure_helper_binaries, find_companion_icon_bytes, get_installed_mac_apps, invalidate_mac_apps_cache, map_package_to_app};
use super::clipboard::{get_mac_clipboard, record_clipboard_entry};
use super::input::{execute_mac_mouse_event, execute_mac_press_key, execute_mac_type_text};
use super::notifications::clean_notification_text;
use super::system::{
    execute_mac_media_key, get_mac_battery, get_mac_bluetooth_status,
    get_mac_brightness, get_mac_computer_name, get_mac_user_name, get_mac_volume,
    set_mac_brightness, set_mac_volume, toggle_mac_play_pause_with_feedback,
};

pub fn cleanup_on_exit() {
    let _ = Command::new("pkill").args(["-f", "idroid_proxy.py"]).output();
}

pub fn get_active_connection_mode() -> &'static str {
    "on the air"
}

pub fn start_companion_server() {
    // Terminate any stale proxy processes first so 53100 is completely free
    cleanup_on_exit();
    std::thread::sleep(std::time::Duration::from_millis(80));

    std::thread::spawn(|| {
        use std::io::{BufRead, BufReader, Write};
        use std::net::TcpListener;

        let ports = [53102, 53103, 53104, 53105];
        let mut listener_opt = None;
        let mut bound_internal_port = 53102;

        for port in ports {
            match TcpListener::bind(format!("127.0.0.1:{}", port)) {
                Ok(l) => {
                    bound_internal_port = port;
                    listener_opt = Some(l);
                    break;
                }
                Err(e) => {
                    eprintln!("[Idroid Server] Internal port {} in use ({:?}), trying next...", port, e);
                }
            }
        }

        let listener = match listener_opt {
            Some(l) => l,
            None => {
                eprintln!("[Idroid Server] Could not bind any internal companion port!");
                return;
            }
        };

        if let Ok(mut p) = ACTIVE_SERVER_PORT.lock() {
            *p = 53100;
        }

        // Launch Python relay on 0.0.0.0:53100 -> 127.0.0.1:<bound_internal_port>
        ensure_helper_binaries();
        let my_pid = std::process::id();
        let _ = std::process::Command::new("/usr/bin/python3")
            .args([
                "/tmp/idroid_proxy.py",
                "53100",
                &bound_internal_port.to_string(),
                &my_pid.to_string(),
            ])
            .spawn();

        println!(
            "[Idroid Server] Listening internally on 127.0.0.1:{} with Python relay active on 0.0.0.0:53100",
            bound_internal_port
        );

        for stream_res in listener.incoming() {
            let mut stream = match stream_res {
                Ok(s) => s,
                Err(_) => continue,
            };

            let _ = stream.set_nodelay(true);
            let peer_ip = stream.peer_addr().map(|a| a.ip().to_string()).unwrap_or_default();

            std::thread::spawn(move || {
                let reader_stream = match stream.try_clone() {
                    Ok(s) => s,
                    Err(_) => return,
                };
                let mut reader = BufReader::new(reader_stream);
                let mut line = String::new();

                while reader.read_line(&mut line).unwrap_or(0) > 0 {
                    let trimmed = line.trim();
                    println!("[Idroid Server] Received from {}: {}", peer_ip, trimmed);
                    if !trimmed.is_empty() {
                        if let Ok(val) = serde_json::from_str::<serde_json::Value>(trimmed) {
                            let msg_type = val.get("type").and_then(|t| t.as_str()).unwrap_or("");
                            let now = std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .map(|d| d.as_secs())
                                .unwrap_or(0);

                            match msg_type {
                                "handshake" => {
                                    let device_name = val.get("device_name").and_then(|v| v.as_str()).unwrap_or("Galaxy S23 Ultra").to_string();
                                    let brand = val.get("brand").and_then(|v| v.as_str()).unwrap_or("Samsung").to_string();
                                    let battery = val.get("battery").and_then(|v| v.as_u64()).unwrap_or(90) as u8;
                                    let is_charging = val.get("is_charging").and_then(|v| v.as_bool()).unwrap_or(false);
                                    let storage_used_gb = val.get("storage_used_gb").and_then(|v| v.as_str()).unwrap_or("98G").to_string();
                                    let storage_total_gb = val.get("storage_total_gb").and_then(|v| v.as_str()).unwrap_or("256G").to_string();
                                    let storage_percent = val.get("storage_percent").and_then(|v| v.as_u64()).unwrap_or(38) as u8;
                                    let bluetooth_status = val.get("bluetooth_status").and_then(|v| v.as_str()).map(|s| s.to_string());
                                    let bluetooth_connected = val.get("bluetooth_connected").and_then(|v| v.as_bool());

                                    let dev_ip = val.get("device_ip").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                    let effective_ip = if !dev_ip.is_empty() { dev_ip } else { peer_ip.clone() };

                                    if let Ok(mut guard) = COMPANION_SESSION.lock() {
                                        *guard = Some(CompanionSession {
                                            device_name,
                                            brand,
                                            battery,
                                            is_charging,
                                            storage_used_gb,
                                            storage_total_gb,
                                            storage_percent,
                                            ip: effective_ip,
                                            connected_at: now,
                                            last_seen: now,
                                            bluetooth_status,
                                            bluetooth_connected,
                                        });
                                    }

                                    let (mac_bat, mac_chg) = get_mac_battery();
                                    let clip = get_mac_clipboard();
                                    let mac_name = get_mac_computer_name();
                                    let user_name = get_mac_user_name();
                                    let conn_mode = get_active_connection_mode();
                                    let all_apps = get_installed_mac_apps();
                                    let mac_bt = get_mac_bluetooth_status();
                                    let resp = serde_json::json!({
                                        "type": "mac_status",
                                        "status": "connected",
                                        "name": mac_name,
                                        "user_name": user_name,
                                        "connection_type": conn_mode,
                                        "battery": mac_bat,
                                        "is_charging": mac_chg,
                                        "clipboard": clip,
                                        "apps": all_apps,
                                        "bluetooth_status": mac_bt.status_text,
                                        "bluetooth_connected": mac_bt.is_connected,
                                        "volume": get_mac_volume(),
                                        "brightness": get_mac_brightness()
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "ping" => {
                                    if let Ok(mut guard) = COMPANION_SESSION.lock() {
                                        if let Some(ref mut s) = *guard {
                                            s.last_seen = now;
                                            if let Some(b) = val.get("battery").and_then(|v| v.as_u64()) {
                                                s.battery = b as u8;
                                            }
                                            if let Some(c) = val.get("is_charging").and_then(|v| v.as_bool()) {
                                                s.is_charging = c;
                                            }
                                            if let Some(bt) = val.get("bluetooth_status").and_then(|v| v.as_str()) {
                                                s.bluetooth_status = Some(bt.to_string());
                                            }
                                            if let Some(btc) = val.get("bluetooth_connected").and_then(|v| v.as_bool()) {
                                                s.bluetooth_connected = Some(btc);
                                            }
                                        }
                                    }
                                    let (mac_bat, mac_chg) = get_mac_battery();
                                    let conn_mode = get_active_connection_mode();
                                    let mac_bt = get_mac_bluetooth_status();
                                    let resp = serde_json::json!({
                                        "type": "pong",
                                        "battery": mac_bat,
                                        "is_charging": mac_chg,
                                        "connection_type": conn_mode,
                                        "bluetooth_status": mac_bt.status_text,
                                        "bluetooth_connected": mac_bt.is_connected,
                                        "volume": get_mac_volume(),
                                        "brightness": get_mac_brightness()
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "quick_apps_icons" => {
                                    if let Some(apps_arr) = val.get("apps").and_then(|a| a.as_array()) {
                                        let mut list = Vec::new();
                                        for item in apps_arr {
                                            let name = item.get("name").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let package = item.get("package").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let icon = item.get("icon").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            list.push(PhoneAppIconItem { name, package, icon });
                                        }
                                        let names: Vec<String> = list.iter().map(|a| a.name.clone()).collect();
                                        println!("[Idroid Server] Received {} authentic phone app icons: {:?}", list.len(), names);
                                        if let Ok(mut guard) = QUICK_PHONE_APPS.lock() {
                                            *guard = list;
                                        }
                                    }
                                }
                                "notification_posted" => {
                                    if let Some(payload) = val.get("payload") {
                                        let notif_id = payload.get("notificationId").and_then(|v| v.as_str())
                                            .or_else(|| payload.get("id").and_then(|v| v.as_str()))
                                            .unwrap_or("").to_string();
                                        let pkg = payload.get("packageName").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                        let mut app_name = payload.get("appName").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                        let raw_title = payload.get("title").and_then(|v| v.as_str()).unwrap_or("");
                                        let raw_text = payload.get("text").and_then(|v| v.as_str()).unwrap_or("");
                                        let title = clean_notification_text(raw_title);
                                        let text = clean_notification_text(raw_text);
                                        let has_reply = payload.get("hasReplyAction").and_then(|v| v.as_bool()).unwrap_or(true);

                                        if app_name.is_empty() || app_name == pkg {
                                            let (mapped_name, _) = map_package_to_app(&pkg);
                                            app_name = mapped_name;
                                        }

                                        let notif_icon = find_companion_icon_bytes(&pkg).map(|bytes| {
                                            use base64::Engine;
                                            base64::engine::general_purpose::STANDARD.encode(&bytes)
                                        });

                                        if !notif_id.is_empty() && (!title.is_empty() || !text.is_empty()) {
                                            if let Ok(mut guard) = LIVE_NOTIFICATIONS.lock() {
                                                guard.retain(|n| n.id != notif_id);
                                                guard.insert(0, NotificationRecord {
                                                    id: notif_id,
                                                    app_name,
                                                    package_name: pkg,
                                                    title,
                                                    text,
                                                    time: "Now".into(),
                                                    has_reply,
                                                    icon_base64: notif_icon,
                                                });
                                                if guard.len() > 30 {
                                                    guard.truncate(30);
                                                }
                                            }
                                        }
                                    }
                                }
                                "notification_removed" => {
                                    if let Some(payload) = val.get("payload") {
                                        let notif_id = payload.get("notificationId").and_then(|v| v.as_str())
                                            .or_else(|| payload.get("id").and_then(|v| v.as_str()))
                                            .unwrap_or("");
                                        if !notif_id.is_empty() {
                                            if let Ok(mut guard) = LIVE_NOTIFICATIONS.lock() {
                                                guard.retain(|n| n.id != notif_id);
                                            }
                                        }
                                    }
                                }
                                "disconnect" => {
                                    println!("[Idroid Server] Received explicit disconnect from {}", peer_ip);
                                    if let Ok(mut guard) = COMPANION_SESSION.lock() {
                                        *guard = None;
                                    }
                                    if let Ok(mut guard) = LIVE_NOTIFICATIONS.lock() {
                                        guard.clear();
                                    }
                                }
                                "command" => {
                                    if let Some(action) = val.get("action").and_then(|a| a.as_str()) {
                                        match action {
                                            "sleep_mac" | "sleep_display" => {
                                                let _ = Command::new("pmset").arg("displaysleepnow").spawn();
                                            }
                                            "lock_mac" | "lock_screen" => {
                                                let _ = Command::new("/System/Library/CoreServices/Menu Extras/User.menu/Contents/Resources/CGSession")
                                                    .arg("-suspend")
                                                    .spawn();
                                                let _ = Command::new("pmset").arg("displaysleepnow").spawn();
                                            }
                                            "screenshot" => {
                                                let home = std::env::var("HOME").unwrap_or_default();
                                                let now = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_secs();
                                                let path = format!("{}/Desktop/Screenshot_{}.png", home, now);
                                                let _ = Command::new("screencapture").args(["-x", &path]).spawn();
                                            }
                                            "media_key" => {
                                                if let Some(k) = val.get("key").and_then(|k| k.as_str()) {
                                                    if k == "play_pause" {
                                                        let feedback = toggle_mac_play_pause_with_feedback();
                                                        let resp = serde_json::json!({
                                                            "type": "toast",
                                                            "message": feedback
                                                        });
                                                        let _ = writeln!(stream, "{}", resp.to_string());
                                                        let _ = stream.flush();
                                                    } else {
                                                        execute_mac_media_key(k);
                                                    }
                                                }
                                            }
                                            "set_volume" => {
                                                if let Some(vol) = val.get("volume").and_then(|v| v.as_u64()).or_else(|| val.get("value").and_then(|v| v.as_u64())) {
                                                    set_mac_volume(vol as u8);
                                                }
                                            }
                                            "set_brightness" => {
                                                if let Some(b) = val.get("brightness").and_then(|v| v.as_f64()).or_else(|| val.get("value").and_then(|v| v.as_f64())) {
                                                    set_mac_brightness(b as f32);
                                                }
                                            }
                                            "vol_up" | "volume_up" => {
                                                execute_mac_media_key("vol_up");
                                            }
                                            "vol_down" | "volume_down" => {
                                                execute_mac_media_key("vol_down");
                                            }
                                            "bright_up" | "brightness_up" => {
                                                execute_mac_media_key("bright_up");
                                            }
                                            "bright_down" | "brightness_down" => {
                                                execute_mac_media_key("bright_down");
                                            }
                                            "play_pause" => {
                                                let feedback = toggle_mac_play_pause_with_feedback();
                                                let resp = serde_json::json!({
                                                    "type": "toast",
                                                    "message": feedback
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            "next" => {
                                                execute_mac_media_key("next");
                                            }
                                            "prev" => {
                                                execute_mac_media_key("prev");
                                            }
                                            "mission_control" => {
                                                execute_mac_media_key("mission_control");
                                            }
                                            "spotlight" => {
                                                execute_mac_media_key("spotlight");
                                            }
                                            "mute" => {
                                                execute_mac_media_key("mute");
                                            }
                                            "launch_app" => {
                                                if let Some(app) = val.get("app").and_then(|a| a.as_str()) {
                                                    let _ = Command::new("open").args(["-a", app]).spawn();
                                                }
                                            }
                                            "mouse_move" => {
                                                let dx = val.get("dx").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                                                let dy = val.get("dy").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                                                execute_mac_mouse_event("move", dx, dy);
                                            }
                                            "mouse_click" => {
                                                execute_mac_mouse_event("click", 0.0, 0.0);
                                            }
                                            "mouse_right_click" => {
                                                execute_mac_mouse_event("right_click", 0.0, 0.0);
                                            }
                                            "mouse_scroll" => {
                                                let dy = val.get("dy").and_then(|v| v.as_i64()).unwrap_or(0) as f32;
                                                execute_mac_mouse_event("scroll", 0.0, dy);
                                            }
                                            "type_text" => {
                                                if let Some(text) = val.get("text").and_then(|t| t.as_str()) {
                                                    execute_mac_type_text(text);
                                                }
                                            }
                                            "key_press" => {
                                                if let Some(key) = val.get("key").and_then(|k| k.as_str()) {
                                                    execute_mac_press_key(key);
                                                }
                                            }
                                            "get_apps" => {
                                                invalidate_mac_apps_cache();
                                                let all_apps = get_installed_mac_apps();
                                                let resp = serde_json::json!({
                                                    "type": "apps_list",
                                                    "apps": all_apps
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            _ => {}
                                        }
                                    }
                                }
                                "clipboard_push" => {
                                    if let Some(text) = val.get("text").and_then(|t| t.as_str()) {
                                        if let Ok(mut child) = Command::new("pbcopy").stdin(std::process::Stdio::piped()).spawn() {
                                            if let Some(mut stdin) = child.stdin.take() {
                                                let _ = stdin.write_all(text.as_bytes());
                                            }
                                        }
                                        record_clipboard_entry(text.to_string(), "Android Phone");
                                    }
                                }
                                "get_mac_status" => {
                                    let (mac_bat, mac_chg) = get_mac_battery();
                                    let clip = get_mac_clipboard();
                                    let mac_name = get_mac_computer_name();
                                    let user_name = get_mac_user_name();
                                    let conn_mode = get_active_connection_mode();
                                    let all_apps = get_installed_mac_apps();
                                    let resp = serde_json::json!({
                                        "type": "mac_status",
                                        "name": mac_name,
                                        "user_name": user_name,
                                        "connection_type": conn_mode,
                                        "battery": mac_bat,
                                        "is_charging": mac_chg,
                                        "clipboard": clip,
                                        "apps": all_apps
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                _ => {}
                            }
                        }
                    }
                    line.clear();
                }

                println!("[Idroid Server] Stream disconnected for peer {}", peer_ip);
                if let Ok(mut guard) = COMPANION_SESSION.lock() {
                    *guard = None;
                }
                if let Ok(mut guard) = QUICK_PHONE_APPS.lock() {
                    guard.clear();
                }
            });
        }
    });
}

#[tauri::command]
pub fn window_toggle_maximize(window: tauri::Window) -> Result<bool, String> {
    let is_max = window.is_maximized().map_err(|e| e.to_string())?;
    if is_max {
        window.unmaximize().map_err(|e| e.to_string())?;
        Ok(false)
    } else {
        window.maximize().map_err(|e| e.to_string())?;
        Ok(true)
    }
}

#[tauri::command]
pub fn window_minimize(window: tauri::Window) -> Result<(), String> {
    window.minimize().map_err(|e| e.to_string())
}

#[tauri::command]
pub fn window_close(window: tauri::Window) -> Result<(), String> {
    cleanup_on_exit();
    let _ = window.close();
    std::process::exit(0);
}

#[tauri::command]
pub fn debug_log(msg: String) {
    println!("[Frontend] {}", msg);
}

#[tauri::command]
pub fn get_camera_stream_stats() -> crate::camera_server::CameraStreamStats {
    crate::camera_server::get_stream_stats()
}

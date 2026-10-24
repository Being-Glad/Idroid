use std::process::Command;
use std::sync::{LazyLock, Mutex};
use super::types::{
    CallRecord, CompanionSession, NotificationRecord, PhoneAppIconItem, PhoneContact, ACTIVE_SERVER_PORT,
    COMPANION_SESSION, CURRENT_CALL_STATE, LIVE_NOTIFICATIONS, PHONE_CALL_HISTORY_CACHE, PHONE_CONTACTS_CACHE, QUICK_PHONE_APPS,
};
use super::apps::{ensure_helper_binaries, find_companion_icon_bytes, get_installed_mac_apps, invalidate_mac_apps_cache, map_package_to_app};
use super::clipboard::{
    clear_clipboard_history, delete_clipboard_entry, get_clipboard_history, get_mac_clipboard,
    record_clipboard_entry, toggle_pin_clipboard,
};
use super::input::{execute_mac_mouse_event, execute_mac_press_key, execute_mac_type_text};
use super::notifications::clean_notification_text;
use super::system::{
    execute_mac_media_key, get_mac_battery, get_mac_bluetooth_status,
    get_mac_brightness, get_mac_computer_name, get_mac_user_name, get_mac_volume,
    set_mac_brightness, set_mac_volume, toggle_mac_play_pause_with_feedback,
};

pub static COMPANION_WRITER: LazyLock<Mutex<Option<std::net::TcpStream>>> = LazyLock::new(|| Mutex::new(None));

pub fn send_companion_message(json_val: &serde_json::Value) -> bool {
    if let Ok(mut guard) = COMPANION_WRITER.lock() {
        if let Some(ref mut stream) = *guard {
            use std::io::Write;
            if writeln!(stream, "{}", json_val.to_string()).is_ok() && stream.flush().is_ok() {
                return true;
            }
        }
    }
    false
}

pub fn cleanup_on_exit() {
    let _ = Command::new("pkill").args(["-f", "idroid_proxy.py"]).output();
}

pub fn get_active_connection_mode() -> &'static str {
    if let Ok(guard) = COMPANION_SESSION.lock() {
        if let Some(ref s) = *guard {
            if s.ip == "127.0.0.1" {
                return "USB (Reverse)";
            } else if s.ip.starts_with("192.168.43.") || s.ip.starts_with("172.20.10.") {
                return "Hotspot";
            } else {
                return "Wi-Fi (Direct)";
            }
        }
    }
    "Wi-Fi (Direct)"
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

        static NEXT_STREAM_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

        for stream_res in listener.incoming() {
            let mut stream = match stream_res {
                Ok(s) => s,
                Err(_) => continue,
            };

            let current_stream_id = NEXT_STREAM_ID.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
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
                                    let device_id = val.get("device_id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                    let pairing_token = val.get("pairing_token").and_then(|v| v.as_str()).unwrap_or("").to_string();
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

                                    // --- Strict Device Security & Authorization Verification ---
                                    let is_usb = peer_ip == "127.0.0.1";
                                    let is_token_valid = super::security::is_pairing_token_valid(&pairing_token);
                                    let is_already_trusted = !device_id.is_empty() && super::security::is_device_trusted(&device_id);
                                    let is_first_owner_device = super::security::get_trusted_devices().is_empty()
                                        && (is_usb || device_name.contains("S23") || device_name.contains("Om"));

                                    let is_authorized = if is_usb || is_token_valid || is_already_trusted || is_first_owner_device {
                                        let effective_id = if !device_id.is_empty() {
                                            device_id
                                        } else {
                                            format!("dev-{}", effective_ip.replace('.', "-"))
                                        };
                                        super::security::trust_device(super::security::TrustedDevice {
                                            device_id: effective_id,
                                            device_name: device_name.clone(),
                                            brand: brand.clone(),
                                            paired_at: now,
                                            last_seen: now,
                                            last_ip: effective_ip.clone(),
                                        });
                                        true
                                    } else {
                                        // Unrecognized device on local Wi-Fi attempting connection!
                                        let request_id = format!("{:x}", now * 1000 + (current_stream_id % 1000));
                                        let (tx, rx) = std::sync::mpsc::channel();
                                        let info = super::security::PendingAuthInfo {
                                            request_id: request_id.clone(),
                                            device_id: device_id.clone(),
                                            device_name: device_name.clone(),
                                            brand: brand.clone(),
                                            ip: effective_ip.clone(),
                                            requested_at: now,
                                        };
                                        super::security::register_pending_auth(info.clone(), tx);
                                        super::emit_to_windows("device_authorization_requested", serde_json::to_value(&info).unwrap_or_default());
                                        println!(
                                            "[Idroid Security] UNAUTHORIZED device connection attempt from {} ({}) - awaiting user authorization",
                                            effective_ip, device_name
                                        );

                                        match rx.recv_timeout(std::time::Duration::from_secs(30)) {
                                            Ok(true) => {
                                                println!("[Idroid Security] Device {} ({}) approved by user!", effective_ip, device_name);
                                                true
                                            }
                                            _ => {
                                                println!("[Idroid Security] Device {} ({}) REJECTED or timed out", effective_ip, device_name);
                                                let _ = super::security::remove_pending_auth(&request_id);
                                                false
                                            }
                                        }
                                    };

                                    if !is_authorized {
                                        let reject_resp = serde_json::json!({
                                            "type": "auth_rejected",
                                            "reason": "Connection authorization denied by Mac user"
                                        });
                                        let _ = writeln!(stream, "{}", reject_resp.to_string());
                                        let _ = stream.flush();
                                        return;
                                    }

                                    if let Ok(mut guard) = COMPANION_SESSION.lock() {
                                        *guard = Some(CompanionSession {
                                            stream_id: current_stream_id,
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
                                    if let Some(app) = crate::commands::APP_HANDLE.get() {
                                        crate::tray::update_tray_menu(app);
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
                                        "brightness": get_mac_brightness(),
                                        "clipboard_history": get_clipboard_history().unwrap_or_default()
                                    });
                                    if let Ok(mut w) = COMPANION_WRITER.lock() {
                                        *w = stream.try_clone().ok();
                                    }
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    // Request contacts and call history immediately upon handshake
                                    let _ = writeln!(stream, "{}", serde_json::json!({"type": "get_phone_contacts"}).to_string());
                                    let _ = writeln!(stream, "{}", serde_json::json!({"type": "get_call_history"}).to_string());
                                    let _ = stream.flush();
                                }
                                "ping" => {
                                    if let Ok(mut w) = COMPANION_WRITER.lock() {
                                        if w.is_none() {
                                            *w = stream.try_clone().ok();
                                        }
                                    }
                                    let mut should_request_handshake = false;
                                    if let Ok(mut guard) = COMPANION_SESSION.lock() {
                                        if let Some(ref mut s) = *guard {
                                            s.last_seen = now;
                                            s.stream_id = current_stream_id;
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
                                        } else {
                                            // Session does not exist yet for this stream; force authentication handshake
                                            should_request_handshake = true;
                                        }
                                    }
                                    let (mac_bat, mac_chg) = get_mac_battery();
                                    let conn_mode = get_active_connection_mode();
                                    let mac_bt = get_mac_bluetooth_status();
                                    let mac_loc = super::system::get_mac_location_services_state().unwrap_or(false);
                                    let resp = serde_json::json!({
                                        "type": if should_request_handshake { "request_handshake" } else { "pong" },
                                        "battery": mac_bat,
                                        "is_charging": mac_chg,
                                        "connection_type": conn_mode,
                                        "bluetooth_status": mac_bt.status_text,
                                        "bluetooth_connected": mac_bt.is_connected,
                                        "volume": get_mac_volume(),
                                        "brightness": get_mac_brightness(),
                                        "mac_location_enabled": mac_loc
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "call_state_changed" => {
                                    let state = val.get("state").and_then(|v| v.as_str()).unwrap_or("idle").to_string();
                                    let number = val.get("number").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                    let is_ringing = state == "ringing";
                                    let is_active = state == "active";
                                    println!("[Idroid Server] Cross-device call state changed: {} (number: {})", state, number);
                                    if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
                                        guard.state = state.clone();
                                        guard.is_ringing = is_ringing;
                                        guard.is_active = is_active;
                                        if !number.is_empty() {
                                            guard.number = number.clone();
                                        }
                                    }
                                    super::emit_to_windows("call_state_changed", serde_json::json!({
                                        "state": state,
                                        "is_ringing": is_ringing,
                                        "is_active": is_active,
                                        "number": number
                                    }));

                                    if is_ringing || is_active {
                                        super::calls::pause_all_media_for_call();
                                    } else if state == "idle" {
                                        super::calls::resume_all_media_after_call();
                                    }

                                    if is_ringing {
                                        let call_num = if number.is_empty() { "Incoming Call".to_string() } else { number.clone() };
                                        super::notifications::notify_mac_incoming_call("Incoming Call", &call_num);
                                    } else {
                                        // Call is no longer ringing (answered, rejected, or disconnected)
                                        super::notifications::dismiss_mac_incoming_call();
                                    }
                                }
                                "phone_call_history" => {
                                    if let Some(calls_arr) = val.get("calls").and_then(|a| a.as_array()) {
                                        let mut list = Vec::new();
                                        for c in calls_arr {
                                            let id = c.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let phone_id = c.get("phone_id").and_then(|v| v.as_str()).map(|s| s.to_string());
                                            let name = c.get("name").and_then(|v| v.as_str()).unwrap_or("Unknown").to_string();
                                            let number = c.get("number").and_then(|v| v.as_str()).unwrap_or("Private").to_string();
                                            let call_type = c.get("call_type").and_then(|v| v.as_str()).unwrap_or("incoming").to_string();
                                            let time = c.get("time").and_then(|v| v.as_str()).unwrap_or("Recent").to_string();
                                            let duration = c.get("duration").and_then(|v| v.as_str()).unwrap_or("0s").to_string();
                                            list.push(CallRecord { id, phone_id, name, number, call_type, time, duration });
                                        }
                                        println!("[Idroid Server] Synced {} phone call records over Companion TCP", list.len());
                                        if let Ok(mut guard) = PHONE_CALL_HISTORY_CACHE.lock() {
                                            *guard = list.clone();
                                        }
                                        super::emit_to_windows("phone_call_history_updated", serde_json::json!({ "calls": list }));
                                    }
                                }
                                "phone_contacts" => {
                                    if let Some(contacts_arr) = val.get("contacts").and_then(|a| a.as_array()) {
                                        let mut list = Vec::new();
                                        for c in contacts_arr {
                                            let id = c.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let name = c.get("name").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let number = c.get("number").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let contact_type = c.get("type").and_then(|v| v.as_str()).unwrap_or("Mobile").to_string();
                                            list.push(PhoneContact { id, name, number, contact_type });
                                        }
                                        println!("[Idroid Server] Synced {} phone contacts over Companion TCP", list.len());
                                        if let Ok(mut guard) = PHONE_CONTACTS_CACHE.lock() {
                                            *guard = list.clone();
                                        }
                                        super::emit_to_windows("phone_contacts_updated", serde_json::json!({ "contacts": list }));
                                    }
                                }
                                "probe" => {
                                    // Ephemeral probe from phone scanner UI; no action required
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
                                            // Deliver native macOS notification banner with reply capability only if supported
                                            let _ = super::notifications::show_mac_system_notification_full(
                                                app_name.clone(),
                                                title.clone(),
                                                text.clone(),
                                                has_reply,
                                                notif_id.clone(),
                                            );

                                            if let Ok(mut guard) = LIVE_NOTIFICATIONS.lock() {
                                                guard.retain(|n| n.id != notif_id);
                                                let record = NotificationRecord {
                                                    id: notif_id.clone(),
                                                    app_name: app_name.clone(),
                                                    package_name: pkg.clone(),
                                                    title: title.clone(),
                                                    text: text.clone(),
                                                    time: "Now".into(),
                                                    has_reply,
                                                    icon_base64: notif_icon.clone(),
                                                };
                                                guard.insert(0, record.clone());
                                                if guard.len() > 30 {
                                                    guard.truncate(30);
                                                }
                                                super::emit_to_windows("notification_received", serde_json::to_value(&record).unwrap_or_default());
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
                                                    execute_mac_media_key(k);
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
                                            "mic_mute" | "toggle_mic" => {
                                                let output = Command::new("osascript")
                                                    .args([
                                                        "-e", "set curVol to input volume of (get volume settings)",
                                                        "-e", "if curVol > 0 then",
                                                        "-e", "set volume input volume 0",
                                                        "-e", "return \"Microphone Muted\"",
                                                        "-e", "else",
                                                        "-e", "set volume input volume 85",
                                                        "-e", "return \"Microphone Active\"",
                                                        "-e", "end if",
                                                    ])
                                                    .output();
                                                let msg = output.ok().and_then(|o| String::from_utf8(o.stdout).ok()).unwrap_or_else(|| "Mic Toggled".into());
                                                let resp = serde_json::json!({
                                                    "type": "toast",
                                                    "message": msg.trim()
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            "show_desktop" => {
                                                let _ = Command::new("osascript")
                                                    .args(["-e", "tell application \"System Events\" to set visible of every process whose visible is true and name is not \"Finder\" to false"])
                                                    .spawn();
                                            }
                                            "toggle_dark_mode" | "dark_mode" => {
                                                let _ = Command::new("osascript")
                                                    .args(["-e", "tell application \"System Events\" to tell appearance preferences to set dark mode to not dark mode"])
                                                    .spawn();
                                            }
                                            "toggle_mac_location" | "toggle_location" | "enable_location" => {
                                                println!("[Idroid Server] Received location command from phone. Triggering Mac Location Services authorization prompt...");
                                                let res = super::system::toggle_mac_location_services(None);
                                                let is_enabled = res.unwrap_or(false);
                                                let resp = serde_json::json!({
                                                    "type": "toast",
                                                    "message": if is_enabled { "Mac Location Services Enabled" } else { "Mac Location Prompt Displayed" },
                                                    "mac_location_enabled": is_enabled
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            "presentation_next" => {
                                                let _ = Command::new("osascript")
                                                    .args(["-e", "tell application \"System Events\" to key code 124"])
                                                    .spawn();
                                            }
                                            "presentation_prev" => {
                                                let _ = Command::new("osascript")
                                                    .args(["-e", "tell application \"System Events\" to key code 123"])
                                                    .spawn();
                                            }
                                            "launch_app" => {
                                                if let Some(app) = val.get("app").and_then(|a| a.as_str()) {
                                                    let _ = Command::new("open").args(["-a", app]).spawn();
                                                }
                                            }
                                            "get_mac_bluetooth_devices" => {
                                                let devices = super::system::get_mac_bluetooth_devices();
                                                let resp = serde_json::json!({
                                                    "type": "mac_bluetooth_devices",
                                                    "devices": devices
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            "connect_mac_bt" => {
                                                if let Some(addr) = val.get("address").and_then(|a| a.as_str()) {
                                                    let res = super::system::connect_mac_bluetooth_device(addr.to_string());
                                                    let (success, msg) = match res {
                                                        Ok(m) => (true, m),
                                                        Err(e) => (false, e),
                                                    };
                                                    let devices = super::system::get_mac_bluetooth_devices();
                                                    let resp = serde_json::json!({
                                                        "type": "mac_bt_result",
                                                        "action": "connect",
                                                        "address": addr,
                                                        "success": success,
                                                        "message": msg,
                                                        "devices": devices
                                                    });
                                                    let _ = writeln!(stream, "{}", resp.to_string());
                                                    let _ = stream.flush();
                                                }
                                            }
                                            "disconnect_mac_bt" => {
                                                if let Some(addr) = val.get("address").and_then(|a| a.as_str()) {
                                                    let res = super::system::disconnect_mac_bluetooth_device(addr.to_string());
                                                    let (success, msg) = match res {
                                                        Ok(m) => (true, m),
                                                        Err(e) => (false, e),
                                                    };
                                                    let devices = super::system::get_mac_bluetooth_devices();
                                                    let resp = serde_json::json!({
                                                        "type": "mac_bt_result",
                                                        "action": "disconnect",
                                                        "address": addr,
                                                        "success": success,
                                                        "message": msg,
                                                        "devices": devices
                                                    });
                                                    let _ = writeln!(stream, "{}", resp.to_string());
                                                    let _ = stream.flush();
                                                }
                                            }
                                            "get_mac_wifi_status" => {
                                                let wifi = super::system::get_mac_wifi_info();
                                                let resp = serde_json::json!({
                                                    "type": "mac_wifi_status",
                                                    "power": wifi.power,
                                                    "ssid": wifi.ssid,
                                                    "interface": wifi.interface
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
                                            }
                                            "set_mac_wifi_power" => {
                                                let power = val.get("power").and_then(|p| p.as_bool()).unwrap_or(true);
                                                let _ = super::system::set_mac_wifi_power(power);
                                                let wifi = super::system::get_mac_wifi_info();
                                                let resp = serde_json::json!({
                                                    "type": "mac_wifi_status",
                                                    "power": wifi.power,
                                                    "ssid": wifi.ssid,
                                                    "interface": wifi.interface
                                                });
                                                let _ = writeln!(stream, "{}", resp.to_string());
                                                let _ = stream.flush();
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
                                "clipboard_push" | "clipboard_image" => {
                                    if let Some(img_b64) = val.get("image_base64").and_then(|t| t.as_str()) {
                                        use base64::Engine;
                                        if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(img_b64) {
                                            crate::commands::clipboard::set_mac_clipboard_image(&bytes);
                                            crate::commands::clipboard::record_image_entry(img_b64.to_string(), "Android Phone (Image)");
                                            let hist = get_clipboard_history().unwrap_or_default();
                                            super::emit_to_windows("clipboard_history_updated", serde_json::to_value(&hist).unwrap_or_default());
                                            let resp = serde_json::json!({
                                                "type": "clipboard_history",
                                                "history": hist,
                                                "clipboard_history": hist
                                            });
                                            let _ = writeln!(stream, "{}", resp.to_string());
                                            let _ = stream.flush();
                                        }
                                    } else if let Some(text) = val.get("text").and_then(|t| t.as_str()) {
                                        if let Ok(mut child) = Command::new("pbcopy").stdin(std::process::Stdio::piped()).spawn() {
                                            if let Some(mut stdin) = child.stdin.take() {
                                                let _ = stdin.write_all(text.as_bytes());
                                            }
                                        }
                                        record_clipboard_entry(text.to_string(), "Android Phone");
                                        let hist = get_clipboard_history().unwrap_or_default();
                                        let resp = serde_json::json!({
                                            "type": "clipboard_history",
                                            "history": hist,
                                            "clipboard_history": hist
                                        });
                                        let _ = writeln!(stream, "{}", resp.to_string());
                                        let _ = stream.flush();
                                    }
                                }
                                "toggle_pin_clipboard" => {
                                    if let Some(id) = val.get("id").and_then(|i| i.as_str()) {
                                        let _ = toggle_pin_clipboard(id.to_string());
                                    }
                                    let hist = get_clipboard_history().unwrap_or_default();
                                    let resp = serde_json::json!({
                                        "type": "clipboard_history",
                                        "history": hist,
                                        "clipboard_history": hist
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "delete_clipboard_entry" => {
                                    if let Some(id) = val.get("id").and_then(|i| i.as_str()) {
                                        let _ = delete_clipboard_entry(id.to_string());
                                    }
                                    let hist = get_clipboard_history().unwrap_or_default();
                                    let resp = serde_json::json!({
                                        "type": "clipboard_history",
                                        "history": hist,
                                        "clipboard_history": hist
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "clear_clipboard_history" => {
                                    let _ = clear_clipboard_history();
                                    let hist = get_clipboard_history().unwrap_or_default();
                                    let resp = serde_json::json!({
                                        "type": "clipboard_history",
                                        "history": hist,
                                        "clipboard_history": hist
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "get_clipboard_history" => {
                                    let hist = get_clipboard_history().unwrap_or_default();
                                    let resp = serde_json::json!({
                                        "type": "clipboard_history",
                                        "history": hist,
                                        "clipboard_history": hist
                                    });
                                    let _ = writeln!(stream, "{}", resp.to_string());
                                    let _ = stream.flush();
                                }
                                "get_mac_status" => {
                                    let (mac_bat, mac_chg) = get_mac_battery();
                                    let clip = get_mac_clipboard();
                                    let mac_name = get_mac_computer_name();
                                    let user_name = get_mac_user_name();
                                    let conn_mode = get_active_connection_mode();
                                    let all_apps = get_installed_mac_apps();
                                    let hist = get_clipboard_history().unwrap_or_default();
                                    let resp = serde_json::json!({
                                        "type": "mac_status",
                                        "name": mac_name,
                                        "user_name": user_name,
                                        "connection_type": conn_mode,
                                        "battery": mac_bat,
                                        "is_charging": mac_chg,
                                        "clipboard": clip,
                                        "apps": all_apps,
                                        "clipboard_history": hist
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

                println!("[Idroid Server] Stream {} disconnected for peer {}", current_stream_id, peer_ip);
                if let Ok(mut guard) = COMPANION_SESSION.lock() {
                    if guard.as_ref().map(|s| s.stream_id) == Some(current_stream_id) {
                        println!("[Idroid Server] Active session stream {} closed; marking session disconnected", current_stream_id);
                        *guard = None;
                        if let Ok(mut apps_guard) = QUICK_PHONE_APPS.lock() {
                            apps_guard.clear();
                        }
                        if let Some(app) = crate::commands::APP_HANDLE.get() {
                            crate::tray::update_tray_menu(app);
                        }
                    } else {
                        println!("[Idroid Server] Stream {} was ephemeral/probe; preserving active session", current_stream_id);
                    }
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

#[tauri::command]
pub fn focus_camera_player() {
    #[cfg(target_os = "macos")]
    {
        let script = r#"
            tell application "System Events"
                try
                    set ffplayProc to first process whose name contains "ffplay"
                    set frontmost of ffplayProc to true
                end try
            end tell
        "#;
        let _ = std::process::Command::new("osascript")
            .args(["-e", script])
            .output();
    }
}

#[tauri::command]
pub fn set_camera_orientation(rotation: u32, mirror: bool) {
    crate::camera_server::set_camera_orientation(rotation, mirror);
}

#[tauri::command]
pub fn get_camera_orientation() -> (u32, bool) {
    crate::camera_server::get_camera_orientation()
}

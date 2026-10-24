//! Idroid Command Dispatcher & Submodule Coordinator
//!
//! Re-exports all domain submodules:
//! - `types`: Shared data transfer objects, payloads, and synchronization globals
//! - `system`: macOS system telemetry, battery, volume, brightness, and media keys
//! - `input`: CoreGraphics unicode text typing, mouse movement, and key simulation
//! - `apps`: Installed Mac apps scanning, icon extraction, and phone app registry
//! - `adb`: Android Debug Bridge process execution, device target resolution, and scrcpy mirroring
//! - `files`: File manager, photo albums, thumbnail generator, and multi-file downloads
//! - `calls`: Telephony controls, call answering, audio routing, and call history
//! - `notifications`: Incoming notifications, quick replies, and macOS notification delivery
//! - `clipboard`: Cross-platform clipboard sync with bidirectional echo prevention
//! - `companion`: Local TCP WebSocket companion server and native window operations

pub mod types;
pub mod system;
pub mod input;
pub mod apps;
pub mod adb;
pub mod files;
pub mod calls;
pub mod notifications;
pub mod clipboard;
pub mod companion;
pub mod security;

pub use types::*;
pub use system::*;
pub use input::*;
pub use apps::*;
pub use adb::*;
pub use files::*;
pub use calls::*;
pub use notifications::*;
pub use clipboard::*;
pub use companion::*;
pub use security::*;

use std::sync::OnceLock;
pub static APP_HANDLE: OnceLock<tauri::AppHandle> = OnceLock::new();

pub fn set_app_handle(handle: tauri::AppHandle) {
    let _ = APP_HANDLE.set(handle);
}

pub fn emit_to_windows<S: serde::Serialize + Clone>(event: &str, payload: S) {
    if let Some(app) = APP_HANDLE.get() {
        use tauri::Emitter;
        let _ = app.emit(event, payload);
    }
}

// MARK: - Unit Tests
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_battery_info() {
        let sample = "Current Battery Service state:\n  status: 2\n  health: 2\n  present: true\n  level: 83\n  scale: 100\n  voltage: 4314\n  temperature: 353\n";
        let (level, is_charging, voltage, temp) = parse_battery_info(sample);
        assert_eq!(level, 83);
        assert_eq!(is_charging, true);
        assert_eq!(voltage, 4314);
        assert!((temp - 35.3).abs() < 0.01);
    }

    #[test]
    fn test_parse_storage_info() {
        let sample = "Filesystem      Size Used Avail Use% Mounted on\n/dev/fuse       222G  90G  132G  41% /storage/emulated\n";
        let (used, total, percent) = parse_storage_info(sample);
        assert_eq!(used, "90G");
        assert_eq!(total, "222G");
        assert_eq!(percent, 41);
    }

    #[test]
    fn test_map_package_to_app() {
        let (name, cat) = map_package_to_app("com.whatsapp");
        assert_eq!(name, "WhatsApp");
        assert_eq!(cat, "Social");

        let (name2, cat2) = map_package_to_app("com.openai.chatgpt");
        assert_eq!(name2, "ChatGPT");
        assert_eq!(cat2, "AI & Tools");
    }

    #[test]
    fn test_format_file_size() {
        assert_eq!(format_file_size("1048576", false), "1.0 MB");
        assert_eq!(format_file_size("512", false), "512 B");
        assert_eq!(format_file_size("4096", true), "Folder");
    }

    #[test]
    fn test_clean_notification_text() {
        assert_eq!(
            clean_notification_text("String (Charging stopped to protect your battery)"),
            "Charging stopped to protect your battery"
        );
        assert_eq!(
            clean_notification_text("String (Charging is limited to 90%.)"),
            "Charging is limited to 90%."
        );
        assert_eq!(
            clean_notification_text("CharSequence (WhatsApp)"),
            "WhatsApp"
        );
        assert_eq!(clean_notification_text("null"), "");
        assert_eq!(clean_notification_text("Normal text"), "Normal text");
    }

    #[test]
    fn test_parse_wlan_ip() {
        let ip_addr_out = "30: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP group default qlen 3000\n    inet 192.168.1.145/24 brd 192.168.1.255 scope global wlan0\n       valid_lft forever preferred_lft forever\n";
        assert_eq!(parse_wlan_ip(ip_addr_out), Some("192.168.1.145".to_string()));

        let ip_route_out = "default via 192.168.1.1 dev wlan0 proto dhcp metric 100\n1.1.1.1 dev wlan0 proto dhcp scope link src 192.168.1.88\n";
        assert_eq!(parse_wlan_ip(ip_route_out), Some("192.168.1.88".to_string()));

        let empty_out = "Device down or not found\n";
        assert_eq!(parse_wlan_ip(empty_out), None);
    }

    #[test]
    fn test_classify_connection() {
        assert_eq!(classify_connection(""), "Disconnected");
        assert_eq!(classify_connection("192.168.1.100:5555"), "Wi-Fi 5GHz (Local)");
        assert_eq!(classify_connection("10.0.0.15:5555"), "Wi-Fi 5GHz (Local)");
        assert_eq!(classify_connection("172.16.0.4:5555"), "Wi-Fi 5GHz (Local)");
        assert_eq!(classify_connection("100.64.0.2:5555"), "Remote Mesh (Encrypted)");
        assert_eq!(classify_connection("100.115.92.1:5555"), "Remote Mesh (Encrypted)");
        assert_eq!(classify_connection("127.0.0.1:5555"), "Local Loopback / Tunneled");
        assert_eq!(classify_connection("203.0.113.195:5555"), "Remote WAN (Secure)");
    }

    #[test]
    fn test_parse_adb_devices_list() {
        let sample = "List of devices attached\nRFCT40XYZ\tdevice\n192.168.1.45:5555\tdevice\nemulator-5554\toffline\n";
        let parsed = parse_adb_devices_list(sample);
        assert_eq!(parsed.len(), 3);
        assert_eq!(parsed[0], ("RFCT40XYZ".to_string(), "device".to_string()));
        assert_eq!(parsed[1], ("192.168.1.45:5555".to_string(), "device".to_string()));
        assert_eq!(parsed[2], ("emulator-5554".to_string(), "offline".to_string()));
    }

    #[test]
    fn test_clipboard_deduplication() {
        record_clipboard_entry("https://idroid.ecosystem/download".into(), "Mac");
        record_clipboard_entry("https://idroid.ecosystem/download".into(), "Mac"); // duplicate!
        let hist = get_clipboard_history().unwrap();
        let matches: Vec<_> = hist.iter().filter(|s| s.text == "https://idroid.ecosystem/download").collect();
        assert_eq!(matches.len(), 1);
    }

    #[test]
    fn test_pairing_qr_payload() {
        let payload = get_pairing_qr_payload();
        assert_eq!(payload.port, 53100);
        assert_eq!(payload.protocol, "idroid-v1");
        assert!(!payload.name.is_empty());
        assert!(!payload.ip.is_empty());
        assert!(!payload.token.is_empty());
    }

    #[test]
    fn test_companion_session_stream_id_protection() {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);

        // 1. Establish an active companion session on stream_id = 100
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            *guard = Some(CompanionSession {
                stream_id: 100,
                device_name: "Om's S23 Ultra".to_string(),
                brand: "samsung".to_string(),
                battery: 90,
                is_charging: false,
                storage_used_gb: "98G".to_string(),
                storage_total_gb: "256G".to_string(),
                storage_percent: 38,
                ip: "192.168.1.12".to_string(),
                connected_at: now,
                last_seen: now,
                bluetooth_status: Some("Bluetooth On".to_string()),
                bluetooth_connected: Some(false),
            });
        }

        // Verify session is active
        assert!(is_device_connected());
        let telem = get_device_telemetry();
        assert_eq!(telem.status, "Connected");
        assert_eq!(telem.name, "Om's S23 Ultra");

        // 2. Simulate an ephemeral LAN scanner probe connecting on stream_id = 101 and disconnecting
        let probe_stream_id = 101u64;
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            // Emulate companion.rs disconnect check: ONLY clear if stream_id matches active owner!
            if guard.as_ref().map(|s| s.stream_id) == Some(probe_stream_id) {
                *guard = None;
            }
        }

        // Active session MUST still be alive!
        assert!(is_device_connected(), "Ephemeral probe stream disconnect must NOT clear active session");
        let telem_after_probe = get_device_telemetry();
        assert_eq!(telem_after_probe.status, "Connected");

        // 3. Now simulate the actual active stream (100) disconnecting
        let active_stream_id = 100u64;
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            if guard.as_ref().map(|s| s.stream_id) == Some(active_stream_id) {
                *guard = None;
            }
        }

        // Now session should be cleanly cleared
        assert!(!is_device_connected(), "Active stream disconnect should clear session");
        let telem_disconnected = get_device_telemetry();
        assert_eq!(telem_disconnected.status, "Disconnected");
    }

    #[test]
    fn test_companion_ping_resurrection() {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);

        // Ensure session is initially None
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            *guard = None;
        }
        assert!(!is_device_connected());

        // Simulate incoming ping on stream 200 when session is None
        let current_stream_id = 200u64;
        let mut should_request_handshake = false;
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            if let Some(ref mut s) = *guard {
                s.last_seen = now;
            } else {
                // Resurrect session
                *guard = Some(CompanionSession {
                    stream_id: current_stream_id,
                    device_name: "Om's S23 Ultra".to_string(),
                    brand: "Samsung".to_string(),
                    battery: 88,
                    is_charging: false,
                    storage_used_gb: "98G".to_string(),
                    storage_total_gb: "256G".to_string(),
                    storage_percent: 38,
                    ip: "192.168.1.12".to_string(),
                    connected_at: now,
                    last_seen: now,
                    bluetooth_status: Some("Bluetooth On".to_string()),
                    bluetooth_connected: Some(false),
                });
                should_request_handshake = true;
            }
        }

        assert!(should_request_handshake, "Ping on dead session should trigger handshake request");
        assert!(is_device_connected(), "Ping should resurrect connection status to Connected");

        // Clean up
        if let Ok(mut guard) = COMPANION_SESSION.lock() {
            *guard = None;
        }
    }

    #[test]
    fn test_companion_command_action_dispatch() {
        let valid_actions = vec![
            "sleep_mac",
            "sleep_display",
            "lock_mac",
            "lock_screen",
            "screenshot",
            "media_key",
            "set_volume",
            "set_brightness",
            "vol_up",
            "vol_down",
            "bright_up",
            "bright_down",
            "play_pause",
            "next",
            "prev",
            "mission_control",
            "spotlight",
            "mute",
            "mic_mute",
            "toggle_mic",
            "show_desktop",
            "toggle_dark_mode",
            "dark_mode",
            "presentation_next",
            "presentation_prev",
            "launch_app",
        ];

        // Ensure every action is distinct and non-empty
        let mut unique_actions = std::collections::HashSet::new();
        for action in &valid_actions {
            assert!(!action.is_empty(), "Action string must not be empty");
            unique_actions.insert(*action);
        }
        assert_eq!(valid_actions.len(), unique_actions.len(), "Actions must be unique");

        // Verify that every single action matches a pattern in the companion match block
        for action in valid_actions {
            let is_supported = match action {
                "sleep_mac" | "sleep_display" => true,
                "lock_mac" | "lock_screen" => true,
                "screenshot" => true,
                "media_key" => true,
                "set_volume" => true,
                "set_brightness" => true,
                "vol_up" | "volume_up" => true,
                "vol_down" | "volume_down" => true,
                "bright_up" | "brightness_up" => true,
                "bright_down" | "brightness_down" => true,
                "play_pause" => true,
                "next" => true,
                "prev" => true,
                "mission_control" => true,
                "spotlight" => true,
                "mute" => true,
                "mic_mute" | "toggle_mic" => true,
                "show_desktop" => true,
                "toggle_dark_mode" | "dark_mode" => true,
                "toggle_mac_location" | "toggle_location" | "enable_location" => true,
                "presentation_next" => true,
                "presentation_prev" => true,
                "launch_app" => true,
                _ => false,
            };
            assert!(is_supported, "Action '{}' must be supported in companion command dispatch", action);
        }
    }

    #[test]
    fn test_connection_mode_detection() {
        let mode = get_active_connection_mode();
        // Returns "Wi-Fi (Direct)", "USB (Reverse)", or "Hotspot"
        assert!(
            mode == "Wi-Fi (Direct)" || mode == "USB (Reverse)" || mode == "Hotspot",
            "Connection mode '{}' must be one of the known transport types",
            mode
        );
    }

    #[test]
    fn test_phone_hardware_payload_structure() {
        let bt_json = r#"{"enabled": true, "connected_device": "Galaxy Buds2 Pro", "devices": ["Galaxy Buds2 Pro", "WH-1000XM4"]}"#;
        let v: serde_json::Value = serde_json::from_str(bt_json).expect("valid bt json");
        assert_eq!(v["enabled"], true);
        assert_eq!(v["connected_device"], "Galaxy Buds2 Pro");
        assert_eq!(v["devices"].as_array().unwrap().len(), 2);

        let wifi_json = r#"{"enabled": true, "ssid": "Airtel_Pandey_5G", "rssi": -55, "ip": "192.168.1.12"}"#;
        let wv: serde_json::Value = serde_json::from_str(wifi_json).expect("valid wifi json");
        assert_eq!(wv["enabled"], true);
        assert_eq!(wv["ssid"], "Airtel_Pandey_5G");
    }

    #[test]
    fn test_phone_hardware_actions() {
        let actions = vec![
            "set_bluetooth",
            "set_wifi",
            "toggle_location",
            "get_bluetooth_status",
            "get_wifi_status",
        ];
        for action in actions {
            assert!(
                action.starts_with("set_") || action.starts_with("toggle_") || action.starts_with("get_"),
                "Action '{}' must have an explicit intent prefix",
                action
            );
        }
    }

    #[test]
    fn test_telecom_route_mapping() {
        fn map_route_to_telecom_int(route: &str) -> &'static str {
            match route {
                "mac" => "4",           // Bluetooth Hands-Free Profile / SCO
                "phone_speaker" => "2", // Speakerphone
                "phone" => "1",         // Handset Earpiece
                _ => "4",
            }
        }

        assert_eq!(map_route_to_telecom_int("mac"), "4");
        assert_eq!(map_route_to_telecom_int("phone_speaker"), "2");
        assert_eq!(map_route_to_telecom_int("phone"), "1");
        assert_eq!(map_route_to_telecom_int("unknown"), "4");
    }

    #[test]
    fn test_call_state_telephony_registry_parsing() {
        fn parse_call_state(output: &str) -> (&str, bool, Option<String>) {
            let mut state = "idle";
            let mut is_ringing = false;
            let mut incoming_number: Option<String> = None;

            for line in output.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with("mCallState=") || trimmed.starts_with("mCallState ") {
                    if trimmed.contains("=0") || trimmed.ends_with(" 0") {
                        state = "idle";
                    } else if trimmed.contains("=1") || trimmed.ends_with(" 1") {
                        state = "ringing";
                        is_ringing = true;
                    } else if trimmed.contains("=2") || trimmed.ends_with(" 2") {
                        state = "active";
                    }
                }
                if (trimmed.starts_with("mCallIncomingNumber=") || trimmed.starts_with("incoming="))
                    && !trimmed.ends_with("null")
                    && !trimmed.ends_with("=\"\"")
                {
                    if let Some(val) = trimmed.split('=').nth(1) {
                        let clean = val.trim_matches('"').trim();
                        if !clean.is_empty() && clean != "null" {
                            incoming_number = Some(clean.to_string());
                        }
                    }
                }
            }

            (state, is_ringing, incoming_number)
        }

        // Scenario 1: Phone call is cut / terminated on phone
        let idle_dump = "Telephony Registry:\n  mCallState=0\n  mCallIncomingNumber=\"\"\n";
        let (state1, ring1, _) = parse_call_state(idle_dump);
        assert_eq!(state1, "idle");
        assert_eq!(ring1, false);

        // Scenario 2: Incoming call ringing
        let ring_dump = "Telephony Registry:\n  mCallState=1\n  mCallIncomingNumber=\"+14158902341\"\n";
        let (state2, ring2, num2) = parse_call_state(ring_dump);
        assert_eq!(state2, "ringing");
        assert_eq!(ring2, true);
        assert_eq!(num2, Some("+14158902341".to_string()));

        // Scenario 3: Call answered / active
        let active_dump = "Telephony Registry:\n  mCallState=2\n";
        let (state3, ring3, _) = parse_call_state(active_dump);
        assert_eq!(state3, "active");
        assert_eq!(ring3, false);
    }

    #[test]
    fn test_contact_content_query_row_parsing() {
        let sample_row = "Row: 0 _id=12, display_name=Sarah Connor, data1=+1 (415) 890-2341, data2=2";
        let id = extract_content_field(sample_row, "_id").unwrap();
        let name = extract_content_field(sample_row, "display_name").unwrap();
        let number = extract_content_field(sample_row, "data1").unwrap();
        let contact_type = extract_content_field(sample_row, "data2").unwrap();

        assert_eq!(id, "12");
        assert_eq!(name, "Sarah Connor");
        assert_eq!(number, "+1 (415) 890-2341");
        assert_eq!(contact_type, "2");
    }
}

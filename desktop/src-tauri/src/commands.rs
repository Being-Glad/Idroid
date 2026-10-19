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
}

use super::types::{NotificationRecord, LIVE_NOTIFICATIONS};
use super::adb::{adb_command, is_device_connected};
use super::apps::{find_companion_icon_bytes, map_package_to_app};

unsafe extern "C" {
    pub fn post_mac_notification(
        title: *const std::ffi::c_char,
        subtitle: *const std::ffi::c_char,
        message: *const std::ffi::c_char,
    );
    pub fn post_mac_notification_full(
        title: *const std::ffi::c_char,
        subtitle: *const std::ffi::c_char,
        message: *const std::ffi::c_char,
        has_reply: std::ffi::c_int,
        notif_id: *const std::ffi::c_char,
    );
    pub fn post_mac_incoming_call_notification(
        caller_name: *const std::ffi::c_char,
        caller_number: *const std::ffi::c_char,
    );
    pub fn cancel_mac_call_notifications();
    pub fn get_mac_pasteboard_change_count() -> std::ffi::c_long;
}

#[unsafe(no_mangle)]
pub extern "C" fn on_mac_notification_reply(c_id: *const std::ffi::c_char, c_text: *const std::ffi::c_char) {
    if c_id.is_null() || c_text.is_null() {
        return;
    }
    let notif_id = unsafe { std::ffi::CStr::from_ptr(c_id) }.to_string_lossy().to_string();
    let reply_text = unsafe { std::ffi::CStr::from_ptr(c_text) }.to_string_lossy().to_string();
    if !notif_id.is_empty() && !reply_text.is_empty() {
        let _ = reply_notification(notif_id, reply_text);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn on_mac_call_notification_action(c_action: *const std::ffi::c_char, c_number: *const std::ffi::c_char) {
    if c_action.is_null() { return; }
    let action = unsafe { std::ffi::CStr::from_ptr(c_action) }.to_string_lossy().to_string();
    let number = if !c_number.is_null() {
        unsafe { std::ffi::CStr::from_ptr(c_number) }.to_string_lossy().to_string()
    } else {
        String::new()
    };
    println!("[Idroid Calls] Native macOS Call Notification Action clicked: {} (number: {})", action, number);
    match action.as_str() {
        "accept_mac" => {
            let _ = super::calls::answer_phone_call();
            let _ = super::calls::set_call_audio_route("mac".into());
        }
        "answer_phone" => {
            let _ = super::calls::answer_phone_call();
            let _ = super::calls::set_call_audio_route("phone".into());
        }
        "decline" => {
            let _ = super::calls::end_phone_call();
        }
        _ => {}
    }
}

pub fn notify_mac_incoming_call(name: &str, number: &str) {
    #[cfg(target_os = "macos")]
    {
        use std::ffi::CString;
        let c_name = CString::new(name).unwrap_or_default();
        let c_num = CString::new(number).unwrap_or_default();
        unsafe {
            post_mac_incoming_call_notification(c_name.as_ptr(), c_num.as_ptr());
        }
    }
}

pub fn dismiss_mac_incoming_call() {
    #[cfg(target_os = "macos")]
    unsafe {
        cancel_mac_call_notifications();
    }
}

pub fn clean_notification_text(raw: &str) -> String {
    let mut s = raw.trim();
    if s == "null" || s == "NULL" {
        return String::new();
    }
    if (s.starts_with("String (") || s.starts_with("CharSequence (")) && s.ends_with(')') {
        let prefix_len = if s.starts_with("String (") { "String (".len() } else { "CharSequence (".len() };
        s = &s[prefix_len..s.len() - 1];
    } else if (s.starts_with("String(") || s.starts_with("CharSequence(")) && s.ends_with(')') {
        let prefix_len = if s.starts_with("String(") { "String(".len() } else { "CharSequence(".len() };
        s = &s[prefix_len..s.len() - 1];
    }
    s = s.trim().trim_matches('"');
    s.to_string()
}

#[tauri::command]
pub fn get_notification_list() -> Result<Vec<NotificationRecord>, String> {
    if !is_device_connected() {
        return Ok(vec![]);
    }

    // 1. Check live notifications received over companion socket
    if let Ok(guard) = LIVE_NOTIFICATIONS.lock() {
        if !guard.is_empty() {
            return Ok(guard.clone());
        }
    }

    // 2. Fast filtered fallback via adb shell grep (under 1s, avoids 10s service dump timeouts)
    let output = adb_command(&[
        "shell",
        "dumpsys notification --noredact | grep -E 'NotificationRecord\\(|android\\.title=|android\\.text=|flags='",
    ])
    .output();

    let mut list = Vec::new();
    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        let mut curr_pkg = String::new();
        let mut curr_title = String::new();
        let mut curr_text = String::new();
        let mut is_ongoing = false;

        for line in stdout.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with("NotificationRecord(") {
                if !curr_pkg.is_empty()
                    && (!curr_title.is_empty() || !curr_text.is_empty())
                    && !is_ongoing
                {
                    let (app_name, _) = map_package_to_app(&curr_pkg);
                    let notif_icon = find_companion_icon_bytes(&curr_pkg).map(|bytes| {
                        use base64::Engine;
                        base64::engine::general_purpose::STANDARD.encode(&bytes)
                    });
                    list.push(NotificationRecord {
                        id: format!("notif-{}", list.len() + 1),
                        app_name,
                        package_name: curr_pkg.clone(),
                        title: if curr_title.is_empty() {
                            "Notification".into()
                        } else {
                            curr_title.clone()
                        },
                        text: curr_text.clone(),
                        time: "Now".into(),
                        has_reply: true,
                        icon_base64: notif_icon,
                    });
                }
                curr_title.clear();
                curr_text.clear();
                is_ongoing = false;
                if let Some(pkg_part) = trimmed.split("pkg=").nth(1) {
                    if let Some(pkg) = pkg_part.split_whitespace().next() {
                        curr_pkg = pkg.to_string();
                    }
                }
            } else if trimmed.starts_with("flags=") {
                if trimmed.contains("ONGOING_EVENT") || trimmed.contains("FOREGROUND_SERVICE") {
                    is_ongoing = true;
                }
            } else if trimmed.starts_with("android.title=") {
                let raw_title = trimmed.strip_prefix("android.title=").unwrap_or("");
                curr_title = clean_notification_text(raw_title);
            } else if trimmed.starts_with("android.text=") {
                let raw_text = trimmed.strip_prefix("android.text=").unwrap_or("");
                curr_text = clean_notification_text(raw_text);
            }

            if list.len() >= 20 {
                break;
            }
        }

        if !curr_pkg.is_empty()
            && (!curr_title.is_empty() || !curr_text.is_empty())
            && !is_ongoing
            && list.len() < 20
        {
            let (app_name, _) = map_package_to_app(&curr_pkg);
            let notif_icon = find_companion_icon_bytes(&curr_pkg).map(|bytes| {
                use base64::Engine;
                base64::engine::general_purpose::STANDARD.encode(&bytes)
            });
            list.push(NotificationRecord {
                id: format!("notif-{}", list.len() + 1),
                app_name,
                package_name: curr_pkg,
                title: curr_title,
                text: curr_text,
                time: "Now".into(),
                has_reply: true,
                icon_base64: notif_icon,
            });
        }
    }

    Ok(list)
}

#[tauri::command]
pub fn reply_notification(id: String, reply_text: String) -> Result<String, String> {
    let msg = serde_json::json!({
        "type": "command",
        "action": "reply_notification",
        "id": id,
        "text": reply_text
    });
    if super::companion::send_companion_message(&msg) {
        return Ok(format!("Reply dispatched via companion to {}: \"{}\"", id, reply_text));
    }
    let escaped = reply_text.replace(' ', "%s");
    let _ = adb_command(&["shell", "input", "text", &escaped]).output();
    Ok(format!("Reply sent to notification {}: \"{}\"", id, reply_text))
}

#[tauri::command]
pub fn send_sms(number: String, message: String) -> Result<String, String> {
    let clean_num: String = number.chars().filter(|c| c.is_ascii_digit() || *c == '+').collect();
    let _ = adb_command(&[
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.SENDTO",
        "-d",
        &format!("sms:{}", clean_num),
        "--es",
        "sms_body",
        &message,
    ])
    .output();
    Ok(format!("Message dispatched to {}", clean_num))
}

#[tauri::command]
pub fn show_mac_system_notification(title: String, subtitle: String, message: String) -> Result<(), String> {
    show_mac_system_notification_full(title, subtitle, message, false, String::new())
}

pub fn show_mac_system_notification_full(
    title: String,
    subtitle: String,
    message: String,
    has_reply: bool,
    notif_id: String,
) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    {
        use std::ffi::CString;
        let c_title = CString::new(title).unwrap_or_default();
        let c_sub = CString::new(subtitle).unwrap_or_default();
        let c_msg = CString::new(message).unwrap_or_default();
        let c_id = CString::new(notif_id).unwrap_or_default();
        unsafe {
            post_mac_notification_full(
                c_title.as_ptr(),
                c_sub.as_ptr(),
                c_msg.as_ptr(),
                if has_reply { 1 } else { 0 },
                c_id.as_ptr(),
            );
        }
        return Ok(());
    }
    #[allow(unreachable_code)]
    Ok(())
}

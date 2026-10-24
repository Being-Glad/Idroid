use std::process::Command;

unsafe extern "C" {
    pub fn mac_mouse_move(dx: f32, dy: f32);
    pub fn mac_mouse_click(is_right: std::ffi::c_int);
    pub fn mac_mouse_scroll(dy: std::ffi::c_int);
    pub fn mac_type_text(utf8_str: *const std::ffi::c_char);
    pub fn mac_press_key(key_name: *const std::ffi::c_char);
    pub fn mac_toggle_spotlight();
}

pub fn execute_mac_type_text(text: &str) {
    if text.is_empty() {
        return;
    }
    if text == "\n" {
        execute_mac_press_key("return");
        return;
    }
    if text == "l" || text == "L" {
        execute_mac_press_key("like");
        return;
    }
    // High-performance CoreGraphics unicode keyboard event injection
    if let Ok(c_str) = std::ffi::CString::new(text) {
        unsafe {
            mac_type_text(c_str.as_ptr());
        }
    } else {
        // Fallback for strings containing null bytes
        let escaped = text.replace('\\', "\\\\").replace('"', "\\\"");
        let script = format!("tell application \"System Events\" to keystroke \"{}\"", escaped);
        let _ = Command::new("osascript").args(["-e", &script]).spawn();
    }
}

pub fn execute_mac_press_key(key: &str) {
    let key_code = match key.to_lowercase().as_str() {
        "return" | "enter" => Some(36),
        "backspace" | "delete" => Some(51),
        "space" => Some(49),
        "escape" | "esc" => Some(53),
        "tab" => Some(48),
        "up" => Some(126),
        "down" => Some(125),
        "left" => Some(123),
        "right" => Some(124),
        "like" | "l" => Some(37), // 'L' - Like shortcut on Instagram Reels & media
        "k" => Some(40),
        "m" | "mute" => Some(46),
        _ => None,
    };

    // 1. Direct low-level C HID event injection
    if let Ok(c_str) = std::ffi::CString::new(key) {
        unsafe {
            mac_press_key(c_str.as_ptr());
        }
    }

    // 2. Secondary AppleScript fallback to guarantee active browser/window delivery
    if let Some(code) = key_code {
        let script = format!("tell application \"System Events\" to key code {}", code);
        let _ = Command::new("osascript").args(["-e", &script]).spawn();
    }
}

pub fn execute_mac_mouse_event(action: &str, dx: f32, dy: f32) {
    unsafe {
        match action {
            "move" => mac_mouse_move(dx, dy),
            "click" => mac_mouse_click(0),
            "right_click" => mac_mouse_click(1),
            "scroll" => mac_mouse_scroll(dy as i32),
            _ => {}
        }
    }
}

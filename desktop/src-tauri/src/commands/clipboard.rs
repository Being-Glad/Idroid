use std::process::Command;
use super::types::{ClipboardSnippet, CLIPBOARD_HISTORY};
use super::adb::adb_command;

pub fn get_mac_clipboard() -> String {
    Command::new("pbpaste")
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default()
}

pub fn set_mac_clipboard(text: &str) {
    use std::io::Write;
    if let Ok(mut child) = Command::new("pbcopy").stdin(std::process::Stdio::piped()).spawn() {
        if let Some(mut stdin) = child.stdin.take() {
            let _ = stdin.write_all(text.as_bytes());
        }
        let _ = child.wait();
    }
}

use std::fs;
use std::path::PathBuf;

fn get_history_file_path() -> PathBuf {
    let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".into());
    let mut path = PathBuf::from(home);
    path.push(".idroid");
    let _ = fs::create_dir_all(&path);
    path.push("clipboard_history.json");
    path
}

pub fn load_persisted_clipboard_history() {
    let path = get_history_file_path();
    if let Ok(data) = fs::read_to_string(&path) {
        if let Ok(mut snippets) = serde_json::from_str::<Vec<ClipboardSnippet>>(&data) {
            snippets.truncate(50);
            for s in &mut snippets {
                if let Some(ref img) = s.image_base64 {
                    if img.len() > 150_000 {
                        s.image_base64 = None;
                    }
                }
            }
            if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
                *guard = snippets;
                sort_clipboard_snippets(&mut guard);
            }
        }
    }
}

pub fn persist_clipboard_history(snippets: &[ClipboardSnippet]) {
    let path = get_history_file_path();
    if let Ok(json) = serde_json::to_string_pretty(snippets) {
        let _ = fs::write(path, json);
    }
}

unsafe extern "C" {
    fn get_mac_pasteboard_image_png(out_data: *mut *mut u8, out_len: *mut usize) -> std::os::raw::c_int;
    fn free_mac_buffer(ptr: *mut u8);
    fn set_mac_pasteboard_image_png(data: *const u8, len: usize);
}

pub fn get_mac_clipboard_image() -> Option<Vec<u8>> {
    unsafe {
        let mut out_data: *mut u8 = std::ptr::null_mut();
        let mut out_len: usize = 0;
        if get_mac_pasteboard_image_png(&mut out_data, &mut out_len) == 1 && !out_data.is_null() && out_len > 0 {
            let slice = std::slice::from_raw_parts(out_data, out_len);
            let vec = slice.to_vec();
            free_mac_buffer(out_data);
            return Some(vec);
        }
    }
    None
}

pub fn set_mac_clipboard_image(data: &[u8]) {
    unsafe {
        set_mac_pasteboard_image_png(data.as_ptr(), data.len());
    }
}

pub fn record_image_entry(image_base64: String, source: &str) {
    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        if let Some(first) = guard.first() {
            if first.image_base64.as_deref() == Some(&image_base64) {
                return;
            }
        }
        let id = format!(
            "clip-img-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis()
        );
        let snippet = ClipboardSnippet {
            id,
            text: "Screenshot / Image".to_string(),
            source: source.to_string(),
            time: "Just now".into(),
            pinned: false,
            image_base64: if image_base64.len() > 300_000 { None } else { Some(image_base64) },
        };
        let insert_idx = guard.iter().position(|s| !s.pinned).unwrap_or(guard.len());
        guard.insert(insert_idx, snippet);
        if guard.len() > 100 {
            guard.truncate(100);
        }
        persist_clipboard_history(&guard);
    }
    if let Some(app) = crate::commands::APP_HANDLE.get() {
        crate::tray::update_tray_menu(app);
    }
}

pub fn record_clipboard_entry(text: String, source: &str) {
    let clean = text.trim();
    if clean.is_empty() {
        return;
    }

    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        // Prevent infinite echo loop: do not duplicate if identical to newest entry
        if let Some(existing_pos) = guard.iter().position(|s| s.text == clean) {
            // If already exists, if it's not pinned, move it to recent position
            if !guard[existing_pos].pinned {
                let mut existing = guard.remove(existing_pos);
                existing.time = "Just now".into();
                let insert_idx = guard.iter().position(|s| !s.pinned).unwrap_or(guard.len());
                guard.insert(insert_idx, existing);
            }
            persist_clipboard_history(&guard);
            return;
        }

        let id = format!(
            "clip-{}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis()
        );

        let snippet = ClipboardSnippet {
            id,
            text: clean.to_string(),
            source: source.to_string(),
            time: "Just now".into(),
            pinned: false,
            image_base64: None,
        };

        // Insert after any pinned items so pinned items remain at the top
        let insert_idx = guard.iter().position(|s| !s.pinned).unwrap_or(guard.len());
        guard.insert(insert_idx, snippet);

        if guard.len() > 100 {
            guard.truncate(100);
        }
        persist_clipboard_history(&guard);
    }
    if let Some(app) = crate::commands::APP_HANDLE.get() {
        crate::tray::update_tray_menu(app);
    }
}

pub fn start_mac_clipboard_monitor() {
    load_persisted_clipboard_history();

    std::thread::spawn(|| {
        let mut last_count = unsafe { super::notifications::get_mac_pasteboard_change_count() };
        loop {
            std::thread::sleep(std::time::Duration::from_millis(350));
            let current_count = unsafe { super::notifications::get_mac_pasteboard_change_count() };
            if current_count != last_count {
                last_count = current_count;

                // 1. Check if pasteboard has an image (screenshot or copied image)
                if let Some(img_bytes) = get_mac_clipboard_image() {
                    use base64::Engine;
                    let b64 = base64::engine::general_purpose::STANDARD.encode(&img_bytes);
                    let is_dup = if let Ok(guard) = CLIPBOARD_HISTORY.lock() {
                        guard.first().and_then(|f| f.image_base64.as_ref()).map(|s| s == &b64).unwrap_or(false)
                    } else {
                        false
                    };

                    if !is_dup {
                        record_image_entry(b64.clone(), "Mac Local (Image)");
                        let hist = get_clipboard_history().unwrap_or_default();

                        // Emit to open UI windows
                        super::emit_to_windows("clipboard_history_updated", serde_json::to_value(&hist).unwrap_or_default());

                        // Push to phone in companion session
                        if let Ok(session) = crate::commands::types::COMPANION_SESSION.lock() {
                            if session.is_some() {
                                let payload = serde_json::json!({
                                    "type": "clipboard_update",
                                    "content": "[Image]",
                                    "image_base64": b64,
                                    "clipboard_history": hist
                                });
                                let _ = crate::commands::companion::send_companion_message(&payload);
                            }
                        }
                    }
                    continue;
                }

                // 2. Fall back to text clipboard
                let clip = get_mac_clipboard();
                let clean = clip.trim();
                if !clean.is_empty() {
                    let is_dup = if let Ok(guard) = CLIPBOARD_HISTORY.lock() {
                        guard.first().map(|f| f.text == clean).unwrap_or(false)
                    } else {
                        false
                    };
                    if !is_dup {
                        record_clipboard_entry(clean.to_string(), "Mac Local");
                        let hist = get_clipboard_history().unwrap_or_default();

                        // Emit to open UI windows
                        super::emit_to_windows("clipboard_history_updated", serde_json::to_value(&hist).unwrap_or_default());

                        // Push to phone if companion session active
                        if let Ok(session) = crate::commands::types::COMPANION_SESSION.lock() {
                            if session.is_some() {
                                let payload = serde_json::json!({
                                    "type": "clipboard_update",
                                    "content": clean,
                                    "clipboard_history": hist
                                });
                                let _ = crate::commands::companion::send_companion_message(&payload);
                            }
                        }
                    }
                }
            }
        }
    });
}

#[tauri::command]
pub fn paste_to_phone(text: String) -> Result<String, String> {
    let escaped = text.replace(" ", "%s");
    let _ = adb_command(&["shell", "input", "text", &escaped]).output();
    record_clipboard_entry(text, "Mac (Pushed to Phone)");
    Ok("Pasted to phone".into())
}

#[tauri::command]
pub fn get_clipboard_history() -> Result<Vec<ClipboardSnippet>, String> {
    if let Ok(guard) = CLIPBOARD_HISTORY.lock() {
        let mut list = guard.clone();
        sort_clipboard_snippets(&mut list);
        Ok(list)
    } else {
        Ok(vec![])
    }
}

#[tauri::command]
pub fn toggle_pin_clipboard(id: String) -> Result<Vec<ClipboardSnippet>, String> {
    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        if let Some(item) = guard.iter_mut().find(|s| s.id == id) {
            item.pinned = !item.pinned;
        }
        sort_clipboard_snippets(&mut guard);
        persist_clipboard_history(&guard);
        Ok(guard.clone())
    } else {
        Err("Failed to lock clipboard history".into())
    }
}

#[tauri::command]
pub fn delete_clipboard_entry(id: String) -> Result<Vec<ClipboardSnippet>, String> {
    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        guard.retain(|s| s.id != id);
        sort_clipboard_snippets(&mut guard);
        persist_clipboard_history(&guard);
        Ok(guard.clone())
    } else {
        Err("Failed to lock clipboard history".into())
    }
}

#[tauri::command]
pub fn clear_clipboard_history() -> Result<Vec<ClipboardSnippet>, String> {
    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        // Retain pinned clips, clear unpinned
        guard.retain(|s| s.pinned);
        persist_clipboard_history(&guard);
        Ok(guard.clone())
    } else {
        Err("Failed to lock clipboard history".into())
    }
}

#[tauri::command]
pub fn sync_clipboard_content(text: String, source: String) -> Result<Vec<ClipboardSnippet>, String> {
    record_clipboard_entry(text, &source);
    get_clipboard_history()
}

#[tauri::command]
pub fn copy_image_to_clipboard(image_base64: String) -> Result<(), String> {
    use base64::Engine;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(&image_base64)
        .map_err(|e| e.to_string())?;
    set_mac_clipboard_image(&bytes);
    Ok(())
}

fn sort_clipboard_snippets(snippets: &mut Vec<ClipboardSnippet>) {
    snippets.sort_by(|a, b| match (b.pinned, a.pinned) {
        (true, false) => std::cmp::Ordering::Greater,
        (false, true) => std::cmp::Ordering::Less,
        _ => std::cmp::Ordering::Equal,
    });
}

use std::process::Command;
use super::types::{ClipboardSnippet, CLIPBOARD_HISTORY};
use super::adb::adb_command;

pub fn get_mac_clipboard() -> String {
    Command::new("pbpaste")
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default()
}

pub fn record_clipboard_entry(text: String, source: &str) {
    let clean = text.trim();
    if clean.is_empty() {
        return;
    }

    if let Ok(mut guard) = CLIPBOARD_HISTORY.lock() {
        // Prevent infinite echo loop: do not duplicate if identical to newest entry
        if let Some(last) = guard.first() {
            if last.text == clean {
                return;
            }
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
        };

        guard.insert(0, snippet);
        if guard.len() > 50 {
            guard.truncate(50);
        }
    }
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
        Ok(guard.clone())
    } else {
        Ok(vec![])
    }
}

#[tauri::command]
pub fn sync_clipboard_content(text: String, source: String) -> Result<Vec<ClipboardSnippet>, String> {
    record_clipboard_entry(text, &source);
    get_clipboard_history()
}

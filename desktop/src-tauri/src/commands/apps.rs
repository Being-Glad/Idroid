use std::process::Command;
use std::sync::{LazyLock, Mutex};
use super::types::{AppInfo, MacAppEntry, PhoneAppIconItem, QUICK_PHONE_APPS};
use super::adb::{adb_command, is_device_connected};

static MAC_APPS_CACHE: LazyLock<Mutex<Vec<MacAppEntry>>> = LazyLock::new(|| Mutex::new(Vec::new()));

static MAC_APP_ICON_BYTES: &[u8] = include_bytes!("../../bin/mac-app-icon");
static MAC_MEDIA_KEY_BYTES: &[u8] = include_bytes!("../../bin/mac-media-key");
static MAC_BT_HELPER_BYTES: &[u8] = include_bytes!("../../bin/mac-bt-helper");
static IDROID_PROXY_SCRIPT: &str = include_str!("../../bin/idroid_proxy.py");

pub fn invalidate_mac_apps_cache() {
    if let Ok(mut guard) = MAC_APPS_CACHE.lock() {
        guard.clear();
    }
}

pub fn ensure_helper_binaries() {
    use std::fs;
    use std::os::unix::fs::PermissionsExt;

    let icon_bin = "/tmp/mac-app-icon";
    if !std::path::Path::new(icon_bin).exists() {
        if fs::write(icon_bin, MAC_APP_ICON_BYTES).is_ok() {
            let _ = fs::set_permissions(icon_bin, fs::Permissions::from_mode(0o755));
        }
    }

    let media_bin = "/tmp/mac-media-key";
    if !std::path::Path::new(media_bin).exists() {
        if fs::write(media_bin, MAC_MEDIA_KEY_BYTES).is_ok() {
            let _ = fs::set_permissions(media_bin, fs::Permissions::from_mode(0o755));
        }
    }

    let bt_bin = "/tmp/mac-bt-helper";
    if !std::path::Path::new(bt_bin).exists() {
        if fs::write(bt_bin, MAC_BT_HELPER_BYTES).is_ok() {
            let _ = fs::set_permissions(bt_bin, fs::Permissions::from_mode(0o755));
        }
    }

    let proxy_script = "/tmp/idroid_proxy.py";
    if !std::path::Path::new(proxy_script).exists() {
        if fs::write(proxy_script, IDROID_PROXY_SCRIPT).is_ok() {
            let _ = fs::set_permissions(proxy_script, fs::Permissions::from_mode(0o755));
        }
    }
}

pub fn get_installed_mac_apps() -> Vec<MacAppEntry> {
    if let Ok(guard) = MAC_APPS_CACHE.lock() {
        if !guard.is_empty() {
            return guard.clone();
        }
    }

    let home = std::env::var("HOME").unwrap_or_default();
    let user_apps = format!("{}/Applications", home);
    let dirs = [
        "/Applications",
        "/System/Applications",
        "/System/Applications/Utilities",
        user_apps.as_str(),
    ];
    let mut apps = Vec::new();

    for d in &dirs {
        if let Ok(entries) = std::fs::read_dir(d) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|e| e.to_str()) == Some("app") {
                    if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                        let name = stem.to_string();
                        if name.starts_with('.') || name.contains("Helper") || name.contains("Crash") {
                            continue;
                        }
                        let mut bundle_id = format!("com.apple.{}", name.replace(' ', ""));
                        let plist_path = path.join("Contents/Info.plist");
                        if plist_path.exists() {
                            if let Ok(contents) = std::fs::read_to_string(&plist_path) {
                                if let Some(idx) = contents.find("<key>CFBundleIdentifier</key>") {
                                    if let Some(str_start) = contents[idx..].find("<string>") {
                                        if let Some(str_end) = contents[idx + str_start..].find("</string>") {
                                            bundle_id = contents[idx + str_start + 8..idx + str_start + str_end].to_string();
                                        }
                                    }
                                }
                            }
                        }
                        let category = if d.contains("Utilities") {
                            "Utilities".to_string()
                        } else if d.contains("System") {
                            "System".to_string()
                        } else {
                            "Applications".to_string()
                        };

                        apps.push(MacAppEntry {
                            name,
                            bundle_id,
                            path: path.to_string_lossy().to_string(),
                            category,
                            icon: String::new(),
                        });
                    }
                }
            }
        }
    }
    apps.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));

    ensure_helper_binaries();
    let icon_helper = "/tmp/mac-app-icon";

    if !icon_helper.is_empty() {
        let paths: Vec<String> = apps.iter().map(|a| a.path.clone()).collect();
        if let Ok(output) = Command::new(icon_helper).args(&paths).output() {
            if let Ok(icons_map) = serde_json::from_slice::<std::collections::HashMap<String, String>>(&output.stdout) {
                for app in &mut apps {
                    if let Some(uri) = icons_map.get(&app.path) {
                        app.icon = uri.clone();
                    }
                }
            }
        }
    }

    if let Ok(mut guard) = MAC_APPS_CACHE.lock() {
        *guard = apps.clone();
    }
    apps
}

#[tauri::command]
pub fn get_quick_phone_apps() -> Vec<PhoneAppIconItem> {
    if let Ok(guard) = QUICK_PHONE_APPS.lock() {
        if !guard.is_empty() {
            return guard.clone();
        }
    }

    let home = std::env::var("HOME").unwrap_or_default();
    let candidates = [
        "public/apps.json".to_string(),
        "../public/apps.json".to_string(),
        format!("{}/Library/Containers/com.linkmymac.mac/Data/Library/Application Support/com.linkmymac.mac/AndroidAppLists/ODkxNDhkNzUzMWNjNDNmMg.json", home),
    ];

    for path in &candidates {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(apps_arr) = val.get("apps").and_then(|a| a.as_array()) {
                    let mut list = Vec::new();
                    for a in apps_arr.iter().take(24) {
                        let name = a.get("name").and_then(|n| n.as_str()).unwrap_or("App").to_string();
                        let package = a.get("packageName").and_then(|p| p.as_str()).unwrap_or("").to_string();
                        let icon = a.get("iconData").and_then(|i| i.as_str()).unwrap_or("").to_string();
                        if !package.is_empty() && !icon.is_empty() {
                            list.push(PhoneAppIconItem { name, package, icon });
                        }
                    }
                    if !list.is_empty() {
                        if let Ok(mut guard) = QUICK_PHONE_APPS.lock() {
                            *guard = list.clone();
                        }
                        return list;
                    }
                }
            }
        }
    }

    Vec::new()
}

pub fn find_companion_icon_bytes(target_pkg: &str) -> Option<Vec<u8>> {
    if let Ok(guard) = QUICK_PHONE_APPS.lock() {
        for app in guard.iter() {
            if app.package == target_pkg {
                let clean_b64 = app.icon.trim_start_matches("data:image/png;base64,")
                    .trim_start_matches("data:image/jpeg;base64,")
                    .trim_start_matches("data:image/webp;base64,");
                use base64::Engine;
                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(clean_b64) {
                    return Some(bytes);
                }
            }
        }
    }

    let home = std::env::var("HOME").unwrap_or_default();
    let candidates = [
        "public/apps.json".to_string(),
        "../public/apps.json".to_string(),
        format!("{}/Library/Containers/com.linkmymac.mac/Data/Library/Application Support/com.linkmymac.mac/AndroidAppLists/ODkxNDhkNzUzMWNjNDNmMg.json", home),
    ];

    for path in &candidates {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(apps_arr) = val.get("apps").and_then(|a| a.as_array()) {
                    for a in apps_arr {
                        let pkg = a.get("packageName").and_then(|p| p.as_str()).unwrap_or("");
                        if pkg == target_pkg {
                            if let Some(icon_str) = a.get("iconData").and_then(|i| i.as_str()) {
                                use base64::Engine;
                                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(icon_str) {
                                    return Some(bytes);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    None
}

#[tauri::command]
pub fn list_phone_apps() -> Result<Vec<AppInfo>, String> {
    if !is_device_connected() {
        return Ok(vec![]);
    }

    if let Ok(guard) = QUICK_PHONE_APPS.lock() {
        if !guard.is_empty() {
            let mut list = Vec::new();
            for a in guard.iter() {
                let clean_icon = if let Some(idx) = a.icon.find(";base64,") {
                    a.icon[idx + 8..].to_string()
                } else if a.icon.starts_with("data:image/") {
                    a.icon.split(',').nth(1).unwrap_or(&a.icon).to_string()
                } else {
                    a.icon.clone()
                };
                list.push(AppInfo {
                    name: a.name.clone(),
                    package: a.package.clone(),
                    icon_base64: if clean_icon.is_empty() { None } else { Some(clean_icon) },
                });
            }
            list.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
            return Ok(list);
        }
    }

    let home = std::env::var("HOME").unwrap_or_default();
    let candidates = [
        "public/apps.json".to_string(),
        "../public/apps.json".to_string(),
        format!("{}/Library/Containers/com.linkmymac.mac/Data/Library/Application Support/com.linkmymac.mac/AndroidAppLists/ODkxNDhkNzUzMWNjNDNmMg.json", home),
    ];

    for path in &candidates {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(apps_arr) = val.get("apps").and_then(|a| a.as_array()) {
                    let mut list = Vec::new();
                    for a in apps_arr {
                        let name = a.get("name").and_then(|n| n.as_str()).unwrap_or("App").to_string();
                        let pkg = a.get("packageName").and_then(|p| p.as_str()).unwrap_or("").to_string();
                        let raw_icon = a.get("iconData").and_then(|i| i.as_str()).map(|s| s.to_string());
                        let clean_icon = raw_icon.map(|ic| {
                            if let Some(idx) = ic.find(";base64,") {
                                ic[idx + 8..].to_string()
                            } else {
                                ic
                            }
                        });
                        if !pkg.is_empty() {
                            list.push(AppInfo {
                                name,
                                package: pkg,
                                icon_base64: clean_icon,
                            });
                        }
                    }
                    list.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
                    return Ok(list);
                }
            }
        }
    }

    let output = adb_command(&["shell", "pm", "list", "packages", "--user", "0", "-3"])
        .output()
        .map_err(|e| e.to_string())?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut apps = Vec::new();

    for line in stdout.lines() {
        if let Some(pkg) = line.trim().strip_prefix("package:") {
            let (name, _) = map_package_to_app(pkg);
            apps.push(AppInfo {
                name,
                package: pkg.to_string(),
                icon_base64: None,
            });
        }
    }

    apps.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(apps)
}

pub fn map_package_to_app(pkg: &str) -> (String, String) {
    if let Ok(guard) = QUICK_PHONE_APPS.lock() {
        for app in guard.iter() {
            if app.package == pkg && !app.name.is_empty() {
                return (app.name.clone(), "Installed".into());
            }
        }
    }

    match pkg {
        "android" => ("Android System".into(), "System".into()),
        "com.android.systemui" => ("System UI".into(), "System".into()),
        "com.google.android.googlequicksearchbox" => ("Google".into(), "Tools".into()),
        "com.google.android.gm" => ("Gmail".into(), "Communication".into()),
        "com.google.android.youtube" => ("YouTube".into(), "Media".into()),
        "com.spotify.music" => ("Spotify".into(), "Media".into()),
        "com.slack" => ("Slack".into(), "Communication".into()),
        "com.whatsapp" => ("WhatsApp".into(), "Social".into()),
        "com.instagram.android" => ("Instagram".into(), "Social".into()),
        "org.telegram.messenger" => ("Telegram".into(), "Social".into()),
        "com.reddit.frontpage" => ("Reddit".into(), "Social".into()),
        "com.linkedin.android" => ("LinkedIn".into(), "Professional".into()),
        "com.openai.chatgpt" => ("ChatGPT".into(), "AI & Tools".into()),
        "com.anthropic.claude" => ("Claude".into(), "AI & Tools".into()),
        "com.android.chrome" => ("Chrome".into(), "Browser".into()),
        "com.sec.android.app.camera" => ("Camera".into(), "Media".into()),
        "com.google.android.apps.photos" => ("Google Photos".into(), "Media".into()),
        "com.samsung.android.messaging" => ("Messages".into(), "Communication".into()),
        "com.samsung.android.calendar" => ("Calendar".into(), "Productivity".into()),
        "com.google.android.calendar" => ("Google Calendar".into(), "Productivity".into()),
        "com.google.android.apps.maps" => ("Google Maps".into(), "Navigation".into()),
        _ => {
            let last_part = pkg.split('.').last().unwrap_or(pkg);
            let mut c = last_part.chars();
            let capitalized = match c.next() {
                None => String::new(),
                Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
            };
            (capitalized, "General".into())
        }
    }
}

#[tauri::command]
pub fn launch_phone_app(package: String) -> Result<String, String> {
    log::info!("Launching app {} on phone", package);
    let _ = adb_command(&["shell", "monkey", "-p", &package, "-c", "android.intent.category.LAUNCHER", "1"])
        .output();
    Ok(format!("Dispatched launcher intent for {}", package))
}

use std::process::Command;
use super::types::{FileItem, PhotoItem};
use super::adb::{adb_command, is_device_connected};

#[tauri::command]
pub fn list_phone_files(remote_dir: Option<String>) -> Result<Vec<FileItem>, String> {
    if !is_device_connected() {
        return Ok(vec![]);
    }
    let dir = remote_dir.unwrap_or_else(|| "/sdcard/Download".into());
    let clean_dir = dir.trim_end_matches('/');
    let target_dir = if clean_dir.is_empty() { "/sdcard" } else { clean_dir };

    let output = adb_command(&["shell", "ls", "-la", target_dir])
        .output()
        .map_err(|e| e.to_string())?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let files = parse_ls_output(&stdout, target_dir);
    Ok(files)
}

pub fn parse_ls_output(output: &str, parent_dir: &str) -> Vec<FileItem> {
    let mut files = Vec::new();
    for line in output.lines() {
        let parts: Vec<&str> = line.split_whitespace().collect();
        if parts.len() >= 8 {
            let perms = parts[0];
            let is_dir = perms.starts_with('d');
            let size = parts[4].to_string();
            let date = format!("{} {}", parts[5], parts[6]);
            let name = parts[7..].join(" ");
            if name != "." && name != ".." && !name.starts_with('.') {
                let full_path = format!("{}/{}", parent_dir.trim_end_matches('/'), name);
                files.push(FileItem {
                    name,
                    path: full_path,
                    size: format_file_size(&size, is_dir),
                    is_dir,
                    modified: date,
                });
            }
        }
    }
    files.sort_by(|a, b| {
        b.is_dir.cmp(&a.is_dir).then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    files
}

pub fn format_file_size(size_str: &str, is_dir: bool) -> String {
    if is_dir {
        return "Folder".into();
    }
    let bytes = size_str.parse::<u64>().unwrap_or(0);
    if bytes < 1024 {
        format!("{} B", bytes)
    } else if bytes < 1024 * 1024 {
        format!("{:.1} KB", bytes as f64 / 1024.0)
    } else if bytes < 1024 * 1024 * 1024 {
        format!("{:.1} MB", bytes as f64 / (1024.0 * 1024.0))
    } else {
        format!("{:.1} GB", bytes as f64 / (1024.0 * 1024.0 * 1024.0))
    }
}

#[tauri::command]
pub fn list_phone_photos(album: Option<String>) -> Result<Vec<PhotoItem>, String> {
    if !is_device_connected() {
        return Ok(vec![]);
    }
    let album_id = album.unwrap_or_else(|| "all".into()).to_lowercase();
    let dirs: Vec<(&str, &str)> = match album_id.as_str() {
        "camera" => vec![("/sdcard/DCIM/Camera", "Camera")],
        "screenshots" => vec![
            ("/sdcard/DCIM/Screenshots", "Screenshots"),
            ("/sdcard/Pictures/Screenshots", "Screenshots"),
        ],
        "whatsapp" => vec![
            ("/sdcard/Pictures/WhatsApp", "WhatsApp"),
            ("/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images", "WhatsApp"),
        ],
        "instagram" => vec![("/sdcard/Pictures/Instagram", "Instagram")],
        "downloads" => vec![("/sdcard/Download", "Downloads")],
        _ => vec![
            ("/sdcard/DCIM/Camera", "Camera"),
            ("/sdcard/DCIM/Screenshots", "Screenshots"),
            ("/sdcard/Pictures/WhatsApp", "WhatsApp"),
            ("/sdcard/Pictures/Instagram", "Instagram"),
            ("/sdcard/Pictures", "Pictures"),
            ("/sdcard/Download", "Downloads"),
        ],
    };

    let mut photos = Vec::new();
    for (dir, album_name) in dirs {
        if let Ok(output) = adb_command(&["shell", "ls", "-la", dir]).output() {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 8 {
                    let size = parts[4].to_string();
                    let date = format!("{} {}", parts[5], parts[6]);
                    let name = parts[7..].join(" ");
                    let lower = name.to_lowercase();
                    if (lower.ends_with(".jpg")
                        || lower.ends_with(".jpeg")
                        || lower.ends_with(".png")
                        || lower.ends_with(".webp")
                        || lower.ends_with(".heic"))
                        && !name.starts_with('.')
                    {
                        photos.push(PhotoItem {
                            name: name.clone(),
                            path: format!("{}/{}", dir.trim_end_matches('/'), name),
                            size: format_file_size(&size, false),
                            modified: date,
                            album: album_name.to_string(),
                            thumbnail_base64: None,
                        });
                    }
                }
            }
        }
    }
    photos.sort_by(|a, b| b.modified.cmp(&a.modified));
    Ok(photos)
}

#[tauri::command]
pub fn choose_mac_folder(default_dir: Option<String>) -> Result<Option<String>, String> {
    let default_path = default_dir.unwrap_or_else(|| {
        let home = std::env::var("HOME").unwrap_or_else(|_| ".".into());
        format!("{}/Downloads", home)
    });

    let script = format!(
        r#"try
            tell application "System Events"
                activate
            end tell
            set targetFolder to choose folder with prompt "Select folder on your Mac to save files:" default location POSIX file "{}"
            return POSIX path of targetFolder
        on error number -128
            return "CANCELED"
        end try"#,
        default_path
    );

    let output = Command::new("osascript")
        .args(["-e", &script])
        .output()
        .map_err(|e| e.to_string())?;

    let result = String::from_utf8_lossy(&output.stdout).trim().to_string();
    if result.is_empty() || result == "CANCELED" {
        Ok(None)
    } else {
        Ok(Some(result))
    }
}

#[tauri::command]
pub fn download_files_to_mac(remote_paths: Vec<String>, local_dir: Option<String>) -> Result<String, String> {
    if remote_paths.is_empty() {
        return Err("No files selected to download".into());
    }

    let home = std::env::var("HOME").unwrap_or_else(|_| ".".into());
    let dest_dir = local_dir.unwrap_or_else(|| format!("{}/Downloads", home));
    let _ = std::fs::create_dir_all(&dest_dir);

    let mut success_count = 0;
    let mut last_err = String::new();

    for remote_path in &remote_paths {
        let output = adb_command(&["pull", remote_path, &dest_dir]).output();
        match output {
            Ok(out) if out.status.success() => {
                success_count += 1;
            }
            Ok(out) => {
                last_err = String::from_utf8_lossy(&out.stderr).to_string();
            }
            Err(e) => {
                last_err = e.to_string();
            }
        }
    }

    if success_count > 0 {
        Ok(format!("Saved {} file(s) to {}", success_count, dest_dir))
    } else {
        Err(format!("Download error: {}", last_err))
    }
}

#[tauri::command]
pub fn download_file_to_mac(remote_path: String) -> Result<String, String> {
    download_files_to_mac(vec![remote_path], None)
}

#[tauri::command]
pub fn get_photo_thumbnail(remote_path: String) -> Result<String, String> {
    let lower = remote_path.to_lowercase();
    if lower.ends_with(".mp4")
        || lower.ends_with(".mov")
        || lower.ends_with(".mkv")
        || lower.ends_with(".3gp")
        || lower.ends_with(".avi")
        || lower.ends_with(".webm")
    {
        return Err("Video files not supported for adb thumbnail generation".into());
    }

    let filename = std::path::Path::new(&remote_path)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("thumb.jpg");

    let cache_dir = std::env::temp_dir().join("idroid_thumb_cache");
    let _ = std::fs::create_dir_all(&cache_dir);
    let local_orig = cache_dir.join(filename);
    let local_thumb = cache_dir.join(format!("{}.thumb.jpg", filename));

    if local_thumb.exists() {
        if let Ok(bytes) = std::fs::read(&local_thumb) {
            use base64::Engine;
            return Ok(base64::engine::general_purpose::STANDARD.encode(&bytes));
        }
    }

    let _ = adb_command(&["pull", &remote_path, local_orig.to_str().unwrap_or_default()]).output();

    if local_orig.exists() {
        let _ = Command::new("sips")
            .args(&[
                "-Z",
                "200",
                local_orig.to_str().unwrap_or_default(),
                "--out",
                local_thumb.to_str().unwrap_or_default(),
            ])
            .output();

        let _ = std::fs::remove_file(&local_orig);

        if local_thumb.exists() {
            if let Ok(bytes) = std::fs::read(&local_thumb) {
                use base64::Engine;
                return Ok(base64::engine::general_purpose::STANDARD.encode(&bytes));
            }
        }
    }

    Err("Could not generate thumbnail".into())
}

#[tauri::command]
pub fn open_received_files() -> Result<String, String> {
    let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".into());
    let drop_dir = format!("{}/Downloads/IdroidDrop", home);
    let _ = std::fs::create_dir_all(&drop_dir);
    let _ = Command::new("open").arg(&drop_dir).spawn();
    Ok(drop_dir)
}

#[tauri::command]
pub fn upload_file_to_phone(local_path: String) -> Result<String, String> {
    let output = adb_command(&["push", &local_path, "/sdcard/Download/"])
        .output()
        .map_err(|e| e.to_string())?;

    if output.status.success() {
        Ok("File sent to phone Downloads".into())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).to_string())
    }
}

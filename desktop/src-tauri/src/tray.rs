use tauri::{
    menu::{IconMenuItemBuilder, Menu, MenuBuilder, MenuItemBuilder, SubmenuBuilder},
    tray::TrayIconBuilder,
    AppHandle, Manager,
};
use crate::commands::clipboard::{get_clipboard_history, set_mac_clipboard, clear_clipboard_history};
use crate::commands::companion::send_companion_message;
use crate::commands::types::COMPANION_SESSION;

pub const TRAY_ID: &str = "idroid_tray";

pub fn build_tray_menu<R: tauri::Runtime>(app: &AppHandle<R>) -> Result<Menu<R>, Box<dyn std::error::Error>> {
    let mut menu_builder = MenuBuilder::new(app);

    // 1. Connection & Device Status
    let (is_connected, device_label, connection_mode) = if let Ok(guard) = COMPANION_SESSION.lock() {
        if let Some(ref s) = *guard {
            let mode = if s.ip == "127.0.0.1" {
                "USB"
            } else if s.ip.starts_with("192.168.43.") || s.ip.starts_with("172.20.10.") {
                "Hotspot"
            } else {
                "Wi-Fi"
            };
            (true, format!("{} • Connected", s.device_name), mode.to_string())
        } else {
            (false, "Idroid • Disconnected".to_string(), String::new())
        }
    } else {
        (false, "Idroid • Disconnected".to_string(), String::new())
    };

    let status_title = if is_connected {
        format!("📱 {} ({})", device_label, connection_mode)
    } else {
        device_label
    };

    let device_status_item = MenuItemBuilder::with_id("device_status", &status_title)
        .enabled(false)
        .build(app)?;
    menu_builder = menu_builder.item(&device_status_item);

    // If disconnected, offer actionable Pair Phone button
    if !is_connected {
        let pair_item = MenuItemBuilder::with_id("pair_phone", "Pair Phone (Scan QR Code)...").build(app)?;
        menu_builder = menu_builder.item(&pair_item);
    }

    menu_builder = menu_builder.separator();

    // 2. Universal Clipboard Submenu
    let history_items = get_clipboard_history().unwrap_or_default();
    let mut clip_submenu_builder = SubmenuBuilder::new(app, "Universal Clipboard");

    if history_items.is_empty() {
        let empty_item = MenuItemBuilder::with_id("clip_empty", "No Clipboard History")
            .enabled(false)
            .build(app)?;
        clip_submenu_builder = clip_submenu_builder.item(&empty_item);
    } else {
        for (i, clip) in history_items.iter().take(6).enumerate() {
            let item_id = format!("copy_clip_{}", i);
            let pin_marker = if clip.pinned { "★ " } else { "" };
            if let Some(ref img_b64) = clip.image_base64 {
                use base64::Engine;
                let title = format!("{}🖼️ Copied Image", pin_marker);
                let mut added = false;
                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(img_b64) {
                    if let Ok(img) = tauri::image::Image::from_bytes(&bytes) {
                        if let Ok(item) = IconMenuItemBuilder::with_id(&item_id, &title).icon(img).build(app) {
                            clip_submenu_builder = clip_submenu_builder.item(&item);
                            added = true;
                        }
                    }
                }
                if !added {
                    if let Ok(item) = MenuItemBuilder::with_id(&item_id, &title).build(app) {
                        clip_submenu_builder = clip_submenu_builder.item(&item);
                    }
                }
            } else {
                let single_line = clip.text.lines().next().unwrap_or(&clip.text).trim();
                let preview = if single_line.len() > 36 {
                    format!("{}...", &single_line[..36])
                } else {
                    single_line.to_string()
                };
                let title = format!("{}{}", pin_marker, preview);
                if let Ok(item) = MenuItemBuilder::with_id(&item_id, &title).build(app) {
                    clip_submenu_builder = clip_submenu_builder.item(&item);
                }
            }
        }
        clip_submenu_builder = clip_submenu_builder.separator();
        if let Ok(clear_item) = MenuItemBuilder::with_id("clear_clip_hist", "Clear Clipboard History").build(app) {
            clip_submenu_builder = clip_submenu_builder.item(&clear_item);
        }
    }

    let clip_submenu = clip_submenu_builder.build()?;
    menu_builder = menu_builder.item(&clip_submenu);

    // 3. Quick Message Reply (Only show when there is an active replyable notification)
    let latest_replyable = if let Ok(guard) = crate::commands::types::LIVE_NOTIFICATIONS.lock() {
        guard.iter().find(|n| n.has_reply).map(|n| (n.id.clone(), n.app_name.clone(), n.title.clone(), n.text.clone()))
    } else {
        None
    };

    if let Some((_, ref app_name, ref sender, _)) = latest_replyable {
        let reply_label = format!("Reply to {} ({})", sender, app_name);
        let reply_item = MenuItemBuilder::with_id("quick_reply_message", &reply_label).build(app)?;
        menu_builder = menu_builder.item(&reply_item);
    }

    // 4. Remote Phone Actions (Enabled only when connected)
    let send_files_item = MenuItemBuilder::with_id("send_files_to_phone", "Send File to Phone...")
        .enabled(is_connected)
        .build(app)?;
    let mirror_screen_item = MenuItemBuilder::with_id("mirror_phone_screen", "Mirror Screen")
        .enabled(is_connected)
        .build(app)?;
    let ring_phone_item = MenuItemBuilder::with_id("ring_phone", "Ring Phone")
        .enabled(is_connected)
        .build(app)?;

    menu_builder = menu_builder
        .item(&send_files_item)
        .separator()
        .item(&mirror_screen_item)
        .item(&ring_phone_item)
        .separator();

    // 5. App Window Management - Single Smart Toggle
    let is_window_visible = app.get_webview_window("main").and_then(|w| w.is_visible().ok()).unwrap_or(false);
    let toggle_window_label = if is_window_visible {
        "Hide Dashboard"
    } else {
        "Open Dashboard"
    };
    let toggle_window_item = MenuItemBuilder::with_id("toggle_window", toggle_window_label).build(app)?;
    menu_builder = menu_builder.item(&toggle_window_item);

    // 6. Native Quit Item (Clean text, standard shortcut)
    menu_builder = menu_builder.separator();
    let quit_item = MenuItemBuilder::with_id("quit_app", "Quit Idroid")
        .accelerator("CmdOrCtrl+Q")
        .build(app)?;
    menu_builder = menu_builder.item(&quit_item);

    menu_builder.build().map_err(|e| e.into())
}

pub fn update_tray_menu<R: tauri::Runtime>(app: &AppHandle<R>) {
    let app_handle = app.clone();
    let _ = app.run_on_main_thread(move || {
        if let Some(tray) = app_handle.tray_by_id(TRAY_ID) {
            if let Ok(new_menu) = build_tray_menu(&app_handle) {
                let _ = tray.set_menu(Some(new_menu));
            }
        }
    });
}

pub fn setup_tray<R: tauri::Runtime>(app: &AppHandle<R>) -> Result<(), Box<dyn std::error::Error>> {
    let initial_menu = build_tray_menu(app)?;
    let tray_icon = tauri::image::Image::from_bytes(include_bytes!("../icons/tray@2x.png"))
        .ok()
        .or_else(|| app.default_window_icon().cloned());

    let mut tray_builder = TrayIconBuilder::with_id(TRAY_ID)
        .menu(&initial_menu)
        .show_menu_on_left_click(true)
        .tooltip("Idroid Ecosystem Hub");

    if let Some(ic) = tray_icon {
        tray_builder = tray_builder.icon(ic).icon_as_template(true);
    }

    let _tray = tray_builder
        .on_menu_event(move |app_handle, event| {
            let id = event.id.as_ref();
            if id.starts_with("copy_clip_") {
                if let Ok(idx) = id.replace("copy_clip_", "").parse::<usize>() {
                    if let Ok(history) = get_clipboard_history() {
                        if let Some(clip) = history.get(idx) {
                            if let Some(ref img_b64) = clip.image_base64 {
                                use base64::Engine;
                                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(img_b64) {
                                    crate::commands::clipboard::set_mac_clipboard_image(&bytes);
                                    let payload = serde_json::json!({
                                        "type": "clipboard_update",
                                        "content": "[Image]",
                                        "image_base64": img_b64,
                                    });
                                    let _ = send_companion_message(&payload);
                                }
                            } else {
                                set_mac_clipboard(&clip.text);
                                // Auto-sync to phone if connected
                                let payload = serde_json::json!({
                                    "type": "clipboard_update",
                                    "content": clip.text,
                                    "clipboard": clip.text
                                });
                                let _ = send_companion_message(&payload);
                            }
                        }
                    }
                }
                return;
            }

            match id {
                "clear_clip_hist" => {
                    let _ = clear_clipboard_history();
                    update_tray_menu(app_handle);
                }
                "pair_phone" => {
                    if let Some(win) = app_handle.get_webview_window("main") {
                        let _ = win.show();
                        let _ = win.unminimize();
                        let _ = win.set_focus();
                    }
                    crate::commands::emit_to_windows("switch_section", serde_json::json!("dashboard"));
                    update_tray_menu(app_handle);
                }
                "quick_reply_message" => {
                    if let Some(win) = app_handle.get_webview_window("main") {
                        let _ = win.show();
                        let _ = win.unminimize();
                        let _ = win.set_focus();
                    }
                    crate::commands::emit_to_windows("switch_section", serde_json::json!("notifications"));
                }
                "send_files_to_phone" => {
                    std::thread::spawn(|| {
                        let script = "POSIX path of (choose file with prompt \"Select a file to send to Phone:\")";
                        if let Ok(output) = std::process::Command::new("osascript").args(["-e", script]).output() {
                            let path_str = String::from_utf8_lossy(&output.stdout).trim().to_string();
                            if !path_str.is_empty() {
                                let _ = crate::commands::files::upload_file_to_phone(path_str);
                            }
                        }
                    });
                }
                "mirror_phone_screen" => {
                    if let Some(win) = app_handle.get_webview_window("main") {
                        let _ = win.show();
                        let _ = win.unminimize();
                        let _ = win.set_focus();
                    }
                    crate::commands::emit_to_windows("switch_section", serde_json::json!("mirror"));
                    update_tray_menu(app_handle);
                }
                "ring_phone" => {
                    let _ = send_companion_message(&serde_json::json!({
                        "type": "ring_device"
                    }));
                }
                "toggle_window" => {
                    if let Some(win) = app_handle.get_webview_window("main") {
                        if win.is_visible().unwrap_or(false) {
                            let _ = win.hide();
                        } else {
                            let _ = win.show();
                            let _ = win.unminimize();
                            let _ = win.set_focus();
                        }
                    }
                    update_tray_menu(app_handle);
                }
                "quit_app" => {
                    app_handle.exit(0);
                }
                _ => {}
            }
        })
        .build(app)?;

    Ok(())
}

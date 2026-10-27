pub mod commands;
pub mod camera_server;
pub mod tray;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
  tauri::Builder::default()
    .setup(|app| {
      if cfg!(debug_assertions) {
        app.handle().plugin(
          tauri_plugin_log::Builder::default()
            .level(log::LevelFilter::Info)
            .build(),
        )?;
      }
      commands::set_app_handle(app.handle().clone());
      commands::start_companion_server();
      commands::files::start_file_drop_server();
      camera_server::start_camera_stream_server();
      commands::clipboard::start_mac_clipboard_monitor();

      // Ensure macOS Dock displays the official Idroid app icon dynamically
      unsafe extern "C" {
        fn set_mac_dock_icon_from_png(path: *const std::os::raw::c_char);
      }
      let icon_bytes = include_bytes!("../icons/icon.png");
      let _ = std::fs::write("/tmp/idroid_dock_icon.png", icon_bytes);
      if let Ok(c_str) = std::ffi::CString::new("/tmp/idroid_dock_icon.png") {
        unsafe { set_mac_dock_icon_from_png(c_str.as_ptr()); }
      }

      if let Err(e) = tray::setup_tray(&app.handle()) {
        eprintln!("[Idroid Tray] Failed to initialize native tray: {:?}", e);
      }
      use tauri::Manager;
      if let Some(win) = app.get_webview_window("main") {
        let _ = win.show();
        let _ = win.set_focus();
        println!("[Idroid Window] Main window successfully initialized and presented!");
      } else {
        eprintln!("[Idroid Window] Warning: main webview window was not found in setup!");
      }
      use tauri::menu::{MenuBuilder, SubmenuBuilder, PredefinedMenuItem, AboutMetadata};
      let handle = app.handle();
      
      let app_submenu = SubmenuBuilder::new(handle, "Idroid")
        .item(&PredefinedMenuItem::about(handle, Some("About Idroid"), Some(AboutMetadata {
          name: Some("Idroid".into()),
          version: Some("1.0.0".into()),
          copyright: Some("© 2026 Idroid Ecosystem".into()),
          ..Default::default()
        }))?)
        .separator()
        .item(&PredefinedMenuItem::services(handle, Some("Services"))?)
        .separator()
        .item(&PredefinedMenuItem::hide(handle, Some("Hide Idroid"))?)
        .item(&PredefinedMenuItem::hide_others(handle, Some("Hide Others"))?)
        .item(&PredefinedMenuItem::show_all(handle, Some("Show All"))?)
        .separator()
        .item(&PredefinedMenuItem::quit(handle, Some("Quit Idroid"))?)
        .build()?;

      let edit_submenu = SubmenuBuilder::new(handle, "Edit")
        .item(&PredefinedMenuItem::undo(handle, Some("Undo"))?)
        .item(&PredefinedMenuItem::redo(handle, Some("Redo"))?)
        .separator()
        .item(&PredefinedMenuItem::cut(handle, Some("Cut"))?)
        .item(&PredefinedMenuItem::copy(handle, Some("Copy"))?)
        .item(&PredefinedMenuItem::paste(handle, Some("Paste"))?)
        .item(&PredefinedMenuItem::select_all(handle, Some("Select All"))?)
        .build()?;

      let view_submenu = SubmenuBuilder::new(handle, "View")
        .item(&PredefinedMenuItem::fullscreen(handle, Some("Toggle Full Screen"))?)
        .build()?;

      let window_submenu = SubmenuBuilder::new(handle, "Window")
        .item(&PredefinedMenuItem::minimize(handle, Some("Minimize"))?)
        .item(&PredefinedMenuItem::maximize(handle, Some("Zoom"))?)
        .separator()
        .item(&PredefinedMenuItem::close_window(handle, Some("Close Window"))?)
        .build()?;

      let menu = MenuBuilder::new(handle)
        .items(&[&app_submenu, &edit_submenu, &view_submenu, &window_submenu])
        .build()?;

      app.set_menu(menu)?;
      Ok(())
    })
    .invoke_handler(tauri::generate_handler![
      commands::get_device_telemetry,
      commands::toggle_dnd,
      commands::list_phone_apps,
      commands::launch_phone_app,
      commands::start_screen_mirror,
      commands::send_phone_key,
      commands::list_phone_files,
      commands::list_phone_photos,
      commands::download_file_to_mac,
      commands::download_files_to_mac,
      commands::choose_mac_folder,
      commands::get_photo_thumbnail,
      commands::show_mac_system_notification,
      commands::reconnect_adb,
      commands::make_phone_call,
      commands::end_phone_call,
      commands::answer_phone_call,
      commands::set_call_audio_route,
      commands::check_incoming_call,
      commands::get_call_state,
      commands::sync_call_overlay,
      commands::resize_call_overlay,
      commands::toggle_call_mute,
      commands::record_call_audio,
      commands::get_call_history,
      commands::delete_call_record,
      commands::clear_call_history,
      commands::get_phone_contacts,
      commands::add_phone_contact,
      commands::delete_phone_contact,
      commands::edit_phone_contact,
      commands::get_notification_list,
      commands::reply_notification,
      commands::send_sms,
      commands::paste_to_phone,
      commands::get_clipboard_history,
      commands::toggle_pin_clipboard,
      commands::delete_clipboard_entry,
      commands::clear_clipboard_history,
      commands::sync_clipboard_content,
      commands::copy_image_to_clipboard,
      commands::switch_to_wireless_tcpip,
      commands::connect_wireless_ip,
      commands::pair_wireless_device,
      commands::disconnect_wireless_device,
      commands::get_connection_transport_info,
      commands::get_pairing_qr_payload,
      commands::install_companion_apk,
      commands::window_toggle_maximize,
      commands::window_minimize,
      commands::window_close,
      commands::get_quick_phone_apps,
      commands::get_host_telemetry,
      commands::get_mac_bluetooth_status,
      commands::get_mac_bluetooth_devices,
      commands::connect_mac_bluetooth_device,
      commands::disconnect_mac_bluetooth_device,
      commands::get_mac_wifi_info,
      commands::set_mac_wifi_power,
      commands::open_mac_bluetooth_settings,
      commands::get_device_wallpapers,
      commands::open_received_files,
      commands::upload_file_to_phone,
      commands::debug_log,
      commands::get_mirror_window_bounds,
      commands::sync_mirror_dock,
      commands::sync_mirror_frame,
      commands::get_camera_stream_stats,
      commands::focus_camera_player,
      commands::set_camera_orientation,
      commands::get_camera_orientation,
      commands::get_phone_bluetooth_state,
      commands::toggle_phone_bluetooth,
      commands::get_phone_wifi_state,
      commands::toggle_phone_wifi,
      commands::get_phone_location_state,
      commands::toggle_phone_location,
      commands::get_mac_location_services_state,
      commands::toggle_mac_location_services,
      commands::get_phone_hotspot_state,
      commands::get_trusted_devices,
      commands::revoke_trusted_device,
      commands::approve_device_authorization,
      commands::reject_device_authorization,
      commands::get_security_status,
    ])
    .on_window_event(|_window, event| {
      if let tauri::WindowEvent::CloseRequested { .. } = event {
        commands::cleanup_on_exit();
        std::process::exit(0);
      }
    })
    .build(tauri::generate_context!())
    .expect("error while building tauri application")
    .run(|app_handle, event| {
      #[cfg(target_os = "macos")]
      if let tauri::RunEvent::Reopen { .. } = event {
        use tauri::Manager;
        if let Some(window) = app_handle.get_webview_window("main") {
          let _ = window.show();
          let _ = window.set_focus();
        }
      }
    });
}


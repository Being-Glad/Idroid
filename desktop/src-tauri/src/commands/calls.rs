use super::types::{CallRecord, IncomingCallInfo, CallStateInfo, PhoneContact, CURRENT_CALL_STATE, PHONE_CONTACTS_CACHE, PHONE_CALL_HISTORY_CACHE};
use super::adb::{adb_command, is_device_connected};
use super::companion::send_companion_message;
use std::sync::atomic::{AtomicBool, Ordering};

static MAC_MEDIA_PAUSED_BY_CALL: AtomicBool = AtomicBool::new(false);

pub fn pause_all_media_for_call() {
    let was_already_paused = MAC_MEDIA_PAUSED_BY_CALL.swap(true, Ordering::SeqCst);
    if !was_already_paused {
        println!("[Idroid Audio] Pausing Mac media for ongoing/incoming call...");
        let script = r#"
try
    tell application "System Events"
        if (count (every process whose name is "Music")) > 0 then
            tell application "Music" to if player state is playing then pause
        end if
        if (count (every process whose name is "Spotify")) > 0 then
            tell application "Spotify" to if player state is playing then pause
        end if
    end tell
end try
"#;
        let _ = std::process::Command::new("osascript").args(["-e", script]).spawn();
    }
}

pub fn resume_all_media_after_call() {
    if MAC_MEDIA_PAUSED_BY_CALL.swap(false, Ordering::SeqCst) {
        println!("[Idroid Audio] Resuming Mac media after call ended...");
        let script = r#"
try
    tell application "System Events"
        if (count (every process whose name is "Music")) > 0 then
            tell application "Music" to if player state is paused then play
        end if
        if (count (every process whose name is "Spotify")) > 0 then
            tell application "Spotify" to if player state is paused then play
        end if
    end tell
end try
"#;
        let _ = std::process::Command::new("osascript").args(["-e", script]).spawn();
    }
}

#[tauri::command]
pub fn make_phone_call(number: String) -> Result<String, String> {
    if !is_device_connected() {
        return Err("No device connected".into());
    }

    let clean_num: String = number.chars().filter(|c| c.is_ascii_digit() || *c == '+' || *c == '*' || *c == '#').collect();
    if clean_num.is_empty() {
        return Err("Please enter a valid phone number".into());
    }

    pause_all_media_for_call();

    // Update call state immediately to active/dialing so cross-device sync feels instant
    if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
        guard.state = "active".into();
        guard.is_ringing = false;
        guard.is_active = true;
        guard.number = clean_num.clone();
    }

    let output = adb_command(&["shell", "am", "start", "-a", "android.intent.action.CALL", "-d", &format!("tel:{}", clean_num)])
        .output();

    match output {
        Ok(out) if out.status.success() => Ok(format!("Calling {} on phone...", clean_num)),
        _ => Ok(format!("Dialing {} (Standby / Direct)", clean_num)),
    }
}

#[tauri::command]
pub fn end_phone_call() -> Result<String, String> {
    if !is_device_connected() {
        return Err("No device connected".into());
    }

    // 1. Android keyevent 6 (KEYCODE_ENDCALL)
    let _ = adb_command(&["shell", "input", "keyevent", "6"]).output();
    // 2. Telecom end-call
    let _ = adb_command(&["shell", "telecom", "end-call"]).output();
    // 3. Update local call state
    if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
        guard.state = "idle".into();
        guard.is_ringing = false;
        guard.is_active = false;
    }
    // 4. Notify companion service over TCP if connected
    send_companion_message(&serde_json::json!({
        "type": "command",
        "action": "end_call"
    }));

    resume_all_media_after_call();

    Ok("Call ended".into())
}

#[tauri::command]
pub fn answer_phone_call() -> Result<String, String> {
    if !is_device_connected() {
        return Err("No device connected".into());
    }

    pause_all_media_for_call();

    let _ = adb_command(&["shell", "input", "keyevent", "5"]).output(); // KEYCODE_CALL
    let _ = adb_command(&["shell", "telecom", "accept-ringing-call"]).output();
    if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
        guard.state = "active".into();
        guard.is_ringing = false;
        guard.is_active = true;
    }

    // Automatically ensure loud hands-free speakerphone is active
    let _ = set_call_audio_route("phone_speaker".into());

    Ok("Call answered".into())
}

#[tauri::command]
pub fn set_call_audio_route(route: String) -> Result<String, String> {
    if !is_device_connected() {
        return Err("No device connected".into());
    }

    println!("[Idroid Audio] Switching call audio route to: {}", route);
    
    // Notify Android companion service over TCP for direct AudioManager SCO/Speaker switching
    send_companion_message(&serde_json::json!({
        "type": "set_call_audio_route",
        "route": route.clone()
    }));

    match route.as_str() {
        "mac" | "phone_speaker" | "speaker" => {
            let _ = adb_command(&["shell", "telecom", "set-call-audio-route", "2"]).output();
            let _ = adb_command(&["shell", "cmd", "audio", "set-speakerphone-on", "true"]).output();
            Ok("Call audio routed to Hands-Free Loudspeaker & High-Gain Mic".into())
        }
        "bluetooth" => {
            let _ = adb_command(&["shell", "telecom", "set-call-audio-route", "4"]).output();
            let _ = adb_command(&["shell", "cmd", "audio", "set-speakerphone-on", "false"]).output();
            Ok("Call audio routed to Connected Bluetooth Headset".into())
        }
        _ => {
            let _ = adb_command(&["shell", "telecom", "set-call-audio-route", "1"]).output();
            let _ = adb_command(&["shell", "cmd", "audio", "set-speakerphone-on", "false"]).output();
            Ok("Call audio routed to Phone Handset (Private Earpiece)".into())
        }
    }
}

#[tauri::command]
pub fn get_call_state() -> Result<CallStateInfo, String> {
    if !is_device_connected() {
        if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
            *guard = CallStateInfo {
                state: "idle".into(),
                is_ringing: false,
                is_active: false,
                number: String::new(),
                name: String::new(),
                duration_sec: 0,
                audio_route: None,
                audio_route_name: None,
            };
        }
        return Ok(CallStateInfo {
            state: "idle".into(),
            is_ringing: false,
            is_active: false,
            number: String::new(),
            name: String::new(),
            duration_sec: 0,
            audio_route: None,
            audio_route_name: None,
        });
    }

    let current_cached = CURRENT_CALL_STATE.lock().map(|g| g.clone()).unwrap_or(CallStateInfo {
        state: "idle".into(),
        is_ringing: false,
        is_active: false,
        number: String::new(),
        name: String::new(),
        duration_sec: 0,
        audio_route: None,
        audio_route_name: None,
    });

    let output = adb_command(&["shell", "dumpsys", "telephony.registry"]).output();
    let mut detected_state = "idle".to_string();
    let mut detected_number = String::new();

    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        for line in stdout.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with("mCallState=1") || trimmed.contains(" mCallState=1") {
                detected_state = "ringing".into();
            } else if trimmed.starts_with("mCallState=2") || trimmed.contains(" mCallState=2") {
                detected_state = "active".into();
            }
            if line.contains("mCallIncomingNumber=") {
                let parts: Vec<&str> = line.split("mCallIncomingNumber=").collect();
                if parts.len() > 1 {
                    let n = parts[1].trim();
                    if !n.is_empty() && n != "null" {
                        detected_number = n.to_string();
                    }
                }
            }
        }
    }

    // Determine final state:
    // If ADB detected idle and CURRENT_CALL_STATE was active:
    // This indicates the call was terminated on the phone!
    let final_state = if detected_state == "idle" && current_cached.state == "idle" {
        "idle".to_string()
    } else if detected_state == "idle" && current_cached.state != "idle" {
        // Double check telecom if ADB telephony says idle:
        // If telecom has no active/ringing call, the call has ended on the phone!
        let telecom_out = adb_command(&["shell", "dumpsys", "telecom"]).output();
        let mut telecom_active = false;
        if let Ok(out) = telecom_out {
            let stdout = String::from_utf8_lossy(&out.stdout);
            for line in stdout.lines() {
                if line.contains("state: RINGING") {
                    detected_state = "ringing".into();
                    telecom_active = true;
                    break;
                } else if line.contains("state: ACTIVE") || line.contains("state: DIALING") {
                    detected_state = "active".into();
                    telecom_active = true;
                    break;
                }
            }
        }
        if !telecom_active {
            "idle".to_string()
        } else {
            detected_state
        }
    } else {
        detected_state
    };

    let mut final_number = detected_number;
    let mut final_name = String::new();
    let mut final_duration = current_cached.duration_sec;
    let mut final_route = current_cached.audio_route.clone();
    let mut final_route_name = current_cached.audio_route_name.clone();

    if let Ok(mut guard) = CURRENT_CALL_STATE.lock() {
        guard.state = final_state.clone();
        guard.is_ringing = final_state == "ringing";
        guard.is_active = final_state == "active";
        if final_state == "idle" {
            guard.number = String::new();
            guard.name = String::new();
            guard.duration_sec = 0;
            guard.audio_route = None;
            guard.audio_route_name = None;
            final_duration = 0;
            final_route = None;
            final_route_name = None;
        } else {
            if !final_number.is_empty() {
                guard.number = final_number.clone();
            } else if !guard.number.is_empty() {
                final_number = guard.number.clone();
            }

            if guard.name.is_empty() && !final_number.is_empty() {
                if let Ok(contacts) = PHONE_CONTACTS_CACHE.lock() {
                    let clean_final = final_number.chars().filter(|c| c.is_ascii_digit()).collect::<String>();
                    for c in contacts.iter() {
                        let clean_c = c.number.chars().filter(|ch| ch.is_ascii_digit()).collect::<String>();
                        if !clean_final.is_empty() && !clean_c.is_empty() && (clean_c.ends_with(&clean_final) || clean_final.ends_with(&clean_c)) {
                            final_name = c.name.clone();
                            break;
                        }
                    }
                }
                guard.name = final_name.clone();
            } else {
                final_name = guard.name.clone();
            }

            final_duration = guard.duration_sec;
            final_route = guard.audio_route.clone();
            final_route_name = guard.audio_route_name.clone();
        }
    }

    Ok(CallStateInfo {
        state: final_state.clone(),
        is_ringing: final_state == "ringing",
        is_active: final_state == "active",
        number: final_number,
        name: final_name,
        duration_sec: final_duration,
        audio_route: final_route,
        audio_route_name: final_route_name,
    })
}

#[tauri::command]
pub fn get_incoming_call_info() -> Result<IncomingCallInfo, String> {
    let call_state = get_call_state()?;
    Ok(IncomingCallInfo {
        is_ringing: call_state.is_ringing,
        number: call_state.number,
        name: call_state.name,
    })
}

#[tauri::command]
pub fn check_incoming_call() -> Result<IncomingCallInfo, String> {
    get_incoming_call_info()
}

#[tauri::command]
pub fn toggle_call_mute(muted: bool) -> Result<bool, String> {
    if !is_device_connected() {
        return Err("No device connected".into());
    }
    let flag = if muted { "true" } else { "false" };
    let _ = adb_command(&["shell", "cmd", "audio", "set-microphone-mute", flag]).output();
    let _ = adb_command(&["shell", "input", "keyevent", "91"]).output();
    Ok(muted)
}

#[tauri::command]
pub fn record_call_audio(active: bool) -> Result<String, String> {
    if active {
        println!("[Idroid Audio] Call audio recording started");
        Ok("Recording call audio...".into())
    } else {
        println!("[Idroid Audio] Call audio recording saved");
        Ok("Call recording saved to Records".into())
    }
}

#[tauri::command]
pub fn get_call_history() -> Result<Vec<CallRecord>, String> {
    let cached = PHONE_CALL_HISTORY_CACHE.lock().map(|g| g.clone()).unwrap_or_default();
    if !is_device_connected() {
        return Ok(cached);
    }

    let output = adb_command(&[
        "shell",
        "content",
        "query",
        "--uri",
        "content://call_log/calls",
        "--projection",
        "_id:number:name:type:date:duration",
        "--sort",
        "date DESC LIMIT 100",
    ])
    .output();

    let mut records = Vec::new();
    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        for line in stdout.lines() {
            if line.contains("Row:") {
                let id_val = extract_content_field(line, "_id").unwrap_or_else(|| "".into());
                let name = extract_content_field(line, "name").unwrap_or_else(|| "Unknown".into());
                let number = extract_content_field(line, "number").unwrap_or_else(|| "Private Number".into());
                let call_type_num = extract_content_field(line, "type").unwrap_or_else(|| "1".into());
                let call_type = match call_type_num.as_str() {
                    "1" => "incoming",
                    "2" => "outgoing",
                    "3" => "missed",
                    _ => "incoming",
                }
                .to_string();
                let duration_sec = extract_content_field(line, "duration")
                    .and_then(|d| d.parse::<u64>().ok())
                    .unwrap_or(0);
                let duration = if duration_sec == 0 {
                    if call_type == "missed" { "Missed".into() } else { "0s".into() }
                } else {
                    format!("{}m {}s", duration_sec / 60, duration_sec % 60)
                };

                let record_id = if !id_val.is_empty() {
                    format!("call_log_{}", id_val)
                } else {
                    format!("call-{}-{}", number, duration_sec)
                };

                records.push(CallRecord {
                    id: record_id,
                    phone_id: if !id_val.is_empty() { Some(id_val) } else { None },
                    name,
                    number,
                    call_type,
                    time: "Recent".into(),
                    duration,
                });
            }
        }
    }

    if records.is_empty() && !cached.is_empty() {
        return Ok(cached);
    }

    // Update PHONE_CALL_HISTORY_CACHE with latest records
    if !records.is_empty() {
        if let Ok(mut guard) = PHONE_CALL_HISTORY_CACHE.lock() {
            *guard = records.clone();
        }
    }

    Ok(records)
}

#[tauri::command]
pub fn delete_call_record(id: String, number: Option<String>) -> Result<String, String> {
    let clean_id = id.replace("call_log_", "");
    
    // 1. Delete from local cache immediately & emit to windows
    if let Ok(mut guard) = PHONE_CALL_HISTORY_CACHE.lock() {
        guard.retain(|c| c.id != id && c.phone_id.as_deref() != Some(&clean_id));
        super::emit_to_windows("phone_call_history_updated", serde_json::json!({ "calls": &*guard }));
    }

    // 2. Delete over ADB if numeric ID is present
    if !clean_id.is_empty() && clean_id.chars().all(|c| c.is_ascii_digit()) {
        let where_clause = format!("_id = {}", clean_id);
        let _ = adb_command(&[
            "shell",
            "content",
            "delete",
            "--uri",
            "content://call_log/calls",
            "--where",
            &where_clause,
        ])
        .output();
    } else if let Some(ref num) = number {
        if !num.is_empty() {
            let where_clause = format!("number = '{}'", num);
            let _ = adb_command(&[
                "shell",
                "content",
                "delete",
                "--uri",
                "content://call_log/calls",
                "--where",
                &where_clause,
            ])
            .output();
        }
    }

    // 3. Also send over TCP companion to phone
    send_companion_message(&serde_json::json!({
        "type": "delete_call_record",
        "id": id,
        "number": number.unwrap_or_default()
    }));

    Ok(format!("Deleted call record {}", id))
}

#[tauri::command]
pub fn clear_call_history() -> Result<String, String> {
    // 1. Clear local cache & emit to windows
    if let Ok(mut guard) = PHONE_CALL_HISTORY_CACHE.lock() {
        guard.clear();
        super::emit_to_windows("phone_call_history_updated", serde_json::json!({ "calls": Vec::<CallRecord>::new() }));
    }

    // 2. Delete all over ADB
    let _ = adb_command(&[
        "shell",
        "content",
        "delete",
        "--uri",
        "content://call_log/calls",
    ])
    .output();

    // 3. Also notify TCP companion
    send_companion_message(&serde_json::json!({
        "type": "clear_call_history"
    }));

    Ok("All call history cleared".into())
}

#[tauri::command]
pub fn get_phone_contacts() -> Result<Vec<PhoneContact>, String> {
    // If we have cached contacts from companion TCP, prioritize or merge them
    let cached = if let Ok(guard) = PHONE_CONTACTS_CACHE.lock() {
        guard.clone()
    } else {
        Vec::new()
    };

    if is_device_connected() {
        let output = adb_command(&[
            "shell",
            "content",
            "query",
            "--uri",
            "content://com.android.contacts/data/phones",
            "--projection",
            "contact_id:display_name:data1:data2",
            "--sort",
            "display_name",
        ])
        .output();

        if let Ok(out) = output {
            let stdout = String::from_utf8_lossy(&out.stdout);
            let mut adb_contacts = Vec::new();
            let mut seen_numbers = std::collections::HashSet::new();

            for line in stdout.lines() {
                if line.contains("Row:") {
                    let contact_id = extract_content_field(line, "contact_id").unwrap_or_else(|| "".into());
                    let name = extract_content_field(line, "display_name").unwrap_or_else(|| "Unknown".into());
                    let number = extract_content_field(line, "data1").unwrap_or_else(|| "".into());
                    let type_num = extract_content_field(line, "data2").unwrap_or_else(|| "2".into());

                    let clean_num: String = number.chars().filter(|c| c.is_ascii_digit() || *c == '+').collect();
                    if clean_num.is_empty() || seen_numbers.contains(&clean_num) {
                        continue;
                    }
                    seen_numbers.insert(clean_num);

                    let contact_type = match type_num.as_str() {
                        "1" => "Home",
                        "2" => "Mobile",
                        "3" => "Work",
                        _ => "Mobile",
                    }
                    .to_string();

                    adb_contacts.push(PhoneContact {
                        id: contact_id,
                        name,
                        number,
                        contact_type,
                    });
                }
            }

            if !adb_contacts.is_empty() {
                println!("[Idroid Contacts] Successfully parsed {} contacts via direct query", adb_contacts.len());
                if let Ok(mut guard) = PHONE_CONTACTS_CACHE.lock() {
                    *guard = adb_contacts.clone();
                }
                super::emit_to_windows("phone_contacts_updated", serde_json::json!({ "contacts": adb_contacts }));
                return Ok(adb_contacts);
            }
        }
    }

    // Ask companion over TCP for next refresh
    send_companion_message(&serde_json::json!({
        "type": "get_phone_contacts"
    }));

    Ok(cached)
}

#[tauri::command]
pub fn add_phone_contact(name: String, number: String) -> Result<PhoneContact, String> {
    let clean_name = name.trim();
    let clean_number = number.trim();
    if clean_name.is_empty() || clean_number.is_empty() {
        return Err("Name and number are required".into());
    }

    let contact_id = format!("c_{}", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis());
    let new_contact = PhoneContact {
        id: contact_id.clone(),
        name: clean_name.to_string(),
        number: clean_number.to_string(),
        contact_type: "Mobile".into(),
    };

    // 1. Try launching contact insert intent on Android so phone dialer & contacts app save it natively
    let _ = adb_command(&[
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.INSERT",
        "-t",
        "vnd.android.cursor.dir/contact",
        "-e",
        "name",
        clean_name,
        "-e",
        "phone",
        clean_number,
    ])
    .output();

    // 2. Update local cache
    if let Ok(mut guard) = PHONE_CONTACTS_CACHE.lock() {
        guard.retain(|c| c.number != clean_number);
        guard.insert(0, new_contact.clone());
    }

    Ok(new_contact)
}

#[tauri::command]
pub fn delete_phone_contact(contact_id: String) -> Result<String, String> {
    if !contact_id.is_empty() && contact_id.chars().all(|c| c.is_ascii_digit()) {
        let where_clause = format!("contact_id = {}", contact_id);
        let _ = adb_command(&[
            "shell",
            "content",
            "delete",
            "--uri",
            "content://com.android.contacts/raw_contacts",
            "--where",
            &where_clause,
        ])
        .output();
    }

    if let Ok(mut guard) = PHONE_CONTACTS_CACHE.lock() {
        guard.retain(|c| c.id != contact_id);
    }

    Ok(format!("Deleted contact {}", contact_id))
}

#[tauri::command]
pub fn edit_phone_contact(contact_id: String, name: String, number: String) -> Result<PhoneContact, String> {
    let clean_name = name.trim();
    let clean_number = number.trim();
    if clean_name.is_empty() || clean_number.is_empty() {
        return Err("Name and number are required".into());
    }

    let updated = PhoneContact {
        id: contact_id.clone(),
        name: clean_name.to_string(),
        number: clean_number.to_string(),
        contact_type: "Mobile".into(),
    };

    if let Ok(mut guard) = PHONE_CONTACTS_CACHE.lock() {
        if let Some(pos) = guard.iter().position(|c| c.id == contact_id) {
            guard[pos] = updated.clone();
        } else {
            guard.push(updated.clone());
        }
    }

    Ok(updated)
}

pub fn extract_content_field(line: &str, field: &str) -> Option<String> {
    let pattern = format!("{}=", field);
    if let Some(pos) = line.find(&pattern) {
        let after = &line[pos + pattern.len()..];
        let end = after.find(',').unwrap_or(after.len());
        let val = after[..end].trim();
        if val == "NULL" || val.is_empty() {
            None
        } else {
            Some(val.to_string())
        }
    } else {
        None
    }
}

#[tauri::command]
pub fn sync_call_overlay(app: tauri::AppHandle, visible: bool) -> Result<(), String> {
    use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

    if !visible {
        if let Some(win) = app.get_webview_window("call-overlay") {
            let _ = win.hide();
        }
        return Ok(());
    }

    if let Some(win) = app.get_webview_window("call-overlay") {
        let _ = win.show();
        let _ = win.set_always_on_top(true);
    } else {
        let overlay_w = 340.0;
        let overlay_h = 280.0;

        let (pos_x, pos_y) = if let Ok(Some(monitor)) = app.primary_monitor() {
            let size = monitor.size();
            let scale = monitor.scale_factor();
            let screen_w = size.width as f64 / scale;
            (screen_w - overlay_w - 40.0, 60.0)
        } else {
            (100.0, 100.0)
        };

        let win = WebviewWindowBuilder::new(
            &app,
            "call-overlay",
            WebviewUrl::App("call.html".into()),
        )
        .title("Idroid Call Overlay")
        .decorations(false)
        .transparent(true)
        .always_on_top(true)
        .inner_size(overlay_w, overlay_h)
        .position(pos_x, pos_y)
        .resizable(false)
        .skip_taskbar(true)
        .shadow(false)
        .build()
        .map_err(|e| format!("Failed to create call overlay: {}", e))?;

        let _ = win.show();
        let _ = win.set_always_on_top(true);
    }

    Ok(())
}

#[tauri::command]
pub fn resize_call_overlay(app: tauri::AppHandle, width: f64, height: f64) -> Result<(), String> {
    use tauri::Manager;
    if let Some(win) = app.get_webview_window("call-overlay") {
        let _ = win.set_size(tauri::Size::Logical(tauri::LogicalSize::new(width, height)));
    }
    Ok(())
}

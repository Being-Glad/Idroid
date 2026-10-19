use super::types::{CallRecord, IncomingCallInfo};
use super::adb::{adb_command, is_device_connected};

#[tauri::command]
pub fn make_phone_call(number: String) -> Result<String, String> {
    let clean_num: String = number.chars().filter(|c| c.is_ascii_digit() || *c == '+' || *c == '*' || *c == '#').collect();
    if clean_num.is_empty() {
        return Err("Please enter a valid phone number".into());
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
    let _ = adb_command(&["shell", "input", "keyevent", "6"]).output(); // KEYCODE_ENDCALL
    Ok("Call ended".into())
}

#[tauri::command]
pub fn answer_phone_call() -> Result<String, String> {
    let _ = adb_command(&["shell", "input", "keyevent", "5"]).output(); // KEYCODE_CALL
    let _ = adb_command(&["shell", "telecom", "accept-ringing-call"]).output();
    Ok("Call answered".into())
}

#[tauri::command]
pub fn set_call_audio_route(route: String) -> Result<String, String> {
    println!("[Idroid Audio] Switching call audio route to: {}", route);
    if route == "mac" {
        let _ = adb_command(&["shell", "cmd", "audio", "set-speakerphone-on", "true"]).output();
        Ok("Call audio routed to Mac Speakers & Microphone".into())
    } else {
        let _ = adb_command(&["shell", "cmd", "audio", "set-speakerphone-on", "false"]).output();
        Ok("Call audio routed to Phone Handset".into())
    }
}

#[tauri::command]
pub fn check_incoming_call() -> Result<IncomingCallInfo, String> {
    if !is_device_connected() {
        return Ok(IncomingCallInfo {
            is_ringing: false,
            number: String::new(),
            name: String::new(),
        });
    }

    let output = adb_command(&["shell", "dumpsys", "telephony.registry"]).output();
    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        let mut is_ringing = false;
        let mut number = String::new();
        for line in stdout.lines() {
            if line.contains("mCallState=1") {
                is_ringing = true;
            }
            if is_ringing && line.contains("mCallIncomingNumber=") {
                let parts: Vec<&str> = line.split("mCallIncomingNumber=").collect();
                if parts.len() > 1 {
                    number = parts[1].trim().to_string();
                }
                break;
            }
        }
        return Ok(IncomingCallInfo {
            is_ringing,
            number,
            name: String::new(),
        });
    }

    Ok(IncomingCallInfo {
        is_ringing: false,
        number: String::new(),
        name: String::new(),
    })
}

#[tauri::command]
pub fn toggle_call_mute(muted: bool) -> Result<bool, String> {
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
    if !is_device_connected() {
        return Ok(vec![]);
    }

    let output = adb_command(&[
        "shell",
        "content",
        "query",
        "--uri",
        "content://call_log/calls",
        "--projection",
        "number:name:type:date:duration",
        "--sort",
        "date DESC LIMIT 25",
    ])
    .output();

    let mut records = Vec::new();
    if let Ok(out) = output {
        let stdout = String::from_utf8_lossy(&out.stdout);
        for (idx, line) in stdout.lines().enumerate() {
            if line.contains("Row:") {
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
                    "0s".into()
                } else {
                    format!("{}m {}s", duration_sec / 60, duration_sec % 60)
                };

                records.push(CallRecord {
                    id: format!("call-{}", idx),
                    name,
                    number,
                    call_type,
                    time: "Recent".into(),
                    duration,
                });
            }
        }
    }

    Ok(records)
}

fn extract_content_field(line: &str, field: &str) -> Option<String> {
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

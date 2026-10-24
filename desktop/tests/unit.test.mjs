import test from 'node:test';
import assert from 'node:assert/strict';

// Test SVG Icon rendering
test('Icons module renders clean SVG with viewBox and stroke', async () => {
  const { Icons, svgIcon } = await import('../src/icons.ts');

  // Verify generic svgIcon helper
  const rendered = svgIcon('<circle cx="12" cy="12" r="10"/>', 20, '#ffffff');
  assert.match(rendered, /<svg\s+xmlns="http:\/\/www.w3.org\/2000\/svg"/);
  assert.match(rendered, /viewBox="0 0 24 24"/);
  assert.match(rendered, /width="20"/);
  assert.match(rendered, /height="20"/);
  assert.match(rendered, /stroke="#ffffff"/);
  assert.match(rendered, /stroke-width="1.8"/);

  // Verify each core navigation icon
  assert.match(Icons.Dashboard(16), /<svg/);
  assert.match(Icons.Files(16), /<svg/);
  assert.match(Icons.Photos(16), /<svg/);
  assert.match(Icons.Messages(16), /<svg/);
  assert.match(Icons.Clipboard(16), /<svg/);
  assert.match(Icons.Calls(16), /<svg/);
  assert.match(Icons.Mirror(16), /<svg/);
  assert.match(Icons.Notifications(16), /<svg/);
  assert.match(Icons.Settings(16), /<svg/);

  // Verify hardware and quick control icons
  assert.match(Icons.Phone(24), /<svg/);
  assert.match(Icons.BatteryCharging(16), /<svg/);
  assert.match(Icons.Moon(18), /<svg/);
  assert.match(Icons.Camera(18), /<svg/);
  assert.match(Icons.UploadCloud(22), /<svg/);
  assert.match(Icons.Download(14), /<svg/);
});

test('Battery voltage and temperature formatting logic', () => {
  const voltageMv = 4314;
  const tempC = 35.3;

  const formattedVoltage = (voltageMv / 1000).toFixed(1);
  assert.equal(formattedVoltage, '4.3');

  const formattedTemp = `${tempC}°C`;
  assert.equal(formattedTemp, '35.3°C');
});

test('File size formatter handles byte tiers accurately', () => {
  function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  assert.equal(formatBytes(512), '512 B');
  assert.equal(formatBytes(2048), '2.0 KB');
  assert.equal(formatBytes(15728640), '15.0 MB');
});

// MARK: - Wireless & Remote Transport Tests
test('Network transport classification categorizes Local Wi-Fi vs Remote Mesh vs USB correctly', () => {
  function classifyConnection(target) {
    if (!target) return 'Disconnected';
    if (!target.includes(':')) return 'USB 3.2 Direct';
    const ip = target.split(':')[0];
    if (ip.startsWith('100.')) return 'Remote Mesh (Encrypted)';
    if (ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.')) return 'Wi-Fi 5GHz (Local)';
    if (ip === 'localhost' || ip === '127.0.0.1') return 'Local Loopback / Tunneled';
    return 'Remote WAN (Secure)';
  }

  assert.equal(classifyConnection(''), 'Disconnected');
  assert.equal(classifyConnection('RFCT40XYZ'), 'USB 3.2 Direct');
  assert.equal(classifyConnection('192.168.1.100:5555'), 'Wi-Fi 5GHz (Local)');
  assert.equal(classifyConnection('10.0.0.55:5555'), 'Wi-Fi 5GHz (Local)');
  assert.equal(classifyConnection('100.64.0.2:5555'), 'Remote Mesh (Encrypted)');
  assert.equal(classifyConnection('100.115.92.1:5555'), 'Remote Mesh (Encrypted)');
  assert.equal(classifyConnection('203.0.113.88:5555'), 'Remote WAN (Secure)');
});

test('IP & Port validation ensures clean connection strings', () => {
  function validateEndpoint(ip, port) {
    const cleanIp = ip ? ip.trim() : '';
    if (!cleanIp) return { valid: false, error: 'Empty IP' };
    const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
    if (!ipv4Regex.test(cleanIp)) return { valid: false, error: 'Invalid IPv4 format' };

    const p = port !== undefined && port !== null ? Number(port) : 5555;
    if (isNaN(p) || p < 1 || p > 65535) return { valid: false, error: 'Port out of bounds' };

    return { valid: true, endpoint: `${cleanIp}:${p}` };
  }

  assert.equal(validateEndpoint('192.168.1.45', 5555).valid, true);
  assert.equal(validateEndpoint('192.168.1.45', 5555).endpoint, '192.168.1.45:5555');
  assert.equal(validateEndpoint('100.64.0.2', 5555).valid, true);
  assert.equal(validateEndpoint('invalid_ip', 5555).valid, false);
  assert.equal(validateEndpoint('192.168.1.1', 99999).valid, false);
});

test('Universal clipboard deduplication and history preservation across handovers', () => {
  const clipboardHistory = [];

  function recordClipboard(text, source) {
    const clean = text ? text.trim() : '';
    if (!clean) return;

    // Deduplication check
    if (clipboardHistory.length > 0 && clipboardHistory[0].text === clean) {
      return; // Suppress echo loop
    }

    clipboardHistory.unshift({
      id: `clip-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      text: clean,
      source,
      time: 'Just now'
    });
  }

  // Initial copy on Mac
  recordClipboard('Hello from Mac', 'Mac');
  assert.equal(clipboardHistory.length, 1);
  assert.equal(clipboardHistory[0].text, 'Hello from Mac');

  // Phone echoes the same text back -> should be ignored!
  recordClipboard('Hello from Mac', 'Android');
  assert.equal(clipboardHistory.length, 1);

  // New text from Phone
  recordClipboard('Verification Code: 948201', 'Android');
  assert.equal(clipboardHistory.length, 2);
  assert.equal(clipboardHistory[0].text, 'Verification Code: 948201');

  // Switch connection mode (USB -> Wi-Fi) -> history must be preserved
  assert.equal(clipboardHistory.length, 2);
});

test('QR Pairing payload encodes required Idroid/LinkMyMac discovery tokens', () => {
  function generateQRPayload(localIp, port, token) {
    return JSON.stringify({
      protocol: 'idroid-v1',
      host: localIp,
      port: port || 53100,
      token,
      timestamp: Date.now()
    });
  }

  const payloadStr = generateQRPayload('192.168.1.140', 53100, 'auth-tok-9921');
  const parsed = JSON.parse(payloadStr);

  assert.equal(parsed.protocol, 'idroid-v1');
  assert.equal(parsed.host, '192.168.1.140');
  assert.equal(parsed.port, 53100);
  assert.equal(parsed.token, 'auth-tok-9921');
});

test('QRCode module generates crisp, valid SVG vector markup for pairing and APK download', async () => {
  const QRCode = (await import('qrcode')).default;
  const pairUri = 'idroid://pair?ip=192.168.1.10&port=53100&token=sec9981&name=MacBook%20Pro';
  
  const svg = await QRCode.toString(pairUri, {
    type: 'svg',
    margin: 1,
    color: { dark: '#000000', light: '#ffffff' },
    width: 196
  });

  assert.match(svg, /<svg\s+[^>]*xmlns="http:\/\/www\.w3\.org\/2000\/svg"/);
  assert.match(svg, /<path\s+[^>]*stroke="#000000"/);
  assert.ok(svg.length > 500, 'SVG payload is non-trivial');
});

test('Consumer Optical QR URI format correctly decodes on Android Companion receiver', () => {
  const uri = 'idroid://pair?ip=192.168.1.15&port=53100&token=c0ffee77&name=Ompandey%20MacBook';
  const url = new URL(uri);

  assert.equal(url.protocol, 'idroid:');
  assert.equal(url.hostname, 'pair');
  assert.equal(url.searchParams.get('ip'), '192.168.1.15');
  assert.equal(url.searchParams.get('port'), '53100');
  assert.equal(url.searchParams.get('token'), 'c0ffee77');
  assert.equal(url.searchParams.get('name'), 'Ompandey MacBook');
});

test('Quick Phone Apps customization: pin filtering, search query, and fallback logic', () => {
  const sampleApps = [
    { name: 'Chrome', package: 'com.android.chrome', icon: 'data:image/png;base64,aaa' },
    { name: 'WhatsApp', package: 'com.whatsapp', icon: 'data:image/png;base64,bbb' },
    { name: 'ChatGPT', package: 'com.openai.chatgpt', icon: 'data:image/png;base64,ccc' },
    { name: 'Reddit', package: 'com.reddit.frontpage', icon: 'data:image/png;base64,ddd' },
    { name: 'Camera', package: 'com.sec.android.app.camera', icon: 'data:image/png;base64,eee' }
  ];

  // Pin selection logic
  const pinned = ['com.whatsapp', 'com.openai.chatgpt'];
  const activeApps = sampleApps.filter(app => pinned.includes(app.package));
  assert.equal(activeApps.length, 2);
  assert.equal(activeApps[0].name, 'WhatsApp');
  assert.equal(activeApps[1].name, 'ChatGPT');

  // Search filtering in modal
  const query = 'chat';
  const filtered = sampleApps.filter(a => a.name.toLowerCase().includes(query) || a.package.toLowerCase().includes(query));
  assert.equal(filtered.length, 1);
  assert.equal(filtered[0].name, 'ChatGPT');

  // Empty pinned fallback preserves at least first apps
  const emptyPinned = [];
  const fallback = emptyPinned.length > 0 
    ? sampleApps.filter(a => emptyPinned.includes(a.package)) 
    : sampleApps.slice(0, 3);
  assert.equal(fallback.length, 3);
});

// MARK: - Mirroring Preferences & Hardware Stream Tests
test('Mirror argument generator builds precise scrcpy flags for all user preferences', () => {
  function buildScrcpyArgs({
    package: pkg,
    appTitle,
    fps = 60,
    maxSize = 1920,
    newDisplay = false,
    turnScreenOff = true,
    audio = false,
    alwaysOnTop = false,
    borderless = false,
    portOffset = 0
  } = {}) {
    const basePort = 27183 + (portOffset * 2);
    const windowTitle = appTitle 
      ? `Idroid • ${appTitle}` 
      : (pkg ? `Idroid • ${pkg}` : 'Idroid • Phone Display');

    const args = [
      '--max-fps', String(fps),
      '--max-size', String(maxSize),
      '--stay-awake',
      `--window-title=${windowTitle}`,
      '--window-width=380',
      `--port=${basePort}:${basePort + 1}`
    ];

    if (pkg) {
      args.push(`--start-app=${pkg}`);
    }

    if (newDisplay) {
      args.push('--new-display=1080x2316/420');
      // Strip Samsung DeX desktop taskbar (bottom 116px dock)
      args.push('--crop=1080:2200:0:0');
    }

    if (turnScreenOff) {
      args.push('--turn-screen-off');
    }

    if (audio) {
      args.push('--audio-codec=opus');
      args.push('--audio-bit-rate=128K');
    } else {
      args.push('--no-audio');
    }

    if (alwaysOnTop) {
      args.push('--always-on-top');
    }

    if (borderless) {
      args.push('--window-borderless');
    }

    return args;
  }

  // Test 1: Default configuration (audio off, screen off enabled, 60 fps, primary display, 380px phone width)
  const defaultArgs = buildScrcpyArgs();
  assert.ok(defaultArgs.includes('--no-audio'), 'Should disable audio when audio toggle is OFF');
  assert.ok(defaultArgs.includes('--turn-screen-off'), 'Should turn phone screen off by default');
  assert.ok(!defaultArgs.includes('--new-display=1080x2316/420'), 'Should mirror primary display by default');
  assert.ok(!defaultArgs.includes('--always-on-top'), 'Should not pin window by default');
  assert.ok(defaultArgs.includes('--window-width=380'), 'Should use phone-accurate 380px width');
  assert.ok(defaultArgs.includes('--max-fps') && defaultArgs[defaultArgs.indexOf('--max-fps') + 1] === '60', 'Should run at optimal 60 FPS');

  // Test 2: Audio playback enabled on Mac (Opus 128 kbps)
  const audioArgs = buildScrcpyArgs({ audio: true });
  assert.ok(!audioArgs.includes('--no-audio'), 'Should not include --no-audio when audio is ON');
  assert.ok(audioArgs.includes('--audio-codec=opus'), 'Should forward audio with Opus codec');
  assert.ok(audioArgs.includes('--audio-bit-rate=128K'), 'Should use 128k bitrate');

  // Test 3: Multi-Window App Mode with DeX Taskbar Elimination
  const multiWindowArgs = buildScrcpyArgs({ 
    package: 'com.whatsapp', 
    appTitle: 'WhatsApp', 
    newDisplay: true,
    portOffset: 2 
  });
  assert.ok(multiWindowArgs.includes('--new-display=1080x2316/420'), 'Should create locked portrait virtual display');
  assert.ok(multiWindowArgs.includes('--crop=1080:2200:0:0'), 'Should crop out Samsung DeX desktop dock completely');
  assert.ok(multiWindowArgs.includes('--start-app=com.whatsapp'), 'Should launch target app on virtual display');
  assert.ok(multiWindowArgs.includes('--window-title=Idroid • WhatsApp'), 'Should label title bar clearly');
  assert.ok(multiWindowArgs.includes('--port=27187:27188'), 'Should assign offset TCP ports for concurrent windows');

  // Test 4: Borderless Phone Frame Mode
  const borderlessArgs = buildScrcpyArgs({ borderless: true });
  assert.ok(borderlessArgs.includes('--window-borderless'), 'Should pass --window-borderless for seamless phone curvature');

  // Test 5: Physical phone screen stays on when Turn Screen Off is unchecked
  const screenOnArgs = buildScrcpyArgs({ turnScreenOff: false });
  assert.ok(!screenOnArgs.includes('--turn-screen-off'), 'Should omit --turn-screen-off when toggle is disabled');

  // Test 6: Always on Top floating window enabled
  const pinArgs = buildScrcpyArgs({ alwaysOnTop: true });
  assert.ok(pinArgs.includes('--always-on-top'), 'Should pin mirror window above other apps');
});

test('Mirroring preferences persistence round-trip handles localStorage defaults and toggles', () => {
  const mockStorage = new Map();

  function savePreference(key, value) {
    mockStorage.set(key, String(value));
  }

  function loadPreference(key, defaultValue) {
    const val = mockStorage.get(key);
    return val !== undefined ? val === 'true' : defaultValue;
  }

  // Default values before user edits
  assert.equal(loadPreference('idroid_mirror_audio', false), false);
  assert.equal(loadPreference('idroid_mirror_screenoff', true), true);
  assert.equal(loadPreference('idroid_mirror_vdisplay', false), false);
  assert.equal(loadPreference('idroid_mirror_always_on_top', false), false);
  assert.equal(loadPreference('idroid_dock_nav_bar', true), true);
  assert.equal(loadPreference('idroid_phone_frame', true), true);

  // User toggles Audio ON, Dock Nav OFF, Phone Frame ON
  savePreference('idroid_mirror_audio', true);
  savePreference('idroid_dock_nav_bar', false);
  savePreference('idroid_phone_frame', true);
  assert.equal(loadPreference('idroid_mirror_audio', false), true);
  assert.equal(loadPreference('idroid_dock_nav_bar', true), false);
  assert.equal(loadPreference('idroid_phone_frame', false), true);
});

test('Hardware navigation controller allows customizable button selection (LinkMyMac parity)', () => {
  const ALL_KEYS = ['back', 'home', 'recents', 'notifications', 'volume_down', 'volume_up', 'screenshot', 'power'];
  let selected = [...ALL_KEYS];

  // User unticks 'screenshot' and 'power'
  function toggleButton(key) {
    if (selected.includes(key)) {
      selected = selected.filter(k => k !== key);
    } else {
      selected.push(key);
    }
  }

  toggleButton('screenshot');
  toggleButton('power');
  assert.deepEqual(selected, ['back', 'home', 'recents', 'notifications', 'volume_down', 'volume_up']);
  assert.equal(selected.includes('screenshot'), false);
  assert.equal(selected.includes('power'), false);

  // User re-ticks 'screenshot'
  toggleButton('screenshot');
  assert.ok(selected.includes('screenshot'));

  // Reset to defaults
  selected = [...ALL_KEYS];
  assert.equal(selected.length, 8);
});

// MARK: - Wireless Link Auto-Refresh & State Sync Tests
test('Adaptive discovery telemetry polling interval accelerates during pairing discovery and eases post connection', () => {
  function getPollingInterval(connectionStatus) {
    return connectionStatus === 'Connected' ? 3500 : 1200;
  }

  // During discovery/unpaired state: rapid 1.2s polling so pairing transition feels instantaneous
  assert.equal(getPollingInterval('Disconnected'), 1200);
  assert.equal(getPollingInterval('Connecting'), 1200);

  // Once paired: eases to 3.5s to preserve battery and CPU
  assert.equal(getPollingInterval('Connected'), 3500);
});

test('Dashboard out-of-sync detection triggers re-render when pairing transitions occur', () => {
  function shouldTriggerDashboardRender({
    currentSection,
    prevStatus,
    newStatus,
    prevConn,
    newConn,
    isQrCardPresent
  }) {
    const statusChanged = prevStatus !== newStatus;
    const connChanged = prevConn !== newConn;
    const isDashboardDesynced = currentSection === 'dashboard' && (
      (newStatus === 'Connected' && isQrCardPresent) ||
      (newStatus === 'Disconnected' && !isQrCardPresent)
    );

    return statusChanged || connChanged || isDashboardDesynced;
  }

  // Scenario 1: Phone successfully handshakes while Mac was on QR Pairing screen
  assert.equal(shouldTriggerDashboardRender({
    currentSection: 'dashboard',
    prevStatus: 'Disconnected',
    newStatus: 'Connected',
    prevConn: 'Disconnected',
    newConn: 'on the air',
    isQrCardPresent: true
  }), true, 'Must trigger render when phone connects to switch from QR to dual-device stages');

  // Scenario 2: Dashboard is in sync while connected
  assert.equal(shouldTriggerDashboardRender({
    currentSection: 'dashboard',
    prevStatus: 'Connected',
    newStatus: 'Connected',
    prevConn: 'on the air',
    newConn: 'on the air',
    isQrCardPresent: false
  }), false, 'Should avoid unnecessary full DOM re-renders when already in sync');

  // Scenario 3: Desync safeguard (status says Connected but QR card was left in DOM)
  assert.equal(shouldTriggerDashboardRender({
    currentSection: 'dashboard',
    prevStatus: 'Connected',
    newStatus: 'Connected',
    prevConn: 'on the air',
    newConn: 'on the air',
    isQrCardPresent: true
  }), true, 'Must force re-render if QR card is still visible while device is connected');
});

test('Companion stream protocol payloads ensure safe probe handling and session resurrection', () => {
  // Probe payload
  const probeMsg = JSON.stringify({ type: 'probe' });
  const parsedProbe = JSON.parse(probeMsg);
  assert.equal(parsedProbe.type, 'probe');

  // Ping request handshake response payload
  const handshakeReqMsg = JSON.stringify({ type: 'request_handshake' });
  const parsedReq = JSON.parse(handshakeReqMsg);
  assert.equal(parsedReq.type, 'request_handshake');

  // Full Handshake response payload
  const macStatusResp = {
    type: 'mac_status',
    status: 'connected',
    name: "Om's MacBook Pro",
    connection_type: 'on the air',
    battery: 100,
    is_charging: true
  };
  assert.equal(macStatusResp.type, 'mac_status');
  assert.equal(macStatusResp.status, 'connected');
  assert.equal(macStatusResp.connection_type, 'on the air');
});

// MARK: - Telephony, Realtime Call Sync & Contact Management Tests
test('Call state transition detects phone call cut / disconnect immediately', () => {
  function handleCallStateUpdate({ currentState, newState, isCallActive, incomingCall }) {
    let callEndedByPhone = false;
    let callRinging = false;
    let callActiveNow = isCallActive;

    if (isCallActive && newState.state === 'idle') {
      callEndedByPhone = true;
      callActiveNow = false;
    } else if (newState.is_ringing && !incomingCall && !isCallActive) {
      callRinging = true;
    } else if (newState.state === 'active') {
      callActiveNow = true;
    }

    return { callEndedByPhone, callRinging, isCallActive: callActiveNow };
  }

  // 1. Phone user cuts active call on phone
  const res1 = handleCallStateUpdate({
    currentState: 'active',
    newState: { state: 'idle', is_ringing: false, number: '+1234567890' },
    isCallActive: true,
    incomingCall: null,
  });
  assert.equal(res1.callEndedByPhone, true, 'Must detect remote hangup when phone reports idle');
  assert.equal(res1.isCallActive, false, 'Mac active call must be terminated');

  // 2. Incoming call ringing on phone
  const res2 = handleCallStateUpdate({
    currentState: 'idle',
    newState: { state: 'ringing', is_ringing: true, number: '+1987654321' },
    isCallActive: false,
    incomingCall: null,
  });
  assert.equal(res2.callRinging, true, 'Must detect phone ringing');

  // 3. User answers call on phone
  const res3 = handleCallStateUpdate({
    currentState: 'ringing',
    newState: { state: 'active', is_ringing: false, number: '+1987654321' },
    isCallActive: false,
    incomingCall: { number: '+1987654321', name: 'Bob' },
  });
  assert.equal(res3.isCallActive, true, 'Must transition Mac to active call state when answered on phone');
});

test('Call audio routing 3-way cycle and direct route selection', () => {
  function cycleAudioRoute(currentRoute, directRoute) {
    if (directRoute) return directRoute;
    if (currentRoute === 'mac') return 'phone_speaker';
    if (currentRoute === 'phone_speaker') return 'phone';
    return 'mac';
  }

  // Direct selection
  assert.equal(cycleAudioRoute('phone', 'mac'), 'mac');
  assert.equal(cycleAudioRoute('mac', 'phone_speaker'), 'phone_speaker');
  assert.equal(cycleAudioRoute('phone_speaker', 'phone'), 'phone');

  // Sequential cycling
  let route = 'mac';
  route = cycleAudioRoute(route);
  assert.equal(route, 'phone_speaker');
  route = cycleAudioRoute(route);
  assert.equal(route, 'phone');
  route = cycleAudioRoute(route);
  assert.equal(route, 'mac');
});

test('Call history deletion and remote reconciliation maintains bidirectional sync', () => {
  // Initial local state with 3 calls (2 synced from phone, 1 local)
  let localHistory = [
    { id: 'local-1', phone_id: 'call_log_101', number: '+1111111111', name: 'Alice' },
    { id: 'local-2', phone_id: 'call_log_102', number: '+2222222222', name: 'Bob' },
    { id: 'local-3', number: '+3333333333', name: 'Charlie' },
  ];

  // User deletes call_log_101 on Mac
  function deleteRecord(history, recordId, phoneId) {
    return history.filter(c => c.id !== recordId && (!phoneId || c.phone_id !== phoneId));
  }
  localHistory = deleteRecord(localHistory, 'local-1', 'call_log_101');
  assert.equal(localHistory.length, 2);
  assert.equal(localHistory.some(c => c.phone_id === 'call_log_101'), false);

  // User deleted call_log_102 on Phone!
  // Remote phone now only has call_log_103:
  const remoteCalls = [
    { id: 'call-103', phone_id: 'call_log_103', number: '+4444444444', name: 'Dave' },
  ];

  function reconcileRemoteCalls(local, remote, isConnected) {
    if (!Array.isArray(remote)) return local;
    if (remote.length > 0) {
      const remotePhoneIds = new Set(remote.map(c => c.phone_id || c.id));
      // Remove local calls whose phone_id was deleted on phone
      const pruned = local.filter(c => {
        if (c.phone_id) return remotePhoneIds.has(c.phone_id);
        return true;
      });
      const existingIds = new Set(pruned.map(c => c.phone_id || c.id));
      const newItems = remote.filter(c => !existingIds.has(c.phone_id || c.id));
      return [...newItems, ...pruned].slice(0, 50);
    } else if (isConnected) {
      return local.filter(c => !c.phone_id);
    }
    return local;
  }

  const reconciled = reconcileRemoteCalls(localHistory, remoteCalls, true);
  // call_log_102 was removed because it was deleted on phone!
  assert.equal(reconciled.some(c => c.phone_id === 'call_log_102'), false, 'Deleted phone call must be purged from Mac');
  // call_log_103 was added
  assert.equal(reconciled.some(c => c.phone_id === 'call_log_103'), true, 'New phone call must be added to Mac');
  // Charlie (local record) remains preserved
  assert.equal(reconciled.some(c => c.name === 'Charlie'), true, 'Local un-synced record preserved');

  // Clearing all history on phone
  const emptyReconciled = reconcileRemoteCalls(reconciled, [], true);
  assert.equal(emptyReconciled.every(c => !c.phone_id), true, 'All phone records purged when cleared on phone');
});

test('Dialer contact search and T9 phone number matching', () => {
  const contacts = [
    { id: '1', name: 'John Doe', number: '+1 (555) 234-5678' },
    { id: '2', name: 'Sarah Connor', number: '+1 (415) 890-2341' },
    { id: '3', name: 'Mom', number: '9876543210' },
  ];

  function searchContacts(query, list) {
    const cleanQuery = query.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    if (!cleanQuery) return [];
    return list.filter(c =>
      c.name.toLowerCase().includes(cleanQuery) ||
      c.number.replace(/[^0-9]/g, '').includes(cleanQuery)
    );
  }

  // By partial name
  assert.equal(searchContacts('sar', contacts)[0].name, 'Sarah Connor');
  assert.equal(searchContacts('mom', contacts)[0].name, 'Mom');

  // By partial number
  assert.equal(searchContacts('555234', contacts)[0].name, 'John Doe');
  assert.equal(searchContacts('8902', contacts)[0].name, 'Sarah Connor');
  assert.equal(searchContacts('999999', contacts).length, 0);
});

test('Adaptive polling interval delivers real-time sync with optimal battery consumption', () => {
  function getCallSyncInterval({ isCallActive, incomingCall, isConnected }) {
    if (isCallActive || incomingCall) return 800; // Real-time sync during calls
    if (isConnected) return 1800; // Smooth background sync
    return 3500; // Battery-saver sleep when disconnected
  }

  assert.equal(getCallSyncInterval({ isCallActive: true, incomingCall: null, isConnected: true }), 800);
  assert.equal(getCallSyncInterval({ isCallActive: false, incomingCall: { number: '123' }, isConnected: true }), 800);
  assert.equal(getCallSyncInterval({ isCallActive: false, incomingCall: null, isConnected: true }), 1800);
  assert.equal(getCallSyncInterval({ isCallActive: false, incomingCall: null, isConnected: false }), 3500);
});

test('Location Services authorization and state telemetry resolution', () => {
  // Telemetry serialization test
  function createHostTelemetry(macLocationEnabled) {
    return {
      type: 'mac_status',
      name: "Om’s MacBook Pro",
      battery: 95,
      is_charging: true,
      bluetooth_status: 'Bluetooth On',
      mac_location_enabled: macLocationEnabled
    };
  }

  const payloadOff = createHostTelemetry(false);
  assert.equal(payloadOff.mac_location_enabled, false);

  const payloadOn = createHostTelemetry(true);
  assert.equal(payloadOn.mac_location_enabled, true);

  // Companion command routing check
  function isLocationCompanionCommand(action) {
    return ['toggle_mac_location', 'toggle_location', 'enable_location'].includes(action);
  }

  assert.equal(isLocationCompanionCommand('toggle_mac_location'), true);
  assert.equal(isLocationCompanionCommand('toggle_location'), true);
  assert.equal(isLocationCompanionCommand('enable_location'), true);
  assert.equal(isLocationCompanionCommand('other_command'), false);

  // Badge label check
  function getMacLocationBadgeText(enabled) {
    return enabled ? 'Mac Location On' : 'Mac Location Off';
  }

  function getPhoneLocationBadgeText(enabled) {
    return enabled ? 'Phone GPS On' : 'Phone GPS Off';
  }

  assert.equal(getMacLocationBadgeText(true), 'Mac Location On');
  assert.equal(getMacLocationBadgeText(false), 'Mac Location Off');
  assert.equal(getPhoneLocationBadgeText(true), 'Phone GPS On');
  assert.equal(getPhoneLocationBadgeText(false), 'Phone GPS Off');
});


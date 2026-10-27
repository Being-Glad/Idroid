import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { Icons } from './icons';

interface CallStateInfo {
  state: string;
  is_ringing: boolean;
  is_active: boolean;
  number: string;
  name: string;
}

let isCollapsed = false;
let callerName = 'Om\'s S23 Ultra';
let callerNumber = '';
let audioRoute = 'phone_speaker'; // 'mac' | 'phone_speaker' | 'phone' | 'bluetooth'
let durationSec = 0;
let isMuted = false;
let durationTimer: any = null;

async function resizeWindow(width: number, height: number) {
  try {
    await invoke('resize_call_overlay', { width, height });
  } catch (_) {
    try {
      const win = getCurrentWindow();
      const { LogicalSize } = await import('@tauri-apps/api/window');
      await win.setSize(new LogicalSize(width, height));
    } catch (_) {}
  }
}

function formatTimer(sec: number): string {
  const m = Math.floor(sec / 60).toString().padStart(2, '0');
  const s = (sec % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

function render() {
  const root = document.getElementById('call-root');
  if (!root) return;

  const timerStr = formatTimer(durationSec);
  const initial = (callerName || 'C').substring(0, 1).toUpperCase();

  if (isCollapsed) {
    // === ONE UI FLOATING BUBBLE / MINI PILL ===
    resizeWindow(176, 48);
    root.innerHTML = `
      <div class="call-bubble-container" id="bubble-pill" title="Ongoing Call • Click to expand">
        <div class="bubble-avatar">
          ${initial}
          <div class="bubble-dot"></div>
        </div>
        <span class="bubble-timer" id="call-timer-display">${timerStr}</span>
        <button class="btn-bubble-expand" id="btn-expand-card" title="Expand Call Controls">
          ${Icons.Maximize(13, 'currentColor')}
        </button>
      </div>
    `;

    const pill = document.getElementById('bubble-pill');
    if (pill) {
      pill.addEventListener('mousedown', async (e) => {
        if ((e.target as HTMLElement).closest('#btn-expand-card')) return;
        try {
          await getCurrentWindow().startDragging();
        } catch (_) {}
      });
      pill.addEventListener('dblclick', () => {
        isCollapsed = false;
        render();
      });
    }

    const btnExpand = document.getElementById('btn-expand-card');
    if (btnExpand) {
      btnExpand.addEventListener('click', (e) => {
        e.stopPropagation();
        isCollapsed = false;
        render();
      });
    }
  } else {
    // === EXPANDED CALL POPUP CARD ===
    resizeWindow(334, 276);
    root.innerHTML = `
      <div class="call-card-container" id="call-full-card">
        <!-- Top Drag Bar & One UI Collapse Button -->
        <div class="call-drag-bar" id="drag-header-bar">
          <div class="drag-handle-pill"></div>
          <button class="btn-minimize-bubble" id="btn-collapse-bubble" title="Minimize into Floating Bubble (One UI)">
            ─
          </button>
        </div>

        <!-- Caller Header -->
        <div class="caller-header" id="caller-drag-section">
          <div class="avatar-wrapper">
            <div class="avatar-circle">${initial}</div>
            <div class="avatar-pulse"></div>
          </div>
          <div class="caller-info">
            <div class="caller-name" title="${callerName}">${callerName}</div>
            <div class="caller-sub">
              <span class="status-dot"></span>
              <span>Connected</span>
              <span>•</span>
              <span class="timer-badge" id="call-timer-display">${timerStr}</span>
            </div>
          </div>
        </div>

        <!-- 3-Way Audio Segmented Control -->
        <div style="display: flex; flex-direction: column; gap: 6px;">
          <div class="route-segmented">
            <button class="route-pill ${audioRoute === 'phone_speaker' || audioRoute === 'mac' ? 'active' : ''}" id="btn-route-speaker" title="Hands-Free Loudspeaker & Mic">
              ${Icons.Volume2(13, 'currentColor')} <span>Hands-Free</span>
            </button>
            <button class="route-pill ${audioRoute === 'phone' ? 'active' : ''}" id="btn-route-phone" title="Private Handset (Earpiece)">
              ${Icons.Phone(13, 'currentColor')} <span>Private</span>
            </button>
            <button class="route-pill ${audioRoute === 'bluetooth' ? 'active' : ''}" id="btn-route-bt" title="Connected Bluetooth Headset">
              ${Icons.Bluetooth(13, 'currentColor')} <span>Headset</span>
            </button>
          </div>
          <div class="route-status-note">
            <span>${audioRoute === 'phone' ? '📱 Private earpiece active' : audioRoute === 'bluetooth' ? '🎧 Bluetooth headset active' : '🔊 Hands-Free Loudspeaker active'}</span>
            <span style="font-size: 9px; opacity: 0.8; color: #fff;">1-Click Switch</span>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="call-actions-row">
          <button class="btn-action-tool ${isMuted ? 'muted' : ''}" id="btn-toggle-mute" title="${isMuted ? 'Unmute Microphone' : 'Mute Microphone'}">
            ${isMuted ? Icons.MicOff(15, 'currentColor') : Icons.Mic(15, 'currentColor')}
            <span>${isMuted ? 'Muted' : 'Mute'}</span>
          </button>
          <button class="btn-action-tool" id="btn-toggle-bubble-mode" title="Minimize into Floating Bubble">
            ${Icons.Minimize(14, 'currentColor')}
            <span>Pop-up</span>
          </button>
          <button class="btn-action-end" id="btn-end-call" title="End Phone Call">
            ${Icons.PhoneOff(17, '#ffffff')}
          </button>
        </div>
      </div>
    `;

    // Native window dragging on header & caller section
    const dragBar = document.getElementById('drag-header-bar');
    const dragSection = document.getElementById('caller-drag-section');
    [dragBar, dragSection].forEach((el) => {
      if (!el) return;
      el.addEventListener('mousedown', async (e) => {
        if ((e.target as HTMLElement).closest('.btn-minimize-bubble')) return;
        try {
          await getCurrentWindow().startDragging();
        } catch (_) {}
      });
    });

    // Minimize button
    const btnCollapse = document.getElementById('btn-collapse-bubble');
    if (btnCollapse) {
      btnCollapse.addEventListener('click', () => {
        isCollapsed = true;
        render();
      });
    }

    const btnBubbleMode = document.getElementById('btn-toggle-bubble-mode');
    if (btnBubbleMode) {
      btnBubbleMode.addEventListener('click', () => {
        isCollapsed = true;
        render();
      });
    }

    // Audio route buttons
    const btnSpeaker = document.getElementById('btn-route-speaker');
    const btnPhone = document.getElementById('btn-route-phone');
    const btnBt = document.getElementById('btn-route-bt');

    const switchRoute = async (newRoute: string) => {
      audioRoute = newRoute;
      render();
      try {
        await invoke('set_call_audio_route', { route: newRoute });
      } catch (err) {
        console.error('Failed to set audio route:', err);
      }
    };

    if (btnSpeaker) btnSpeaker.addEventListener('click', () => switchRoute('phone_speaker'));
    if (btnPhone) btnPhone.addEventListener('click', () => switchRoute('phone'));
    if (btnBt) btnBt.addEventListener('click', () => switchRoute('bluetooth'));

    // Mute toggle
    const btnMute = document.getElementById('btn-toggle-mute');
    if (btnMute) {
      btnMute.addEventListener('click', async () => {
        isMuted = !isMuted;
        render();
        try {
          await invoke('toggle_call_mute', { muted: isMuted });
        } catch (err) {
          console.error('Failed to toggle mute:', err);
        }
      });
    }

    // End call
    const btnEnd = document.getElementById('btn-end-call');
    if (btnEnd) {
      btnEnd.addEventListener('click', async () => {
        try {
          await invoke('end_phone_call');
          await invoke('sync_call_overlay', { visible: false });
        } catch (err) {
          console.error('Failed to end call:', err);
        }
      });
    }
  }
}

async function init() {
  render();

  if (durationTimer) clearInterval(durationTimer);
  durationTimer = setInterval(() => {
    durationSec++;
    const timerEl = document.getElementById('call-timer-display');
    if (timerEl) {
      timerEl.textContent = formatTimer(durationSec);
    }
  }, 1000);

  // Sync state from daemon
  try {
    const state: any = await invoke('get_call_state');
    if (state) {
      if (state.state === 'idle') {
        invoke('sync_call_overlay', { visible: false });
        return;
      }
      callerName = state.name || state.number || callerName;
      callerNumber = state.number || '';
      render();
    }
  } catch (_) {}

  // Real-time listener
  try {
    await listen('call_state_changed', (event: any) => {
      const state = event.payload as CallStateInfo;
      if (!state) return;
      if (state.state === 'idle') {
        invoke('sync_call_overlay', { visible: false });
      } else {
        callerName = state.name || state.number || callerName;
        callerNumber = state.number || '';
        render();
      }
    });
  } catch (_) {}

  // Periodic poll to ensure quick auto-dismiss when call ends on phone
  setInterval(async () => {
    try {
      const state: any = await invoke('get_call_state');
      if (state && state.state === 'idle') {
        invoke('sync_call_overlay', { visible: false });
      }
    } catch (_) {}
  }, 1500);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => init());
} else {
  init();
}

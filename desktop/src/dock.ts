import { invoke } from '@tauri-apps/api/core';
import { Icons } from './icons';

interface NavButtonDef {
  key: string;
  label: string;
  icon: (size: number, color?: string) => string;
  hint: string;
}

const ALL_NAV_BUTTONS: NavButtonDef[] = [
  { key: 'recents', label: 'Recents', icon: (s, c) => Icons.AndroidRecents(s, c), hint: 'Recent Apps Switcher (|||)' },
  { key: 'home', label: 'Home', icon: (s, c) => Icons.AndroidHome(s, c), hint: 'Home Screen (○)' },
  { key: 'back', label: 'Back', icon: (s, c) => Icons.AndroidBack(s, c), hint: 'Back (‹ / ESC)' },
  { key: 'notifications', label: 'Shade', icon: (s, c) => Icons.Bell(s, c), hint: 'Notifications Shade' },
  { key: 'volume_down', label: 'Vol -', icon: (s, c) => Icons.Volume1(s, c), hint: 'Volume Down' },
  { key: 'volume_up', label: 'Vol +', icon: (s, c) => Icons.Volume2(s, c), hint: 'Volume Up' },
  { key: 'screenshot', label: 'Capture', icon: (s, c) => Icons.Camera(s, c), hint: 'Take Screenshot' },
  { key: 'power', label: 'Lock', icon: (s, c) => Icons.Power(s, c || '#38b6ff'), hint: 'Lock / Sleep Phone Screen' },
];

// Default to authentic Samsung Galaxy / Android 3-Button Navigation Bar
const DEFAULT_KEYS = ['recents', 'home', 'back'];

function render() {
  const root = document.getElementById('dock-root');
  if (!root) return;

  let enabledKeys: string[] = DEFAULT_KEYS;
  try {
    const raw = localStorage.getItem('idroid_nav_buttons');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        enabledKeys = parsed;
      }
    }
  } catch (_) {}

  root.innerHTML = `
    <div class="dock-pill">
      ${enabledKeys.map((key, idx, arr) => {
        const btn = ALL_NAV_BUTTONS.find(b => b.key === key);
        if (!btn) return '';
        const isPower = btn.key === 'power';
        const color = isPower ? '#38b6ff' : '#cbd5e1';
        const isCoreNav = key === 'recents' || key === 'home' || key === 'back';
        const nextKey = arr[idx + 1];
        const nextIsTool = nextKey && !['recents', 'home', 'back'].includes(nextKey);
        const showDivider = (isCoreNav && nextIsTool) || (key === 'volume_up' && idx < arr.length - 1);
        return `
          <button class="dock-btn ${isPower ? 'power' : ''}" data-key="${btn.key}" title="${btn.hint}" type="button">
            ${btn.icon(17, color)}
          </button>
          ${showDivider ? `<div class="dock-divider"></div>` : ''}
        `;
      }).join('')}
    </div>
  `;

  root.querySelectorAll('.dock-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const key = (e.currentTarget as HTMLElement).getAttribute('data-key');
      if (key) {
        invoke('send_phone_key', { key }).catch(console.error);
      }
    });
  });
}

render();

window.addEventListener('storage', (e) => {
  if (e.key === 'idroid_nav_buttons') {
    render();
  }
});

// Modern Vector SVG Icon System (Lucide Style, 1.8px stroke, crisp geometry)

export function svgIcon(path: string, size = 18, color = 'currentColor'): string {
  return `
    <svg 
      xmlns="http://www.w3.org/2000/svg" 
      width="${size}" 
      height="${size}" 
      viewBox="0 0 24 24" 
      fill="none" 
      stroke="${color}" 
      stroke-width="1.8" 
      stroke-linecap="round" 
      stroke-linejoin="round"
      class="idroid-icon"
    >
      ${path}
    </svg>
  `.trim();
}

const rawIcons = {
  // Navigation & Workspace Icons
  Dashboard: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/>', size, color),
  
  Files: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"/>', size, color),
  
  Photos: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>', size, color),
  
  Messages: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>', size, color),
  
  Clipboard: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="8" height="4" x="8" y="2" rx="1" ry="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>', size, color),
  
  Calls: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>', size, color),
  
  Mirror: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="14" height="20" x="5" y="2" rx="2" ry="2"/><path d="M12 18h.01"/><path d="m10 6 4 4-4 4"/>', size, color),
  
  Notifications: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>', size, color),
  
  Settings: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>', size, color),

  // Hardware & Telemetry Icons
  Phone: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="14" height="20" x="5" y="2" rx="2" ry="2"/><path d="M12 18h.01"/>', size, color),
  
  Mic: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" x2="12" y1="19" y2="22"/>', size, color),

  MicOff: (size = 18, color = 'currentColor') =>
    svgIcon('<line x1="2" x2="22" y1="2" y2="22"/><path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2"/><path d="M5 10v2a7 7 0 0 0 12 5"/><path d="M15 9.34V5a3 3 0 0 0-5.68-1.33"/><path d="M9 9v3a3 3 0 0 0 5.12 2.12"/><line x1="12" x2="12" y1="19" y2="22"/>', size, color),

  Keypad: (size = 18, color = 'currentColor') =>
    svgIcon('<circle cx="6" cy="6" r="1.5"/><circle cx="12" cy="6" r="1.5"/><circle cx="18" cy="6" r="1.5"/><circle cx="6" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="18" cy="12" r="1.5"/><circle cx="6" cy="18" r="1.5"/><circle cx="12" cy="18" r="1.5"/><circle cx="18" cy="18" r="1.5"/>', size, color),

  Users: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>', size, color),

  PhoneOff: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.42 19.42 0 0 1-3.33-2.67m-2.67-3.34a19.79 19.79 0 0 1-3.07-8.63A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91"/><line x1="2" x2="22" y1="2" y2="22"/>', size, color),
  
  BatteryCharging: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M15 7h1a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-2"/><path d="M6 7H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h1"/><path d="m11 7-3 5h4l-3 5"/><line x1="22" x2="22" y1="11" y2="13"/>', size, color),
  
  Lock: (size = 14, color = 'currentColor') =>
    svgIcon('<rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>', size, color),
  
  Usb: (size = 14, color = 'currentColor') =>
    svgIcon('<circle cx="12" cy="19" r="2"/><circle cx="5" cy="8" r="1.5"/><circle cx="19" cy="8" r="1.5"/><path d="M12 17V7"/><path d="m5 9.5 7 3.5 7-3.5"/>', size, color),
  
  Wifi: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M12 20h.01"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M1.5 9.288a15 15 0 0 1 21 0"/>', size, color),

  // Quick Controls
  Moon: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>', size, color),
  
  Camera: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/><circle cx="12" cy="13" r="3"/>', size, color),
  
  Sparkles: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>', size, color),
  
  UploadCloud: (size = 20, color = 'currentColor') =>
    svgIcon('<path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/><path d="M12 12v9"/><path d="m16 16-4-4-4 4"/>', size, color),
  
  Download: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>', size, color),
  
  Check: (size = 14, color = 'currentColor') =>
    svgIcon('<polyline points="20 6 9 17 4 12"/>', size, color),
  
  Play: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="6 3 20 12 6 21 6 3"/>', size, color),
  
  Refresh: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>', size, color),

  ChevronRight: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="m9 18 6-6-6-6"/>', size, color),

  ChevronDown: (size = 12, color = 'currentColor') =>
    svgIcon('<path d="m6 9 6 6 6-6"/>', size, color),
  
  Laptop: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M20 16V7a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v9m16 0H4m16 0 1.28 2.55a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45L4 16"/>', size, color),
  
  Radio: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M4.9 19.1C1 15.2 1 8.8 4.9 4.9"/><path d="M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5"/><circle cx="12" cy="12" r="2"/><path d="M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5"/><path d="M19.1 4.9C23 8.8 23 15.1 19.1 19"/>', size, color),

  Home: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>', size, color),

  ArrowLeft: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>', size, color),

  Layers: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>', size, color),

  Power: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M12 2v10"/><path d="M18.4 6.6a9 9 0 1 1-12.77.04"/>', size, color),

  Search: (size = 14, color = 'currentColor') =>
    svgIcon('<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>', size, color),

  Cpu: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="16" height="16" x="4" y="4" rx="2"/><rect width="6" height="6" x="9" y="9" rx="1"/><path d="M15 2v2"/><path d="M15 20v2"/><path d="M2 15h2"/><path d="M2 9h2"/><path d="M20 15h2"/><path d="M20 9h2"/><path d="M9 2v2"/><path d="M9 20v2"/>', size, color),

  Compass: (size = 18, color = 'currentColor') =>
    svgIcon('<circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/>', size, color),

  Globe: (size = 16, color = 'currentColor') =>
    svgIcon('<circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/>', size, color),

  QrCode: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="5" height="5" x="3" y="3" rx="1"/><rect width="5" height="5" x="16" y="3" rx="1"/><rect width="5" height="5" x="3" y="16" rx="1"/><path d="M21 16h-3a2 2 0 0 0-2 2v3"/><path d="M21 21v.01"/><path d="M12 7v3a2 2 0 0 1-2 2H7"/><path d="M3 12h.01"/><path d="M12 3h.01"/><path d="M12 16v.01"/><path d="M16 12h1"/><path d="M21 12v.01"/><path d="M12 21v-1"/>', size, color),

  ShieldCheck: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>', size, color),

  ArrowRightLeft: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m16 3 4 4-4 4"/><path d="M20 7H4"/><path d="m8 21-4-4 4-4"/><path d="M4 17h16"/>', size, color),

  Copy: (size = 14, color = 'currentColor') =>
    svgIcon('<rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>', size, color),

  Bell: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>', size, color),

  Computer: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="16" height="12" x="4" y="3" rx="2"/><line x1="8" x2="16" y1="21" y2="21"/><line x1="12" x2="12" y1="17" y2="21"/>', size, color),

  Monitor: (size = 18, color = 'currentColor') =>
    svgIcon('<rect width="20" height="14" x="2" y="3" rx="2"/><line x1="8" x2="16" y1="21" y2="21"/><line x1="12" x2="12" y1="17" y2="21"/>', size, color),

  Folder: (size = 18, color = 'currentColor') =>
    svgIcon('<path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"/>', size, color),

  Grid: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/>', size, color),

  Volume1: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>', size, color),

  Volume2: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>', size, color),

  Sliders: (size = 16, color = 'currentColor') =>
    svgIcon('<line x1="4" x2="4" y1="21" y2="14"/><line x1="4" x2="4" y1="10" y2="3"/><line x1="12" x2="12" y1="21" y2="12"/><line x1="12" x2="12" y1="8" y2="3"/><line x1="20" x2="20" y1="21" y2="16"/><line x1="20" x2="20" y1="12" y2="3"/><line x1="1" x2="7" y1="14" y2="14"/><line x1="9" x2="15" y1="8" y2="8"/><line x1="17" x2="23" y1="16" y2="16"/>', size, color),

  Edit: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>', size, color),

  Close: (size = 14, color = 'currentColor') =>
    svgIcon('<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>', size, color),

  X: (size = 14, color = 'currentColor') =>
    svgIcon('<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>', size, color),

  VolumeUp: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>', size, color),

  VolumeMute: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><line x1="22" x2="16" y1="9" y2="15"/><line x1="16" x2="22" y1="9" y2="15"/>', size, color),

  MediaPlayPause: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="5 4 15 12 5 20 5 4"/><line x1="19" y1="5" x2="19" y2="19"/>', size, color),

  SkipForward: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="5 4 15 12 5 20 5 4"/><line x1="19" y1="5" x2="19" y2="19"/>', size, color),

  PanelLeft: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/>', size, color),

  PanelLeftClose: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/><path d="m14 9-3 3 3 3"/>', size, color),

  PanelLeftOpen: (size = 16, color = 'currentColor') =>
    svgIcon('<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/><path d="m12 15 3-3-3-3"/>', size, color),

  Plus: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M5 12h14"/><path d="M12 5v14"/>', size, color),

  Bluetooth: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m7 7 10 10-5 5V2l5 5L7 17"/>', size, color),

  BluetoothConnected: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="m7 7 10 10-5 5V2l5 5L7 17"/><line x1="18" x2="21" y1="12" y2="12"/><line x1="3" x2="6" y1="12" y2="12"/>', size, color),

  Doctor: (size = 16, color = 'currentColor') =>
    svgIcon('<path d="M4.8 2.3A.3.3 0 1 0 5 2H4a2 2 0 0 0-2 2v5a6 6 0 0 0 6 6v0a6 6 0 0 0 6-6V4a2 2 0 0 0-2-2h-1a.2.2 0 1 0 .3.3"/><path d="M8 15v1a6 6 0 0 0 6 6v0a6 6 0 0 0 6-6v-4"/><circle cx="20" cy="10" r="2"/>', size, color),

  HelpCircle: (size = 16, color = 'currentColor') =>
    svgIcon('<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/>', size, color),

  Zap: (size = 16, color = 'currentColor') =>
    svgIcon('<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>', size, color),

  // Authentic Android Native Navigation Bar Icons (Samsung One UI & Material)
  AndroidBack: (size = 18, color = 'currentColor') =>
    svgIcon('<polyline points="15 6 9 12 15 18"/>', size, color),

  AndroidHome: (size = 18, color = 'currentColor') =>
    svgIcon('<circle cx="12" cy="12" r="6"/>', size, color),

  AndroidRecents: (size = 18, color = 'currentColor') =>
    svgIcon('<line x1="7" y1="5" x2="7" y2="19"/><line x1="12" y1="5" x2="12" y2="19"/><line x1="17" y1="5" x2="17" y2="19"/>', size, color),

  AndroidRecentsSquare: (size = 18, color = 'currentColor') =>
    svgIcon('<rect x="5.5" y="5.5" width="13" height="13" rx="3"/>', size, color),

  Pin: (size = 16, color = 'currentColor') =>
    svgIcon('<line x1="12" y1="17" x2="12" y2="22"/><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a1 1 0 0 0 0-2H8a1 1 0 0 0 0 2h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/>', size, color),

  PinFilled: (size = 16, color = 'currentColor') =>
    svgIcon('<line x1="12" y1="17" x2="12" y2="22"/><path fill="currentColor" d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a1 1 0 0 0 0-2H8a1 1 0 0 0 0 2h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/>', size, color),

  Trash: (size = 16, color = 'currentColor') =>
    svgIcon('<polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/>', size, color),

  Eye: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>', size, color),

  EyeOff: (size = 14, color = 'currentColor') =>
    svgIcon('<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/>', size, color),

  BrandLogo: (size = 28) => `
    <img src="/favicon.png" width="${size}" height="${size}" alt="Idroid" class="idroid-brand-logo" style="width: ${size}px; height: ${size}px; max-width: ${size}px; max-height: ${size}px; display: inline-block; vertical-align: middle; object-fit: contain;" />
  `.trim(),
};

// Safe proxy to prevent any missing icon from ever throwing an uncaught TypeError
export const Icons: Record<string, (size?: number, color?: string) => string> = new Proxy(rawIcons, {
  get(target, prop: string) {
    if (prop in target) {
      return (target as any)[prop];
    }
    // Return a safe circular dot icon fallback
    return (size = 16, color = 'currentColor') =>
      svgIcon('<circle cx="12" cy="12" r="6"/>', size, color);
  },
});




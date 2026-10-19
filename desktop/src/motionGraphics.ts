// Idroid Motion Graphics & Tactile Interaction System

// MARK: - Click Ripple Motion Graphic
export function attachGlobalClickRipples() {
  document.addEventListener('click', (e) => {
    const target = e.target as HTMLElement;
    const clickable = target.closest<HTMLElement>(
      'button:not(.btn-launch-app), .nav-item, .keypad-btn'
    );
    if (!clickable) return;

    const rect = clickable.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ripple = document.createElement('span');
    ripple.className = 'motion-ripple';
    ripple.style.left = `${x}px`;
    ripple.style.top = `${y}px`;

    clickable.style.position = clickable.style.position || 'relative';
    clickable.style.overflow = 'hidden';
    clickable.appendChild(ripple);

    setTimeout(() => {
      ripple.remove();
    }, 600);
  });
}

// MARK: - App Launching Motion Graphic HUD
export function showAppLaunchHUD(appName: string, iconSrc?: string) {
  const existing = document.getElementById('idroid-launch-hud');
  if (existing) existing.remove();

  const hud = document.createElement('div');
  hud.id = 'idroid-launch-hud';
  hud.className = 'launch-hud-container';
  hud.innerHTML = `
    <div class="launch-hud-card" style="padding: 10px 16px; min-width: 220px; flex-direction: row; align-items: center; gap: 12px;">
      ${
        iconSrc && iconSrc.startsWith('data:')
          ? `<img src="${iconSrc}" class="launch-hud-icon" style="width: 26px; height: 26px; border-radius: 6px; object-fit: contain;" alt="${appName}" />`
          : `<div style="width: 10px; height: 10px; border-radius: 50%; background: var(--accent-solar); box-shadow: 0 0 8px var(--accent-solar);"></div>`
      }
      <div style="display: flex; flex-direction: column;">
        <div style="font-size: 13px; font-weight: 600; color: #fff;">${appName}</div>
        <div style="font-size: 11px; color: var(--text-secondary);">Streaming...</div>
      </div>
    </div>
  `;

  document.body.appendChild(hud);

  setTimeout(() => {
    hud.classList.add('hud-closing');
    setTimeout(() => hud.remove(), 250);
  }, 900);
}

// MARK: - Clean Natural App Pop
export function attachTactileCardPhysics(selector = '.app-tile') {
  const cards = document.querySelectorAll<HTMLElement>(selector);

  cards.forEach((card) => {
    card.addEventListener('mouseenter', () => {
      card.style.transform = 'translateY(-4px) scale(1.06)';
      card.style.transition = 'transform 0.18s cubic-bezier(0.16, 1, 0.3, 1)';
    });

    card.addEventListener('mouseleave', () => {
      card.style.transform = 'translateY(0) scale(1)';
      card.style.transition = 'transform 0.25s cubic-bezier(0.16, 1, 0.3, 1)';
    });
  });
}

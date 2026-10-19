# Creation Gotchas

Common failure modes and AI pitfalls when writing UI animation code. Always self-check against this list before presenting motion code to the user.

---

## 1. Animating Layout Properties
- **Gotcha**: Animating `height`, `width`, `top`, `left`, `margin`, `padding`.
- **Why it fails**: Forces continuous browser layout recalculation (reflow) and repaint on every frame, causing dropped frames (jank), especially on mobile.
- **Fix**: Animate `transform` (`scale`, `translate`), `opacity`, and `filter`. If animating height is unavoidable (accordions/dropdowns), use CSS grid `grid-template-rows: 0fr` to `1fr` or `clip-path`.

---

## 2. Animating from `scale(0)`
- **Gotcha**: Popups, dialogs, or dropdowns animated with `initial={{ scale: 0 }}`.
- **Why it fails**: Unnatural "pinpoint explosion" effect that feels like 2012 Android/Flash.
- **Fix**: Gentle initial scale: `scale: 0.95` (or `0.92` to `0.98`) combined with opacity.

---

## 3. Omitting `prefers-reduced-motion`
- **Gotcha**: Generating pure motion without wrapping or accommodating reduced motion preferences.
- **Why it fails**: Inaccessible, fails basic design audits, causes nausea for vestibular disorder users.
- **Fix**: Always include media query in CSS or `useReducedMotion()` in React/Framer Motion.

---

## 4. Default Easing Everywhere
- **Gotcha**: Relying on bare `transition: all 0.3s ease`.
- **Why it fails**: Weak acceleration curves look amateurish and sluggish.
- **Fix**: Use custom Bézier curves (e.g. `cubic-bezier(0.16, 1, 0.3, 1)`) or springs with zero bounce (`bounce: 0`).

---

## 5. Exit Animations as Heavy as Entrance
- **Gotcha**: Copy-pasting the enter animation directly into `exit={{ ... }}`.
- **Why it fails**: When an element is leaving, the user's attention has already moved on. Symmetrical exit animations feel clunky and slow the user down.
- **Fix**: Make exits faster (~60-70% duration of entrance) and subtler (smaller translate delta, simple fade).

---

## 6. Staggering Every List by Default
- **Gotcha**: Slapping `staggerChildren: 0.05` across every settings list, dropdown, or search result.
- **Why it fails**: Turns a simple lookup into an agonizing 1-second delay before the user can click their desired row.
- **Fix**: Restrict stagger to celebratory moments, onboarding flows, or empty state reveals. Never stagger frequent utility lists.

---

## 7. Pulsing / Continuous Loop Fatigue
- **Gotcha**: Adding infinite breathing glows or pulsing dots to buttons or badges.
- **Why it fails**: Distracts the peripheral vision, drains battery, and reads as "AI slop".
- **Fix**: High-contrast static badge or a single subtle entrance flash.
